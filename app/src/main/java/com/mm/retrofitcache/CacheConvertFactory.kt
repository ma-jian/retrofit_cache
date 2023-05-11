package com.mm.retrofitcache

import android.util.Log
import com.mm.http.RetrofitCache
import com.mm.http.cache.CacheConverter
import retrofit2.Response

/**
 * Created by : majian
 * Date : 2021/9/30
 * 自定义存储规则，决定是否存储当前请求,返回null 不存储
 */

class CacheConvertFactory : CacheConverter.Factory() {

    override fun <T> converterCache(retrofit: RetrofitCache): CacheConverter<T>? {
        return object : CacheConverter<T> {
            override fun convert(response: Response<T>): Response<T>? {
                val body = response.body()
                Log.e("CacheConvertFactory", body.toString())
                return response
            }

        }
    }

}