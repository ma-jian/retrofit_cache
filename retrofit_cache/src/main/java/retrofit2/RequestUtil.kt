package retrofit2

import com.mm.http.HOST
import com.mm.http.RealRequestInterceptorChain
import com.mm.http.RetrofitCache
import okhttp3.Request
import java.io.IOException
import java.lang.reflect.Method

/**
 * 通过原生retrofit获取request
 */
class RequestUtil {
    companion object {
        fun createRequest(retrofit: RetrofitCache, host: HOST, service: Class<*>,method: Method, proxy: Any, args: Array<Any>): Request {
            val request = RequestFactory.parseAnnotations(retrofit.loadRetrofit(host), service,method).create(proxy,args)
            return getRawCallWithInterceptorChain(retrofit, request)
        }

        /**
         * getRawCall from interceptors
         * @param retrofit
         * @param request
         * @return rawRequest
         * @throws IOException
         */
        @Throws(IOException::class)
        private fun getRawCallWithInterceptorChain(retrofit: RetrofitCache, request: Request): Request {
            val client = retrofit.callFactory()
            val interceptors = retrofit.cacheInterceptors
            val connectTimeoutMillis = client.connectTimeoutMillis
            val readTimeoutMillis = client.readTimeoutMillis
            val writeTimeoutMillis = client.writeTimeoutMillis
            val chain = RealRequestInterceptorChain(client.newCall(request), retrofit.cacheHelper, interceptors, 0, request, connectTimeoutMillis, readTimeoutMillis, writeTimeoutMillis)
            return chain.proceed(request).request
        }
    }
}