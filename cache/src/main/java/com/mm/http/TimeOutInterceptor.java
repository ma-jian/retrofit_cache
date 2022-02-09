package com.mm.http;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Invocation;

/**
 * Created by : majian
 * Date : 2021/9/24
 * 超时拦截器，为单独接口配置超时时间
 * @see TimeOut 可配置超时单位，默认 java.util.concurrent.TimeUnit.SECONDS
 */

public class TimeOutInterceptor implements Interceptor {

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        Invocation tag = request.tag(Invocation.class);
        if (tag != null) {
            TimeOut time = tag.method().getAnnotation(TimeOut.class);
            if (time != null) {
                TimeUnit unit = time.unit();
                Chain newChain = chain;
                int connectTimeOut = time.connectTimeOut();
                if (connectTimeOut > 0) {
                    newChain = newChain.withConnectTimeout(connectTimeOut, unit);
                }

                int readTimeOut = time.readTimeOut();
                if (readTimeOut > 0) {
                    newChain = newChain.withReadTimeout(readTimeOut, unit);
                }

                int writeTimeOut = time.writeTimeOut();
                if (writeTimeOut > 0) {
                    newChain = newChain.withWriteTimeout(writeTimeOut, unit);
                }
                return newChain.proceed(request);
            }
        }
        return chain.proceed(request);
    }
}
