package com.jerry.ksl.gesturerecognizer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.jerry.ksl.gesturerecognizer.R
import com.jerry.ksl.gesturerecognizer.model.ValidationRecord
import java.text.SimpleDateFormat
import java.util.*

class ValidationHistoryAdapter(
    private val onDeleteClicked: (ValidationRecord) -> Unit
) : ListAdapter<ValidationRecord, ValidationHistoryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_validation_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, onDeleteClicked)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.image_view_sign)
        private val expectedLabelText: TextView = itemView.findViewById(R.id.text_expected_label)
        private val predictedLabelText: TextView = itemView.findViewById(R.id.text_predicted_label)
        private val confidenceText: TextView = itemView.findViewById(R.id.text_confidence)
        private val inferenceTimeText: TextView = itemView.findViewById(R.id.text_inference_time)
        private val timestampText: TextView = itemView.findViewById(R.id.text_timestamp)
        private val resultIndicatorText: TextView = itemView.findViewById(R.id.text_result_indicator)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.button_delete_validation)

        fun bind(
            item: ValidationRecord,
            onDeleteClicked: (ValidationRecord) -> Unit
        ) {
            expectedLabelText.text = "Expected: ${item.expectedLabel}"
            predictedLabelText.text = "Predicted: ${item.predictedLabel}"
            confidenceText.text = "Confidence: ${String.format("%.2f", item.confidenceScore)}"
            inferenceTimeText.text = "Inference time: ${item.inferenceTimeMs}ms"

            timestampText.text = item.timestamp?.toDate()?.let {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(it)
            } ?: "No date"

            // Set result indicator (check/cross)
            if (item.isCorrect) {
                resultIndicatorText.text = "✓"
                resultIndicatorText.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.holo_green_dark))
            } else {
                resultIndicatorText.text = "✗"
                resultIndicatorText.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.holo_red_dark))
            }

            // Load image with Glide
            Glide.with(itemView.context)
                .load(item.imageUrl)
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_broken_image)
                .into(imageView)

            deleteButton.setOnClickListener { onDeleteClicked(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ValidationRecord>() {
        override fun areItemsTheSame(oldItem: ValidationRecord, newItem: ValidationRecord): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ValidationRecord, newItem: ValidationRecord): Boolean {
            return oldItem == newItem
        }
    }
}