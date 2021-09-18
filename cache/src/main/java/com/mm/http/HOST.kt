package com.mm.http

/**
 * Created by : majian
 * Date : 2021/8/24
 */
@kotlin.annotation.Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
annotation class HOST(
    val value: String // host for service
)