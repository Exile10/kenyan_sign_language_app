package com.jerry.ksl.gesturerecognizer.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.bumptech.glide.Glide
import com.jerry.ksl.gesturerecognizer.SharedViewModel
import com.jerry.ksl.gesturerecognizer.databinding.FragmentTrainingImageDetailBinding
import com.jerry.ksl.gesturerecognizer.model.TrainingImageItem
import java.text.SimpleDateFormat
import java.util.Locale

class TrainingImageDetailFragment : Fragment() {

    private var _binding: FragmentTrainingImageDetailBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SharedViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrainingImageDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedViewModel.selectedTrainingImageItem.observe(viewLifecycleOwner) {
            it?.let { item ->
                displayDetails(item)
            }
        }
    }

    private fun displayDetails(item: TrainingImageItem) {
        Glide.with(this)
            .load(item.imageUrl)
            .into(binding.imageViewDetail)

        binding.textViewDetailLabel.text = "Label: ${item.label}"
        binding.textViewDetailTimestamp.text = item.timestamp?.toDate()?.let {
            "Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(it)}"
        } ?: "Timestamp: N/A"
        binding.textViewDetailValidated.text = "Validated: ${if (item.validated) "Yes" else "No"}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}