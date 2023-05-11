package com.mm.http

import com.mm.http.cache.CacheConverter
import retrofit2.Response

/**
 * Created by : majian
 * Date : 2021/9/30
 */


class DefaultCacheConverterFactory : CacheConverter.Factory() {

    override fun <T> converterCache(retrofit: RetrofitCache): CacheConverter<T> {
        return object : CacheConverter<T> {
            override fun convert(response: Response<T>): Response<T>? {
                return response
            }
        }
    }
}