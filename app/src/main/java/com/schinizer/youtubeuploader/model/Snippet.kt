package com.schinizer.youtubeuploader.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

/**
 * Created by DPSUser on 1/1/2017.
 */
data class Snippet (

        @SerializedName("title")
        @Expose
        val title: String? = null,
        @SerializedName("description")
        @Expose
        val description: String? = null,
        @SerializedName("tags")
        @Expose
        val tags: List<String>? = ArrayList(),
        @SerializedName("categoryId")
        @Expose
        val categoryId: Int? = null

)