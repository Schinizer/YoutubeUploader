package com.schinizer.youtubeuploader

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem

class MainActivity : AppCompatActivity() {

    val ACTION_GET_CONTENT = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        YoutubeUploaderApplication.apiComponent.inject(this)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, result: Intent?)
    {
        when(requestCode)
        {
            ACTION_GET_CONTENT ->
            {
                when(resultCode)
                {
                    RESULT_OK ->
                    {
                        val uri = result?.data
                        Log.d("activity result", uri.toString())
                    }
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, result)
        }
    }
}
