package com.schinizer.youtubeuploader

import android.app.Application
import com.schinizer.youtubeuploader.model.UploadTask
import io.realm.Realm
import io.realm.RealmConfiguration
import java.util.concurrent.atomic.AtomicLong


/**
 * Created by DPSUser on 12/25/2016.
 */
class YoutubeUploaderApplication : Application()
{
    companion object {
        //platformStatic allow access it from java code
        @JvmStatic lateinit var apiComponent: APIComponent
        @JvmStatic lateinit var primaryKeyValue: AtomicLong
    }

    override fun onCreate() {
        super.onCreate()

        apiComponent = DaggerAPIComponent.builder()
                .androidModule(AndroidModule())
                .appModule(AppModule(this))
                .netModule(NetModule())
                .youtubeModule(YoutubeModule("https://www.googleapis.com/"))
                .build()

        Realm.init(this)
        RealmConfiguration.Builder().build().let {
            Realm.getInstance(it).use {
                primaryKeyValue = AtomicLong(it.where(UploadTask::class.java).max("id")?.toLong() ?: 0L)
            }
        }
    }
}