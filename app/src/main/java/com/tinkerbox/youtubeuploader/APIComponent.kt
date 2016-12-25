package com.tinkerbox.youtubeuploader

import dagger.Component
import javax.inject.Singleton

/**
 * Created by DPSUser on 12/25/2016.
 */
@Singleton
@Component(modules = arrayOf(NetModule::class, YoutubeModule::class, AndroidModule::class, AppModule::class))
interface APIComponent
{
    fun inject(activity: MainActivity)
}