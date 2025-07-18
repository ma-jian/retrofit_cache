package com.mm.http

import java.util.concurrent.TimeUnit

/**
 * 接口超时设置
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER
)
annotation class TimeOut(
    val connectTimeOut: Int = 0,
    val readTimeOut: Int = 0,
    val writeTimeOut: Int = 0,
    val unit: TimeUnit = TimeUnit.SECONDS
)