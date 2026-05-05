package com.piratebay.app.adapter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.piratebay.app.R
import com.piratebay.app.model.TorrentItem

class TorrentAdapter(
    private val context: Context,
    private val torrents: MutableList<TorrentItem>
) : RecyclerView.Adapter<TorrentAdapter.TorrentViewHolder>() {

    class TorrentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleTextView: TextView = view.findViewById(R.id.titleTextView)
        val sizeTextView: TextView = view.findViewById(R.id.sizeTextView)
        val seedersTextView: TextView = view.findViewById(R.id.seedersTextView)
        val leechersTextView: TextView = view.findViewById(R.id.leechersTextView)
        val dateTextView: TextView = view.findViewById(R.id.dateTextView)
        val uploaderTextView: TextView = view.findViewById(R.id.uploaderTextView)
        val copyMagnetButton: Button = view.findViewById(R.id.copyMagnetButton)
        val shareButton: Button = view.findViewById(R.id.shareButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TorrentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_torrent, parent, false)
        return TorrentViewHolder(view)
    }

    override fun onBindViewHolder(holder: TorrentViewHolder, position: Int) {
        val torrent = torrents[position]
        
        holder.titleTextView.text = torrent.title
        holder.sizeTextView.text = torrent.size
        holder.seedersTextView.text = "种子: ${torrent.seeders}"
        holder.leechersTextView.text = "下载: ${torrent.leechers}"
        holder.dateTextView.text = torrent.uploadDate
        holder.uploaderTextView.text = "上传者: ${torrent.uploader}"
        
        holder.copyMagnetButton.setOnClickListener {
            copyToClipboard(torrent.magnetLink, "磁力链接")
            Toast.makeText(context, "磁力链接已复制", Toast.LENGTH_SHORT).show()
        }
        
        holder.shareButton.setOnClickListener {
            shareMagnetLink(torrent.magnetLink, torrent.title)
        }
        
        holder.itemView.setOnLongClickListener {
            copyToClipboard(torrent.magnetLink, "磁力链接")
            Toast.makeText(context, "磁力链接已复制", Toast.LENGTH_SHORT).show()
            true
        }
    }

    override fun getItemCount() = torrents.size

    fun updateData(newTorrents: List<TorrentItem>) {
        torrents.clear()
        torrents.addAll(newTorrents)
        notifyDataSetChanged()
    }

    fun clear() {
        torrents.clear()
        notifyDataSetChanged()
    }

    private fun copyToClipboard(text: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

    private fun shareMagnetLink(magnetLink: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, magnetLink)
        }
        val chooserIntent = Intent.createChooser(intent, "分享磁力链接")
        context.startActivity(chooserIntent)
    }
}
