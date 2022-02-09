package com.mm.http;


import androidx.annotation.NonNull;

import com.mm.http.cache.CacheConverter;
import com.mm.http.cache.CacheHelper;
import com.mm.http.cache.CacheStrategyCompute;
import com.mm.http.cache.StrategyType;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.ResponseBody;
import okhttp3.internal.Util;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;
import okio.Timeout;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Created by : majian
 * Date : 2021/8/24
 */

public class HttpCacheCall<T> implements Call<T> {

    private final RequestFactory requestFactory;
    private final Converter<ResponseBody, T> responseConverter;
    private final CacheConverter<T> cacheConverter;
    private final CacheHelper cacheHelper;
    private volatile boolean canceled;

    @GuardedBy("this")
    @Nullable
    private final okhttp3.Call rawCall;

    @GuardedBy("this") // Either a RuntimeException, non-fatal Error, or IOException.
    @Nullable
    private Throwable creationFailure;

    @GuardedBy("this")
    private boolean executed;

    HttpCacheCall(
            RequestFactory requestFactory,
            @androidx.annotation.Nullable okhttp3.Call rawCall,
            Converter<ResponseBody, T> responseConverter,
            CacheConverter<T> cacheConverter,
            CacheHelper cacheHelper) {
        this.requestFactory = requestFactory;
        this.rawCall = rawCall;
        this.responseConverter = responseConverter;
        this.cacheConverter = cacheConverter;
        this.cacheHelper = cacheHelper;
    }

    @NonNull
    @Override
    public Response<T> execute() throws IOException {
        okhttp3.Call call;
        synchronized (this) {
            if (executed) throw new IllegalStateException("Already executed.");
            executed = true;
            call = getRawCall();
        }

        if (canceled) {
            call.cancel();
        }

        return parseResponse(call.execute());
    }

    @Override
    public void enqueue(Callback<T> callback) {
        Objects.requireNonNull(callback, "callback == null");

        okhttp3.Call call;
        Throwable failure;

        synchronized (this) {
            if (executed) throw new IllegalStateException("Already executed.");
            executed = true;
            call = getRawCall();
            failure = creationFailure;
        }

        if (failure != null) {
            callback.onFailure(this, failure);
            return;
        }
        if (canceled) {
            call.cancel();
        }
        CacheRequest cacheRequest = requestFactory.cacheRequest();
        int strategy = cacheRequest.getCacheStrategy();
        Long duration = cacheRequest.getDuration();
        TimeUnit timeUnit = cacheRequest.getTimeUnit();
        List<String> ignoreKey = cacheRequest.getIgnoreKey();
        //添加忽略
        CacheHelper.ignoreKey(ignoreKey);
        CacheStrategyCompute compute = new CacheStrategyCompute.Factory(
                System.currentTimeMillis(),
                duration,
                timeUnit,
                cacheHelper.get(call.request()),
                cacheHelper
        ).compute();

        okhttp3.Response cacheResponse = compute.getCacheResponse();

        switch (strategy) {
            case StrategyType.FORCE_CACHE:
                responseCache(cacheResponse, callback);
                break;
            case StrategyType.IF_CACHE_ELSE_NETWORK:
                if (cacheResponse != null) {
                    responseCache(cacheResponse, callback);
                } else {
                    responseRemote(strategy, callback);
                }
                break;
            case StrategyType.IF_NETWORK_ELSE_CACHE:
                getRawCall().enqueue(new okhttp3.Callback() {
                    @Override
                    public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) {
                        try {
                            Response<T> res = parseResponse(response);
                            if (res.isSuccessful()) {
                                callback.onResponse(HttpCacheCall.this, res);
                                Response<T> convert = cacheConverter.convert(res);
                                if (convert != null) {
                                    cacheHelper.put(response, convert);
                                }
                            } else {
                                responseCache(cacheResponse, callback);
                            }
                        } catch (Throwable e) {
                            responseCache(cacheResponse, callback);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull okhttp3.Call call, @NonNull IOException e) {
                        if (cacheResponse != null) {
                            responseCache(cacheResponse, callback);
                        } else {
                            callback.onFailure(HttpCacheCall.this, e);
                        }
                    }
                });

                break;
            case StrategyType.CACHE_AND_NETWORK:
                if (cacheResponse != null) {
                    responseCache(cacheResponse, callback);
                }
                responseRemote(strategy, callback);
                break;
            default:
                responseRemote(strategy, callback);
                break;
        }
    }

    private void responseRemote(int cacheStrategy, Callback<T> callback) {
        getRawCall().enqueue(new okhttp3.Callback() {
            @Override
            public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) {
                try {
                    Response<T> res = parseResponse(response);
                    if (callback != null) {
                        callback.onResponse(HttpCacheCall.this, res);
                    }
                    if (res.isSuccessful() && cacheStrategy != StrategyType.NO_CACHE) {
                        Response<T> convert = cacheConverter.convert(res);
                        if (convert != null) {
                            cacheHelper.put(response, convert);
                        }
                    }
                } catch (Throwable e) {
                    callFailure(e);
                }
            }

            @Override
            public void onFailure(@NonNull okhttp3.Call call, @NonNull IOException e) {
                callFailure(e);
            }

            private void callFailure(Throwable e) {
                if (callback != null) {
                    callback.onFailure(HttpCacheCall.this, e);
                }
            }

        });
    }

    private void responseCache(okhttp3.Response cacheResponse, Callback<T> callback) {
        try {
            Response<T> res = parseResponse(cacheResponse);
            if (callback != null) {
                callback.onResponse(HttpCacheCall.this, res);
            }
        } catch (Throwable e) {
            callback.onFailure(HttpCacheCall.this, e);
        }
    }

    private okhttp3.Call getRawCall() {
        try {
            return rawCall;
        } catch (RuntimeException | Error e) {
            Utils.throwIfFatal(e); // Do not assign a fatal error to creationFailure.
            creationFailure = e;
            throw e;
        }
    }

    Response<T> parseResponse(okhttp3.Response rawResponse) throws IOException {
        if (rawResponse == null) {
            okhttp3.Response response = new okhttp3.Response.Builder().request(getRawCall().request())
                    .protocol(Protocol.HTTP_1_1).code(HttpURLConnection.HTTP_GATEWAY_TIMEOUT)
                    .message("Unsatisfiable Request (only-if-cached)")
                    .body(Util.EMPTY_RESPONSE).build();
            ResponseBody responseBody = response.body();
            NoContentResponseBody noContentResponseBody =
                    new NoContentResponseBody(responseBody.contentType(), responseBody.contentLength());
            return Response.error(HttpURLConnection.HTTP_GATEWAY_TIMEOUT, noContentResponseBody);
        }

        ResponseBody rawBody = rawResponse.body();
        // Remove the body's source (the only stateful object) so we can pass the response along.
        rawResponse = rawResponse
                .newBuilder()
                .body(new NoContentResponseBody(rawBody.contentType(), rawBody.contentLength()))
                .build();
        int code = rawResponse.code();
        if (code < 200 || code >= 300) {
            try {
                // Buffer the entire body to avoid future I/O.
                ResponseBody bufferedBody = Utils.buffer(rawBody);
                return Response.error(bufferedBody, rawResponse);
            } finally {
                rawBody.close();
            }
        }

        if (code == 204 || code == 205) {
            rawBody.close();
            return Response.success(null, rawResponse);
        }

        ExceptionCatchingResponseBody catchingBody = new ExceptionCatchingResponseBody(rawBody);
        try {
            T body = responseConverter.convert(catchingBody);
            return Response.success(body, rawResponse);
        } catch (RuntimeException e) {
            // If the underlying source threw an exception, propagate that rather than indicating it was
            // a runtime exception.
            catchingBody.throwIfCaught();
            throw e;
        }
    }

    @Override
    public boolean isExecuted() {
        return executed;
    }

    @Override
    public void cancel() {
        canceled = true;

        okhttp3.Call call;
        synchronized (this) {
            call = getRawCall();
        }
        if (call != null) {
            call.cancel();
        }
    }

    @Override
    public boolean isCanceled() {
        if (canceled) {
            return true;
        }
        synchronized (this) {
            return getRawCall() != null && getRawCall().isCanceled();
        }
    }

    @NonNull
    @Override
    public Call<T> clone() {
        return new HttpCacheCall<>(requestFactory, rawCall, responseConverter, cacheConverter, cacheHelper);
    }

    @NonNull
    @Override
    public Request request() {
        return getRawCall().request();
    }

    @NonNull
    @Override
    public Timeout timeout() {
        try {
            return getRawCall().timeout();
        } catch (Exception e) {
            throw new RuntimeException("Unable to create call.", e);
        }
    }


    static final class NoContentResponseBody extends ResponseBody {
        @Nullable
        private final MediaType contentType;
        private final long contentLength;

        NoContentResponseBody(@Nullable MediaType contentType, long contentLength) {
            this.contentType = contentType;
            this.contentLength = contentLength;
        }

        @Override
        public MediaType contentType() {
            return contentType;
        }

        @Override
        public long contentLength() {
            return contentLength;
        }

        @Override
        public BufferedSource source() {
            throw new IllegalStateException("Cannot read raw response body of a converted body.");
        }
    }

    static final class ExceptionCatchingResponseBody extends ResponseBody {
        private final ResponseBody delegate;
        private final BufferedSource delegateSource;
        @Nullable
        IOException thrownException;

        ExceptionCatchingResponseBody(ResponseBody delegate) {
            this.delegate = delegate;
            this.delegateSource =
                    Okio.buffer(
                            new ForwardingSource(delegate.source()) {
                                @Override
                                public long read(Buffer sink, long byteCount) throws IOException {
                                    try {
                                        return super.read(sink, byteCount);
                                    } catch (IOException e) {
                                        thrownException = e;
                                        throw e;
                                    }
                                }
                            });
        }

        @Override
        public MediaType contentType() {
            return delegate.contentType();
        }

        @Override
        public long contentLength() {
            return delegate.contentLength();
        }

        @Override
        public BufferedSource source() {
            return delegateSource;
        }

        @Override
        public void close() {
            delegate.close();
        }

        void throwIfCaught() throws IOException {
            if (thrownException != null) {
                throw thrownException;
            }
        }
    }
}
