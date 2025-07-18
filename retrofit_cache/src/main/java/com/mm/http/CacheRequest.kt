package com.mm.http

import com.mm.http.cache.StrategyType
import java.util.ArrayList
import java.util.concurrent.TimeUnit

/**
 * 缓存请求参数
 */
class CacheRequest private constructor(
    val cacheStrategy: Int,
    val duration: Long,
    val timeUnit: TimeUnit,
    val ignoreKey: List<String>
) {

    class Builder {
        private var strategy = StrategyType.NO_CACHE
        private var duration = 24L
        private var timeUnit = TimeUnit.HOURS
        private var ignoreKey: List<String> = ArrayList()
        fun cacheStrategy(@StrategyType cacheStrategy: Int): Builder {
            strategy = cacheStrategy
            return this
        }

        fun duration(duration: Long): Builder {
            this.duration = duration
            return this
        }

        fun timeUnit(timeUnit: TimeUnit): Builder {
            this.timeUnit = timeUnit
            return this
        }

        fun ignoreKey(ignoreKey: List<String>): Builder {
            this.ignoreKey = ignoreKey
            return this
        }

        fun build(): CacheRequest {
            return CacheRequest(strategy, duration, timeUnit, ignoreKey)
        }
    }
}