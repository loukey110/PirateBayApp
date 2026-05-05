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

    fun updateAndSort(newTorrents: List<TorrentItem>, sortType: Int) {
        torrents.clear()
        torrents.addAll(newTorrents)
        
        when (sortType) {
            1 -> {
                val sorted = torrents.sortedBy { parseDate(it.uploadDate) }
                torrents.clear()
                torrents.addAll(sorted)
            }
            2 -> {
                val sorted = torrents.sortedByDescending { parseDate(it.uploadDate) }
                torrents.clear()
                torrents.addAll(sorted)
            }
            3 -> {
                val sorted = torrents.sortedBy { parseSize(it.size) }
                torrents.clear()
                torrents.addAll(sorted)
            }
            4 -> {
                val sorted = torrents.sortedByDescending { parseSize(it.size) }
                torrents.clear()
                torrents.addAll(sorted)
            }
            5 -> {
                val sorted = torrents.sortedBy { it.seeders.toIntOrNull() ?: 0 }
                torrents.clear()
                torrents.addAll(sorted)
            }
            6 -> {
                val sorted = torrents.sortedByDescending { it.seeders.toIntOrNull() ?: 0 }
                torrents.clear()
                torrents.addAll(sorted)
            }
        }
        
        notifyDataSetChanged()
    }

    fun sortByDate(descending: Boolean = true) {
        val sorted = torrents.sortedByDescending { 
            parseDate(it.uploadDate) 
        }
        torrents.clear()
        torrents.addAll(sorted)
        notifyDataSetChanged()
    }

    fun sortBySize(descending: Boolean = true) {
        val sorted = if (descending) {
            torrents.sortedByDescending { parseSize(it.size) }
        } else {
            torrents.sortedBy { parseSize(it.size) }
        }
        torrents.clear()
        torrents.addAll(sorted)
        notifyDataSetChanged()
    }

    fun sortBySeeders(descending: Boolean = true) {
        val sorted = if (descending) {
            torrents.sortedByDescending { it.seeders.toIntOrNull() ?: 0 }
        } else {
            torrents.sortedBy { it.seeders.toIntOrNull() ?: 0 }
        }
        torrents.clear()
        torrents.addAll(sorted)
        notifyDataSetChanged()
    }

    private fun parseDate(dateStr: String): Long {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            sdf.parse(dateStr)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun parseSize(sizeStr: String): Long {
        return try {
            val regex = Regex("(\\d+\\.?\\d*)\\s*(B|KB|MB|GB|TB)", RegexOption.IGNORE_CASE)
            val match = regex.find(sizeStr)
            if (match != null) {
                val value = match.groupValues[1].toDouble()
                val unit = match.groupValues[2].uppercase()
                when (unit) {
                    "B" -> value.toLong()
                    "KB" -> (value * 1024).toLong()
                    "MB" -> (value * 1024 * 1024).toLong()
                    "GB" -> (value * 1024 * 1024 * 1024).toLong()
                    "TB" -> (value * 1024 * 1024 * 1024 * 1024).toLong()
                    else -> 0L
                }
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
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
