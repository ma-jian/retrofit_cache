package com.mm.http

import retrofit2.Response

/**
 * Created by : majian
 * Date : 2021/8/24
 * ResponseConverter 处理response数据
 */
interface ResponseConverter<T> {

    fun convert(response: Response<T>): Response<T>

    /**
     * Creates [ResponseConverter] instances based on a type and target usage.
     */
    abstract class Factory {
        open fun converterResponse(retrofit: RetrofitCache): ResponseConverter<Any>? {
            return null
        }
    }
}