package com.mm.http

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonIOException
import com.google.gson.TypeAdapter
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonToken
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okio.Buffer
import retrofit2.Converter
import retrofit2.Retrofit
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.Writer
import java.lang.reflect.Type
import java.nio.charset.Charset

/**
 * .
 * Date : 2023/2/23
 * 防止解析异常接口直接报错。
 */
class GsonExceptionFactory private constructor(private val gson: Gson) : Converter.Factory() {

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *> {
        val adapter: TypeAdapter<*> = gson.getAdapter(TypeToken.get(type) as TypeToken<*>)
        return GsonResponseBodyConverter(gson, adapter)
    }

    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<Annotation>,
        methodAnnotations: Array<Annotation>,
        retrofit: Retrofit
    ): Converter<*, RequestBody> {
        val adapter = gson.getAdapter(TypeToken.get(type))
        return GsonRequestBodyConverter(gson, adapter)
    }

    private class GsonResponseBodyConverter<T> constructor(private val gson: Gson, private val adapter: TypeAdapter<T>) :
        Converter<ResponseBody, T?> {
        @Throws(IOException::class)
        override fun convert(value: ResponseBody): T? {
            val jsonReader = gson.newJsonReader(value.charStream())
            return try {
                val result = adapter.read(jsonReader)
                if (jsonReader.peek() != JsonToken.END_DOCUMENT) {
                    throw JsonIOException("JSON document was not fully consumed.")
                }
                result
            } catch (e: Exception) {
                Log.e("GsonConverterFactory", Log.getStackTraceString(e))
                null
            } finally {
                value.close()
            }
        }
    }

    private class GsonRequestBodyConverter<T> constructor(private val gson: Gson, private val adapter: TypeAdapter<T>) :
        Converter<T, RequestBody> {
        @Throws(IOException::class)
        override fun convert(value: T): RequestBody {
            val buffer = Buffer()
            val writer: Writer = OutputStreamWriter(buffer.outputStream(), UTF_8)
            val jsonWriter = gson.newJsonWriter(writer)
            adapter.write(jsonWriter, value)
            jsonWriter.close()
            return buffer.readByteString().toRequestBody(MEDIA_TYPE)
        }

        companion object {
            private val MEDIA_TYPE: MediaType = "application/json; charset=UTF-8".toMediaType()
            private val UTF_8 = Charset.forName("UTF-8")
        }
    }

    companion object {
        /**
         * Create an instance using `gson` for conversion. Encoding to JSON and decoding from JSON
         * (when no charset is specified by a header) will use UTF-8.
         */
        @JvmStatic
        fun create(gson: Gson? = Gson()): GsonExceptionFactory {
            if (gson == null) throw NullPointerException("gson == null")
            return GsonExceptionFactory(gson)
        }
    }
}