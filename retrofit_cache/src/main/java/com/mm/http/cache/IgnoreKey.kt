package com.mm.http.cache

/**
 *
 * 缓存[CacheHelper.key]key值:以url链接和 get query参数 和 post body 参数拼接形成
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class IgnoreKey(vararg val value: String = [""])