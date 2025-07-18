package com.mm.http

import com.mm.http.cache.CacheConverter
import com.mm.http.cache.StrategyType
import okhttp3.Response
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.RequestUtil
import retrofit2.await
import retrofit2.awaitResponse
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * 处理缓存的Retrofit接口方法
 */
internal abstract class HttpServiceMethod<ResponseT: Any, ReturnT>(
    private val requestFactory: RequestFactory,
    private val responseBodyConverter: Converter<ResponseBody, ResponseT>,
    private val cacheConverter: CacheConverter<ResponseT>,
    private val responseConverter: ResponseConverter<ResponseT>,
    private val retrofit: RetrofitCache,
) : ServiceMethod<ReturnT>() {

    override suspend fun invoke(proxy: Any,args: Array<Any>): ReturnT {
        val service = requestFactory.service
        val method = requestFactory.method
        val isKotlinSuspendFunction = requestFactory.isKotlinSuspendFunction
        val host = service.getAnnotation(HOST::class.java)
        val realArgs = if (isKotlinSuspendFunction) arrayOf(*args,method.parameterTypes.last()) else args
        val request = RequestUtil.createRequest(retrofit,host!!,service,method,proxy,realArgs)
        val rawCall = retrofit.callFactory().newCall(request)
        val call: Call<ResponseT> =
            HttpCacheCall(requestFactory, rawCall, responseBodyConverter, cacheConverter, responseConverter, retrofit)
        return adapt(call, realArgs)
    }

    protected abstract suspend fun adapt(call: Call<ResponseT>, args: Array<Any>?): ReturnT

    internal class CallAdapted<ResponseT: Any, ReturnT>(
        requestFactory: RequestFactory,
        responseBodyConverter: Converter<ResponseBody, ResponseT>,
        cacheConverter: CacheConverter<ResponseT>,
        responseConverter: ResponseConverter<ResponseT>,
        private val callAdapter: CacheCallAdapter<ResponseT, ReturnT>,
        retrofit: RetrofitCache,
    ) : HttpServiceMethod<ResponseT, ReturnT>(
        requestFactory,responseBodyConverter,
        cacheConverter, responseConverter, retrofit
    ) {
        override suspend fun adapt(call: Call<ResponseT>, args: Array<Any>?): ReturnT {
            return callAdapter.adapt(call)
        }
    }

    internal class SuspendForResponse<ResponseT: Any>(
        requestFactory: RequestFactory,
        responseBodyConverter: Converter<ResponseBody, ResponseT>,
        cacheConverter: CacheConverter<ResponseT>,
        responseConverter: ResponseConverter<ResponseT>,
        private val callAdapter: CacheCallAdapter<ResponseT, Call<ResponseT>>,
        retrofit: RetrofitCache,
    ) : HttpServiceMethod<ResponseT, Any?>(
        requestFactory, responseBodyConverter,
        cacheConverter, responseConverter, retrofit
    ) {
        override suspend fun adapt(call: Call<ResponseT>, args: Array<Any>?): retrofit2.Response<ResponseT> {
            val adaptedCall = callAdapter.adapt(call)
            return adaptedCall.awaitResponse()
        }
    }

    internal class SuspendForBody<ResponseT: Any>(
        requestFactory: RequestFactory,
        responseBodyConverter: Converter<ResponseBody, ResponseT>,
        cacheConverter: CacheConverter<ResponseT>,
        responseConverter: ResponseConverter<ResponseT>,
        private val callAdapter: CacheCallAdapter<ResponseT, Call<ResponseT>>,
        retrofit: RetrofitCache,
    ) : HttpServiceMethod<ResponseT, Any?>(
        requestFactory, responseBodyConverter,
        cacheConverter, responseConverter, retrofit
    ) {
        override suspend fun adapt(call: Call<ResponseT>, args: Array<Any>?): ResponseT {
            val adaptedCall = callAdapter.adapt(call)
            return adaptedCall.await()
        }
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun <ResponseT: Any, ReturnT> parseAnnotations(retrofit: RetrofitCache, method: Method, requestFactory: RequestFactory):
                HttpServiceMethod<ResponseT, ReturnT> {
            val annotations = method.annotations
            val adapterType: Type
            val isKotlinSuspendFunction: Boolean = requestFactory.isKotlinSuspendFunction
            val strategy = requestFactory.cacheRequest().cacheStrategy
            var continuationWantsResponse = false
            if (isKotlinSuspendFunction) {
                val parameterTypes = method.genericParameterTypes
                var responseType = (parameterTypes[parameterTypes.size - 1] as ParameterizedType).getParameterLowerBound(0)
                if (responseType.getRawType() == retrofit2.Response::class.java && responseType is ParameterizedType) {
                    // Unwrap the actual body type from Response<T>.
                    responseType = responseType.getParameterUpperBound(0)
                    continuationWantsResponse = true
                }
                adapterType = ParameterizedTypeImpl(null, Call::class.java, responseType)
            } else {
                adapterType = method.genericReturnType
            }
            val callAdapter = createCallAdapter<ResponseT, ReturnT>(retrofit, method, adapterType, annotations)
            val responseType = callAdapter.responseType()
            if (responseType === Response::class.java) {
                throw method.methodError("'" + responseType.getRawType().name + "' is not a valid response body type. Did you mean ResponseBody?")
            }
            if (responseType === retrofit2.Response::class.java) {
                throw method.methodError("Response must include generic type (e.g., Response<String>)")
            }

            if (strategy == StrategyType.CACHE_AND_NETWORK && isKotlinSuspendFunction) {
                throw IllegalStateException("CACHE_AND_NETWORK 策略下不允许使用 suspend fun，请用 Flow/callback 方式获取数据")
            }
            val responseBodyConverter: Converter<ResponseBody, ResponseT> = createResponseBodyConverter(retrofit, method, responseType)
            val cacheConverter: CacheConverter<ResponseT> = createCacheConverter(retrofit, method, responseType)
            val responseConverter: ResponseConverter<ResponseT> = createResponseConverter(retrofit, method, responseType)
            return if (!isKotlinSuspendFunction) {
                CallAdapted(requestFactory, responseBodyConverter, cacheConverter, responseConverter, callAdapter, retrofit)
            } else if (continuationWantsResponse) {
                SuspendForResponse(requestFactory,responseBodyConverter, cacheConverter, responseConverter, callAdapter as CacheCallAdapter<ResponseT, Call<ResponseT>>, retrofit) as HttpServiceMethod<ResponseT, ReturnT>
            } else {
                SuspendForBody(requestFactory,responseBodyConverter, cacheConverter, responseConverter, callAdapter as CacheCallAdapter<ResponseT, Call<ResponseT>>, retrofit) as HttpServiceMethod<ResponseT, ReturnT>
            }
        }

        private fun <ResponseT> createCacheConverter(retrofit: RetrofitCache, method: Method, returnType: Type): CacheConverter<ResponseT> {
            return try {
                retrofit.responseCacheConverter(returnType)
            } catch (e: RuntimeException) { // Wide exception range because factories are user code.
                throw method.methodError(e, "Unable to create cache converter for %s", returnType)
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun <ResponseT, ReturnT> createCallAdapter(
            retrofit: RetrofitCache,
            method: Method,
            returnType: Type,
            annotations: Array<Annotation>,
        ): CacheCallAdapter<ResponseT, ReturnT> {
            return try {
                retrofit.callAdapter(returnType, annotations) as CacheCallAdapter<ResponseT, ReturnT>
            } catch (e: RuntimeException) { // Wide exception range because factories are user code.
                throw method.methodError(e, "Unable to create call adapter for %s", returnType)
            }
        }

        private fun <ResponseT> createResponseBodyConverter(
            retrofit: RetrofitCache, method: Method, responseType: Type,
        ): Converter<ResponseBody, ResponseT> {
            val annotations = method.annotations
            return try {
                retrofit.responseBodyConverter(responseType, annotations)
            } catch (e: RuntimeException) { // Wide exception range because factories are user code.
                throw method.methodError( e, "Unable to create converter for %s", responseType)
            }
        }

        private fun <ResponseT> createResponseConverter(
            retrofit: RetrofitCache,
            method: Method,
            returnType: Type,
        ): ResponseConverter<ResponseT> {
            return try {
                retrofit.responseConverter(returnType)
            } catch (e: RuntimeException) { // Wide exception range because factories are user code.
                throw method.methodError(e, "Unable to create response converter for %s", returnType)
            }
        }
    }
}