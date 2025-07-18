@file:JvmName("HttpExtensions")
@file:Suppress("EXPERIMENTAL_IS_NOT_ENABLED")

package com.mm.http

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.suspendCancellableCoroutine
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * flow流对网络库扩展
 */
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
            Log.e("HttpExtensions", e.stackTraceToString())
        }
    }
}.flowOn(Dispatchers.Main)

fun <T : Any> callFlow(block: () -> Call<T>) = block.invoke().asCallFlow()

fun <T : Any> T?.asEmitFlow() = flow {
    emit(this@asEmitFlow)
}.flowOn(Dispatchers.IO).catch { e ->
    currentCoroutineContext().apply {
        this[CoroutineExceptionHandler]?.handleException(this, e) ?: run {
            Log.e("HttpExtensions", e.stackTraceToString())
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