package com.mm.http.cache

/**
 * Created by : majian
 * Date : 2021/8/24
 * 设置忽略缓存策略。执行优先级高于[CacheStrategy]
 * ```
 *  @IgnoreCache
 *  @CacheStrategy(value = StrategyType.CACHE_AND_NETWORK)
 *  @GET("users/{user}")
 *  suspend fun getUser(@Path("user") user: String): Any
 * ```
 */

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class IgnoreCache