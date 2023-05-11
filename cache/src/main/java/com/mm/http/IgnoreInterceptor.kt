package com.mm.http

/**
 * Created by : majian
 * Date : 2021/8/24
 * 忽略拦截器进入缓存预处理逻辑
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class IgnoreInterceptor