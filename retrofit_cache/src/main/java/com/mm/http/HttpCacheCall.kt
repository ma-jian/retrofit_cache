package com.mm.http

import androidx.annotation.GuardedBy
import com.mm.http.cache.CacheConverter
import com.mm.http.cache.CacheHelper
import com.mm.http.cache.CacheStrategyCompute
import com.mm.http.cache.StrategyType
import okhttp3.Call
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.internal.EMPTY_RESPONSE
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Timeout
import okio.buffer
import okio.use
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import java.net.HttpURLConnection
import java.util.Objects

/**
 * 请求与缓存逻辑处理
 */
class HttpCacheCall<T> internal constructor(
    private val requestFactory: RequestFactory,
    @field:GuardedBy("this") private val rawCall: Call,
    private val responseBodyConverter: Converter<ResponseBody, T>,
    private val cacheConverter: CacheConverter<T>,
    private val responseConverter: ResponseConverter<T>?,
    retrofitCache: RetrofitCache
) : retrofit2.Call<T> {

    private val cacheHelper: CacheHelper? = retrofitCache.cacheHelper
    private val retrofit: RetrofitCache = retrofitCache

    @Volatile
    private var canceled = false

    @GuardedBy("this") // Either a RuntimeException, non-fatal Error, or IOException.
    private var creationFailure: Throwable? = null

    @GuardedBy("this")
    private var executed = false

    @Throws(IOException::class)
    override fun execute(): Response<T> {
        var call: Call
        synchronized(this) {
            check(!executed) { "Already executed." }
            executed = true
            call = getRawCall()
        }
        if (canceled) {
            call.cancel()
        }
        return parseResponse(call.execute())
    }

    override fun enqueue(callback: Callback<T>) {
        Objects.requireNonNull(callback, "callback == null")
        var call: Call
        var failure: Throwable?
        synchronized(this) {
            check(!executed) { "Already executed." }
            executed = true
            call = getRawCall()
            failure = creationFailure
        }
        failure?.let {
            callback.onFailure(this@HttpCacheCall, it)
            return
        }
        if (canceled) {
            call.cancel()
        }
        val cacheRequest = requestFactory.cacheRequest()
        val strategy = cacheRequest.cacheStrategy
        val duration = cacheRequest.duration
        val timeUnit = cacheRequest.timeUnit
        val ignoreKey = cacheRequest.ignoreKey
        //添加忽略
        CacheHelper.ignoreKey(ignoreKey)
        val compute: CacheStrategyCompute = CacheStrategyCompute.Factory(
            System.currentTimeMillis(),
            duration,
            timeUnit,
            cacheHelper?.get(call.request()),
            cacheHelper
        ).compute()

        val cacheResponse = compute.cacheResponse
        when (strategy) {
            StrategyType.FORCE_CACHE -> responseCache(cacheResponse, callback)
            StrategyType.IF_CACHE_ELSE_NETWORK -> if (cacheResponse != null) {
                responseCache(cacheResponse, callback)
            } else {
                responseRemote(strategy, callback)
            }

            StrategyType.IF_NETWORK_ELSE_CACHE -> getRawCall().enqueue(object : okhttp3.Callback {
                override fun onResponse(call: Call, response: okhttp3.Response) {
                    try {
                        val res = parseResponse(response)
                        if (res.isSuccessful) {
                            callback.onResponse(this@HttpCacheCall, res)
                            cacheConverter.convert(res)?.let {
                                cacheHelper?.put(response, it)
                            }
                        } else {
                            responseCache(cacheResponse, callback)
                        }
                    } catch (e: Throwable) {
                        responseCache(cacheResponse, callback)
                    }
                }

                override fun onFailure(call: Call, e: IOException) {
                    if (cacheResponse != null) {
                        responseCache(cacheResponse, callback)
                    } else {
                        callback.onFailure(this@HttpCacheCall, e)
                    }
                }
            })

            StrategyType.CACHE_AND_NETWORK -> {
                if (cacheResponse != null) {
                    responseCache(cacheResponse, callback)
                }
                responseRemote(strategy, callback)
            }

            else -> responseRemote(strategy, callback)
        }
    }

    private fun responseRemote(cacheStrategy: Int, callback: Callback<T>?) {
        getRawCall().enqueue(object : okhttp3.Callback {
            override fun onResponse(call: Call, response: okhttp3.Response) {
                try {
                    val res = parseResponse(response)
                    callback?.onResponse(this@HttpCacheCall, res)
                    if (res.isSuccessful && cacheStrategy != StrategyType.NO_CACHE) {
                        cacheConverter.convert(res)?.let {
                            cacheHelper?.put(response, it)
                        }
                    }
                } catch (e: Throwable) {
                    callFailure(e)
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                callFailure(e)
            }

            private fun callFailure(e: Throwable) {
                callback?.onFailure(this@HttpCacheCall, e)
            }
        })
    }

    private fun responseCache(cacheResponse: okhttp3.Response?, callback: Callback<T>?) {
        try {
            val response = cacheResponse?.let { handleInterceptorChain(cacheResponse.request) }
            val res = parseResponse(response)
            callback?.onResponse(this@HttpCacheCall, res)
        } catch (e: Throwable) {
            callback?.onFailure(this@HttpCacheCall, e)
        }
    }

    /**
     * getRawCall from interceptors
     * @param request
     * @return rawRequest
     */
    @Throws(IOException::class)
    private fun handleInterceptorChain(request: Request): okhttp3.Response {
        val interceptors = retrofit.cacheResponseInterceptors
        val client = retrofit.callFactory()
        val connectTimeoutMillis = client.connectTimeoutMillis
        val readTimeoutMillis = client.readTimeoutMillis
        val writeTimeoutMillis = client.writeTimeoutMillis
        val chain = RealRequestInterceptorChain(
            client.newCall(request), cacheHelper, interceptors,
            0, request, connectTimeoutMillis, readTimeoutMillis, writeTimeoutMillis
        )
        return chain.proceed(request)
    }

    private fun getRawCall(): Call {
        return try {
            rawCall
        } catch (e: RuntimeException) {
            e.throwIfFatal() // Do not assign a fatal error to creationFailure.
            creationFailure = e
            throw e
        } catch (e: Error) {
            e.throwIfFatal()
            creationFailure = e
            throw e
        }
    }

    @Throws(IOException::class)
    fun parseResponse(response: okhttp3.Response?): Response<T> {
        var rawResponse = response
        if (rawResponse == null) {
            rawResponse = okhttp3.Response.Builder().request(getRawCall().request())
                .protocol(Protocol.HTTP_1_1).code(HttpURLConnection.HTTP_GATEWAY_TIMEOUT)
                .message("Unsatisfiable Request (only-if-cached)")
                .body(EMPTY_RESPONSE).build()
            val responseBody = rawResponse.body
            val noContentResponseBody = NoContentResponseBody(responseBody?.contentType(), responseBody?.contentLength() ?: -1)
            return Response.error(HttpURLConnection.HTTP_GATEWAY_TIMEOUT, noContentResponseBody)
        }
        val rawBody = rawResponse.body
        // Remove the body's source (the only stateful object) so we can pass the response along.
        rawResponse = rawResponse
            .newBuilder()
            .body(NoContentResponseBody(rawBody?.contentType(), rawBody?.contentLength() ?: -1))
            .build()
        val code = rawResponse.code
        if (code < 200 || code >= 300) {
            return rawBody.use {
                // Buffer the entire body to avoid future I/O.
                val bufferedBody = it.toBuffer()
                Response.error(bufferedBody, rawResponse)
            }
        }
        if (code == 204 || code == 205) {
            rawBody?.close()
            return Response.success(null, rawResponse)
        }
        val catchingBody = ExceptionCatchingResponseBody(rawBody!!)
        return try {
            val body = responseBodyConverter.convert(catchingBody)
            val success = Response.success(body, rawResponse)
            responseConverter?.convert(success) ?: success
        } catch (e: RuntimeException) {
            // If the underlying source threw an exception, propagate that rather than indicating it was
            // a runtime exception.
            catchingBody.throwIfCaught()
            throw e
        }
    }

    override fun isExecuted(): Boolean {
        return executed
    }

    override fun cancel() {
        canceled = true
        synchronized(this) { getRawCall().cancel() }
    }

    override fun isCanceled(): Boolean {
        if (canceled) {
            return true
        }
        synchronized(this) { return getRawCall().isCanceled() }
    }

    override fun clone(): retrofit2.Call<T> {
        return HttpCacheCall(requestFactory, rawCall, responseBodyConverter, cacheConverter, responseConverter, retrofit)
    }

    override fun request(): Request {
        return getRawCall().request()
    }

    override fun timeout(): Timeout {
        return try {
            getRawCall().timeout()
        } catch (e: Exception) {
            throw RuntimeException("Unable to create call.", e)
        }
    }

    internal class NoContentResponseBody(private val contentType: MediaType?, private val contentLength: Long) : ResponseBody() {
        override fun contentType(): MediaType? {
            return contentType
        }

        override fun contentLength(): Long {
            return contentLength
        }

        override fun source(): BufferedSource {
            throw IllegalStateException("Cannot read raw response body of a converted body.")
        }
    }

    internal class ExceptionCatchingResponseBody(private val delegate: ResponseBody) : ResponseBody() {
        private val delegateSource: BufferedSource
        var thrownException: IOException? = null

        init {
            delegateSource = object : ForwardingSource(delegate.source()) {
                @Throws(IOException::class)
                override fun read(sink: Buffer, byteCount: Long): Long {
                    return try {
                        super.read(sink, byteCount)
                    } catch (e: IOException) {
                        thrownException = e
                        throw e
                    }
                }
            }.buffer()
        }

        override fun contentType(): MediaType? {
            return delegate.contentType()
        }

        override fun contentLength(): Long {
            return delegate.contentLength()
        }

        override fun source(): BufferedSource {
            return delegateSource
        }

        override fun close() {
            delegate.close()
        }

        @Throws(IOException::class)
        fun throwIfCaught() {
            thrownException?.let {
                throw it
            }
        }
    }
}