package com.mm.retrofitcache;

import com.mm.http.cache.CacheHelper;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;
import okio.BufferedSource;

/**
 * Created by : majian
 * Date : 2021/8/30
 */

public class HeaderInterceptor implements Interceptor {

    @NotNull
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
        Request request = chain.request();
        Response response = chain.proceed(request);
        if (response.code() < 500) {
            synchronized (this) {
                log("↓↓↓ ---------------------------------------------------------------------------- ↓↓↓");
                log("--> " + request.method() + " " + request.url());
                RequestBody body = request.body();
                if (body != null) {
                    MediaType contentType = body.contentType();
                    Charset charset;
                    if (contentType != null) {
                        charset = contentType.charset(StandardCharsets.UTF_8);
                    } else {
                        charset = StandardCharsets.UTF_8;
                    }
                    Buffer buffer = new Buffer();
                    body.writeTo(buffer);
                    String readString = buffer.readString(charset);
                    log("--> " + "body: " + readString);
                }

                Headers headers = request.headers();
                if (headers.size() > 0) {
                    log("--> --------------------------------Request  Headers-------------------------------- ");
                }
                for (int i = 0; i < headers.size(); i++) {
                    log("--> " + headers.name(i) + ": " + headers.value(i));
                }

                Headers resHeader = response.headers();
                if (resHeader.size() > 0) {
                    log("--> --------------------------------Response Headers-------------------------------- ");
                }
                for (int i = 0; i < resHeader.size(); i++) {
                    log("--> " + resHeader.name(i) + ": " + resHeader.value(i));
                }
                if (resHeader.size() > 0 || headers.size() > 0) {
                    log("--> -------------------------------------------------------------------------------- ");
                }

                if (response.body() != null) {
                    BufferedSource source = response.body().source();
                    source.request(Long.MAX_VALUE);
                    Buffer buffer = source.getBuffer();
                    String data = buffer.clone().readString(StandardCharsets.UTF_8);
                    log(data);
                    log("↑↑↑ --------------------------- END HTTP (" + buffer.size() + ") byte --------------------------- ↑↑↑");
                }
            }
        }
        return response;
    }

    private void log(String msg) {
        if (!BuildConfig.BUILD_TYPE.equals("release")) {
            if (msg.length() <= 3 * 1024) {
                Logger.getLogger("OkHttpClient").log(Level.INFO, msg);
            } else {
                String substring = msg.substring(0, 3 * 1024);
                Logger.getLogger("OkHttpClient").log(Level.INFO, substring);
                log(msg.substring(3 * 1024));
            }
        }
    }
}
