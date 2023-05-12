package com.mm.retrofitcache

import com.mm.http.HOST
import com.mm.http.cache.CacheStrategy
import com.mm.http.cache.IgnoreCache
import com.mm.http.cache.StrategyType
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Created by : majian
 * Date : 2021/9/18
 */

@HOST("https://api.github.com/", hostType = 1)
interface MyService {

    @CacheStrategy(value = StrategyType.NO_CACHE)
    @GET("users/{user}")
    fun getUser(@Path("user") user: String): Call<Any>
}