package com.mm.http

import android.annotation.SuppressLint
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Created by : majian
 * Date : 2021/8/25
 */
object SslSocketFactory {
    val sSLSocketFactory: SSLSocketFactory
        get() {
            var sslSocketFactory: SSLSocketFactory? = null
            try {
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
                sslSocketFactory = sslContext.socketFactory
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return sslSocketFactory ?: SSLSocketFactory.getDefault() as SSLSocketFactory
        }

    val trustManager: X509TrustManager
        get() = TrustAllCerts()

    @SuppressLint("CustomX509TrustManager")
    private class TrustAllCerts : X509TrustManager {
        @SuppressLint("TrustAllX509TrustManager")
        @Throws(CertificateException::class)
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        }

        @SuppressLint("TrustAllX509TrustManager")
        @Throws(CertificateException::class)
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        }

        override fun getAcceptedIssuers(): Array<X509Certificate?> {
            return arrayOfNulls(0)
        }
    }
}