package com.mm.http

import com.mm.http.cache.CacheConverter
import com.mm.http.cache.CacheHelper
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Created by : majian
 * Date : 2021/8/24
 */
internal abstract class HttpServiceMethod<ResponseT, ReturnT>(
    private val requestFactory: RequestFactory,
    private val callAdapter: CacheCallAdapter<ResponseT, ReturnT>,
    private val responseConverter: Converter<ResponseBody, ResponseT>,
    private val cacheConverter: CacheConverter<ResponseT>,
    private val cache: CacheHelper
) : ServiceMethod<ReturnT>() {

    override fun invoke(args: Array<Any>): ReturnT {
        val service = requestFactory.service
        val method = requestFactory.method
        val isKotlinSuspendFunction = requestFactory.isKotlinSuspendFunction
        val rawCall = callAdapter.rawCall(service, method, args,isKotlinSuspendFunction)
        val call: Call<ResponseT> = HttpCacheCall(requestFactory, rawCall, responseConverter, cacheConverter, cache)
        return adapt(call, args)
    }

    protected abstract fun adapt(call: Call<ResponseT>, args: Array<Any>): ReturnT

    internal class CallAdapted<ResponseT, ReturnT>(
        requestFactory: RequestFactory,
        responseConverter: Converter<ResponseBody, ResponseT>,
        cacheConverter: CacheConverter<ResponseT>,
        private val callAdapter: CacheCallAdapter<ResponseT, ReturnT>,
        cache: CacheHelper) : HttpServiceMethod<ResponseT, ReturnT>(requestFactory, callAdapter, responseConverter, cacheConverter, cache) {

        override fun adapt(call: Call<ResponseT>, args: Array<Any>): ReturnT {
            return callAdapter.adapt(call)
        }
    }

//    internal class SuspendForResponse<ResponseT, ReturnT>(requestFactory: RequestFactory, responseConverter: Converter<ResponseBody, ResponseT>, cacheConverter: CacheConverter<ResponseT>, private val callAdapter: CacheCallAdapter<ResponseT, ReturnT>, cache: CacheHelper) : HttpServiceMethod<ResponseT, ReturnT>(requestFactory, callAdapter, responseConverter, cacheConverter, cache) {
//
//        override fun adapt(call: Call<ResponseT>, args: Array<Any>): ReturnT {
//
//        }
//    }


    companion object {
        @JvmStatic
        fun <ResponseT, ReturnT> parseAnnotations(retrofit: RetrofitCache, method: Method, requestFactory: RequestFactory): HttpServiceMethod<ResponseT, ReturnT> {
            val annotations = method.annotations
            val adapterType: Type
            val isKotlinSuspendFunction: Boolean = requestFactory.isKotlinSuspendFunction
            var continuationWantsResponse = false
            if (isKotlinSuspendFunction) {
                val parameterTypes = method.genericParameterTypes
                var responseType = Utils.getParameterLowerBound(0, parameterTypes[parameterTypes.size - 1] as ParameterizedType)
                if (Utils.getRawType(responseType) == retrofit2.Response::class.java && responseType is ParameterizedType) {
                    // Unwrap the actual body type from Response<T>.
                    responseType = Utils.getParameterUpperBound(0, responseType as ParameterizedType)
                    continuationWantsResponse = true
                }
                adapterType = Utils.ParameterizedTypeImpl(null, Call::class.java, responseType)
            } else {
                adapterType = method.genericReturnType
            }

            val callAdapter: CacheCallAdapter<ResponseT, ReturnT> = createCallAdapter(retrofit, method, adapterType, annotations)
            val responseType = callAdapter.responseType()
            if (responseType === Response::class.java) {
                throw Utils.methodError(method, "'" + Utils.getRawType(responseType).name + "' is not a valid response body type. Did you mean ResponseBody?")
            }
            if (responseType === retrofit2.Response::class.java) {
                throw Utils.methodError(method, "Response must include generic type (e.g., Response<String>)")
            }
            val cache = retrofit.cache
            val responseConverter: Converter<ResponseBody, ResponseT> = createResponseConverter(retrofit, method, responseType)
            val cacheConverter: CacheConverter<ResponseT> = createCacheConverter(retrofit, method, responseType)
            return CallAdapted(requestFactory, responseConverter, cacheConverter, callAdapter, cache)
//            if (!isKotlinSuspendFunction) {
//                return CallAdapted(requestFactory, responseConverter, cacheConverter, callAdapter, cache)
//            } else if (continuationWantsResponse) {
//                return SuspendForResponse(requestFactory, responseConverter, cacheConverter, callAdapter, cache)
//            } else {
//            }
        }

        private fun <ResponseT> createCacheConverter(
            retrofit: RetrofitCache,
            method: Method,
            responseType: Type
        ): CacheConverter<ResponseT> {
            return try {
                retrofit.responseCacheConverter(responseType)
            } catch (e: RuntimeException) { // Wide exception range because factories are user code.
                throw Utils.methodError(
                    method,
                    e,
                    "Unable to create converter for %s",
                    responseType
                )
            }
        }

        private fun <ResponseT, ReturnT> createCallAdapter(retrofit: RetrofitCache, method: Method,
                                                           returnType: Type, annotations: Array<Annotation>):
                CacheCallAdapter<ResponseT, ReturnT> {
            return try {
                retrofit.callAdapter(returnType, annotations) as CacheCallAdapter<ResponseT, ReturnT>
            } catch (e: RuntimeException) { // Wide exception range because factories are user code.
                throw Utils.methodError(method, e, "Unable to create call adapter for %s", returnType)
            }
        }

        private fun <ResponseT> createResponseConverter(
            retrofit: RetrofitCache, method: Method, responseType: Type
        ): Converter<ResponseBody, ResponseT> {
            val annotations = method.annotations
            return try {
                retrofit.responseBodyConverter(responseType, annotations)
            } catch (e: RuntimeException) { // Wide exception range because factories are user code.
                throw Utils.methodError(method, e, "Unable to create converter for %s", responseType)
            }
        }
    }
}