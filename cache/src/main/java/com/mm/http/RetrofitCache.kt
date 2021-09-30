package com.mm.http

import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import com.mm.http.ServiceMethod.Companion.parseAnnotations
import com.mm.http.cache.CacheConverter
import com.mm.http.cache.CacheHelper
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.reflect.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * Created by : majian
 * Date : 2021/8/24
 */
open class RetrofitCache internal constructor(
    private val okHttpClient: OkHttpClient,
    /**
     * for create [Retrofit]
     * @return return [Retrofit.Builder]
     */
    private val retrofitBuilder: Retrofit.Builder,
    internal val cache: CacheHelper,
    private val converterFactories: List<Converter.Factory>,
    private val cacheConverterFactories: List<CacheConverter.Factory>,
    private val callAdapterFactories: List<CacheCallAdapter.Factory>,
    private val callbackExecutor: Executor?,
    private val validateEagerly: Boolean
) {
    private val serviceMethodCache: MutableMap<Method, ServiceMethod<*>> = ConcurrentHashMap()

    // Single-interface proxy creation guarded by parameter safety.
    fun <T> create(service: Class<T>): T {
        validateServiceInterface(service)
        return Proxy.newProxyInstance(
            service.classLoader, arrayOf<Class<*>>(service),
            object : InvocationHandler {
                private val emptyArgs = arrayOfNulls<Any>(0)

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
                val message =
                    StringBuilder("Type parameters are unsupported on ").append(candidate.name)
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

    private fun loadServiceMethod(service: Class<*>, method: Method): ServiceMethod<*> {
        var result = serviceMethodCache[method]
        if (result != null) return result
        synchronized(serviceMethodCache) {
            result = serviceMethodCache[method]
            if (result == null) {
                result = parseAnnotations<Any>(this, service, method)
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
    internal fun callAdapter(
        returnType: Type,
        annotations: Array<Annotation>
    ): CacheCallAdapter<*, *> {
        return nextCallAdapter(null, returnType, annotations)
    }

    /**
     * Returns the [CacheCallAdapter] for `returnType` from the available [ ][.callAdapterFactories] except `skipPast`.
     *
     * @throws IllegalArgumentException if no call adapter available for `type`.
     */
    private fun nextCallAdapter(
        skipPast: CacheCallAdapter.Factory?,
        returnType: Type,
        annotations: Array<Annotation>
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
        val builder =
            StringBuilder("Could not locate call adapter for ").append(returnType).append(".\n")
        if (skipPast != null) {
            builder.append("  Skipped:")
            for (i in 0 until start) {
                builder.append("\n   * ").append(callAdapterFactories[i].javaClass.name)
            }
            builder.append('\n')
        }
        builder.append("  Tried:")
        var i = start
        val count = callAdapterFactories.size
        while (i < count) {
            builder.append("\n   * ").append(callAdapterFactories[i].javaClass.name)
            i++
        }
        throw IllegalArgumentException(builder.toString())
    }

    fun converterFactories(): List<Converter.Factory?> {
        return converterFactories
    }

    /**
     * create retrofit service
     * @param clazz
     * @return return service
     */
    internal fun <T> createService(clazz: Class<T>): T {
        val host = clazz.getAnnotation(HOST::class.java)?.value ?: ""
        require(!TextUtils.isEmpty(host)) { "请指定相应的host参数 HOST.class value" }
        return retrofitBuilder.baseUrl(host).build().create(clazz)
    }

    /**
     * The factory used to create [OkHttp calls][okhttp3.Call] for sending a HTTP requests.
     * Typically an instance of [OkHttpClient].
     */
    fun callFactory(): OkHttpClient {
        return okHttpClient
    }

    /**
     * Returns a [CacheConverter] for [ResponseBody] to `type` from the available
     * [factories][.converterFactories].
     *
     * @throws IllegalArgumentException if no converter available for `type`.
     */
    internal fun <T> responseCacheConverter(type: Type): CacheConverter<T> {
        return nextCacheResponseConverter<T>(null, type)
    }

    private fun <T> nextCacheResponseConverter(
        skipPast: CacheConverter.Factory?,
        type: Type
    ): CacheConverter<T> {
        val start: Int = cacheConverterFactories.indexOf(skipPast) + 1
        for (i in start until cacheConverterFactories.size) {
            val converter = cacheConverterFactories[i].converterCache<T>(retrofit = this)
            if (converter != null) {
                return converter
            }
        }
        val builder = java.lang.StringBuilder("Could not locate Cache converter for ")
            .append(type)
            .append(".\n")
        if (skipPast != null) {
            builder.append("  Skipped:")
            for (i in 0 until start) {
                builder.append("\n   * ").append(converterFactories[i]::class.java.name)
            }
            builder.append('\n')
        }
        builder.append("  Tried:")
        var i = start
        val count = converterFactories.size
        while (i < count) {
            builder.append("\n   * ").append(converterFactories[i]::class.java.name)
            i++
        }
        throw java.lang.IllegalArgumentException(builder.toString())
    }

    /**
     * Returns a [Converter] for [ResponseBody] to `type` from the available
     * [factories][.converterFactories].
     *
     * @throws IllegalArgumentException if no converter available for `type`.
     */
    internal fun <T> responseBodyConverter(
        type: Type,
        annotations: Array<Annotation>?
    ): Converter<ResponseBody, T> {
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

        val builder = StringBuilder("Could not locate ResponseBody converter for ")
            .append(type)
            .append(".\n")
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
     * The executor used for [Callback] methods on a [Call]. This may be `null`, in
     * which case callbacks should be made synchronously on the background thread.
     */
    fun callbackExecutor(): Executor? {
        return callbackExecutor
    }

    fun newBuilder(): Builder {
        return Builder(this)
    }

    class Builder constructor() {
        private var cache: CacheHelper? = null
        private val converterFactories: MutableList<Converter.Factory?> = ArrayList()
        private val cacheConverterFactories: MutableList<CacheConverter.Factory?> = ArrayList()
        private val callAdapterFactories: MutableList<CacheCallAdapter.Factory?> = ArrayList()
        private val interceptors: MutableList<Interceptor> = ArrayList()
        private var callbackExecutor: Executor? = null
        private var validateEagerly = false
        private var okHttpClient: OkHttpClient? = null

        constructor(retrofitCache: RetrofitCache) : this() {
            cache = retrofitCache.cache
            okHttpClient = retrofitCache.okHttpClient

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

            callbackExecutor = retrofitCache.callbackExecutor
            validateEagerly = retrofitCache.validateEagerly
        }

        /**
         * use [RetrofitCache.Builder.build] before set [CacheHelper]
         * the cache be required
         */
        fun cache(cache: CacheHelper) = apply {
            this.cache = cache
        }

        /**
         * The HTTP client used for requests.
         */
        fun client(client: OkHttpClient?) = apply {
            require(client != null) { "client == null" }
            this.okHttpClient = client
        }

        /**
         * Add cache converter factory for save cache
         */
        fun addCacheConverterFactory(factory: CacheConverter.Factory?) = apply {
            cacheConverterFactories.add(Objects.requireNonNull(factory, "factory == null"))
        }

        /**
         * Add converter factory for serialization and deserialization of objects.
         */
        fun addConverterFactory(factory: Converter.Factory?) = apply {
            converterFactories.add(Objects.requireNonNull(factory, "factory == null"))
        }

        /**
         * Add a call adapter factory for supporting service method return types other than [ ].
         */
        fun addCallAdapterFactory(factory: CacheCallAdapter.Factory?) = apply {
            callAdapterFactories.add(Objects.requireNonNull(factory, "factory == null"))
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
        fun callAdapterFactories(): List<CacheCallAdapter.Factory?> {
            return callAdapterFactories
        }

        /**
         * Returns a modifiable list of converter factories.
         */
        fun converterFactories(): List<Converter.Factory?> {
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
        fun addInterceptor(interceptor: Interceptor?) = apply {
            require(interceptor != null) { "interceptor == null" }
            interceptors.add(interceptor)
        }

        /**
         * Create the [RetrofitCache] instance using the configured values.
         */
        fun build(): RetrofitCache {
            checkNotNull(cache) { "cache be required." }
            var callbackExecutor = callbackExecutor
            if (callbackExecutor == null) {
                callbackExecutor = MainThreadExecutor()
            }

            // Make a defensive copy of the adapters and add the default Call adapter.
            val callAdapterFactories: MutableList<CacheCallAdapter.Factory> = ArrayList(
                this.callAdapterFactories
            )
            callAdapterFactories.add(DefaultCallAdapterFactory(callbackExecutor))

            val converterFactories: MutableList<Converter.Factory> =
                ArrayList(this.converterFactories)
            converterFactories.add(CacheGsonConverterFactory.create())

            val cacheConverterFactories: MutableList<CacheConverter.Factory> =
                ArrayList(this.cacheConverterFactories)
            cacheConverterFactories.add(DefaultCacheConverterFactory())

            if (okHttpClient == null) {
                val builder: OkHttpClient.Builder = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .sslSocketFactory(
                        SslSocketFactory.sSLSocketFactory,
                        SslSocketFactory.trustManager
                    )
                for (interceptor in interceptors) {
                    builder.addInterceptor(interceptor)
                }
                okHttpClient = builder.build()
            }

            val builder = Retrofit.Builder().client(okHttpClient!!)
                .addConverterFactory(GsonConverterFactory.create())
            return RetrofitCache(
                okHttpClient!!,
                builder,
                cache!!,
                Collections.unmodifiableList(converterFactories),
                Collections.unmodifiableList(cacheConverterFactories),
                Collections.unmodifiableList(callAdapterFactories),
                callbackExecutor,
                validateEagerly
            )
        }
    }

    internal class MainThreadExecutor : Executor {
        private val handler = Handler(Looper.getMainLooper())
        override fun execute(r: Runnable) {
            handler.post(r)
        }
    }
}