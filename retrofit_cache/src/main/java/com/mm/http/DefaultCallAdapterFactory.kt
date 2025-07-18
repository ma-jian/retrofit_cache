package com.mm.http

import okhttp3.Request
import okio.Timeout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.SkipCallbackExecutor
import java.io.IOException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.Objects
import java.util.concurrent.Executor

/**
 * 默认的CallAdapterFactory
 */
class DefaultCallAdapterFactory internal constructor(private val callbackExecutor: Executor) : CacheCallAdapter.Factory() {

    override fun get(returnType: Type, annotations: Array<Annotation>, retrofit: RetrofitCache): CacheCallAdapter<*, *>? {
        if (getRawType(returnType) != Call::class.java) {
            return null
        }
        require(returnType is ParameterizedType) { "Call return type must be parameterized as Call<Foo> or Call<? extends Foo>" }
        val responseType = getParameterUpperBound(0, returnType)
        val executor = if (annotations.isAnnotationPresent(SkipCallbackExecutor::class.java)) null else callbackExecutor
        return object : CacheCallAdapter<Any, Call<Any>?> {
            override fun responseType(): Type {
                return responseType
            }

            override fun adapt(call: Call<Any>): Call<Any> {
                return if (executor == null) call else ExecutorCallbackCall(executor, call)
            }
        }
    }

    internal class ExecutorCallbackCall<T>(val callbackExecutor: Executor, val delegate: Call<T>) : Call<T> {
        override fun enqueue(callback: Callback<T>) {
            Objects.requireNonNull(callback, "callback == null")
            delegate.enqueue(object : Callback<T> {
                override fun onResponse(call: Call<T>, response: Response<T>) {
                    callbackExecutor.execute {
                        if (delegate.isCanceled) {
                            callback.onFailure(this@ExecutorCallbackCall, IOException("Canceled"))
                        } else {
                            callback.onResponse(this@ExecutorCallbackCall, response)
                        }
                    }
                }

                override fun onFailure(call: Call<T>, t: Throwable) {
                    callbackExecutor.execute { callback.onFailure(this@ExecutorCallbackCall, t) }
                }
            })
        }

        override fun isExecuted(): Boolean {
            return delegate.isExecuted
        }

        @Throws(IOException::class)
        override fun execute(): Response<T> {
            return delegate.execute()
        }

        override fun cancel() {
            delegate.cancel()
        }

        override fun isCanceled(): Boolean {
            return delegate.isCanceled
        }

        override fun clone(): Call<T> {
            return ExecutorCallbackCall(callbackExecutor, delegate.clone())
        }

        override fun request(): Request {
            return delegate.request()
        }

        override fun timeout(): Timeout {
            return delegate.timeout()
        }
    }
}