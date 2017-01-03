package com.schinizer.youtubeuploader.model

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

/**
 * Created by DPSUser on 1/1/2017.
 */
open class UploadTask(

    @PrimaryKey open var id: Long = 0,
    open var uri: String = "",
    open var sessionURL: String = "",
    open var bytesWritten: Long = 0,
    open var contentLength: Long = 0,
    open var title: String? = null,
    open var description: String? = null,
    open var privacyStatus: String? = null

) : RealmObject()
