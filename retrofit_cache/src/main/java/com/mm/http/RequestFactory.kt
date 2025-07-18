package com.mm.http

import com.mm.http.cache.CacheStrategy
import com.mm.http.cache.IgnoreCache
import com.mm.http.cache.IgnoreKey
import com.mm.http.cache.StrategyType
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation

/**
 * 请求参数配置
 */
class RequestFactory private constructor(
    val service: Class<*>,
    val method: Method,
    @param:StrategyType private val strategy: Int,
    private val duration: Long,
    private val timeUnit: TimeUnit,
    private val ignoreKey: List<String>,
    val isKotlinSuspendFunction: Boolean
) {
    fun cacheRequest(): CacheRequest {
        return CacheRequest.Builder()
            .cacheStrategy(strategy)
            .duration(duration)
            .timeUnit(timeUnit)
            .ignoreKey(ignoreKey)
            .build()
    }

    internal class Builder(
        retrofit: RetrofitCache,
        private val service: Class<*>,
        private val method: Method
    ) {
        private var strategy = StrategyType.NO_CACHE
        private var duration = 24L
        private var timeUnit = TimeUnit.HOURS
        private var ignoreKey = listOf<String>()
        private var isKotlinSuspendFunction: Boolean = false
        fun build(): RequestFactory {
            /**
             * 缓存策略优先级： [IgnoreCache]忽略缓存 > 方法策略 > 类策略
             */
            if (service.isAnnotationPresent(CacheStrategy::class.java)) {
                service.getAnnotation(CacheStrategy::class.java)?.let {
                    strategy = it.value
                    duration = it.duration
                    timeUnit = it.timeUnit
                }
            }
            if (method.isAnnotationPresent(CacheStrategy::class.java)) {
                method.getAnnotation(CacheStrategy::class.java)?.let {
                    strategy = it.value
                    duration = it.duration
                    timeUnit = it.timeUnit
                }
            }
            if (method.isAnnotationPresent(IgnoreCache::class.java)) {
                strategy = StrategyType.NO_CACHE
            }
            if (method.isAnnotationPresent(IgnoreKey::class.java)) {
                method.getAnnotation(IgnoreKey::class.java)?.let {
                    ignoreKey = it.value.asList()
                }
            }

            val parameterTypes = method.genericParameterTypes
            if (parameterTypes[parameterTypes.size - 1].getRawType() == Continuation::class.java) {
                isKotlinSuspendFunction = true
            }
            return RequestFactory(service, method, strategy, duration, timeUnit, ignoreKey, isKotlinSuspendFunction)
        }
    }

    companion object {
        fun parseAnnotations(
            retrofit: RetrofitCache,
            service: Class<*>,
            method: Method
        ): RequestFactory {
            return Builder(retrofit, service, method).build()
        }
    }
}