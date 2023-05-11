package com.mm.http

import com.google.gson.Gson
import com.google.gson.JsonIOException
import com.google.gson.TypeAdapter
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonToken
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okio.Buffer
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.Writer
import java.lang.reflect.Type
import java.nio.charset.StandardCharsets

/**
 * Created by : majian
 * Date : 2021/8/24
 */
internal class CacheGsonConverterFactory private constructor(private val gson: Gson) : Converter.Factory() {

    override fun <T> responseBodyConverter(type: Type, annotations: Array<Annotation>?, retrofit: RetrofitCache): Converter<ResponseBody, T> {
        val adapter: TypeAdapter<T> = gson.getAdapter(TypeToken.get(type) as TypeToken<T>)
        return GsonResponseBodyConverter(gson, adapter)
    }

    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<Annotation>?,
        methodAnnotations: Array<Annotation>?,
        retrofit: RetrofitCache
    ): Converter<*, RequestBody> {
        val adapter = gson.getAdapter(TypeToken.get(type))
        return GsonRequestBodyConverter(gson, adapter as TypeAdapter<*>)
    }

    internal class GsonResponseBodyConverter<T>(
        private val gson: Gson,
        private val adapter: TypeAdapter<out T>
    ) : Converter<ResponseBody, T> {

        @Throws(IOException::class)
        override fun convert(value: ResponseBody): T {
            val jsonReader = gson.newJsonReader(value.charStream())
            return value.use {
                val result = adapter.read(jsonReader)
                if (jsonReader.peek() != JsonToken.END_DOCUMENT) {
                    throw JsonIOException("JSON document was not fully consumed.")
                }
                result
            }
        }
    }

    internal class GsonRequestBodyConverter<T>(
        private val gson: Gson,
        private val adapter: TypeAdapter<T>
    ) : Converter<T, RequestBody> {

        @Throws(IOException::class)
        override fun convert(value: T): RequestBody {
            val buffer = Buffer()
            val writer: Writer = OutputStreamWriter(
                buffer.outputStream(),
                UTF_8
            )
            val jsonWriter = gson.newJsonWriter(writer)
            adapter.write(jsonWriter, value)
            jsonWriter.close()
            return RequestBody.create(MEDIA_TYPE, buffer.readByteString())
        }

        companion object {
            private val MEDIA_TYPE: MediaType = "application/json; charset=UTF-8".toMediaType()
            private val UTF_8 = StandardCharsets.UTF_8
        }
    }

    companion object {
        @JvmStatic
        fun create(gson: Gson? = Gson()): CacheGsonConverterFactory {
            if (gson == null) throw NullPointerException("gson == null")
            return CacheGsonConverterFactory(gson)
        }
    }
}