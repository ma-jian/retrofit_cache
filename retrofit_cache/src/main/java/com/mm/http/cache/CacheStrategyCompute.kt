package com.mm.http.cache

import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 缓存计算
 */
class CacheStrategyCompute internal constructor(val cacheResponse: Response?) {

    class Factory(
        private val nowMillis: Long,
        duration: Long,
        timeUnit: TimeUnit,
        private val cacheResponse: Response?,
        private val cache: CacheHelper?
    ) {
        /**
         * The last modified date of the cached response, if known.
         */
        private var receivedResponseAtMillis = 0L

        /**
         * The expiration date of the cached response, if known. If both this field and the max age are
         * set, the max age is preferred.
         */
        private var expires = 0L
        private fun isExpires(): Boolean {
            return nowMillis - receivedResponseAtMillis > expires
        }

        fun compute(): CacheStrategyCompute {
            return computeCandidate()
        }

        private fun computeCandidate(): CacheStrategyCompute {
            if (cacheResponse == null) {
                return CacheStrategyCompute(null)
            }
            if (isExpires()) {
                try {
                    cache?.remove(cacheResponse.request)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                return CacheStrategyCompute(null)
            }
            return CacheStrategyCompute(cacheResponse)
        }

        init {
            if (cacheResponse != null) {
                receivedResponseAtMillis = cacheResponse.receivedResponseAtMillis
                expires = timeUnit.toMillis(duration)
            }
        }
    }
}