package com.mm.http;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mm.http.cache.CacheHelper;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.Interceptor;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.internal.Util;

/**
 * Created by : majian
 * Date : 2021/8/25
 * 处理自定义拦截器中对request 请求更改的逻辑。
 */

public class RealRequestInterceptorChain implements Interceptor.Chain {
    private final Call call;
    private final List<Interceptor> interceptors;
    private final int index;
    private final Request request;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final int writeTimeoutMillis;
    private final CacheHelper cache;

    RealRequestInterceptorChain(Call call, CacheHelper cache, List<Interceptor> interceptors, int index, Request request, int connectTimeoutMillis, int readTimeoutMillis, int writeTimeoutMillis) {
        this.interceptors = interceptors;
        this.index = index;
        this.request = request;
        this.call = call;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.writeTimeoutMillis = writeTimeoutMillis;
        this.cache = cache;
    }

    @NonNull
    @Override
    public Call call() {
        return call;
    }

    @Override
    public int connectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    @Nullable
    @Override
    public Connection connection() {
        return null;
    }

    @NonNull
    @Override
    public Response proceed(@NonNull Request request) throws IOException {
        // Call the next interceptor in the chain.
        if (index < interceptors.size()) {
            RealRequestInterceptorChain next = copy(index + 1, request, connectTimeoutMillis, readTimeoutMillis, writeTimeoutMillis);
            Interceptor interceptor = interceptors.get(index);
            return interceptor.intercept(next);
        }
        Response response = cache.get(request);
        if (response != null) {
            return response.newBuilder().request(request).build();
        }
        return new Response.Builder().request(request)
                .protocol(Protocol.HTTP_1_1).code(HttpURLConnection.HTTP_GATEWAY_TIMEOUT)
                .message("Unsatisfiable Request (only-if-cached)")
                .body(Util.EMPTY_RESPONSE).build();
    }

    @Override
    public int readTimeoutMillis() {
        return readTimeoutMillis;
    }

    @NonNull
    @Override
    public Request request() {
        return request;
    }

    @NonNull
    @Override
    public Interceptor.Chain withConnectTimeout(int i, @NonNull TimeUnit timeUnit) {
        int connectTimeoutMillis = checkDuration(i, timeUnit);
        return copy(index, request, connectTimeoutMillis, readTimeoutMillis, writeTimeoutMillis);
    }

    @NonNull
    @Override
    public Interceptor.Chain withReadTimeout(int i, @NonNull TimeUnit timeUnit) {
        int readTimeoutMillis = checkDuration(i, timeUnit);
        return copy(index, request, connectTimeoutMillis, readTimeoutMillis, writeTimeoutMillis);
    }

    @NonNull
    @Override
    public Interceptor.Chain withWriteTimeout(int i, @NonNull TimeUnit timeUnit) {
        int writeTimeoutMillis = checkDuration(i, timeUnit);
        return copy(index, request, connectTimeoutMillis, readTimeoutMillis, writeTimeoutMillis);
    }

    private RealRequestInterceptorChain copy(int index, Request request,
                                             int connectTimeoutMillis, int readTimeoutMillis, int writeTimeoutMillis) {
        return new RealRequestInterceptorChain(call, cache, interceptors, index, request, connectTimeoutMillis, readTimeoutMillis, writeTimeoutMillis);
    }

    private int checkDuration(int duration, TimeUnit unit) {
        if (duration < 0) {
            throw new IllegalArgumentException("duration < 0");
        }
        if (unit == null) {
            throw new IllegalArgumentException("unit == null");
        }
        return (int) unit.toMillis(duration);
    }

    @Override
    public int writeTimeoutMillis() {
        return writeTimeoutMillis;
    }
}
