# retrofit_cache

**一个注解实现对Retrofit框架的网络数据缓存**

支持多种缓存逻辑
- NO_CACHE = -1 //不缓存
- FORCE_NETWORK = 1 // 仅网络
- FORCE_CACHE = 2 // 仅缓存
- IF_CACHE_ELSE_NETWORK = 3 // 优先缓存
- IF_NETWORK_ELSE_CACHE = 4 // 优先网络
- CACHE_AND_NETWORK = 5 // 先缓存后网络
- CACHE_AND_NETWORK_DIFF = 6 // 暂未实现该策略

### **CHANGELOG**
#### v1.1
1. 新增注解[HOST](cache/src/main/java/com/mm/http/HOST.kt) 支持多baseUrl设置，可自定义动态切换
2. 新增支持自定义解析数据，方便对Response数据进行统一处理
3. 新增注解[IgnoreInterceptor](cache/src/main/java/com/mm/http/IgnoreInterceptor.kt) 可用于忽略拦截器进入缓存预处理逻辑
4. 修复[RetrofitCache](cache/src/main/java/com/mm/http/RetrofitCache.kt) 中newBuilder()无法正确使用的问题
5. 修改其他已知的bug
6. HTTP网络扩展新增支持返回Result<T>结果
7. 移除cache路径必须设置的限制; 缓存路径为null时直接返回原始Retrofit逻辑

```kotlin
private fun createCache(): RetrofitCache {
    val cacheHelper = CacheHelper(cacheDir, Long.MAX_VALUE)
    return RetrofitCache.Builder().cache(cacheHelper)
        .addCacheConverterFactory(CacheConvertFactory())
        .addResponseConverterFactory(object : ResponseConverter.Factory() {
            override fun converterResponse(retrofit: RetrofitCache): ResponseConverter<Any>? {
                //自定义修改结果并返回Response
                return super.converterResponse(retrofit)
            }
        })
        .addHostInterceptor(object : DynamicHostInterceptor {
            override fun hostUrl(host: HOST): HttpUrl {
                return when (host.hostType) {
                    1 -> "https://api.github.com/".toHttpUrl()
                    else -> super.hostUrl(host)
                }
            }
        })
        .addInterceptor(LogInterceptor())
        .build()
}
```

```kotlin
service.getUser(binding.input.text.toString()).asResultFlow().collect {
    it.onSuccess { value ->
        binding.text.text = value.toString()
    }
    it.onFailure { error ->
        binding.text.text = error.toString()
    }
}
```