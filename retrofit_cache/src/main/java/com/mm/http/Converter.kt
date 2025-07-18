package com.mm.http

import okhttp3.RequestBody
import okhttp3.ResponseBody
import java.io.IOException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * 缓存 Converter
 */
interface Converter<F, T> {

    @Throws(IOException::class)
    fun convert(value: F): T

    /** Creates [Converter] instances based on a type and target usage.  */
    abstract class Factory {

        open fun <T> responseBodyConverter(
            type: Type,
            annotations: Array<Annotation>?,
            retrofit: RetrofitCache
        ): Converter<ResponseBody, T>? {
            return null
        }

        /**
         * Returns a [Converter] for converting `type` to an HTTP request body, or null if
         * `type` cannot be handled by this factory. This is used to create converters for types
         * specified by [@Body][Body], [@Part][Part], and [@PartMap][PartMap] values.
         */
        open fun requestBodyConverter(
            type: Type, parameterAnnotations: Array<Annotation>?,
            methodAnnotations: Array<Annotation>?, retrofit: RetrofitCache
        ): Converter<*, RequestBody>? {
            return null
        }

        companion object {
            /**
             * Extract the upper bound of the generic parameter at `index` from `type`. For
             * example, index 1 of `Map<String, ? extends Runnable>` returns `Runnable`.
             */
            protected fun getParameterUpperBound(index: Int, type: ParameterizedType): Type {
                return type.getParameterUpperBound(index)
            }

            /**
             * Extract the raw class type from `type`. For example, the type representing `List<? extends Runnable>` returns `List.class`.
             */
            protected fun getRawType(type: Type): Class<*> {
                return type.getRawType()
            }
        }
    }
}