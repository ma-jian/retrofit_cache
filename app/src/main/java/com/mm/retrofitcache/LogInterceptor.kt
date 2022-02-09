package com.mm.retrofitcache

import android.content.Context
import com.mm.http.cache.CacheHelper
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Created by : majian
 * Date : 2021/8/30
 */
class LogInterceptor(private val context: Context) : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request: Request = chain.request()
        val response: Response = chain.proceed(request)
        if (response.code < 500 && (!context.isConnect || response.header(CacheHelper.CACHE_HEADER) == null)) {
            synchronized(this) {
                log("↓↓↓ ---------------------------------------------------------------------------- ↓↓↓")
                log("--> " + request.method + " " + request.url)
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
        if (BuildConfig.BUILD_TYPE != "release") {
            if (msg.length <= 3 * 1024) {
                Logger.getLogger("OkHttpClient").log(Level.INFO, msg)
            } else {
                val substring = msg.substring(0, 3 * 1024)
                Logger.getLogger("OkHttpClient").log(Level.INFO, substring)
                log(msg.substring(3 * 1024))
            }
        }
    }
}