package com.mm.http

import retrofit2.Call
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * 缓存CallAdapter
 */
interface CacheCallAdapter<R, T> {

    fun responseType(): Type

    fun adapt(call: Call<R>): T

    abstract class Factory {
        /**
         * Returns a call adapter for interface methods that return `returnType`, or null if it
         * cannot be handled by this factory.
         */
        abstract operator fun get(returnType: Type, annotations: Array<Annotation>, retrofit: RetrofitCache): CacheCallAdapter<*, *>?

        companion object {
            /**
             * Extract the upper bound of the generic parameter at `index` from `type`. For
             * example, index 1 of `Map<String, ? extends Runnable>` returns `Runnable`.
             */
            @JvmStatic
            protected fun getParameterUpperBound(index: Int, type: ParameterizedType): Type {
                return type.getParameterUpperBound(index)
            }

            /**
             * Extract the raw class type from `type`. For example, the type representing `List<? extends Runnable>` returns `List.class`.
             */
            @JvmStatic
            protected fun getRawType(type: Type): Class<*> {
                return type.getRawType()
            }
        }
    }
}