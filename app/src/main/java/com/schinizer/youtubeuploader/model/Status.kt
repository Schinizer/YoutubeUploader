package com.schinizer.youtubeuploader.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

/**
 * Created by DPSUser on 1/1/2017.
 */
data class Status (

        @SerializedName("privacyStatus")
        @Expose
        val privacyStatus: String? = null,
        @SerializedName("embeddable")
        @Expose
        val embeddable: Boolean? = null,
        @SerializedName("license")
        @Expose
        val license: String? = null

)