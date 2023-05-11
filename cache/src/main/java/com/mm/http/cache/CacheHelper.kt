package com.mm.http.cache

import android.text.TextUtils
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.internal.EMPTY_HEADERS
import okhttp3.internal.cache.CacheRequest
import okhttp3.internal.closeQuietly
import okhttp3.internal.concurrent.TaskRunner
import okhttp3.internal.http.StatusLine
import okhttp3.internal.io.FileSystem
import okhttp3.internal.platform.Platform
import okhttp3.internal.toLongOrDefault
import okio.*
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import java.io.Closeable
import java.io.File
import java.io.Flushable
import java.io.IOException
import java.nio.charset.Charset
import java.security.cert.Certificate
import java.security.cert.CertificateEncodingException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.util.*

/**
 * Created by : majian
 * Date : 2021/8/19
 * 缓存存储数据
 */

class CacheHelper internal constructor(
    private val directory: File,
    private val maxSize: Long,
    private val fileSystem: FileSystem
) : Closeable, Flushable {
    internal val cache = DiskLruCacheHelper(
        fileSystem = fileSystem,
        directory = directory,
        appVersion = VERSION,
        valueCount = ENTRY_COUNT,
        maxSize = maxSize,
        taskRunner = TaskRunner.INSTANCE
    )

    /**
     * @param directory 缓存路径
     * @param maxSize 最大字节数
     */
    constructor(directory: File, maxSize: Long) : this(directory, maxSize, FileSystem.SYSTEM)

    fun get(request: Request): Response? {
        val key = key(request)
        val snapshot: DiskLruCacheHelper.Snapshot = try {
            cache[key] ?: return null
        } catch (_: IOException) {
            return null // Give up because the cache cannot be read.
        }

        val entry: Entry = try {
            Entry(snapshot.getSource(ENTRY_METADATA))
        } catch (_: IOException) {
            snapshot.closeQuietly()
            return null
        }

        val response = entry.response(snapshot)
        if (!entry.matches(request, response)) {
            response.body?.closeQuietly()
            return null
        }
        return response
    }


    fun <T> put(response: Response, res: retrofit2.Response<T>): Boolean {
        if (response.hasVaryAll()) {
            return false
        }
        val entry = Entry(response)
        var editor: DiskLruCacheHelper.Editor? = null
        try {
            editor = cache.edit(key(response.request)) ?: return false
            entry.writeTo(editor)
            val body = res.body()
            editor.newSink(ENTRY_BODY).buffer().use { sink ->
                sink.writeUtf8(Gson().toJson(body))
            }

            editor.commit()
        } catch (_: IOException) {
            abortQuietly(editor)
            return false
        }
        return true
    }

    private fun abortQuietly(editor: DiskLruCacheHelper.Editor?) {
        // Give up because the cache cannot be written.
        try {
            editor?.abort()
        } catch (_: IOException) {
        }
    }

    private class Entry {
        private val url: String
        private val varyHeaders: Headers
        private val requestMethod: String
        private val protocol: Protocol
        private val code: Int
        private val message: String
        private val responseHeaders: Headers
        private val handshake: Handshake?
        private val sentRequestMillis: Long
        private val receivedResponseMillis: Long
        private val isHttps: Boolean get() = url.startsWith("https://")
        private var body: RequestBody? = null

        @Throws(IOException::class)
        constructor(rawSource: Source) {
            try {
                val source = rawSource.buffer()
                url = source.readUtf8LineStrict()
                requestMethod = source.readUtf8LineStrict()
                if (requestMethod.equals("post", ignoreCase = true)) {
                    val line = source.readUtf8LineStrict()
                    if (line.startsWith("body:")) {
                        val replace = line.replace("body:", "")
                        val builder = FormBody.Builder()
                        if (replace.contains("&")) {
                            val split = replace.split("&")
                            for (i in split.indices) {
                                val s = split[i]
                                if (!TextUtils.isEmpty(s)) {
                                    val index = s.indexOf("=")
                                    builder.add(s.substring(0, index), s.substring(index + 1))
                                }
                            }
                        }
                        body = builder.build()
                    }
                }
                val varyHeadersBuilder = Headers.Builder()
                val varyRequestHeaderLineCount = readInt(source)
                for (i in 0 until varyRequestHeaderLineCount) {
                    varyHeadersBuilder.addWithLine(source.readUtf8LineStrict())
                }
                varyHeaders = varyHeadersBuilder.build()

                val statusLine = StatusLine.parse(source.readUtf8LineStrict())
                protocol = statusLine.protocol
                code = statusLine.code
                message = statusLine.message
                val responseHeadersBuilder = Headers.Builder()
                val responseHeaderLineCount = readInt(source)
                for (i in 0 until responseHeaderLineCount) {
                    responseHeadersBuilder.addWithLine(source.readUtf8LineStrict())
                }
                val sendRequestMillisString = responseHeadersBuilder[SENT_MILLIS]
                val receivedResponseMillisString = responseHeadersBuilder[RECEIVED_MILLIS]
                responseHeadersBuilder.removeAll(SENT_MILLIS)
                responseHeadersBuilder.removeAll(RECEIVED_MILLIS)
                sentRequestMillis = sendRequestMillisString?.toLong() ?: 0L
                receivedResponseMillis = receivedResponseMillisString?.toLong() ?: 0L
                responseHeaders = responseHeadersBuilder.build()

                if (isHttps) {
                    val blank = source.readUtf8LineStrict()
                    if (blank.isNotEmpty()) {
                        throw IOException("expected \"\" but was \"$blank\"")
                    }
                    val cipherSuiteString = source.readUtf8LineStrict()
                    val cipherSuite = CipherSuite.forJavaName(cipherSuiteString)
                    val peerCertificates = readCertificateList(source)
                    val localCertificates = readCertificateList(source)
                    val tlsVersion = if (!source.exhausted()) {
                        TlsVersion.forJavaName(source.readUtf8LineStrict())
                    } else {
                        TlsVersion.SSL_3_0
                    }
                    handshake =
                        Handshake.get(tlsVersion, cipherSuite, peerCertificates, localCertificates)
                } else {
                    handshake = null
                }
            } finally {
                rawSource.close()
            }
        }

        constructor(response: Response) {
            this.url = response.request.url.toString()
            this.varyHeaders = response.varyHeaders()
            this.requestMethod = response.request.method
            this.protocol = response.protocol
            this.code = response.code
            this.body = response.request.body
            this.message = response.message
            this.responseHeaders = response.headers
            this.handshake = response.handshake
            this.sentRequestMillis = response.sentRequestAtMillis
            this.receivedResponseMillis = response.receivedResponseAtMillis
        }

        @Throws(IOException::class)
        fun writeTo(editor: DiskLruCacheHelper.Editor) {
            editor.newSink(ENTRY_METADATA).buffer().use { sink ->
                sink.writeUtf8(url).writeByte('\n'.toInt())
                sink.writeUtf8(requestMethod).writeByte('\n'.toInt())
                if ("post".equals(requestMethod, ignoreCase = true)) {
                    sink.writeUtf8("body:")
                    body?.let {
                        if (it is FormBody) {
                            for (i in 0 until it.size) {
                                sink.writeUtf8(it.name(i))
                                    .writeUtf8("=")
                                    .writeUtf8(it.value(i))
                                if (i < it.size - 1) {
                                    sink.writeUtf8("&")
                                }
                            }
                        }
                    }
                    sink.writeByte('\n'.toInt())
                }

                sink.writeDecimalLong(varyHeaders.size.toLong()).writeByte('\n'.toInt())
                for (i in 0 until varyHeaders.size) {
                    sink.writeUtf8(varyHeaders.name(i))
                        .writeUtf8(": ")
                        .writeUtf8(varyHeaders.value(i))
                        .writeByte('\n'.toInt())
                }

                sink.writeUtf8(StatusLine(protocol, code, message).toString())
                    .writeByte('\n'.toInt())
                sink.writeDecimalLong((responseHeaders.size + 2).toLong()).writeByte('\n'.toInt())
                for (i in 0 until responseHeaders.size) {
                    sink.writeUtf8(responseHeaders.name(i))
                        .writeUtf8(": ")
                        .writeUtf8(responseHeaders.value(i))
                        .writeByte('\n'.toInt())
                }
                sink.writeUtf8(SENT_MILLIS)
                    .writeUtf8(": ")
                    .writeDecimalLong(sentRequestMillis)
                    .writeByte('\n'.toInt())
                sink.writeUtf8(RECEIVED_MILLIS)
                    .writeUtf8(": ")
                    .writeDecimalLong(receivedResponseMillis)
                    .writeByte('\n'.toInt())

                if (isHttps) {
                    sink.writeByte('\n'.toInt())
                    sink.writeUtf8(handshake!!.cipherSuite.javaName).writeByte('\n'.toInt())
                    writeCertList(sink, handshake.peerCertificates)
                    writeCertList(sink, handshake.localCertificates)
                    sink.writeUtf8(handshake.tlsVersion.javaName).writeByte('\n'.toInt())
                }
            }
        }

        @Throws(IOException::class)
        private fun readCertificateList(source: BufferedSource): List<Certificate> {
            val length = readInt(source)
            if (length == -1) return emptyList() // OkHttp v1.2 used -1 to indicate null.

            try {
                val certificateFactory = CertificateFactory.getInstance("X.509")
                val result = ArrayList<Certificate>(length)
                for (i in 0 until length) {
                    val line = source.readUtf8LineStrict()
                    val bytes = Buffer()
                    bytes.write(line.decodeBase64()!!)
                    result.add(certificateFactory.generateCertificate(bytes.inputStream()))
                }
                return result
            } catch (e: CertificateException) {
                throw IOException(e.message)
            }
        }

        @Throws(IOException::class)
        private fun writeCertList(sink: BufferedSink, certificates: List<Certificate>) {
            try {
                sink.writeDecimalLong(certificates.size.toLong()).writeByte('\n'.toInt())
                for (element in certificates) {
                    val bytes = element.encoded
                    val line = bytes.toByteString().base64()
                    sink.writeUtf8(line).writeByte('\n'.toInt())
                }
            } catch (e: CertificateEncodingException) {
                throw IOException(e.message)
            }
        }

        fun matches(request: Request, response: Response): Boolean {
            return url == request.url.toString() &&
                    requestMethod == request.method &&
                    varyMatches(response, varyHeaders, request)
        }

        fun response(snapshot: DiskLruCacheHelper.Snapshot): Response {
            val contentType = responseHeaders["Content-Type"]
            val contentLength = responseHeaders["Content-Length"]
            val cacheRequest = Request.Builder()
                .url(url)
                .method(requestMethod, body)
                .headers(varyHeaders)
                .build()
            val responseHeaders = responseHeaders.newBuilder().add(CACHE_HEADER, "local_cache").build()
            return Response.Builder()
                .request(cacheRequest)
                .protocol(protocol)
                .code(code)
                .message(message)
                .headers(responseHeaders)
                .body(CacheResponseBody(snapshot, contentType, contentLength))
                .handshake(handshake)
                .sentRequestAtMillis(sentRequestMillis)
                .receivedResponseAtMillis(receivedResponseMillis)
                .build()
        }

        companion object {
            /** Synthetic response header: the local time when the request was sent. */
            private val SENT_MILLIS = "${Platform.get().getPrefix()}-Sent-Millis"

            /** Synthetic response header: the local time when the response was received. */
            private val RECEIVED_MILLIS = "${Platform.get().getPrefix()}-Received-Millis"
        }
    }

    private class CacheResponseBody(
        val snapshot: DiskLruCacheHelper.Snapshot,
        private val contentType: String?,
        private val contentLength: String?
    ) : ResponseBody() {
        private val bodySource: BufferedSource

        init {
            val source = snapshot.getSource(ENTRY_BODY)
            bodySource = object : ForwardingSource(source) {
                @Throws(IOException::class)
                override fun close() {
                    snapshot.close()
                    super.close()
                }
            }.buffer()
        }

        override fun contentType(): MediaType? = contentType?.toMediaTypeOrNull()

        override fun contentLength(): Long = contentLength?.toLongOrDefault(-1L) ?: -1L

        override fun source(): BufferedSource = bodySource
    }

    private inner class RealCacheRequest(
        private val editor: DiskLruCacheHelper.Editor
    ) : CacheRequest {
        private val cacheOut: Sink = editor.newSink(ENTRY_BODY)
        private val body: Sink
        var done = false

        init {
            this.body = object : ForwardingSink(cacheOut) {
                @Throws(IOException::class)
                override fun close() {
                    synchronized(this@CacheHelper) {
                        if (done) return
                        done = true
                    }
                    super.close()
                    editor.commit()
                }
            }
        }

        override fun abort() {
            synchronized(this@CacheHelper) {
                if (done) return
                done = true
            }
            cacheOut.closeQuietly()
            try {
                editor.abort()
            } catch (_: IOException) {
            }
        }

        override fun body(): Sink = body
    }

    @Throws(IOException::class)
    fun remove(request: Request) {
        cache.remove(key(request))
    }

    internal fun update(cached: Response, network: Response) {
        val entry = Entry(network)
        val snapshot = (cached.body as CacheResponseBody).snapshot
        var editor: DiskLruCacheHelper.Editor? = null
        try {
            editor = snapshot.edit() ?: return // edit() returns null if snapshot is not current.
            entry.writeTo(editor)
            editor.commit()
        } catch (_: IOException) {
            abortQuietly(editor)
        }
    }

    @Throws(IOException::class)
    fun initialize() {
        cache.initialize()
    }

    @Throws(IOException::class)
    override fun flush() {
        cache.flush()
    }

    @Throws(IOException::class)
    override fun close() {
        cache.close()
    }

    fun size(): Long = cache.size()

    /** Max size of the cache (in bytes). */
    fun maxSize(): Long = cache.maxSize

    /**
     * Deletes all values stored in the cache. In-flight writes to the cache will complete normally,
     * but the corresponding responses will not be stored.
     */
    fun evictAll() {
        cache.evictAll()
    }

    /**
     * Closes the cache and deletes all of its stored values. This will delete all files in the cache
     * directory including files that weren't created by the cache.
     */
    @Throws(IOException::class)
    fun delete() {
        cache.delete()
    }

    companion object {
        private const val VERSION = 201105
        private const val ENTRY_METADATA = 0
        private const val ENTRY_BODY = 1
        private const val ENTRY_COUNT = 2
        const val CACHE_HEADER = "cache_header"
        private var uniqueId = ""
        private val ignoreKey = arrayListOf<String>()

        //忽略列表
        @JvmStatic
        fun ignoreKey(list: List<String>) {
            ignoreKey.clear()
            ignoreKey.addAll(list)
        }

        /**
         * 身份唯一标识，参与key值计算
         * @param id unique identification
         */
        @JvmStatic
        fun uniqueId(id: String) {
            this.uniqueId = id
        }

        @JvmStatic
        fun key(request: Request): String = kotlin.run {
            var key = request.url.toString()
            if (request.method.equals("post", ignoreCase = true)) {
                val charset = request.body?.contentType()?.let {
                    it.charset(Charset.forName("utf-8"))
                } ?: Charset.forName("utf-8")
                val buffer = Buffer()
                val sb = StringBuilder()
                try {
                    if (request.body is FormBody) {
                        val formBody = request.body as FormBody
                        val size = formBody.size
                        for (i in 0 until size) {
                            if (ignoreKey.contains(formBody.encodedName(i))) {
                                continue
                            }
                            sb.append(formBody.encodedName(i)).append("=")
                                .append(formBody.encodedValue(i))
                            if (i != size - 1) {
                                sb.append("&")
                            }
                        }
                    } else {
                        request.body?.writeTo(buffer)
                        sb.append(buffer.readString(charset))
                    }
                    key = "$key/$sb"
                } catch (e: IOException) {
                    e.printStackTrace();
                } finally {
                    buffer.close()
                }
            } else {
                val url = request.url
                val newBuilder = url.newBuilder()
                url.queryParameterNames.forEach {
                    if (ignoreKey.contains(it)) {
                        newBuilder.removeAllQueryParameters(it)
                    }
                }
                key = newBuilder.build().toString()
            }
            "$key$uniqueId".encodeUtf8().md5().hex()
        }

        @Throws(IOException::class)
        internal fun readInt(source: BufferedSource): Int {
            try {
                val result = source.readDecimalLong()
                val line = source.readUtf8LineStrict()
                if (result < 0L || result > Integer.MAX_VALUE || line.isNotEmpty()) {
                    throw IOException("expected an int but was \"$result$line\"")
                }
                return result.toInt()
            } catch (e: NumberFormatException) {
                throw IOException(e.message)
            }
        }

        /**
         * Returns true if none of the Vary headers have changed between [cachedRequest] and
         * [newRequest].
         */
        fun varyMatches(
            cachedResponse: Response,
            cachedRequest: Headers,
            newRequest: Request
        ): Boolean {
            return newRequest.headers.varyFields().none {
                cachedResponse.headers.values(it) != newRequest.headers(it)
            }
        }

        /** Returns true if a Vary header contains an asterisk. Such responses cannot be cached. */
        fun Response.hasVaryAll() = "*" in headers.varyFields()

        /**
         * Returns the names of the request headers that need to be checked for equality when caching.
         */
        private fun Headers.varyFields(): Set<String> {
            var result: MutableSet<String>? = null
            for (i in 0 until size) {
                if (!"Vary".equals(name(i), ignoreCase = true)) {
                    continue
                }

                val value = value(i)
                if (result == null) {
                    result = TreeSet(String.CASE_INSENSITIVE_ORDER)
                }
                for (varyField in value.split(',')) {
                    result.add(varyField.trim())
                }
            }
            return result ?: emptySet()
        }

        /**
         * Returns the subset of the headers in this's request that impact the content of this's body.
         */
        fun Response.varyHeaders(): Headers {
            // Use the request headers sent over the network, since that's what the response varies on.
            // Otherwise OkHttp-supplied headers like "Accept-Encoding: gzip" may be lost.
            val requestHeaders = networkResponse!!.request.headers
            val responseHeaders = headers
            return varyHeaders(requestHeaders, responseHeaders)
        }

        /**
         * Returns the subset of the headers in [requestHeaders] that impact the content of the
         * response's body.
         */
        private fun varyHeaders(requestHeaders: Headers, responseHeaders: Headers): Headers {
            val varyFields = responseHeaders.varyFields()
            if (varyFields.isEmpty()) return EMPTY_HEADERS

            val result = Headers.Builder()
            for (i in 0 until requestHeaders.size) {
                val fieldName = requestHeaders.name(i)
                if (fieldName in varyFields) {
                    result.add(fieldName, requestHeaders.value(i))
                }
            }
            return result.build()
        }
    }
}