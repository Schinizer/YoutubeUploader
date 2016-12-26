package com.schinizer.youtubeuploader

import android.content.Context
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

/**
 * Created by DPSUser on 12/25/2016.
 */
@Module
class AppModule(val application: Context) {

    @Provides
    @Singleton
    internal fun application() = application
}
