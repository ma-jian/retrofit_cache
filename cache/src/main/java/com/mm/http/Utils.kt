package com.mm.http

import okhttp3.ResponseBody
import okio.Buffer
import java.io.IOException
import java.lang.reflect.*
import java.util.*

/**
 * Created by : majian
 * Date : 2021/8/24
 */
object Utils {
    fun getParameterUpperBound(index: Int, type: ParameterizedType): Type {
        val types = type.actualTypeArguments
        require(!(index < 0 || index >= types.size)) { "Index " + index + " not in range [0," + types.size + ") for " + type }
        val paramType = types[index]
        return if (paramType is WildcardType) {
            paramType.upperBounds[0]
        } else paramType
    }

    fun getRawType(type: Type): Class<*> {
        Objects.requireNonNull(type, "type == null")
        if (type is Class<*>) {
            // Type is a normal class.
            return type
        }
        if (type is ParameterizedType) {

            // I'm not exactly sure why getRawType() returns Type instead of Class. Neal isn't either but
            // suspects some pathological case related to nested classes exists.
            val rawType = type.rawType
            require(rawType is Class<*>)
            return rawType
        }
        if (type is GenericArrayType) {
            val componentType = type.genericComponentType
            return java.lang.reflect.Array.newInstance(getRawType(componentType), 0).javaClass
        }
        if (type is TypeVariable<*>) {
            // We could use the variable's bounds, but that won't work if there are multiple. Having a raw
            // type that's more general than necessary is okay.
            return Any::class.java
        }
        if (type is WildcardType) {
            return getRawType(type.upperBounds[0])
        }
        throw IllegalArgumentException(
            "Expected a Class, ParameterizedType, or "
                    + "GenericArrayType, but <"
                    + type
                    + "> is of type "
                    + type.javaClass.name
        )
    }

    fun isAnnotationPresent(annotations: Array<Annotation>, cls: Class<out Annotation?>): Boolean {
        for (annotation in annotations) {
            if (cls.isInstance(annotation)) {
                return true
            }
        }
        return false
    }

    @Throws(IOException::class)
    fun buffer(body: ResponseBody?): ResponseBody {
        val buffer = Buffer()
        body?.source()?.readAll(buffer)
        return ResponseBody.create(body?.contentType(), body?.contentLength() ?: -1, buffer)
    }

    fun methodError(method: Method?, message: String?, vararg args: Any?): RuntimeException {
        return methodError(method, null, message, *args)
    }

    fun methodError(
        method: Method,
        cause: Throwable?,
        message: String,
        vararg args: Any?
    ): RuntimeException {
        return IllegalArgumentException("${String.format(message, *args)}  for method ${method.declaringClass.simpleName}.${method.name}", cause)
    }

    fun throwIfFatal(t: Throwable?) {
        when (t) {
            is VirtualMachineError -> {
                throw t
            }
            is ThreadDeath -> {
                throw t
            }
            is LinkageError -> {
                throw t
            }
        }
    }

    fun getParameterLowerBound(index: Int, type: ParameterizedType): Type {
        val paramType = type.actualTypeArguments[index]
        return if (paramType is WildcardType) {
            paramType.lowerBounds[0]
        } else paramType
    }

    internal class ParameterizedTypeImpl(
        ownerType: Type?,
        rawType: Type,
        vararg typeArguments: Type
    ) : ParameterizedType {
        private val ownerType: Type?
        private val rawType: Type
        private val typeArguments: Array<Type>

        override fun getActualTypeArguments(): Array<Type> {
            return typeArguments.clone()
        }

        override fun getRawType(): Type {
            return rawType
        }

        override fun getOwnerType(): Type? {
            return ownerType
        }

        override fun equals(other: Any?): Boolean {
            return other is ParameterizedType
        }

        override fun hashCode(): Int {
            return (typeArguments.contentHashCode()
                    xor rawType.hashCode()
                    xor (ownerType?.hashCode() ?: 0))
        }

        init {
            // Require an owner type if the raw type needs it.
            require(
                !(rawType is Class<*>
                        && ownerType == null != (rawType.enclosingClass == null))
            )
            for (typeArgument in typeArguments) {
                Objects.requireNonNull(typeArgument, "typeArgument == null")
                checkNotPrimitive(typeArgument)
            }
            this.ownerType = ownerType
            this.rawType = rawType
            this.typeArguments = typeArguments.clone() as Array<Type>
        }
    }

    fun checkNotPrimitive(type: Type?) {
        require(!(type is Class<*> && type.isPrimitive))
    }

}