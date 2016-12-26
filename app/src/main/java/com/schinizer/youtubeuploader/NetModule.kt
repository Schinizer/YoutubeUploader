package com.schinizer.youtubeuploader

import com.google.gson.Gson
import com.jakewharton.retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import dagger.Module
import dagger.Provides
import io.reactivex.Scheduler
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Singleton

/**
 * Created by DPSUser on 12/25/2016.
 */

@Module
class NetModule
{
    @Provides
    @Singleton
    internal fun gson() = Gson()

    @Provides
    @Singleton
    fun httpLoggingInterceptor() = HttpLoggingInterceptor().setLevel(if(BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE)

    @Provides
    @Singleton
    internal fun okHttpClient(httpLoggingInterceptor: HttpLoggingInterceptor): OkHttpClient =
            OkHttpClient.Builder()
                .addInterceptor(httpLoggingInterceptor)
                .build()

    @Provides
    @Singleton
    internal fun rxJava2CallAdapterFactory(schedulerProvider: Scheduler): RxJava2CallAdapterFactory = RxJava2CallAdapterFactory.createWithScheduler(schedulerProvider)
}