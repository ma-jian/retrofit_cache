package com.mm.http.cache

import java.util.concurrent.TimeUnit

/**
 * 缓存策略及缓存时间设置
 * 默认缓存有效期 24H
 */
@Target(
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER
)
@Retention(AnnotationRetention.RUNTIME)
annotation class CacheStrategy(
    val value: Int = StrategyType.NO_CACHE,

    val duration: Long = 24L,

    val timeUnit: TimeUnit = TimeUnit.HOURS
)