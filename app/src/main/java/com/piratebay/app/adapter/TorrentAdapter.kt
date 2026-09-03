package com.piratebay.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.piratebay.app.R
import com.piratebay.app.databinding.ItemTorrentBinding
import com.piratebay.app.model.TorrentItem

class TorrentAdapter(
    private val onTranslateClick: (TorrentItem) -> Unit,
    private val onItemClick: (TorrentItem) -> Unit,
    private val onCopyClick: (TorrentItem) -> Unit,
    private val onShareClick: (TorrentItem) -> Unit
) : ListAdapter<TorrentItem, TorrentAdapter.TorrentViewHolder>(TorrentDiffCallback()) {

    class TorrentViewHolder(val binding: ItemTorrentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TorrentViewHolder {
        val binding = ItemTorrentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TorrentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TorrentViewHolder, position: Int) {
        val torrent = getItem(position)
        val binding = holder.binding

        binding.titleTextView.text = torrent.displayTitle

        when {
            torrent.isTranslating -> {
                binding.translateButton.isEnabled = false
                binding.translateButton.text = "..."
                binding.translateButton.setBackgroundResource(R.drawable.button_secondary_background)
            }
            torrent.isTranslated -> {
                binding.translateButton.isEnabled = true
                binding.translateButton.text = "原文"
                binding.translateButton.setBackgroundResource(R.drawable.button_green_background)
            }
            else -> {
                binding.translateButton.isEnabled = true
                binding.translateButton.text = "翻译"
                binding.translateButton.setBackgroundResource(R.drawable.button_secondary_background)
            }
        }

        binding.sizeTextView.text = torrent.formattedSize
        binding.seedersTextView.text = "● ${torrent.seedersCount} 做种"
        binding.leechersTextView.text = "● ${torrent.leechersCount} 下载"
        binding.dateTextView.text = torrent.formattedDate
        binding.uploaderTextView.text = "上传者: ${torrent.uploader}"

        binding.translateButton.setOnClickListener {
            onTranslateClick(torrent)
        }

        binding.copyMagnetButton.setOnClickListener {
            onCopyClick(torrent)
        }

        binding.shareButton.setOnClickListener {
            onShareClick(torrent)
        }

        binding.root.setOnClickListener {
            onItemClick(torrent)
        }

        binding.root.setOnLongClickListener {
            onCopyClick(torrent)
            true
        }
    }

    class TorrentDiffCallback : DiffUtil.ItemCallback<TorrentItem>() {
        override fun areItemsTheSame(oldItem: TorrentItem, newItem: TorrentItem): Boolean {
            return oldItem.infoHash.equals(newItem.infoHash, ignoreCase = true) || oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TorrentItem, newItem: TorrentItem): Boolean {
            return oldItem == newItem
        }
    }
}

