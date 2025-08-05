package com.jerry.ksl.gesturerecognizer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.jerry.ksl.gesturerecognizer.R
import com.jerry.ksl.gesturerecognizer.model.TrainingImageItem
import java.text.SimpleDateFormat
import java.util.Locale

class SubmissionHistoryAdapter(
    private val onDeleteClicked: (TrainingImageItem) -> Unit,
    private val onSubmissionItemClicked: (TrainingImageItem) -> Unit,
    private val lifecycleScope: LifecycleCoroutineScope
) : ListAdapter<TrainingImageItem, SubmissionHistoryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_submission_history, parent, false)
        return ViewHolder(view, onSubmissionItemClicked)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, onDeleteClicked)
    }

    class ViewHolder(itemView: View, private val onSubmissionItemClicked: (TrainingImageItem) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.image_view_submitted_sign)
        private val labelTextView: TextView = itemView.findViewById(R.id.text_view_label)
        private val timestampTextView: TextView = itemView.findViewById(R.id.text_view_timestamp)
        private val validatedTextView: TextView = itemView.findViewById(R.id.text_view_validated)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.button_delete_submission)

        fun bind(
            item: TrainingImageItem,
            onDeleteClicked: (TrainingImageItem) -> Unit
        ) {
            labelTextView.text = item.label
            timestampTextView.text = item.timestamp?.toDate()?.let {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(it)
            } ?: "No date"
            val validationTextContent= "Validated: ${if (item.validated) "Yes" else "No"}"
            validatedTextView.text = validationTextContent

            // Load image using Glide
            Glide.with(itemView.context)
                .load(item.imageUrl)
                .placeholder(R.drawable.ic_image_placeholder) // Placeholder while loading
                .error(R.drawable.ic_broken_image) // Image to show if loading fails
                .into(imageView)

            deleteButton.setOnClickListener { onDeleteClicked(item) }

            // Set click listener for the item view
            itemView.setOnClickListener { onSubmissionItemClicked(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<TrainingImageItem>() {
        override fun areItemsTheSame(oldItem: TrainingImageItem, newItem: TrainingImageItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TrainingImageItem, newItem: TrainingImageItem): Boolean {
            return oldItem == newItem
        }
    }
}

