package com.mm.retrofitcache

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.mm.http.RetrofitCache
import com.mm.http.cache.CacheHelper
import com.mm.retrofitcache.databinding.ActivityMainBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {
    val tag = "MainActivity"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val retrofit = createCache()
        val service = retrofit.create(MyService::class.java)
        binding.button.setOnClickListener {
            service.getUser(binding.input.text.toString()).enqueue(object : Callback<Any> {
                @SuppressLint("SetTextI18n")
                override fun onResponse(call: Call<Any>, response: Response<Any>) {
                    binding.text.text = "${response.headers()}\n${response.body()}"
                }

                override fun onFailure(call: Call<Any>, t: Throwable) {
                    Log.e(tag, Log.getStackTraceString(t))
                }
            })
        }
    }

    private fun createCache(): RetrofitCache {
        val cacheHelper = CacheHelper(cacheDir, Long.MAX_VALUE)
        return RetrofitCache.Builder().cache(cacheHelper)
            .addInterceptor(LogInterceptor(this))
            .addCacheConverterFactory(CacheConvertFactory())
            .build()
    }

}