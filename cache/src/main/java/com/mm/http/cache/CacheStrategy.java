package com.mm.http.cache;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Created by : majian
 * Date : 2021/8/24
 */

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RUNTIME)
public @interface CacheStrategy {
    @StrategyType
    int value() default StrategyType.NO_CACHE;

    long duration() default 24L;

    TimeUnit timeUnit() default TimeUnit.HOURS;
}
