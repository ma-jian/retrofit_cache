package com.mm.http

import retrofit2.Response

/**
 * Created by : majian
 * Date : 2021/9/29
 */
class DefaultResponseConverterFactory : ResponseConverter.Factory() {

    override fun converterResponse(retrofit: RetrofitCache): ResponseConverter<Any>? {
        return object : ResponseConverter<Any> {
            override fun convert(response: Response<Any>): Response<Any> {
                return response
            }
        }
    }
}