package com.tinkerbox.youtubeuploader

import com.google.gson.Gson
import com.jakewharton.retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import dagger.Module
import dagger.Provides
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Created by DPSUser on 12/25/2016.
 */

@Module
class YoutubeModule(val baseURL: String)
{
    @Provides
    internal fun youtubeAPI(okHttpClient: OkHttpClient, gson: Gson, rxJava2CallAdapterFactory: RxJava2CallAdapterFactory) = Retrofit.Builder()
            .baseUrl(baseURL)
            .addCallAdapterFactory(rxJava2CallAdapterFactory)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(okHttpClient)
            .build()
            .create(YoutubeAPI::class.java)
}