package com.mm.retrofitcache

import com.mm.http.HOST
import com.mm.http.cache.CacheStrategy
import com.mm.http.cache.StrategyType
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * 
 * Date : 2021/9/18
 */

@HOST("https://api.github.com/", hostType = 2)
interface MyService {

    @CacheStrategy(value = StrategyType.CACHE_AND_NETWORK)
    @GET("users/{user}")
    fun getUser(@Path("user") user: String): Call<Any>

    @CacheStrategy(value = StrategyType.NO_CACHE)
    @GET("users/{user}")
    suspend fun getUser2(@Path("user") user: String): Any

    @GET("users/{user}")
    suspend fun getUser3(@Path("user") user: String): Response<Any>
}