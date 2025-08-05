package com.jerry.ksl.gesturerecognizer.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.jerry.ksl.gesturerecognizer.R
import com.jerry.ksl.gesturerecognizer.databinding.FragmentLearnKslBinding

/**
 * Fragment for learning Kenya Sign Language (KSL)
 */
class LearnKslFragment : Fragment() {
    private var _binding: FragmentLearnKslBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLearnKslBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up click listeners for card views
        binding.cardAlphabet.setOnClickListener {
            // Navigate to alphabet learning screen
            // Future implementation: Open alphabet learning details screen
        }

        binding.cardNumbers.setOnClickListener {
            // Navigate to numbers learning screen
            // Future implementation: Open numbers learning details screen
        }

        binding.cardCommonPhrases.setOnClickListener {
            // Navigate to phrases learning screen
            // Future implementation: Open common phrases details screen
        }

        binding.fabPractice.setOnClickListener {
            // Navigate to practice screen (camera fragment)
            // Future implementation: Navigate to the camera fragment for practice
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
