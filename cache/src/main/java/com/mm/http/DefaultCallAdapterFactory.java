package com.mm.http;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okio.Timeout;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.SkipCallbackExecutor;

/**
 * Created by : majian
 * Date : 2021/8/24
 */

public class DefaultCallAdapterFactory extends CacheCallAdapter.Factory {
    private final Executor callbackExecutor;

    DefaultCallAdapterFactory(Executor callbackExecutor) {
        this.callbackExecutor = callbackExecutor;
    }

    @Override
    public @Nullable
    CacheCallAdapter<?, ?> get(Type returnType, Annotation[] annotations, RetrofitCache retrofit) {
        if (getRawType(returnType) != Call.class) {
            return null;
        }
        if (!(returnType instanceof ParameterizedType)) {
            throw new IllegalArgumentException(
                    "Call return type must be parameterized as Call<Foo> or Call<? extends Foo>");
        }
        final Type responseType = getParameterUpperBound(0, (ParameterizedType) returnType);

        final Executor executor = Utils.isAnnotationPresent(annotations, SkipCallbackExecutor.class)
                ? null : callbackExecutor;

        return new CacheCallAdapter<Object, Call<?>>() {
            @Override
            public Type responseType() {
                return responseType;
            }

            @Override
            public Call<Object> adapt(Call<Object> call) {
                return executor == null ? call : new ExecutorCallbackCall<>(executor, call);
            }

            @Override
            public okhttp3.Call rawCall(Class<?> service, Method method, Object[] args) {
                Object ser = retrofit.createService(service);
                try {
                    // 删除无效map数据,防止retrofit报错
                    for (Object arg : args) {
                        if (arg instanceof Map) {
                            Iterator<? extends Map.Entry<?, ?>> iterator = ((Map<?, ?>) arg).entrySet().iterator();
                            while (iterator.hasNext()) {
                                Map.Entry<?, ?> entry = iterator.next();
                                if (entry.getKey() == null || entry.getValue() == null) {
                                    iterator.remove();
                                }
                            }
                        }
                    }
                    Object invoke = method.invoke(ser, args);
                    Call<?> call = (Call<?>) invoke;
                    Request request = getRawCallWithInterceptorChain(retrofit, call.request());
                    return retrofit.callFactory().newCall(request);
                } catch (Exception e) {
                    throw new IllegalArgumentException(Log.getStackTraceString(e));
                }
            }
        };
    }

    /**
     * getRawCall from interceptors
     *
     * @param retrofit
     * @param request
     * @return rawRequest
     * @throws IOException
     */
    private Request getRawCallWithInterceptorChain(RetrofitCache retrofit, Request request) throws IOException {
        OkHttpClient client = retrofit.callFactory();
        List<Interceptor> interceptors = client.interceptors();
        int connectTimeoutMillis = client.connectTimeoutMillis();
        int readTimeoutMillis = client.readTimeoutMillis();
        int writeTimeoutMillis = client.writeTimeoutMillis();
        RealRequestInterceptorChain chain = new RealRequestInterceptorChain(client.newCall(request), retrofit.cache, interceptors, 0, request, connectTimeoutMillis, readTimeoutMillis, writeTimeoutMillis);
        return chain.proceed(request).request();
    }


    static final class ExecutorCallbackCall<T> implements Call<T> {
        final Executor callbackExecutor;
        final Call<T> delegate;

        ExecutorCallbackCall(Executor callbackExecutor, Call<T> delegate) {
            this.callbackExecutor = callbackExecutor;
            this.delegate = delegate;
        }

        @Override
        public void enqueue(@NonNull final Callback<T> callback) {
            Objects.requireNonNull(callback, "callback == null");
            delegate.enqueue(new Callback<T>() {
                @Override
                public void onResponse(@NonNull Call<T> call, @NonNull final Response<T> response) {
                    callbackExecutor.execute(() -> {
                        if (delegate.isCanceled()) {
                            callback.onFailure(ExecutorCallbackCall.this, new IOException("Canceled"));
                        } else {
                            callback.onResponse(ExecutorCallbackCall.this, response);
                        }
                    });
                }

                @Override
                public void onFailure(@NonNull Call<T> call, @NonNull final Throwable t) {
                    callbackExecutor.execute(() -> callback.onFailure(ExecutorCallbackCall.this, t));
                }
            });
        }

        @Override
        public boolean isExecuted() {
            return delegate.isExecuted();
        }

        @Override
        public Response<T> execute() throws IOException {
            return delegate.execute();
        }

        @Override
        public void cancel() {
            delegate.cancel();
        }

        @Override
        public boolean isCanceled() {
            return delegate.isCanceled();
        }

        @SuppressWarnings("CloneDoesntCallSuperClone") // Performing deep clone.
        @Override
        public Call<T> clone() {
            return new ExecutorCallbackCall<>(callbackExecutor, delegate.clone());
        }

        @Override
        public Request request() {
            return delegate.request();
        }

        @Override
        public Timeout timeout() {
            return delegate.timeout();
        }
    }
}
