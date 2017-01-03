package com.schinizer.youtubeuploader

import android.app.NotificationManager
import android.content.Context
import android.preference.PreferenceManager
import android.support.v4.app.NotificationManagerCompat
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

/**
 * Created by DPSUser on 12/25/2016.
 */
@Module
class AndroidModule
{
    @Provides
    @Singleton
    fun preferenceManager(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)

    @Provides
    @Singleton
    fun notificationManager(context: Context): NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}