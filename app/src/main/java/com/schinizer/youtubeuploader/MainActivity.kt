package com.schinizer.youtubeuploader

import android.content.Intent
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
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.observers.DisposableObserver
import io.reactivex.schedulers.Schedulers

class MainActivity : AppCompatActivity() {

    val ACTION_GET_CONTENT = 1
    val ACTION_SIGN_IN = 2
    val ACTION_AUTHORIZATION = 3

    val disposable = CompositeDisposable()
    lateinit var googleApiClient: GoogleApiClient
    lateinit var googleSignInResult: GoogleSignInResult

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

                        val signInIntent = Auth.GoogleSignInApi.getSignInIntent(googleApiClient)
                        startActivityForResult(signInIntent, ACTION_SIGN_IN)
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
                        val task = Observable.fromCallable {
                            return@fromCallable GoogleAuthUtil.getToken(this, it.account, "oauth2:https://www.googleapis.com/auth/youtube.upload")
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

                        disposable.clear()
                        disposable.add(task.subscribeWith(object : DisposableObserver<String>() {
                            override fun onNext(t: String?) {
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

    override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
    }
}
