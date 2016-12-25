package com.tinkerbox.youtubeuploader

import android.content.Context
import android.preference.PreferenceManager
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
}