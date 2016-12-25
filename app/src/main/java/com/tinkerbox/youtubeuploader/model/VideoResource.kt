package com.tinkerbox.youtubeuploader.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName



/**
 * Created by DPSUser on 12/25/2016.
 */
data class VideoResource (

    @SerializedName("snippet")
    @Expose
    var snippet: Snippet? = null,
    @SerializedName("status")
    @Expose
    var status: Status? = null

)
{
    data class Status (

        @SerializedName("privacyStatus")
        @Expose
        var privacyStatus: String? = null,
        @SerializedName("embeddable")
        @Expose
        var embeddable: Boolean? = null,
        @SerializedName("license")
        @Expose
        var license: String? = null

    )

    data class Snippet (

        @SerializedName("title")
        @Expose
        var title: String? = null,
        @SerializedName("description")
        @Expose
        var description: String? = null,
        @SerializedName("tags")
        @Expose
        var tags: List<String>? = null,
        @SerializedName("categoryId")
        @Expose
        var categoryId: Int? = null

    )
}