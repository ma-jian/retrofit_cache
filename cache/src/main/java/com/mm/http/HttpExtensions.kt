@file:JvmName("HttpExtensions")
@file:Suppress("EXPERIMENTAL_IS_NOT_ENABLED")

package com.mm.http

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Created by : majian
 * Date : 4/24/21
 * flow流对网络库扩展
 */
val exceptionHandler = CoroutineExceptionHandler { coroutineContext, throwable ->
    if (BuildConfig.BUILD_TYPE == "debug") {
        Log.e("job ${coroutineContext[Job]}", Log.getStackTraceString(throwable))
    }
}

fun uiScope(block: suspend () -> Unit) = CoroutineScope(Dispatchers.Main + exceptionHandler).launch {
    block.invoke()
}

fun ioScope(block: suspend () -> Unit) = CoroutineScope(Dispatchers.IO + exceptionHandler).launch {
    block.invoke()
}

//ui 返回值
fun <T> uiAsync(block: suspend () -> T) = CoroutineScope(Dispatchers.Main + exceptionHandler).async {
    block.invoke()
}


//ui 返回值
fun <T> ioAsync(block: suspend () -> T) = CoroutineScope(Dispatchers.IO + exceptionHandler).async {
    block.invoke()
}

fun <T : Any> Call<T>.asCallFlow(): Flow<T> = channelFlow {
    this.trySend(this@asCallFlow).isSuccess
}.transform { call ->
    val channel = call.channel()
    val iterator = channel.iterator()
    while (iterator.hasNext()) {
        iterator.next()?.let {
            emit(it)
        }
    }
}.flowOn(Dispatchers.IO).catch { e ->
    currentCoroutineContext().apply {
        this[CoroutineExceptionHandler]?.handleException(this, e) ?: run {
            if (BuildConfig.BUILD_TYPE == "debug") {
                Log.e("HttpExtensions", e.stackTraceToString())
            }
        }
    }
}.flowOn(Dispatchers.Main)

fun <T : Any> callFlow(block: () -> Call<T>) = block.invoke().asCallFlow()

fun <T : Any> T?.asEmitFlow() = flow {
    emit(this@asEmitFlow)
}.flowOn(Dispatchers.IO).catch { e ->
    currentCoroutineContext().apply {
        this[CoroutineExceptionHandler]?.handleException(this, e) ?: run {
            if (BuildConfig.BUILD_TYPE == "debug") {
                Log.e("HttpExtensions", e.stackTraceToString())
            }
        }
    }
}.flowOn(Dispatchers.Main)

fun <T : Any> emitFlow(block: () -> T) = block.invoke().asEmitFlow()

fun <T : Any> Call<T>.channel(): ReceiveChannel<T?> {
    val channel = Channel<T?>(5)
    enqueue(object : Callback<T?> {
        override fun onResponse(call: Call<T?>, response: Response<T?>) {
            channel.trySend(response.body()).isSuccess
        }

        override fun onFailure(call: Call<T?>, t: Throwable) {
            channel.trySend(null)
        }
    })
    return channel
}

fun <T : Any> Call<T>.asResultFlow(): Flow<Result<T>> = channelFlow<Result<T>> {
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            cancel()
        }
        enqueue(object : Callback<T> {
            override fun onResponse(call: Call<T>, response: Response<T>) {
                response.body()?.let {
                    trySend(Result.success(it))
                } ?: trySend(Result.failure(Throwable(response.raw().toString())))
            }

            override fun onFailure(call: Call<T>, t: Throwable) {
                trySend(Result.failure(t))
            }
        })
    }
}.flowOn(Dispatchers.Main)