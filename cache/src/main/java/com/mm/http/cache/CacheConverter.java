package com.mm.http.cache;


import com.mm.http.RetrofitCache;
import javax.annotation.Nullable;
import retrofit2.Response;

/**
 * Created by : majian
 * Date : 2021/8/24
 * CacheConverter
 */

public interface CacheConverter<T> {
    @Nullable
    Response<T> convert(Response<T> response);

    /**
     * Creates {@link CacheConverter} instances based on a type and target usage.
     */
    abstract class Factory {

        @Nullable
        public CacheConverter<?> converterCache(RetrofitCache retrofit) {
            return null;
        }
    }
}
