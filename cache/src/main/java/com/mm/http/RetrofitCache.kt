package com.mm.http

import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import com.mm.http.DefaultCallAdapterFactory.ExecutorCallbackCall
import com.mm.http.cache.CacheConverter
import com.mm.http.cache.CacheHelper
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.lang.reflect.Type
import java.util.ArrayDeque
import java.util.Collections
import java.util.Deque
import java.util.Objects
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * Created by : majian
 * Date : 2021/8/24
 */
class RetrofitCache internal constructor(
    private val okHttpClient: OkHttpClient,
    /**
     * for create [Retrofit]
     *
     * @return return [Retrofit.Builder]
     */
    private val retrofitBuilder: Retrofit.Builder,
    internal val cacheHelper: CacheHelper?,
    private val converterFactories: List<Converter.Factory>,
    private val cacheConverterFactories: List<CacheConverter.Factory>,
    private val responseConverterFactories: List<ResponseConverter.Factory>,
    private val callAdapterFactories: List<CacheCallAdapter.Factory>,
    private val callbackExecutor: Executor?,
    private val hostInterceptor: DynamicHostInterceptor?,
    private val validateEagerly: Boolean
) {
    private val serviceMethodCache: MutableMap<Method, ServiceMethod<*>> = ConcurrentHashMap()

    //用于处理缓存逻辑拦截器预加载
    internal val cacheInterceptors: MutableList<Interceptor> = mutableListOf()

    //处理返回缓存数据时的拦截器(一般用于打印缓存数据日志)
    internal val cacheResponseInterceptors: MutableList<Interceptor> = mutableListOf()

    init {
        //预处理拦截器逻辑
        val client = callFactory()
        cacheInterceptors.addAll(client.interceptors)
        val iterator = cacheInterceptors.iterator()
        //忽略掉不需要预处理的拦截器
        while (iterator.hasNext()) {
            val interceptor = iterator.next()
            if (interceptor.javaClass.isAnnotationPresent(IgnoreInterceptor::class.java)) {
                val cacheHandle = interceptor.javaClass.getAnnotation(IgnoreInterceptor::class.java)?.cacheHandle
                if (cacheHandle == true) {
                    cacheResponseInterceptors.add(interceptor)
                }
                iterator.remove()
            }
        }
    }

    // Single-interface proxy creation guarded by parameter safety.
    fun <T> create(service: Class<T>): T {
        //缓存位置为空时直接跳过缓存处理逻辑
        if (cacheHelper == null) {
            return createService(service)
        }
        validateServiceInterface(service)
        return Proxy.newProxyInstance(service.classLoader, arrayOf<Class<*>>(service), object : InvocationHandler {
            @Throws(Throwable::class)
            override fun invoke(proxy: Any, method: Method, args: Array<Any>?): Any? {
                // If the method is a method from Object then defer to normal invocation.
                if (method.declaringClass == Any::class.java) {
                    return method.invoke(this, *(args ?: arrayOf()))
                }
                return loadServiceMethod(service, method).invoke(args ?: arrayOf())
            }
        }) as T
    }

    private fun validateServiceInterface(service: Class<*>) {
        require(service.isInterface) { "API declarations must be interfaces." }
        val check: Deque<Class<*>> = ArrayDeque(1)
        check.add(service)
        while (!check.isEmpty()) {
            val candidate = check.removeFirst()
            if (candidate.typeParameters.isNotEmpty()) {
                val message = StringBuilder("Type parameters are unsupported on ").append(candidate.name)
                if (candidate != service) {
                    message.append(" which is an interface of ").append(service.name)
                }
                throw IllegalArgumentException(message.toString())
            }
            Collections.addAll(check, *candidate.interfaces)
        }
        if (validateEagerly) {
            for (method in service.declaredMethods) {
                if (!Modifier.isStatic(method.modifiers)) {
                    loadServiceMethod(service, method)
                }
            }
        }
    }

    fun loadServiceMethod(service: Class<*>, method: Method): ServiceMethod<*> {
        var result = serviceMethodCache[method]
        if (result != null) return result
        synchronized(serviceMethodCache) {
            result = serviceMethodCache[method]
            if (result == null) {
                result = ServiceMethod.parseAnnotations<Any>(this, service, method)
                serviceMethodCache[method] = result as ServiceMethod<*>
            }
        }
        return result!!
    }

    /**
     * Returns a list of the factories tried when creating a [.callAdapter] call adapter}.
     */
    fun callAdapterFactories(): List<CacheCallAdapter.Factory?> {
        return callAdapterFactories
    }

    /**
     * Returns the [CacheCallAdapter] for `returnType` from the available [ ][.callAdapterFactories].
     *
     * @throws IllegalArgumentException if no call adapter available for `type`.
     */
    fun callAdapter(returnType: Type, annotations: Array<Annotation>): CacheCallAdapter<*, *> {
        return nextCallAdapter(null, returnType, annotations)
    }

    /**
     * Returns the [CacheCallAdapter] for `returnType` from the available [ ][.callAdapterFactories] except `skipPast`.
     *
     * @throws IllegalArgumentException if no call adapter available for `type`.
     */
    private fun nextCallAdapter(
        skipPast: CacheCallAdapter.Factory?, returnType: Type, annotations: Array<Annotation>
    ): CacheCallAdapter<*, *> {
        Objects.requireNonNull(returnType, "returnType == null")
        Objects.requireNonNull(annotations, "annotations == null")
        val start = callAdapterFactories.indexOf(skipPast) + 1
        for (i in start until callAdapterFactories.size) {
            val adapter = callAdapterFactories[i][returnType, annotations, this]
            if (adapter != null) {
                return adapter;
            }
        }
        val builder = StringBuilder("Could not locate call adapter for ").append(returnType).append(".\n")
        if (skipPast != null) {
            builder.append("  Skipped:")
            for (i in 0 until start) {
                builder.append("\n   * ").append(callAdapterFactories[i]!!.javaClass.name)
            }
            builder.append('\n')
        }
        builder.append("  Tried:")
        var i = start
        val count = callAdapterFactories.size
        while (i < count) {
            builder.append("\n   * ").append(callAdapterFactories[i]!!.javaClass.name)
            i++
        }
        throw IllegalArgumentException(builder.toString())
    }

    fun converterFactories(): List<Converter.Factory?> {
        return converterFactories
    }

    /**
     * create retrofit default service
     *
     * @param clazz
     * @return return service
     */
    fun <T> createService(clazz: Class<T>): T {
        require(clazz.getAnnotation(HOST::class.java) != null) {
            "请添加 @HOST.class"
        }
        require(!TextUtils.isEmpty(clazz.getAnnotation(HOST::class.java)?.value)) {
            "请指定相应的host参数 HOST.class value"
        }
        val annotation = clazz.getAnnotation(HOST::class.java)
        return if (hostInterceptor != null) {
            val hostUrl = hostInterceptor.hostUrl(annotation!!)
            retrofitBuilder.baseUrl(hostUrl).build().create(clazz)
        } else {
            val host = annotation!!.value
            retrofitBuilder.baseUrl(host).build().create(clazz)
        }
    }

    /**
     * The factory used to create [OkHttp calls][okhttp3.Call] for sending a HTTP requests.
     * Typically an instance of [OkHttpClient].
     */
    fun callFactory(): OkHttpClient {
        return okHttpClient
    }

    /**
     * Returns a [Converter] for [ResponseBody] to `type` from the available
     * [factories][.converterFactories].
     *
     * @throws IllegalArgumentException if no converter available for `type`.
     */
    fun <T> responseBodyConverter(type: Type, annotations: Array<Annotation>?): Converter<ResponseBody, T> {
        return nextResponseBodyConverter(null, type, annotations)
    }

    /**
     * Returns a [Converter] for [ResponseBody] to `type` from the available
     * [factories][.converterFactories] except `skipPast`.
     *
     * @throws IllegalArgumentException if no converter available for `type`.
     */
    private fun <T> nextResponseBodyConverter(
        skipPast: Converter.Factory?, type: Type, annotations: Array<Annotation>?
    ): Converter<ResponseBody, T> {
        Objects.requireNonNull(type, "type == null")
        Objects.requireNonNull(annotations, "annotations == null")
        val start = converterFactories.indexOf(skipPast) + 1
        val count = converterFactories.size
        for (i in start until count) {
            val converter = converterFactories[i].responseBodyConverter<T>(type, annotations, this)
            if (converter != null) {
                return converter
            }
        }
        val builder = StringBuilder("Could not locate ResponseBody converter for ").append(type).append(".\n")
        if (skipPast != null) {
            builder.append("  Skipped:")
            for (i in 0 until start) {
                builder.append("\n   * ").append(converterFactories[i]!!.javaClass.name)
            }
            builder.append('\n')
        }
        builder.append("  Tried:")
        var i = start
        while (i < count) {
            builder.append("\n   * ").append(converterFactories[i]!!.javaClass.name)
            i++
        }
        throw IllegalArgumentException(builder.toString())
    }

    /**
     * Returns a [CacheConverter] for [ResponseBody] to `type` from the available
     * [factories][.converterFactories].
     *
     * @throws IllegalArgumentException if no converter available for `type`.
     */
    fun <T> responseCacheConverter(type: Type): CacheConverter<T> {
        return nextCacheResponseConverter(null, type)
    }

    private fun <T> nextCacheResponseConverter(skipPast: CacheConverter.Factory?, type: Type): CacheConverter<T> {
        val start = cacheConverterFactories.indexOf(skipPast) + 1
        val count = cacheConverterFactories.size
        for (i in start until count) {
            val converter: CacheConverter<T>? = cacheConverterFactories[i].converterCache<T>(this)
            if (converter != null) {
                return converter
            }
        }
        val builder = StringBuilder("Could not locate Cache converter for ").append(type).append(".\n")
        if (skipPast != null) {
            builder.append("  Skipped:")
            for (i in 0 until start) {
                builder.append("\n   * ").append(converterFactories[i].javaClass.name)
            }
            builder.append('\n')
        }
        builder.append("  Tried:")
        var i = start
        while (i < count) {
            builder.append("\n   * ").append(converterFactories[i].javaClass.name)
            i++
        }
        throw IllegalArgumentException(builder.toString())
    }

    /**
     * Returns a [ResponseConverter] for [ResponseBody] to `type` from the available
     *
     * @throws IllegalArgumentException if no converter available for `type`.
     */
    fun <T> responseConverter(type: Type): ResponseConverter<T> {
        return nextResponseConverter(null, type)
    }

    private fun <T> nextResponseConverter(skipPast: ResponseConverter.Factory?, type: Type): ResponseConverter<T> {
        val start = responseConverterFactories.indexOf(skipPast) + 1
        val count = responseConverterFactories.size
        for (i in start until count) {
            val converter = responseConverterFactories[i].converterResponse(this)
            if (converter != null) {
                return converter as ResponseConverter<T>
            }
        }
        val builder = StringBuilder("Could not locate response converter for ").append(type).append(".\n")
        if (skipPast != null) {
            builder.append("  Skipped:")
            for (i in 0 until start) {
                builder.append("\n   * ").append(responseConverterFactories[i].javaClass.name)
            }
            builder.append('\n')
        }
        builder.append("  Tried:")
        var i = start
        while (i < count) {
            builder.append("\n   * ").append(responseConverterFactories[i].javaClass.name)
            i++
        }
        throw IllegalArgumentException(builder.toString())
    }

    /**
     * The executor used for [ExecutorCallbackCall] methods on a [ExecutorCallbackCall.enqueue]. This may be `null`, in
     * which case callbacks should be made synchronously on the background thread.
     */
    fun callbackExecutor(): Executor? {
        return callbackExecutor
    }

    fun newBuilder(): Builder {
        return Builder(this)
    }

    class Builder constructor() {
        private var cacheHelper: CacheHelper? = null
        private val converterFactories: MutableList<Converter.Factory> = ArrayList()
        private val cacheConverterFactories: MutableList<CacheConverter.Factory> = ArrayList()
        private val responseConverterFactories: MutableList<ResponseConverter.Factory> = ArrayList()
        private val callAdapterFactories: MutableList<CacheCallAdapter.Factory> = ArrayList()
        private val interceptors: MutableList<Interceptor> = ArrayList()
        private var callbackExecutor: Executor? = null
        private var validateEagerly = false
        private var okHttpClient: OkHttpClient? = null
        private var hostInterceptor: DynamicHostInterceptor? = null

        constructor(retrofitCache: RetrofitCache) : this() {
            cacheHelper = retrofitCache.cacheHelper
            okHttpClient = retrofitCache.okHttpClient
            hostInterceptor = retrofitCache.hostInterceptor

            // Do not add the default CacheGsonConverterFactory.
            val size = retrofitCache.converterFactories.size - 1
            for (i in 0..size) {
                converterFactories.add(retrofitCache.converterFactories[i])
            }

            // Do not add the default DefaultCallAdapterFactory.
            val count = retrofitCache.callAdapterFactories.size - 1
            for (i in 0..count) {
                callAdapterFactories.add(retrofitCache.callAdapterFactories[i])
            }

            // Do not add the default DefaultCallAdapterFactory.
            val num = retrofitCache.cacheConverterFactories.size - 1
            for (i in 0..num) {
                cacheConverterFactories.add(retrofitCache.cacheConverterFactories[i])
            }

            // Do not add the default DefaultCacheConverterFactory.
            val list = retrofitCache.responseConverterFactories.size - 1
            for (i in 0..list) {
                responseConverterFactories.add(retrofitCache.responseConverterFactories[i])
            }

            callbackExecutor = retrofitCache.callbackExecutor
            validateEagerly = retrofitCache.validateEagerly
        }

        /**
         * use [Builder.build] before set [CacheHelper]
         * the cache support is set to null
         * @since 1.01
         */
        fun cache(cache: CacheHelper?) = apply {
            cache?.let { this.cacheHelper = cache }
        }

        /**
         * The HTTP client used for requests.
         */
        fun client(client: OkHttpClient?) = apply {
            client?.let { this.okHttpClient = client }
        }

        /**
         * Add converter factory for serialization and deserialization of objects.
         */
        fun addConverterFactory(factory: Converter.Factory?) = apply {
            factory?.let { converterFactories.add(it) }
        }

        /**
         * Add cache converter factory for save cache
         */
        fun addCacheConverterFactory(factory: CacheConverter.Factory?) = apply {
            factory?.let { cacheConverterFactories.add(it) }
        }

        /**
         * Add response converter factory for process request results.
         */
        fun addResponseConverterFactory(factory: ResponseConverter.Factory?) = apply {
            factory?.let { responseConverterFactories.add(it) }
        }

        /**
         * Add a call adapter factory for supporting service method return types other than [ ].
         */
        fun addCallAdapterFactory(factory: CacheCallAdapter.Factory?) = apply {
            factory?.let { callAdapterFactories.add(it) }
        }

        /**
         * The executor on which [Callback] methods are invoked when returning [Call] from
         * your service method.
         *
         *
         * Note: `executor` is not used for [custom method][.addCallAdapterFactory].
         */
        fun callbackExecutor(executor: Executor) = apply {
            callbackExecutor = Objects.requireNonNull(executor, "executor == null")
        }

        /**
         * Returns a modifiable list of call adapter factories.
         */
        fun callAdapterFactories(): List<CacheCallAdapter.Factory> {
            return callAdapterFactories
        }

        /**
         * Returns a modifiable list of converter factories.
         */
        fun converterFactories(): List<Converter.Factory> {
            return converterFactories
        }

        /**
         * When calling [.create] on the resulting [RetrofitCache] instance, eagerly validate the
         * configuration of all methods in the supplied interface.
         */
        fun validateEagerly(validateEagerly: Boolean) = apply {
            this.validateEagerly = validateEagerly
        }

        /**
         * add [Interceptor] for default HTTP client
         */
        fun addInterceptor(interceptor: Interceptor) = apply {
            if (okHttpClient != null) {
                okHttpClient = okHttpClient!!.newBuilder().addInterceptor(interceptor).build()
            }
            interceptors.add(Objects.requireNonNull(interceptor, "interceptor == null"))
        }

        /**
         * add [DynamicHostInterceptor] for default Host value
         */
        fun addHostInterceptor(interceptor: DynamicHostInterceptor?) = apply {
            hostInterceptor = interceptor
        }

        /**
         * Create the [RetrofitCache] instance using the configured values.
         */
        fun build(): RetrofitCache {
            var callbackExecutor = callbackExecutor
            if (callbackExecutor == null) {
                callbackExecutor = MainThreadExecutor()
            }

            // Make a defensive copy of the adapters and add the default Call adapter.
            val callAdapterFactories: MutableList<CacheCallAdapter.Factory> = ArrayList(callAdapterFactories)
            callAdapterFactories.add(DefaultCallAdapterFactory(callbackExecutor))

            val converterFactories: MutableList<Converter.Factory> = ArrayList(converterFactories)
            converterFactories.add(CacheGsonConverterFactory.create())

            val cacheConverterFactories: MutableList<CacheConverter.Factory> = ArrayList(cacheConverterFactories)
            cacheConverterFactories.add(DefaultCacheConverterFactory())

            val responseConverterFactories: MutableList<ResponseConverter.Factory> = ArrayList(responseConverterFactories)
            responseConverterFactories.add(DefaultResponseConverterFactory())

            if (okHttpClient == null) {
                val builder: OkHttpClient.Builder =
                    OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS).addInterceptor(TimeOutInterceptor())
                        .sslSocketFactory(SslSocketFactory.sSLSocketFactory, SslSocketFactory.trustManager)
                for (interceptor in interceptors) {
                    builder.addInterceptor(interceptor)
                }
                okHttpClient = builder.build()
            }
            val builder = Retrofit.Builder().client(okHttpClient!!).addConverterFactory(GsonExceptionFactory.create())
            return RetrofitCache(
                okHttpClient!!,
                builder,
                cacheHelper,
                Collections.unmodifiableList(converterFactories),
                Collections.unmodifiableList(cacheConverterFactories),
                Collections.unmodifiableList(responseConverterFactories),
                Collections.unmodifiableList(callAdapterFactories),
                callbackExecutor,
                hostInterceptor,
                validateEagerly
            )
        }
    }

    class MainThreadExecutor : Executor {
        private val handler = Handler(Looper.getMainLooper())
        override fun execute(r: Runnable) {
            handler.post(r)
        }
    }
}