package com.mm.retrofitcache

import android.content.Context
import android.net.ConnectivityManager

/**
 * Created by : majian
 * Date : 2021/9/30
 */

val Context.isConnect: Boolean
    get() {
        val systemService =
            this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        return systemService.activeNetworkInfo?.isConnected ?: false
    }