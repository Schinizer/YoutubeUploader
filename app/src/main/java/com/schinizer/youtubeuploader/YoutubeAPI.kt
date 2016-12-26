package com.schinizer.youtubeuploader

import com.jakewharton.retrofit2.adapter.rxjava2.Result
import com.schinizer.youtubeuploader.model.VideoResource
import io.reactivex.Observable
import retrofit2.http.*

/**
 * Created by DPSUser on 12/25/2016.
 */
interface YoutubeAPI
{
    @POST("/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status,contentDetails")
    @Headers("content-type: application/json; charset=utf-8")
    fun startSession(
            @Header("Authorization") auth_token: String,
            @Header("X-Upload-Content-Length") videoContentLength: Int,
            @Header("X-Upload-Content-Type") mimeType: String,
            @Body videoResource: VideoResource
    ) : Observable<Result<Void>>

    @PUT
    fun uploadSession(
            @Url sessionURL: String,
            @Header("Authorization") auth_token: String
    ) : Observable<Result<Void>>
}