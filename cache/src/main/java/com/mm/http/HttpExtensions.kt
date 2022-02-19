@file:JvmName("HttpExtensions")

package com.mm.http

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.HttpException
import retrofit2.Response

/**
 * Created by : majian
 * Date : 4/24/21
 * flow流对网络库扩展
 */

fun <T> T.uiScope(block: suspend (T) -> Unit) = CoroutineScope(Dispatchers.Main).launch {
    block.invoke(this@uiScope)
}

fun <T> T.ioScope(block: suspend (T) -> Unit) = CoroutineScope(Dispatchers.IO).launch {
    block.invoke(this@ioScope)
}

suspend fun <T : Any> Call<T>.asCallFlow(): Flow<T> = supervisorScope {
    channelFlow {
        offer(this@asCallFlow)
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
}

suspend fun <T : Any> callFlow(block: suspend () -> Call<T>) = block.invoke().asCallFlow()

suspend fun <T : Any> T?.asEmitFlow() = supervisorScope {
    flow {
        emit(this@asEmitFlow)
    }.flowOn(Dispatchers.IO).catch { e ->
        currentCoroutineContext().apply {
            this[CoroutineExceptionHandler]?.handleException(this, e) ?: run {
                Log.e("HttpExtensions", e.stackTraceToString())
            }
        }
    }.flowOn(Dispatchers.Main)
}

suspend fun <T : Any> emitFlow(block: suspend () -> T) = block.invoke().asEmitFlow()

fun <T : Any> Call<T>.channel(): ReceiveChannel<T?> {
    val channel = Channel<T?>(5)
    enqueue(object : Callback<T?> {
        override fun onResponse(call: Call<T?>, response: Response<T?>) {
            if (response.isSuccessful) {
                if (!channel.isClosedForSend && !channel.isClosedForReceive) {
                    channel.offer(response.body())
                }
            } else {
                channel.close(HttpException(response))
            }
        }

        override fun onFailure(call: Call<T?>, t: Throwable) {
            channel.close(t)
        }
    })
    return channel
}