package com.schinizer.youtubeuploader

import android.app.IntentService
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.jakewharton.retrofit2.adapter.rxjava2.Result
import com.pavlospt.rxfile.RxFile
import com.schinizer.youtubeuploader.model.*
import hu.akarnokd.rxjava.interop.RxJavaInterop
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.realm.Realm
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.Okio
import java.io.File
import javax.inject.Inject

class UploadService : IntentService("UploadService") {

    val SEGMENT_SIZE = 8L * 1024L

    @Inject
    lateinit var notificationManager: NotificationManager

    @Inject
    lateinit var youtubeAPI: YoutubeAPI

    @Inject
    lateinit var disposables: HashMap<Long, Disposable>

    @Inject
    lateinit var authToken: AuthToken

    companion object
    {
        val TAG = UploadService::class.java.simpleName
    }

    override fun onCreate() {
        super.onCreate()
        YoutubeUploaderApplication.apiComponent.inject(this)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onHandleIntent(intent: Intent?) {

        val authTokenHeader = "Bearer ${authToken.token}"
        val id = intent?.extras?.getLong("id")

        if(disposables.containsKey(id))
            return

        val d = Observable.fromCallable {
            Realm.getDefaultInstance().use { realm ->
                val task = realm.where(UploadTask::class.java).equalTo("id", id).findFirst()

                Flowable.just(task)
                        .flatMap { task ->
                            when(task.sessionURL.isEmpty())
                            {
                                true -> return@flatMap upload(authTokenHeader, task, VideoResource(Snippet(title = task.title, description = task.description), Status(privacyStatus = task.privacyStatus)), realm)
                                else -> return@flatMap resumeUpload(authTokenHeader, task, realm)
                            }
                        }
                        .toList()
                        .blockingGet()
            }
        }
                .subscribeOn(Schedulers.io())
                .subscribe({}, { t -> Log.d(TAG, "OnError", t)})

        disposables.put(id ?: -1L, d)
    }

    fun upload(authToken: String, uploadTask: UploadTask, videoResource: VideoResource?, realm: Realm) : Flowable<Any>
    {
        return RxJavaInterop.toV2Flowable(RxFile.createFileFromUri(this@UploadService, Uri.parse(uploadTask.uri)))
                .flatMap { file ->
                    youtubeAPI.startSession(authToken, file.length(), "video/*", videoResource)
                            .toFlowable(BackpressureStrategy.LATEST)
                            .flatMap { res ->
                                when(res.response()?.isSuccessful)
                                {
                                    true -> {
                                        uploadTask.apply {
                                            realm.executeTransaction {
                                                sessionURL = res.response().headers().get("Location") ?: ""
                                                contentLength = file.length()
                                            }
                                        }
                                        uploadVideoTask(authToken, file, uploadTask.sessionURL)
                                                .doOnNext { data ->
                                                    uploadTask.apply {
                                                        when(data)
                                                        {
                                                            is Long -> realm.executeTransaction {
                                                                bytesWritten = data
                                                            }

                                                            is Result<*> -> when(data.response()?.isSuccessful)
                                                            {
                                                                true -> realm.executeTransaction {
                                                                    deleteFromRealm()
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                    }
                                    else -> Flowable.empty()
                                }
                            }
                }
    }

    fun uploadVideoTask(authToken: String, file: File, sessionURL: String) : Flowable<Any>
    {
        return Flowable.create<Any>({ flowableSource ->

            val requestBody = object : RequestBody(){

                override fun contentType(): MediaType = MediaType.parse("video/*")
                override fun contentLength(): Long = file.length()
                override fun writeTo(sink: BufferedSink) {

                    val countingSink = Okio.buffer(object: ForwardingSink(sink)
                    {
                        var total: Long = 0
                        override fun write(source: Buffer?, byteCount: Long) {
                            total += byteCount
                            flowableSource.onNext(total)
                            super.write(source, byteCount)
                        }
                    })

                    Okio.buffer(Okio.source(file)).use {
                        while(it.read(countingSink.buffer(), SEGMENT_SIZE) != -1L)
                        {
                            countingSink.flush()
                        }
                    }
                }
            }

            val disposable = youtubeAPI.uploadVideo(sessionURL, authToken, requestBody)
                    .doOnNext { res ->
                        flowableSource.onNext(res)
                        flowableSource.onComplete()
                    }
                    .subscribe()

            flowableSource.setCancellable { disposable.dispose() }

        }, BackpressureStrategy.LATEST)
    }

    fun resumeUpload(authToken: String, uploadTask: UploadTask, realm: Realm) : Flowable<Any>
    {
        return RxJavaInterop.toV2Flowable(RxFile.createFileFromUri(this@UploadService, Uri.parse(uploadTask.uri)))
                .flatMap { file ->
                    youtubeAPI.uploadStatus(uploadTask.sessionURL, authToken, "bytes */${file.length()}")
                            .toFlowable(BackpressureStrategy.LATEST)
                            .flatMap { res ->
                                when(res.response().code())
                                {
                                    308 -> {
                                        val rangeHeader = res.response().headers()["Range"] ?: "" // If there is no header, init as empty string
                                        val offset = rangeHeader.substringAfterLast("-", "-1").toLong() + 1L // Range is 0 based index
                                        resumeUploadTask(authToken, file, offset, uploadTask.sessionURL)
                                                .doOnNext { data ->
                                                    uploadTask.apply {
                                                        when(data)
                                                        {
                                                            is Long -> realm.executeTransaction {
                                                                bytesWritten = data
                                                            }

                                                            is Result<*> -> when(data.response()?.isSuccessful)
                                                            {
                                                                true -> realm.executeTransaction {
                                                                    deleteFromRealm()
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                    }
                                    else -> Flowable.empty<Any>()
                                }
                            }
                }

    }

    fun resumeUploadTask(authToken: String, file: File, offset: Long, sessionURL: String) : Flowable<Any>
    {
        return Flowable.create<Any>({ flowableSource->
            val requestBody = object : RequestBody(){

                override fun contentType(): MediaType = MediaType.parse("video/*")
                override fun contentLength(): Long = file.length() - offset
                override fun writeTo(sink: BufferedSink) {

                    val countingSink = Okio.buffer(object: ForwardingSink(sink)
                    {
                        var total: Long = offset
                        override fun write(source: Buffer?, byteCount: Long) {
                            total += byteCount
                            flowableSource.onNext(total)
                            super.write(source, byteCount)
                        }
                    })

                    Okio.buffer(Okio.source(file)).use {
                        it.skip(offset)
                        while(it.read(countingSink.buffer(), SEGMENT_SIZE) != -1L)
                        {
                            countingSink.flush()
                        }
                    }
                }
            }

            val disposable = youtubeAPI.resumeUpload(sessionURL, authToken, "bytes $offset-${file.length()-1L}/${file.length()}", requestBody)
                    .doOnNext { res ->
                        flowableSource.onNext(res)
                        flowableSource.onComplete()
                    }
                    .subscribe()

            flowableSource.setCancellable { disposable.dispose() }
        }, BackpressureStrategy.LATEST)
    }
}
