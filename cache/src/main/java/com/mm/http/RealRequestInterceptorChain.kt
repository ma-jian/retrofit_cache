package com.mm.http

import com.mm.http.cache.CacheHelper
import okhttp3.*
import okhttp3.internal.EMPTY_RESPONSE
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit

/**
 * Created by : majian
 * Date : 2021/8/25
 * 处理自定义拦截器中对request 请求更改的逻辑。
 */
class RealRequestInterceptorChain internal constructor(
    private val call: Call,
    private val cache: CacheHelper?,
    private val interceptors: List<Interceptor>,
    private val index: Int,
    private val request: Request,
    private val connectTimeoutMillis: Int,
    private val readTimeoutMillis: Int,
    private val writeTimeoutMillis: Int
) : Interceptor.Chain {
    override fun call(): Call {
        return call
    }

    override fun connectTimeoutMillis(): Int {
        return connectTimeoutMillis
    }

    override fun connection(): Connection? {
        return null
    }

    @Throws(IOException::class)
    override fun proceed(request: Request): Response {
        // Call the next interceptor in the chain.
        if (index < interceptors.size) {
            val next = copy(
                index + 1,
                request,
                connectTimeoutMillis,
                readTimeoutMillis,
                writeTimeoutMillis
            )
            val interceptor = interceptors[index]
            return interceptor.intercept(next)
        }
        val response = cache?.get(request)
        return response?.newBuilder()?.request(request)?.build()
            ?: Response.Builder().request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(HttpURLConnection.HTTP_VERSION)
                .message("Unsatisfiable Request (only-if-cached)")
                .body(EMPTY_RESPONSE).build()
    }

    override fun readTimeoutMillis(): Int {
        return readTimeoutMillis
    }

    override fun request(): Request {
        return request
    }

    override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain {
        val connectTimeoutMillis = checkDuration(timeout, unit)
        return copy(index, request, connectTimeoutMillis, readTimeoutMillis, writeTimeoutMillis)
    }

    override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain {
        val readTimeoutMillis = checkDuration(timeout, unit)
        return copy(index, request, connectTimeoutMillis, readTimeoutMillis, writeTimeoutMillis)
    }

    override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain {
        val writeTimeoutMillis = checkDuration(timeout, unit)
        return copy(index, request, connectTimeoutMillis, readTimeoutMillis, writeTimeoutMillis)
    }

    private fun copy(
        index: Int, request: Request,
        connectTimeoutMillis: Int, readTimeoutMillis: Int, writeTimeoutMillis: Int
    ): RealRequestInterceptorChain {
        return RealRequestInterceptorChain(
            call,
            cache,
            interceptors,
            index,
            request,
            connectTimeoutMillis,
            readTimeoutMillis,
            writeTimeoutMillis
        )
    }

    private fun checkDuration(duration: Int, unit: TimeUnit?): Int {
        require(duration >= 0) { "duration < 0" }
        requireNotNull(unit) { "unit == null" }
        return unit.toMillis(duration.toLong()).toInt()
    }

    override fun writeTimeoutMillis(): Int {
        return writeTimeoutMillis
    }
}