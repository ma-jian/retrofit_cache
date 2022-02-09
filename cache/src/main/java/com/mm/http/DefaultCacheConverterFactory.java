package com.mm.http;

import androidx.annotation.Nullable;


import com.mm.http.cache.CacheConverter;

import retrofit2.Response;

/**
 * Created by : majian
 * Date : 2021/9/29
 */

public class DefaultCacheConverterFactory extends CacheConverter.Factory {

    @Nullable
    @Override
    public CacheConverter<?> converterCache(RetrofitCache retrofit) {
        return new CacheConverter<Object>() {
            @Nullable
            @Override
            public Response<Object> convert(Response<Object> response) {
                return response;
            }
        };
    }

}
