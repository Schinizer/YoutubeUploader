package com.schinizer.youtubeuploader

import android.content.Context
import com.schinizer.youtubeuploader.model.AuthToken
import dagger.Module
import dagger.Provides
import io.reactivex.disposables.Disposable
import javax.inject.Singleton

/**
 * Created by DPSUser on 12/25/2016.
 */
@Module
class AppModule(val application: Context) {

    @Provides
    @Singleton
    internal fun application() = application

    @Provides
    @Singleton
    internal fun subscriptions() = HashMap<Long, Disposable>()

    @Provides
    @Singleton
    internal fun authToken() =  AuthToken()
}
