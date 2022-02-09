package com.mm.http

import java.util.concurrent.TimeUnit

/**
 * Created by : majian
 * Date : 2021/9/24
 * 接口超时设置
 */
@kotlin.annotation.Retention(AnnotationRetention.RUNTIME)
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