package com.mm.http

import retrofit2.Response

/**
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