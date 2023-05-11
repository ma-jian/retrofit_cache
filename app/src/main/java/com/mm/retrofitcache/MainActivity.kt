package com.mm.retrofitcache

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mm.http.DynamicHostInterceptor
import com.mm.http.HOST
import com.mm.http.ResponseConverter
import com.mm.http.RetrofitCache
import com.mm.http.asResultFlow
import com.mm.http.cache.CacheHelper
import com.mm.http.uiScope
import com.mm.retrofitcache.databinding.ActivityMainBinding
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {
    val tag = "MainActivity"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.input.setSelection(binding.input.text.length)
        val retrofit = createCache()
        val service = retrofit.create(MyService::class.java)
        binding.button.setOnClickListener {
            service.getUser(binding.input.text.toString()).enqueue(object : Callback<Any> {

                override fun onResponse(call: Call<Any>, response: Response<Any>) {
                    binding.text.text = response.body().toString()
                }

                override fun onFailure(call: Call<Any>, t: Throwable) {

                }
            })
        }

        binding.flow.setOnClickListener {
            uiScope {
                service.getUser(binding.input.text.toString()).asResultFlow().collect {
                    it.onSuccess { value ->
                        binding.text.text = value.toString()
                    }
                    it.onFailure { error ->
                        binding.text.text = error.toString()
                    }
                }
            }
        }
    }

    private fun createCache(): RetrofitCache {
        val cacheHelper = CacheHelper(cacheDir, Long.MAX_VALUE)
        return RetrofitCache.Builder().cache(cacheHelper).addCacheConverterFactory(CacheConvertFactory())
            .addResponseConverterFactory(object : ResponseConverter.Factory() {
                override fun converterResponse(retrofit: RetrofitCache): ResponseConverter<Any>? {
                    //自定义修改结果并返回Response
                    return super.converterResponse(retrofit)
                }
            }).addHostInterceptor(object : DynamicHostInterceptor {
                override fun hostUrl(host: HOST): HttpUrl {
                    return when (host.hostType) {
                        1 -> "https://api.github.com/".toHttpUrl()
                        else -> super.hostUrl(host)
                    }
                }
            }).addInterceptor(LogInterceptor()).build()
    }

}