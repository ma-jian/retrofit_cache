package com.mm.retrofitcache

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mm.http.DynamicHostInterceptor
import com.mm.http.HOST
import com.mm.http.ResponseConverter
import com.mm.http.RetrofitCache
import com.mm.http.asCallFlow
import com.mm.http.cache.CacheHelper
import com.mm.retrofitcache.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.text.SimpleDateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {
    val tag = "MainActivity"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.input.setSelection(binding.input.text.length)
        val retrofit = createCache()
        val service = retrofit.create(MyService::class.java)
        val myService = createRetrofit().create<MyService>(MyService::class.java)
        binding.button.setOnClickListener {
            GlobalScope.launch(Dispatchers.Main) {
                val user2 = service.getUser2(binding.input.text.toString())
                binding.text.text = user2.toString()
                binding.time.text = refreshTime()
            }
        }

        binding.button2.setOnClickListener {
            GlobalScope.launch(Dispatchers.Main) {
                val user3 = service.getUser3(binding.input.text.toString())
                if (user3.isSuccessful) {
                    binding.text.text = user3.body().toString()
                } else {
                    binding.text.text = user3.errorBody().toString()
                }
                binding.time.text = refreshTime()
            }
        }

        binding.flow.setOnClickListener {
            GlobalScope.launch(Dispatchers.Main) {
                service.getUser(binding.input.text.toString()).asCallFlow().collect { user ->
                    binding.text.text = user.toString()
                    binding.time.text = refreshTime()
                }
            }
        }
    }

    @SuppressLint("SimpleDateFormat")
    private fun refreshTime(): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val millis = System.currentTimeMillis()
        return format.format(millis)
    }

    private fun createCache(): RetrofitCache {
        val cacheHelper = CacheHelper(cacheDir, Long.MAX_VALUE)
        return RetrofitCache.Builder()
            .addCacheConverterFactory(CacheConvertFactory())
            .addResponseConverterFactory(object : ResponseConverter.Factory() {
            }).addHostInterceptor(object : DynamicHostInterceptor {
                override fun hostUrl(host: HOST): HttpUrl {
                    return when (host.hostType) {
                        1 -> "https://api.github.com/".toHttpUrl()
                        else -> super.hostUrl(host)
                    }
                }
            }).addInterceptor(LogInterceptor()).build()
    }

    private fun createRetrofit(): Retrofit {
        val client = OkHttpClient.Builder().addInterceptor(LogInterceptor()).build()
        return Retrofit.Builder().baseUrl("https://api.github.com/").client(client).build()
    }
}