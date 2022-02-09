package com.mm.http;

import com.mm.http.cache.CacheStrategy;
import com.mm.http.cache.IgnoreCache;
import com.mm.http.cache.IgnoreKey;
import com.mm.http.cache.StrategyType;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Created by : majian
 * Date : 2021/8/24
 */

public class RequestFactory {
    private final int strategy;
    private final Long duration;
    private final TimeUnit timeUnit;
    private CacheRequest cacheRequest;
    private final Class<?> service;
    private final Method method;
    private final String[] ignoreKey;

    private RequestFactory(Class<?> service, Method method, @StrategyType int strategy, Long duration, TimeUnit timeUnit, String[] ignoreKey) {
        this.service = service;
        this.method = method;
        this.strategy = strategy;
        this.duration = duration;
        this.timeUnit = timeUnit;
        this.ignoreKey = ignoreKey;
    }

    public CacheRequest cacheRequest() {
        if (cacheRequest == null) {
            cacheRequest = new CacheRequest.Builder()
                    .cacheStrategy(strategy)
                    .duration(duration)
                    .timeUnit(timeUnit)
                    .ignoreKey(Arrays.asList(ignoreKey))
                    .build();
        }
        return cacheRequest;
    }

    public Class<?> getService() {
        return service;
    }

    public Method getMethod() {
        return method;
    }

    public static RequestFactory parseAnnotations(RetrofitCache retrofit, Class<?> service, Method method) {
        return new Builder(retrofit, service, method).build();
    }

    static class Builder {
        private final Class<?> service;
        private final Method method;
        private int strategy = StrategyType.NO_CACHE;
        private Long duration = 24L;
        private TimeUnit timeUnit = TimeUnit.HOURS;
        private String[] ignoreKey = new String[]{};

        Builder(RetrofitCache retrofit, Class<?> service, Method method) {
            this.service = service;
            this.method = method;
        }

        RequestFactory build() {
            /**
             * 缓存策略优先级： 忽略缓存 > 方法策略 > 类策略
             */
            if (service.isAnnotationPresent(CacheStrategy.class)) {
                CacheStrategy annotation = service.getAnnotation(CacheStrategy.class);
                if (annotation != null) {
                    strategy = annotation.value();
                    duration = annotation.duration();
                    timeUnit = annotation.timeUnit();
                }
            }

            if (method.isAnnotationPresent(CacheStrategy.class)) {
                CacheStrategy annotation = method.getAnnotation(CacheStrategy.class);
                if (annotation != null) {
                    strategy = annotation.value();
                    duration = annotation.duration();
                    timeUnit = annotation.timeUnit();
                }
            }

            if (method.isAnnotationPresent(IgnoreCache.class)) {
                strategy = StrategyType.NO_CACHE;
            }

            if (method.isAnnotationPresent(IgnoreKey.class)) {
                IgnoreKey annotation = method.getAnnotation(IgnoreKey.class);
                if (annotation != null) {
                    ignoreKey = annotation.value();
                }
            }

            return new RequestFactory(service, method, strategy, duration, timeUnit, ignoreKey);
        }
    }
}

