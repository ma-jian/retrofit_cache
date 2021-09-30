package com.mm.http.cache

import com.mm.http.RetrofitCache
import retrofit2.Response

/**
 * Created by : majian
 * Date : 2021/9/30
 */

interface CacheConverter<T> {
    fun convert(response: Response<T>): Response<T>?

    /**
     * Creates [CacheConverter] instances based on a type and target usage.
     */
    abstract class Factory {

        open fun <T> converterCache(retrofit: RetrofitCache): CacheConverter<T>? {
            return null
        }
    }
}
