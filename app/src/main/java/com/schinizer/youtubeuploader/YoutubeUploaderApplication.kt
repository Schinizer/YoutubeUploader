package com.schinizer.youtubeuploader

import android.app.Application

/**
 * Created by DPSUser on 12/25/2016.
 */
class YoutubeUploaderApplication : Application()
{
    companion object {
        //platformStatic allow access it from java code
        @JvmStatic lateinit var apiComponent: APIComponent
    }

    override fun onCreate() {
        super.onCreate()

        apiComponent = DaggerAPIComponent.builder()
                .androidModule(AndroidModule())
                .appModule(AppModule(this))
                .netModule(NetModule())
                .youtubeModule(YoutubeModule("https://www.googleapis.com/"))
                .build()
    }
}