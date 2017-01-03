package com.schinizer.youtubeuploader.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName


/**
 * Created by DPSUser on 12/25/2016.
 */
data class VideoResource (

    @SerializedName("snippet")
    @Expose
    val snippet: Snippet? = null,
    @SerializedName("status")
    @Expose
    val status: Status? = null

)