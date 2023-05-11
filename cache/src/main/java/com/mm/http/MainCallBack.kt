package com.mm.http

import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException

/**
 * Date : 2023/4/24
 */
interface MainCallBack : Callback {
    companion object {
        val EXECUTOR = RetrofitCache.MainThreadExecutor()
    }

    @Throws(IOException::class)
    override fun onResponse(call: Call, response: Response) {
        EXECUTOR.execute {
            try {
                onMainResponse(call, response)
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }

    override fun onFailure(call: Call, e: IOException) {
        EXECUTOR.execute { onMainFailure(call, e) }
    }

    @Throws(IOException::class)
    fun onMainResponse(call: Call, response: Response)

    fun onMainFailure(call: Call, e: IOException)
}