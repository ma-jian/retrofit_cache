package com.mm.http

import java.lang.reflect.Method

/**
 * Created by : majian
 * Date : 2021/8/24
 */
abstract class ServiceMethod<T> {

    abstract operator fun invoke(args: Array<Any>?): T?

    companion object {
        @JvmStatic
        fun <T> parseAnnotations(
            retrofit: RetrofitCache,
            service: Class<*>,
            method: Method
        ): ServiceMethod<T> {
            val requestFactory = RequestFactory.parseAnnotations(retrofit, service, method)
            return HttpServiceMethod.parseAnnotations<Any, T>(retrofit, method, requestFactory)
        }
    }
}