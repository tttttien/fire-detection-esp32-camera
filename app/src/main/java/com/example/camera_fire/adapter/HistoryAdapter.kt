package com.example.camera_fire.adapter

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.camera_fire.R
import com.example.camera_fire.VideoPlayerActivity
import com.example.camera_fire.databinding.ItemHistoryBinding
import com.example.camera_fire.models.FireEvent
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class HistoryAdapter : ListAdapter<FireEvent, HistoryAdapter.HistoryViewHolder>(FireEventDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class HistoryViewHolder(private val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(event: FireEvent) {
            binding.eventId.text = binding.root.context.getString(R.string.event_id_format, event.id)
            binding.eventTimestamp.text = formatTimestamp(event.createdAt)

            Glide.with(binding.root.context)
                .load(event.publicUrl)
                .placeholder(R.drawable.ic_launcher_background)
                .error(R.drawable.ic_launcher_background)
                .into(binding.eventImage)

            if (event.eventType == "video") {
                binding.playIcon.visibility = View.VISIBLE
                binding.root.setOnClickListener { _ ->
                    event.publicUrl?.let {
                        val intent = Intent(binding.root.context, VideoPlayerActivity::class.java)
                        intent.putExtra("VIDEO_URL", it)
                        binding.root.context.startActivity(intent)
                    }
                }
            } else {
                binding.playIcon.visibility = View.GONE
                binding.root.setOnClickListener(null) // Remove listener for image items
            }
        }

        private fun formatTimestamp(timestamp: String): String {
            // First, try parsing with fractional seconds
            try {
                val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX", Locale.US)
                parser.timeZone = TimeZone.getTimeZone("UTC")
                val date = parser.parse(timestamp)
                val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                return date?.let { formatter.format(it) } ?: timestamp
            } catch (e: Exception) {
                // If that fails, try parsing without fractional seconds
                try {
                    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                    parser.timeZone = TimeZone.getTimeZone("UTC")
                    val date = parser.parse(timestamp)
                    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    return date?.let { formatter.format(it) } ?: timestamp
                } catch (e2: Exception) {
                    // If both fail, return the original string
                    return timestamp
                }
            }
        }
    }

    class FireEventDiffCallback : DiffUtil.ItemCallback<FireEvent>() {
        override fun areItemsTheSame(oldItem: FireEvent, newItem: FireEvent): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: FireEvent, newItem: FireEvent): Boolean {
            return oldItem == newItem
        }
    }
}
