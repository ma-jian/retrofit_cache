package com.mm.http

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import retrofit2.Invocation
import java.io.IOException

/**
 * 超时拦截器，为单独接口配置超时时间
 * @see TimeOut 可配置超时单位，默认 java.util.concurrent.TimeUnit.SECONDS
 */
class TimeOutInterceptor : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request: Request = chain.request()
        val tag = request.tag(Invocation::class.java)
        if (tag != null) {
            val time = tag.method().getAnnotation(TimeOut::class.java)
            if (time != null) {
                val newChain = chain.run {
                    if (time.connectTimeOut > 0) withConnectTimeout(
                        timeout = time.connectTimeOut,
                        time.unit
                    ) else chain
                }.run {
                    if (time.readTimeOut > 0) withReadTimeout(
                        timeout = time.readTimeOut,
                        time.unit
                    ) else chain
                }.run {
                    if (time.writeTimeOut > 0) withWriteTimeout(
                        timeout = time.writeTimeOut,
                        time.unit
                    ) else chain
                }
                return newChain.proceed(request)
            }
        }
        return chain.proceed(request)
    }
}