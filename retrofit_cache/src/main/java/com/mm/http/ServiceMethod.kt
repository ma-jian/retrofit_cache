package com.mm.http

import java.lang.reflect.Method


abstract class ServiceMethod<T> {

    abstract suspend operator fun invoke(proxy: Any,args: Array<Any>): T

    companion object {
        @JvmStatic
        fun <T> parseAnnotations(retrofit: RetrofitCache, service: Class<*>, method: Method): ServiceMethod<T> {
            val requestFactory = RequestFactory.parseAnnotations(retrofit, service, method)
            return HttpServiceMethod.parseAnnotations<Any, T>(retrofit, method, requestFactory)
        }
    }
}