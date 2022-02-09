package com.mm.http;


import com.mm.http.cache.CacheConverter;
import com.mm.http.cache.CacheHelper;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

import javax.annotation.Nullable;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Created by : majian
 * Date : 2021/8/24
 */

abstract class HttpServiceMethod<ResponseT, ReturnT> extends ServiceMethod<ReturnT> {

    HttpServiceMethod(
            RequestFactory requestFactory,
            CacheCallAdapter<ResponseT, ReturnT> callAdapter,
            Converter<ResponseBody, ResponseT> responseConverter,
            CacheConverter<ResponseT> cacheConverter,
            CacheHelper cache) {
        this.requestFactory = requestFactory;
        this.responseConverter = responseConverter;
        this.cacheConverter = cacheConverter;
        this.callAdapter = callAdapter;
        this.cache = cache;
    }

    private final RequestFactory requestFactory;
    private final CacheCallAdapter<ResponseT, ReturnT> callAdapter;
    private final Converter<ResponseBody, ResponseT> responseConverter;
    private final CacheConverter<ResponseT> cacheConverter;
    private final CacheHelper cache;

    static <ResponseT, ReturnT> HttpServiceMethod<ResponseT, ReturnT> parseAnnotations(RetrofitCache retrofit, Method method, RequestFactory requestFactory) {
        Annotation[] annotations = method.getAnnotations();
        Type adapterType = method.getGenericReturnType();

        CacheCallAdapter<ResponseT, ReturnT> callAdapter = createCallAdapter(retrofit, method, adapterType, annotations);
        Type responseType = callAdapter.responseType();
        if (responseType == okhttp3.Response.class) {
            throw Utils.methodError(
                    method, "'" + Utils.getRawType(responseType).getName() + "' is not a valid response body type. Did you mean ResponseBody?");
        }
        if (responseType == Response.class) {
            throw Utils.methodError(method, "Response must include generic type (e.g., Response<String>)");
        }
        CacheHelper cache = retrofit.cache;
        Converter<ResponseBody, ResponseT> responseConverter = createResponseConverter(retrofit, method, responseType);
        CacheConverter<ResponseT> cacheConverter = createCacheConverter(retrofit, method, responseType);
        return new CallAdapted<>(requestFactory, responseConverter, cacheConverter, callAdapter, cache);
    }

    private static <ResponseT> CacheConverter<ResponseT> createCacheConverter(RetrofitCache retrofit, Method method, Type returnType) {
        try {
            return retrofit.responseCacheConverter(returnType);
        } catch (RuntimeException e) { // Wide exception range because factories are user code.
            throw Utils.methodError(method, e, "Unable to create cache converter for %s", returnType);
        }
    }

    private static <ResponseT, ReturnT> CacheCallAdapter<ResponseT, ReturnT> createCallAdapter(
            RetrofitCache retrofit, Method method, Type returnType, Annotation[] annotations) {
        try {
            //noinspection unchecked
            return (CacheCallAdapter<ResponseT, ReturnT>) retrofit.callAdapter(returnType, annotations);
        } catch (RuntimeException e) { // Wide exception range because factories are user code.
            throw Utils.methodError(method, e, "Unable to create call adapter for %s", returnType);
        }
    }

    private static <ResponseT> Converter<ResponseBody, ResponseT> createResponseConverter(
            RetrofitCache retrofit, Method method, Type responseType) {
        Annotation[] annotations = method.getAnnotations();
        try {
            return retrofit.responseBodyConverter(responseType, annotations);
        } catch (RuntimeException e) { // Wide exception range because factories are user code.
            throw Utils.methodError(method, e, "Unable to create converter for %s", responseType);
        }
    }

    @Override
    @Nullable
    final ReturnT invoke(Object[] args) {
        Class<?> service = requestFactory.getService();
        Method method = requestFactory.getMethod();
        okhttp3.Call rawCall = callAdapter.rawCall(service, method, args);
        Call<ResponseT> call = new HttpCacheCall<>(requestFactory, rawCall, responseConverter, cacheConverter, cache);
        return adapt(call, args);
    }

    @Nullable
    protected abstract ReturnT adapt(Call<ResponseT> call, Object[] args);

    static final class CallAdapted<ResponseT, ReturnT> extends HttpServiceMethod<ResponseT, ReturnT> {
        private final CacheCallAdapter<ResponseT, ReturnT> callAdapter;

        CallAdapted(
                RequestFactory requestFactory,
                Converter<ResponseBody, ResponseT> responseConverter,
                CacheConverter<ResponseT> cacheConverter,
                CacheCallAdapter<ResponseT, ReturnT> callAdapter,
                CacheHelper cache) {
            super(requestFactory, callAdapter, responseConverter, cacheConverter, cache);
            this.callAdapter = callAdapter;
        }

        @Override
        protected ReturnT adapt(Call<ResponseT> call, Object[] args) {
            return callAdapter.adapt(call);
        }
    }

}
