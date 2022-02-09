package com.mm.http;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Created by : majian
 * Date : 2021/9/24
 * 接口超时设置
 */

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TimeOut {
    int connectTimeOut() default 0;

    int readTimeOut() default 0;

    int writeTimeOut() default 0;

    TimeUnit unit() default TimeUnit.SECONDS;
}
