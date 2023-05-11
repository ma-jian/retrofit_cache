package com.mm.http

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Created by : m
 * Date : 2022/3/14
 * 根据[HOST.hostType]值动态修改请求host
 */
interface DynamicHostInterceptor {

    fun hostUrl(host: HOST): HttpUrl {
        return host.value.toHttpUrl()
    }
}