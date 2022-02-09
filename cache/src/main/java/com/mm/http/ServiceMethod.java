package com.mm.http;


import java.lang.reflect.Method;

import javax.annotation.Nullable;

/**
 * Created by : majian
 * Date : 2021/8/24
 */

abstract class ServiceMethod<T> {

    static <T> ServiceMethod<T> parseAnnotations(RetrofitCache retrofit, Class<?> service, Method method) {
        RequestFactory requestFactory = RequestFactory.parseAnnotations(retrofit, service, method);
        return HttpServiceMethod.parseAnnotations(retrofit, method, requestFactory);
    }

    abstract @Nullable
    T invoke(Object[] args);
}
