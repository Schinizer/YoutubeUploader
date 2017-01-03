package com.schinizer.youtubeuploader

import android.content.Context
import android.content.Intent
import android.support.v7.widget.RecyclerView
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import butterknife.BindView
import butterknife.ButterKnife
import com.schinizer.youtubeuploader.model.AuthToken
import com.schinizer.youtubeuploader.model.UploadTask
import io.reactivex.disposables.Disposable
import io.realm.OrderedRealmCollection
import io.realm.RealmRecyclerViewAdapter
import javax.inject.Inject


/**
 * Created by DPSUser on 1/2/2017.
 */
class UploadTaskAdapter(incomingContext: Context, incomingData: OrderedRealmCollection<UploadTask>) : RealmRecyclerViewAdapter<UploadTask, UploadTaskAdapter.ViewHolder>(incomingContext, incomingData, true)
{
    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int) = ViewHolder(LayoutInflater.from(context).inflate(R.layout.view_upload_progress, parent, false))

    override fun onBindViewHolder(holder: ViewHolder?, position: Int) {
        val task = data?.get(position)

        holder?.apply {
            task?.let {
                title.text = task.title
                info.text = "${Formatter.formatFileSize(context, task.bytesWritten)} of ${Formatter.formatFileSize(context, task.contentLength)}"
                progressBar.progress = (task.bytesWritten.toDouble() / task.contentLength.toDouble() * 100.0).toInt()

                pauseButton.apply {
                    if(disposables.containsKey(task.id))
                    {
                        pauseButton.text = "Pause"
                    }
                    else
                    {
                        pauseButton.text = "Resume"
                    }
                }
                pauseButton.setOnClickListener {
                    if(disposables.containsKey(task.id))
                    {
                        disposables.get(task.id)?.dispose()
                        pauseButton.text = "Resume"
                        disposables.remove(task.id)
                    }
                    else
                    {
                        val intent = Intent(context, UploadService::class.java)
                        intent.putExtra("id", task.id)
                        context.startService(intent)
                    }
                }
            }
        }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view)
    {
        @BindView(R.id.title)
        lateinit var title: TextView

        @BindView(R.id.info)
        lateinit var info: TextView

        @BindView(R.id.progressBar)
        lateinit var progressBar: ProgressBar

        @BindView(R.id.pauseButton)
        lateinit var pauseButton : Button

        @Inject
        lateinit var disposables: HashMap<Long, Disposable>

        @Inject
        lateinit var authToken: AuthToken

        init{
            ButterKnife.bind(this, view)
            YoutubeUploaderApplication.apiComponent.inject(this)
        }
    }
}