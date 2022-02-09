package com.mm.retrofitcache

import android.util.Log
import com.mm.http.RetrofitCache
import com.mm.http.cache.CacheConverter

/**
 * Created by : majian
 * Date : 2021/9/30
 * 自定义存储规则，决定是否存储当前请求
 */

class CacheConvertFactory : CacheConverter.Factory() {

    override fun converterCache(retrofit: RetrofitCache): CacheConverter<*>? {
        return CacheConverter<Any> { response ->
            val body = response.body()
            Log.e("CacheConvertFactory ：", body.toString())
            response
        }
    }
}