package com.mm.http;

import static java.util.Collections.unmodifiableList;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.mm.http.cache.CacheConverter;
import com.mm.http.cache.CacheHelper;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Created by : majian
 * Date : 2021/8/24
 */

public class RetrofitCache {
    private final Map<Method, ServiceMethod<?>> serviceMethodCache = new ConcurrentHashMap<>();
    private final List<Converter.Factory> converterFactories;
    private final List<CacheConverter.Factory> cacheConverterFactories;
    private final List<CacheCallAdapter.Factory> callAdapterFactories;
    @Nullable
    private final Executor callbackExecutor;
    private final boolean validateEagerly;
    protected final CacheHelper cache;
    private final Retrofit.Builder retrofitBuilder;
    private final OkHttpClient okHttpClient;

    RetrofitCache(
            OkHttpClient okHttpClient,
            Retrofit.Builder retrofitBuilder,
            CacheHelper cache,
            List<Converter.Factory> converterFactories,
            List<CacheConverter.Factory> cacheConverterFactories,
            List<CacheCallAdapter.Factory> callAdapterFactories,
            @Nullable Executor callbackExecutor,
            boolean validateEagerly) {
        this.converterFactories = converterFactories; // Copy+unmodifiable at call site.
        this.cacheConverterFactories = cacheConverterFactories; // Copy+unmodifiable at call site.
        this.callAdapterFactories = callAdapterFactories; // Copy+unmodifiable at call site.
        this.callbackExecutor = callbackExecutor;
        this.validateEagerly = validateEagerly;
        this.cache = cache;
        this.retrofitBuilder = retrofitBuilder;
        this.okHttpClient = okHttpClient;
    }


    @SuppressWarnings("unchecked") // Single-interface proxy creation guarded by parameter safety.
    public <T> T create(final Class<T> service) {
        validateServiceInterface(service);
        return (T)
                Proxy.newProxyInstance(
                        service.getClassLoader(),
                        new Class<?>[]{service},
                        new InvocationHandler() {
                            private final Object[] emptyArgs = new Object[0];

                            @Override
                            public @Nullable
                            Object invoke(Object proxy, Method method, @Nullable Object[] args)
                                    throws Throwable {
                                // If the method is a method from Object then defer to normal invocation.
                                if (method.getDeclaringClass() == Object.class) {
                                    return method.invoke(this, args);
                                }
                                args = args != null ? args : emptyArgs;
                                return loadServiceMethod(service, method).invoke(args);
                            }
                        });
    }

    private void validateServiceInterface(Class<?> service) {
        if (!service.isInterface()) {
            throw new IllegalArgumentException("API declarations must be interfaces.");
        }

        Deque<Class<?>> check = new ArrayDeque<>(1);
        check.add(service);
        while (!check.isEmpty()) {
            Class<?> candidate = check.removeFirst();
            if (candidate.getTypeParameters().length != 0) {
                StringBuilder message =
                        new StringBuilder("Type parameters are unsupported on ").append(candidate.getName());
                if (candidate != service) {
                    message.append(" which is an interface of ").append(service.getName());
                }
                throw new IllegalArgumentException(message.toString());
            }
            Collections.addAll(check, candidate.getInterfaces());
        }

        if (validateEagerly) {
            for (Method method : service.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers())) {
                    loadServiceMethod(service, method);
                }
            }
        }
    }

    ServiceMethod<?> loadServiceMethod(Class<?> service, Method method) {
        ServiceMethod<?> result = serviceMethodCache.get(method);
        if (result != null) return result;

        synchronized (serviceMethodCache) {
            result = serviceMethodCache.get(method);
            if (result == null) {
                result = ServiceMethod.parseAnnotations(this, service, method);
                serviceMethodCache.put(method, result);
            }
        }
        return result;
    }

    /**
     * Returns a list of the factories tried when creating a {@linkplain #callAdapter(Type,
     * Annotation[])} call adapter}.
     */
    public List<CacheCallAdapter.Factory> callAdapterFactories() {
        return callAdapterFactories;
    }

    /**
     * Returns the {@link CacheCallAdapter} for {@code returnType} from the available {@linkplain
     * #callAdapterFactories() factories}.
     *
     * @throws IllegalArgumentException if no call adapter available for {@code type}.
     */
    public CacheCallAdapter<?, ?> callAdapter(Type returnType, Annotation[] annotations) {
        return nextCallAdapter(null, returnType, annotations);
    }

    /**
     * Returns the {@link CacheCallAdapter} for {@code returnType} from the available {@linkplain
     * #callAdapterFactories() factories} except {@code skipPast}.
     *
     * @throws IllegalArgumentException if no call adapter available for {@code type}.
     */
    public CacheCallAdapter<?, ?> nextCallAdapter(
            @Nullable CacheCallAdapter.Factory skipPast, Type returnType, Annotation[] annotations) {
        Objects.requireNonNull(returnType, "returnType == null");
        Objects.requireNonNull(annotations, "annotations == null");

        int start = callAdapterFactories.indexOf(skipPast) + 1;
        for (int i = start, count = callAdapterFactories.size(); i < count; i++) {
            CacheCallAdapter<?, ?> adapter = callAdapterFactories.get(i).get(returnType, annotations, this);
            if (adapter != null) {
                return adapter;
            }
        }

        StringBuilder builder =
                new StringBuilder("Could not locate call adapter for ").append(returnType).append(".\n");
        if (skipPast != null) {
            builder.append("  Skipped:");
            for (int i = 0; i < start; i++) {
                builder.append("\n   * ").append(callAdapterFactories.get(i).getClass().getName());
            }
            builder.append('\n');
        }
        builder.append("  Tried:");
        for (int i = start, count = callAdapterFactories.size(); i < count; i++) {
            builder.append("\n   * ").append(callAdapterFactories.get(i).getClass().getName());
        }
        throw new IllegalArgumentException(builder.toString());
    }

    public List<Converter.Factory> converterFactories() {
        return converterFactories;
    }

    /**
     * for create {@link Retrofit}
     *
     * @return return {@link Retrofit.Builder}
     */
    public Retrofit.Builder getRetrofitBuilder() {
        return retrofitBuilder;
    }

    /**
     * create retrofit service
     *
     * @param clazz
     * @return return service
     */
    public <T> T createService(Class<T> clazz) {
        if (clazz.getAnnotation(HOST.class) == null || TextUtils.isEmpty(clazz.getAnnotation(HOST.class).value())) {
            throw new IllegalArgumentException("请指定相应的host参数 HOST.class value");
        }
        String host = clazz.getAnnotation(HOST.class).value();
        return retrofitBuilder.baseUrl(host).build().create(clazz);
    }

    /**
     * The factory used to create {@linkplain okhttp3.Call OkHttp calls} for sending a HTTP requests.
     * Typically an instance of {@link OkHttpClient}.
     */
    public OkHttpClient callFactory() {
        return okHttpClient;
    }

    /**
     * Returns a {@link Converter} for {@code type} to {@link RequestBody} from the available
     * {@linkplain #converterFactories() factories} except {@code skipPast}.
     *
     * @throws IllegalArgumentException if no converter available for {@code type}.
     */
    public <T> Converter<T, RequestBody> nextRequestBodyConverter(
            @Nullable Converter.Factory skipPast,
            Type type,
            Annotation[] parameterAnnotations,
            Annotation[] methodAnnotations) {
        Objects.requireNonNull(type, "type == null");
        Objects.requireNonNull(parameterAnnotations, "parameterAnnotations == null");
        Objects.requireNonNull(methodAnnotations, "methodAnnotations == null");

        int start = converterFactories.indexOf(skipPast) + 1;
        for (int i = start, count = converterFactories.size(); i < count; i++) {
            Converter.Factory factory = converterFactories.get(i);
            Converter<?, RequestBody> converter =
                    factory.requestBodyConverter(type, parameterAnnotations, methodAnnotations, this);
            if (converter != null) {
                //noinspection unchecked
                return (Converter<T, RequestBody>) converter;
            }
        }

        StringBuilder builder =
                new StringBuilder("Could not locate RequestBody converter for ").append(type).append(".\n");
        if (skipPast != null) {
            builder.append("  Skipped:");
            for (int i = 0; i < start; i++) {
                builder.append("\n   * ").append(converterFactories.get(i).getClass().getName());
            }
            builder.append('\n');
        }
        builder.append("  Tried:");
        for (int i = start, count = converterFactories.size(); i < count; i++) {
            builder.append("\n   * ").append(converterFactories.get(i).getClass().getName());
        }
        throw new IllegalArgumentException(builder.toString());
    }

    /**
     * Returns a {@link Converter} for {@link ResponseBody} to {@code type} from the available
     * {@linkplain #converterFactories() factories}.
     *
     * @throws IllegalArgumentException if no converter available for {@code type}.
     */
    public <T> Converter<ResponseBody, T> responseBodyConverter(Type type, Annotation[] annotations) {
        return nextResponseBodyConverter(null, type, annotations);
    }

    /**
     * Returns a {@link Converter} for {@link ResponseBody} to {@code type} from the available
     * {@linkplain #converterFactories() factories} except {@code skipPast}.
     *
     * @throws IllegalArgumentException if no converter available for {@code type}.
     */
    public <T> Converter<ResponseBody, T> nextResponseBodyConverter(
            @Nullable Converter.Factory skipPast, Type type, Annotation[] annotations) {
        Objects.requireNonNull(type, "type == null");
        Objects.requireNonNull(annotations, "annotations == null");

        int start = converterFactories.indexOf(skipPast) + 1;
        for (int i = start, count = converterFactories.size(); i < count; i++) {
            Converter<ResponseBody, ?> converter =
                    converterFactories.get(i).responseBodyConverter(type, annotations, this);
            if (converter != null) {
                //noinspection unchecked
                return (Converter<ResponseBody, T>) converter;
            }
        }

        StringBuilder builder =
                new StringBuilder("Could not locate ResponseBody converter for ")
                        .append(type)
                        .append(".\n");
        if (skipPast != null) {
            builder.append("  Skipped:");
            for (int i = 0; i < start; i++) {
                builder.append("\n   * ").append(converterFactories.get(i).getClass().getName());
            }
            builder.append('\n');
        }
        builder.append("  Tried:");
        for (int i = start, count = converterFactories.size(); i < count; i++) {
            builder.append("\n   * ").append(converterFactories.get(i).getClass().getName());
        }
        throw new IllegalArgumentException(builder.toString());
    }

    /**
     * Returns a {@link CacheConverter} for {@link ResponseBody} to {@code type} from the available
     * {@linkplain #converterFactories() factories}.
     *
     * @throws IllegalArgumentException if no converter available for {@code type}.
     */
    public <T> CacheConverter<T> responseCacheConverter(Type type) {
        return nextCacheResponseConverter(null, type);
    }

    private <T> CacheConverter<T> nextCacheResponseConverter(@Nullable CacheConverter.Factory skipPast, Type type) {

        int start = cacheConverterFactories.indexOf(skipPast) + 1;
        for (int i = start, count = cacheConverterFactories.size(); i < count; i++) {
            CacheConverter<?> converter =
                    cacheConverterFactories.get(i).converterCache(this);
            if (converter != null) {
                //noinspection unchecked
                return (CacheConverter<T>) converter;
            }
        }

        StringBuilder builder =
                new StringBuilder("Could not locate Cache converter for ")
                        .append(type)
                        .append(".\n");
        if (skipPast != null) {
            builder.append("  Skipped:");
            for (int i = 0; i < start; i++) {
                builder.append("\n   * ").append(converterFactories.get(i).getClass().getName());
            }
            builder.append('\n');
        }
        builder.append("  Tried:");
        for (int i = start, count = converterFactories.size(); i < count; i++) {
            builder.append("\n   * ").append(converterFactories.get(i).getClass().getName());
        }
        throw new IllegalArgumentException(builder.toString());
    }


    /**
     * The executor used for {@link Callback} methods on a {@link Call}. This may be {@code null}, in
     * which case callbacks should be made synchronously on the background thread.
     */
    @Nullable
    public Executor callbackExecutor() {
        return callbackExecutor;
    }

    public Builder newBuilder() {
        return new Builder(this);
    }


    public static final class Builder {
        private CacheHelper cache;
        private final List<Converter.Factory> converterFactories = new ArrayList<>();
        private final List<CacheConverter.Factory> cacheConverterFactories = new ArrayList<>();
        private final List<CacheCallAdapter.Factory> callAdapterFactories = new ArrayList<>();
        private final List<Interceptor> interceptors = new ArrayList<>();
        @Nullable
        private Executor callbackExecutor;
        private boolean validateEagerly;
        private Retrofit.Builder builder;
        @Nullable
        private OkHttpClient okHttpClient;

        public Builder() {
        }

        public Builder(RetrofitCache retrofitCache) {
            cache = retrofitCache.cache;
            builder = retrofitCache.retrofitBuilder;
            okHttpClient = retrofitCache.okHttpClient;

            // Do not add the default CacheGsonConverterFactory.
            for (int i = 0, size = retrofitCache.converterFactories.size() - 1; i < size; i++) {
                converterFactories.add(retrofitCache.converterFactories.get(i));
            }

            // Do not add the default DefaultCallAdapterFactory.
            for (int i = 0, size = retrofitCache.callAdapterFactories.size() - 1; i < size; i++) {
                callAdapterFactories.add(retrofitCache.callAdapterFactories.get(i));
            }

            // Do not add the default DefaultCacheConverterFactory.
            for (int i = 0, size = retrofitCache.cacheConverterFactories.size() - 1; i < size; i++) {
                cacheConverterFactories.add(retrofitCache.cacheConverterFactories.get(i));
            }

            callbackExecutor = retrofitCache.callbackExecutor;
            validateEagerly = retrofitCache.validateEagerly;
        }

        /**
         * use {@link Builder#build()} before set {@link CacheHelper}
         * the cache be required
         */
        public Builder cache(CacheHelper cache) {
            this.cache = Objects.requireNonNull(cache, "cache == null");
            return this;
        }

        /**
         * The HTTP client used for requests.
         */
        public Builder client(OkHttpClient client) {
            this.okHttpClient = Objects.requireNonNull(client, "client == null");
            return this;
        }

        /**
         * Add converter factory for serialization and deserialization of objects.
         */
        public Builder addConverterFactory(Converter.Factory factory) {
            converterFactories.add(Objects.requireNonNull(factory, "factory == null"));
            return this;
        }


        /**
         * Add cache converter factory for serialization and deserialization of objects.
         */
        public Builder addCacheConverterFactory(CacheConverter.Factory factory) {
            cacheConverterFactories.add(Objects.requireNonNull(factory, "factory == null"));
            return this;
        }

        /**
         * Add a call adapter factory for supporting service method return types other than {@link
         * Call}.
         */
        public Builder addCallAdapterFactory(CacheCallAdapter.Factory factory) {
            callAdapterFactories.add(Objects.requireNonNull(factory, "factory == null"));
            return this;
        }

        /**
         * The executor on which {@link Callback} methods are invoked when returning {@link Call} from
         * your service method.
         *
         * <p>Note: {@code executor} is not used for {@linkplain #addCallAdapterFactory custom method
         * return types}.
         */
        public Builder callbackExecutor(Executor executor) {
            this.callbackExecutor = Objects.requireNonNull(executor, "executor == null");
            return this;
        }

        /**
         * Returns a modifiable list of call adapter factories.
         */
        public List<CacheCallAdapter.Factory> callAdapterFactories() {
            return this.callAdapterFactories;
        }

        /**
         * Returns a modifiable list of converter factories.
         */
        public List<Converter.Factory> converterFactories() {
            return this.converterFactories;
        }

        /**
         * When calling {@link #create} on the resulting {@link RetrofitCache} instance, eagerly validate the
         * configuration of all methods in the supplied interface.
         */
        public Builder validateEagerly(boolean validateEagerly) {
            this.validateEagerly = validateEagerly;
            return this;
        }

        /**
         * add {@link Interceptor} for default HTTP client
         */
        public Builder addInterceptor(Interceptor interceptor) {
            interceptors.add(Objects.requireNonNull(interceptor, "interceptor == null"));
            return this;
        }

        /**
         * Create the {@link RetrofitCache} instance using the configured values.
         */
        public RetrofitCache build() {
            if (cache == null) {
                throw new IllegalStateException("cache be required.");
            }

            Executor callbackExecutor = this.callbackExecutor;
            if (callbackExecutor == null) {
                callbackExecutor = new MainThreadExecutor();
            }

            // Make a defensive copy of the adapters and add the default Call adapter.
            List<CacheCallAdapter.Factory> callAdapterFactories = new ArrayList<>(this.callAdapterFactories);
            callAdapterFactories.add(new DefaultCallAdapterFactory(callbackExecutor));
            List<Converter.Factory> converterFactories = new ArrayList<>(this.converterFactories);
            converterFactories.add(CacheGsonConverterFactory.create());
            List<CacheConverter.Factory> cacheConverterFactories = new ArrayList<>(this.cacheConverterFactories);
            cacheConverterFactories.add(new DefaultCacheConverterFactory());
            if (okHttpClient == null) {
                OkHttpClient.Builder builder = new OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .addInterceptor(new TimeOutInterceptor())
                        .sslSocketFactory(SslSocketFactory.getSSLSocketFactory(), SslSocketFactory.getTrustManager());
                for (Interceptor interceptor : interceptors) {
                    builder.addInterceptor(interceptor);
                }
                okHttpClient = builder.build();
            }

            builder = new Retrofit.Builder().client(okHttpClient).addConverterFactory(GsonConverterFactory.create());

            return new RetrofitCache(
                    okHttpClient,
                    builder,
                    cache,
                    unmodifiableList(converterFactories),
                    unmodifiableList(cacheConverterFactories),
                    unmodifiableList(callAdapterFactories),
                    callbackExecutor,
                    validateEagerly);
        }
    }

    static final class MainThreadExecutor implements Executor {
        private final Handler handler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(Runnable r) {
            handler.post(r);
        }
    }
}
