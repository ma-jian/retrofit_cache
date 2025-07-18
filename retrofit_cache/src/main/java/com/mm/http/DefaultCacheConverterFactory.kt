package com.mm.http

import com.mm.http.cache.CacheConverter
import retrofit2.Response

/**
 * 默认的缓存转换器工厂
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