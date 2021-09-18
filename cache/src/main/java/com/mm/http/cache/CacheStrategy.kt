package com.mm.http.cache

import java.util.concurrent.TimeUnit

/**
 * Created by : majian
 * Date : 2021/8/24
 */
@Target(
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER
)
@kotlin.annotation.Retention(AnnotationRetention.RUNTIME)
annotation class CacheStrategy(
    val value: Int = StrategyType.NO_CACHE,
    val duration: Long = 24L,
    val timeUnit: TimeUnit = TimeUnit.HOURS
)