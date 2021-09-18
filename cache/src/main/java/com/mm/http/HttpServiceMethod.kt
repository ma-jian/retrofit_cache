package com.mm.http

import com.mm.http.cache.CacheHelper
import okhttp3.Response
import okhttp3.ResponseBody
import retrofit2.Call
import java.lang.reflect.Method
import java.lang.reflect.Type

/**
 * Created by : majian
 * Date : 2021/8/24
 */
internal abstract class HttpServiceMethod<ResponseT, ReturnT>(
    private val requestFactory: RequestFactory,
    private val callAdapter: CacheCallAdapter<ResponseT, ReturnT>,
    private val responseConverter: Converter<ResponseBody, ResponseT>,
    private val cache: CacheHelper?
) : ServiceMethod<ReturnT>() {

    override fun invoke(args: Array<Any>?): ReturnT? {
        val service = requestFactory.service
        val method = requestFactory.method
        val rawCall = callAdapter.rawCall(service, method, args)
        val call: Call<ResponseT> = HttpCacheCall(requestFactory, rawCall, responseConverter, cache)
        return adapt(call, args)
    }

    protected abstract fun adapt(call: Call<ResponseT>?, args: Array<Any>?): ReturnT?

    internal class CallAdapted<ResponseT, ReturnT>(
        requestFactory: RequestFactory,
        responseConverter: Converter<ResponseBody, ResponseT>,
        private val callAdapter: CacheCallAdapter<ResponseT, ReturnT>,
        cache: CacheHelper?
    ) : HttpServiceMethod<ResponseT, ReturnT>(
        requestFactory,
        callAdapter,
        responseConverter,
        cache
    ) {
        override fun adapt(call: Call<ResponseT>?, args: Array<Any>?): ReturnT {
            return callAdapter.adapt(call!!)
        }
    }

    companion object {
        @JvmStatic
        fun <ResponseT, ReturnT> parseAnnotations(retrofit: RetrofitCache, method: Method, requestFactory: RequestFactory):
                HttpServiceMethod<ResponseT, ReturnT> {
            val annotations = method.annotations
            val adapterType = method.genericReturnType
            val callAdapter: CacheCallAdapter<ResponseT, ReturnT> = createCallAdapter(retrofit, method, adapterType, annotations)
            val responseType = callAdapter.responseType()
            if (responseType === Response::class.java) {
                throw Utils.methodError(
                    method,
                    "'" + Utils.getRawType(responseType).name + "' is not a valid response body type. Did you mean ResponseBody?"
                )
            }
            if (responseType === retrofit2.Response::class.java) {
                throw Utils.methodError(
                    method,
                    "Response must include generic type (e.g., Response<String>)"
                )
            }
            val cache = retrofit.cache
            val responseConverter: Converter<ResponseBody, ResponseT> =
                createResponseConverter(retrofit, method, responseType)
            return CallAdapted(requestFactory, responseConverter, callAdapter, cache)
        }

        private fun <ResponseT, ReturnT> createCallAdapter(
            retrofit: RetrofitCache,
            method: Method,
            returnType: Type,
            annotations: Array<Annotation>
        ): CacheCallAdapter<ResponseT, ReturnT> {
            return try {
                retrofit.callAdapter(
                    returnType,
                    annotations
                ) as CacheCallAdapter<ResponseT, ReturnT>
            } catch (e: RuntimeException) { // Wide exception range because factories are user code.
                throw Utils.methodError(
                    method,
                    e,
                    "Unable to create call adapter for %s",
                    returnType
                )
            }
        }

        private fun <ResponseT> createResponseConverter(
            retrofit: RetrofitCache, method: Method, responseType: Type
        ): Converter<ResponseBody, ResponseT> {
            val annotations = method.annotations
            return try {
                retrofit.responseBodyConverter(responseType, annotations)
            } catch (e: RuntimeException) { // Wide exception range because factories are user code.
                throw Utils.methodError(
                    method,
                    e,
                    "Unable to create converter for %s",
                    responseType
                )
            }
        }
    }
}