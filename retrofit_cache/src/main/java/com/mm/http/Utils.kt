package com.mm.http

import okhttp3.Headers.Companion.headersOf
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.lang.reflect.GenericArrayType
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import java.util.Objects

fun ParameterizedType.getParameterUpperBound(index: Int): Type {
    val types = actualTypeArguments
    require(!(index < 0 || index >= types.size)) { "Index " + index + " not in range [0," + types.size + ") for " + this }
    val paramType = types[index]
    return if (paramType is WildcardType) {
        paramType.upperBounds[0]
    } else paramType
}

fun Type.getRawType(): Class<*> {
    Objects.requireNonNull(this, "type == null")
    if (this is Class<*>) {
        // Type is a normal class.
        return this
    }
    if (this is ParameterizedType) {
        // I'm not exactly sure why getRawType() returns Type instead of Class. Neal isn't either but
        // suspects some pathological case related to nested classes exists.
        val rawType = this.rawType
        require(rawType is Class<*>)
        return rawType
    }
    if (this is GenericArrayType) {
        val componentType = this.genericComponentType
        return java.lang.reflect.Array.newInstance(componentType.getRawType(), 0).javaClass
    }
    if (this is TypeVariable<*>) {
        // We could use the variable's bounds, but that won't work if there are multiple. Having a raw
        // type that's more general than necessary is okay.
        return Any::class.java
    }
    if (this is WildcardType) {
        return this.upperBounds[0].getRawType()
    }
    throw IllegalArgumentException(
        "Expected a Class, ParameterizedType, or "
                + "GenericArrayType, but <"
                + this
                + "> is of type "
                + this.javaClass.name
    )
}

fun Array<Annotation>.isAnnotationPresent(cls: Class<out Annotation?>): Boolean {
    for (annotation in this) {
        if (cls.isInstance(annotation)) {
            return true
        }
    }
    return false
}

fun ResponseBody?.toBuffer(): ResponseBody {
    val buffer = Buffer()
    this?.source()?.readAll(buffer)
    return buffer.asResponseBody(this?.contentType(), this?.contentLength() ?: -1)
}

fun Method?.methodError(cause: Throwable?, message: String, vararg args: Any?): RuntimeException {
    return IllegalArgumentException(
        "${
            String.format(
                message,
                *args
            )
        } for method ${this?.declaringClass?.simpleName}.${this?.name}", cause
    )
}

fun Method?.methodError(message: String?, vararg args: Any?): RuntimeException {
    return IllegalArgumentException(
        "${
            String.format(
                message!!,
                *args
            )
        } for method ${this?.declaringClass?.simpleName}.${this?.name}"
    )
}

fun ParameterizedType.getParameterLowerBound(index: Int): Type {
    val paramType = this.actualTypeArguments[index]
    return if (paramType is WildcardType) {
        paramType.lowerBounds[0]
    } else paramType
}

fun Throwable?.throwIfFatal() {
    when (this) {
        is VirtualMachineError -> {
            throw this
        }

        is ThreadDeath -> {
            throw this
        }

        is LinkageError -> {
            throw this
        }
    }
}

fun Type?.checkNotPrimitive() {
    require(!(this is Class<*> && this.isPrimitive))
}

internal class ParameterizedTypeImpl(
    ownerType: Type?,
    rawType: Type,
    vararg typeArguments: Type,
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
            typeArgument.checkNotPrimitive()
        }
        this.ownerType = ownerType
        this.rawType = rawType
        this.typeArguments = typeArguments.clone() as Array<Type>
    }
}

@JvmField
val EMPTY_BYTE_ARRAY = ByteArray(0)

@JvmField
val EMPTY_HEADERS = headersOf()

@JvmField
val EMPTY_RESPONSE = EMPTY_BYTE_ARRAY.toResponseBody()