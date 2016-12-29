package com.schinizer.youtubeuploader

import android.accounts.Account
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInResult
import com.google.android.gms.common.api.GoogleApiClient
import com.pavlospt.rxfile.RxFile
import com.schinizer.youtubeuploader.model.VideoResource
import hu.akarnokd.rxjava.interop.RxJavaInterop
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.observers.DisposableObserver
import io.reactivex.schedulers.Schedulers
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.Okio
import java.io.File
import javax.inject.Inject

class MainActivity : AppCompatActivity() {

    val ACTION_GET_CONTENT = 1
    val ACTION_SIGN_IN = 2
    val ACTION_AUTHORIZATION = 3

    val disposable = CompositeDisposable()
    lateinit var googleApiClient: GoogleApiClient
    lateinit var googleSignInResult: GoogleSignInResult
    lateinit var authToken: String

    @Inject
    lateinit var youtubeAPI: YoutubeAPI

    @Inject
    lateinit var okhttpClient: OkHttpClient

    companion object
    {
        val TAG = MainActivity::class.java.simpleName
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        YoutubeUploaderApplication.apiComponent.inject(this)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build()

        googleApiClient = GoogleApiClient.Builder(this)
                .enableAutoManage(this, { connectionResult ->
                    Log.d("MainActivity", "onConnectionFailed:" + connectionResult)
                })
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build()

        val signInIntent = Auth.GoogleSignInApi.getSignInIntent(googleApiClient)
        startActivityForResult(signInIntent, ACTION_SIGN_IN)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.actions_main_activity, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean = when (item?.itemId)
    {
        R.id.menu_upload -> {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT) // Grants persisting permissions, API > 19 only
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "video/*"
            //intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            startActivityForResult(intent, ACTION_GET_CONTENT)
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
    {
        when(requestCode)
        {
            ACTION_GET_CONTENT ->
            {
                when(resultCode)
                {
                    RESULT_OK ->
                    {
                        val uri = data?.data
                        contentResolver.takePersistableUriPermission(uri, intent.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
                        upload("Bearer $authToken", uri)

                        Log.d("activity result", uri.toString())
                    }
                }
            }
            ACTION_SIGN_IN ->
            {
                googleSignInResult = Auth.GoogleSignInApi.getSignInResultFromIntent(data)

                if(googleSignInResult.isSuccess) {
                    val signInAccount = googleSignInResult.signInAccount
                    signInAccount?.let {
                        val task = getAuthToken(it.account)

                        disposable.clear()
                        disposable.add(task.subscribeWith(object : DisposableObserver<String>() {
                            override fun onNext(t: String?) {
                                authToken = t ?: ""
                                Log.d("activity result", t)
                            }

                            override fun onComplete() = Unit

                            override fun onError(e: Throwable?) {
                                Log.e("Main Activity", "onError", e)
                            }
                        }))
                    }
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    fun getAuthToken(account: Account?) : Observable<String>
    {
        return Observable.fromCallable {
            return@fromCallable GoogleAuthUtil.getToken(this, account, "oauth2:https://www.googleapis.com/auth/youtube.upload")
        }
                .retryWhen { error ->
                    error.flatMap {
                        when(it)
                        {
                            is UserRecoverableAuthException ->
                            {
                                startActivityForResult(it.intent, ACTION_AUTHORIZATION)
                                return@flatMap Observable.empty<Throwable>()
                            }
                            else -> return@flatMap Observable.error<Throwable>(it)
                        }
                    }
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
    }

    fun upload(authToken: String, uri: Uri?)
    {
        val params = VideoResource(VideoResource.Snippet("test", "test", arrayListOf(""), 15), VideoResource.Status("unlisted", false, "youtube"))

        RxJavaInterop.toV2Flowable(RxFile.createFileFromUri(this@MainActivity, uri))
                .flatMap { file ->
                    youtubeAPI.startSession(authToken, file.length(), "video/*", params)
                            .toFlowable(BackpressureStrategy.LATEST)
                            .flatMap { res ->
                                uploadVideoTask(file, res.response().headers().get("Location"))
                            }
                }
                .subscribe { totalBytes ->
                    Log.d(TAG, "onNext: $totalBytes")
                }
    }

    fun uploadVideoTask(file: File, sessionURL: String) : Flowable<Long>
    {
        val SEGMENT_SIZE = 8L * 1024L

        return Flowable.create<Long>({ flowableSource ->
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

            val request = Request.Builder()
                    .url(sessionURL)
                    .put(requestBody)
                    .addHeader("Authorization", "Bearer $authToken")
                    .build()


            val response = okhttpClient.newCall(request)
                    .apply {
                        flowableSource.setCancellable { cancel() }
                    }
                    .execute()

            if(response.isSuccessful)
            {
                flowableSource.onComplete()
            }

        }, BackpressureStrategy.LATEST)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
    }
}
