package com.mm.http

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * 根据[HOST.hostType]值动态修改请求host
 */
interface DynamicHostInterceptor {

    fun hostUrl(host: HOST): HttpUrl {
        return host.value.toHttpUrl()
    }
}