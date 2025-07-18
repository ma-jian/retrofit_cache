package com.mm.http

import retrofit2.Response

/**
 * 默认的Response转换器
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