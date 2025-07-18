package com.mm.retrofitcache

import android.util.Log
import com.mm.http.IgnoreInterceptor
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.logging.Level
import java.util.logging.Logger

/**
 *
 * @param enableCacheHandling 是否参与缓存处理
 */
@IgnoreInterceptor(enableCacheHandling = true)
class LogInterceptor : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request: Request = chain.request()
        val response: Response = chain.proceed(request)
        if (response.code < 505) {
            synchronized(this) {
                log("↓↓↓ ---------------------------------------------------------------------------- ↓↓↓")
                log("--> " + request.method + " " + response.code + " " + request.url)
                val body = request.body
                if (body != null) {
                    val contentType = body.contentType()
                    val charset = if (contentType != null) {
                        contentType.charset(StandardCharsets.UTF_8)
                    } else {
                        StandardCharsets.UTF_8
                    }
                    val buffer = Buffer()
                    body.writeTo(buffer)
                    val readString = buffer.readString(charset!!)
                    log("--> body: $readString")
                }
                val headers = request.headers
                if (headers.size > 0) {
                    log("--> --------------------------------Request  Headers-------------------------------- ")
                }
                for (i in 0 until headers.size) {
                    log("--> " + headers.name(i) + ": " + headers.value(i))
                }
                val resHeader = response.headers
                if (resHeader.size > 0) {
                    log("--> --------------------------------Response Headers-------------------------------- ")
                }
                for (i in 0 until resHeader.size) {
                    log("--> " + resHeader.name(i) + ": " + resHeader.value(i))
                }
                if (resHeader.size > 0 || headers.size > 0) {
                    log("--> -------------------------------------------------------------------------------- ")
                }
                if (response.body != null) {
                    val source = response.body!!.source()
                    source.request(Long.MAX_VALUE)
                    val buffer = source.buffer
                    val data = buffer.clone().readString(StandardCharsets.UTF_8)
                    log(data)
                    log("↑↑↑ --------------------------- END HTTP (" + buffer.size + ") byte --------------------------- ↑↑↑")
                }
            }
        }
        return response
    }

    private fun log(msg: String) {
        try {
            Logger.getLogger("OkHttpClient").log(Level.INFO, msg)
        } catch (e: Exception) {
            e.printStackTrace()
            Logger.getLogger("OkHttpClient").log(Level.WARNING, Log.getStackTraceString(e))
        }
    }
}