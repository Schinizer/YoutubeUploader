package com.schinizer.youtubeuploader

import android.accounts.Account
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.OnClick
import com.afollestad.materialdialogs.MaterialDialog
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

class MainActivity : AppCompatActivity() {

    val ACTION_GET_CONTENT = 1
    val ACTION_SIGN_IN = 2
    val ACTION_AUTHORIZATION = 3

    val disposable = CompositeDisposable()
    lateinit var googleApiClient: GoogleApiClient
    lateinit var googleSignInResult: GoogleSignInResult

    @BindView(R.id.fab)
    lateinit var fab: FloatingActionButton

    @BindView(R.id.recyclerView)
    lateinit var recyclerView: RecyclerView

    lateinit var realm: Realm

    @Inject
    lateinit var authToken: AuthToken

    companion object
    {
        val TAG = MainActivity::class.java.simpleName
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ButterKnife.bind(this)
        YoutubeUploaderApplication.apiComponent.inject(this)

        realm = Realm.getDefaultInstance()

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = UploadTaskAdapter(this, realm.where(UploadTask::class.java).findAll())
        recyclerView.setHasFixedSize(true)
        recyclerView.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

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
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?) = when(item?.itemId)
    {
        R.id.menu_delete_all->
        {
            MaterialDialog.Builder(this)
                    .content("Delete all tasks? You cannot undo this.")
                    .positiveText("Delete")
                    .negativeText("No")
                    .onPositive { dialog, which ->
                        realm.executeTransaction {
                            realm.deleteAll()
                        }
                    }
                    .show()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    @OnClick(R.id.fab)
    fun launchSelectFileIntent()
    {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT) // Grants persisting permissions, API > 19 only
        intent.type = "video/*"
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true)
        //intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(Intent.createChooser(intent, "Select file to upload"), ACTION_GET_CONTENT)
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
                        data?.let {
                            val uri = data.data
                            contentResolver.takePersistableUriPermission(uri, data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)

                            val uploadActivity = Intent(this, UploadVideoActivity::class.java)
                            uploadActivity.putExtra("uri", uri.toString())
                            startActivity(uploadActivity)

                            Log.d(TAG, uri.toString())
                        }

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
                        disposable.add(task.subscribeWith(object : DisposableObserver<String?>() {
                            override fun onNext(t: String?) {
                                authToken.token = t ?: ""
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

    fun resumeUploads()
    {
        realm.where(UploadTask::class.java).findAll().forEach {
            val intent = Intent(this, UploadService::class.java)
            intent.putExtra("id", it.id)
            startService(intent)
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

    override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
        realm.close()
    }
}
