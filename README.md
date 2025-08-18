# retrofit_cache

**一个注解实现对Retrofit框架的网络数据缓存**

```
implementation 'io.github.ma-jian:retrofit-cache:1.2.0'
```

支持多种缓存逻辑

- NO_CACHE = -1 //不缓存
- FORCE_NETWORK = 1 // 仅网络
- FORCE_CACHE = 2 // 仅缓存
- IF_CACHE_ELSE_NETWORK = 3 // 优先缓存
- IF_NETWORK_ELSE_CACHE = 4 // 优先网络
- CACHE_AND_NETWORK = 5 // 先缓存后网络

### **CHANGELOG**

#### v1.2.0
1. 新增对suspend函数的支持
2. 完全兼容retrofit原生支持的返回值类型
3. 最高支持retrofit：2.10.0 okhttp：4.12.0
4. 修复bug

#### v1.1.1
1. 修复bug

#### v1.1

1. 新增注解[HOST](cache/src/main/java/com/mm/http/HOST.kt) 支持多baseUrl设置，可自定义动态切换
2. 新增支持自定义解析数据，方便对Response数据进行统一处理
3. 新增注解[IgnoreInterceptor](cache/src/main/java/com/mm/http/IgnoreInterceptor.kt) 可用于忽略拦截器进入缓存预处理逻辑
4. 修复[RetrofitCache](cache/src/main/java/com/mm/http/RetrofitCache.kt) 中newBuilder()无法正确使用的问题
5. 修改其他已知的bug
6. HTTP网络扩展新增支持返回Result<T>结果
7. 移除cache路径必须设置的限制; 缓存路径为null时直接返回原始Retrofit逻辑

#### 使用示例
```kotlin
// CACHE_AND_NETWORK 策略下不支持使用suspend函数
@CacheStrategy(value = StrategyType.CACHE_AND_NETWORK)
@GET("users/{user}")
fun getUser(@Path("user") user: String): Call<Any>

@CacheStrategy(value = StrategyType.NO_CACHE)
@GET("users/{user}")
suspend fun getUser2(@Path("user") user: String): Any

@GET("users/{user}")
suspend fun getUser3(@Path("user") user: String): Response<Any>
```

```kotlin
service.getUser(binding.input.text.toString()).asCallFlow().collect {
    Log.e("user", it.toString())
}
```
