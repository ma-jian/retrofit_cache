package com.mm.http.cache;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Created by : majian
 * Date : 2021/8/24
 */
@Target(ElementType.METHOD)
@Retention(RUNTIME)
public @interface IgnoreKey {
    String[] value() default {"binding"};
}
