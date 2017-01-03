package com.schinizer.youtubeuploader

import android.accounts.Account
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.design.widget.TextInputLayout
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.AppCompatSpinner
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import butterknife.BindView
import butterknife.ButterKnife
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInResult
import com.google.android.gms.common.api.GoogleApiClient
import com.schinizer.youtubeuploader.model.AuthToken
import com.schinizer.youtubeuploader.model.UploadTask
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.observers.DisposableObserver
import io.reactivex.schedulers.Schedulers
import io.realm.Realm
import javax.inject.Inject

class UploadVideoActivity : AppCompatActivity() {

    val ACTION_SIGN_IN = 2
    val ACTION_AUTHORIZATION = 3

    @BindView(R.id.title)
    lateinit var title: TextInputLayout
    @BindView(R.id.description)
    lateinit var description: TextInputLayout
    @BindView(R.id.spinner)
    lateinit var spinner : AppCompatSpinner

    val disposable = CompositeDisposable()

    lateinit var realm: Realm
    lateinit var uri: String

    lateinit var googleApiClient: GoogleApiClient
    lateinit var googleSignInResult: GoogleSignInResult

    @Inject
    lateinit var authToken: AuthToken

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upload_video)
        ButterKnife.bind(this)
        YoutubeUploaderApplication.apiComponent.inject(this)

        spinner.adapter = ArrayAdapter.createFromResource(this, R.array.privacyStatus_array, R.layout.support_simple_spinner_dropdown_item)

        when(intent?.action)
        {
            Intent.ACTION_SEND ->
            {
                val data = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                uri = data.toString()
            }
            else ->
            {
                uri = intent.extras.getString("uri")
            }
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build()

        googleApiClient = GoogleApiClient.Builder(this)
                .enableAutoManage(this, { connectionResult ->
                    Log.d("MainActivity", "onConnectionFailed:" + connectionResult)
                })
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {

        menuInflater.inflate(R.menu.actions_upload_video_activity, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when(requestCode)
        {
            ACTION_SIGN_IN ->
            {
                googleSignInResult = Auth.GoogleSignInApi.getSignInResultFromIntent(data)

                if(googleSignInResult.isSuccess) {
                    val signInAccount = googleSignInResult.signInAccount
                    signInAccount?.let {
                        val task = getAuthToken(it.account)
                                .map { token ->
                                    authToken.token = token
                                    createTaskAndLaunchService()
                                }

                        disposable.clear()
                        disposable.add(task.subscribeWith(object : DisposableObserver<Unit>() {
                            override fun onNext(t: Unit?) = Unit
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

    fun createTaskAndLaunchService()
    {
        val id = YoutubeUploaderApplication.primaryKeyValue.incrementAndGet()
        Realm.getDefaultInstance().use {
            it.executeTransaction {
                it.createObject(UploadTask::class.java, id).apply {
                    uri = this@UploadVideoActivity.uri
                    title = this@UploadVideoActivity.title.editText?.text?.toString()
                    description = this@UploadVideoActivity.description.editText?.text?.toString()
                    privacyStatus = spinner.selectedItem.toString()
                }
            }
        }
        val intent = Intent(this, UploadService::class.java)
        intent.putExtra("id", id)
        startService(intent)
        finish()

        if(this.intent.action == Intent.ACTION_SEND)
        {
            startActivity(Intent(this, MainActivity::class.java))
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

    override fun onOptionsItemSelected(item: MenuItem?) = when(item?.itemId)
    {
        R.id.menu_send->
        {
            when(authToken.token)
            {
                "" ->
                {
                    val signInIntent = Auth.GoogleSignInApi.getSignInIntent(googleApiClient)
                    startActivityForResult(signInIntent, ACTION_SIGN_IN)
                }
                else ->  createTaskAndLaunchService()
            }
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
