package com.jerry.ksl.gesturerecognizer.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.jerry.ksl.gesturerecognizer.adapter.ValidationHistoryAdapter
import com.jerry.ksl.gesturerecognizer.databinding.FragmentValidationHistoryBinding
import com.jerry.ksl.gesturerecognizer.model.ValidationRecord
import com.jerry.ksl.gesturerecognizer.service.ValidationService
import kotlinx.coroutines.launch

class ValidationHistoryFragment : Fragment() {

    private var _binding: FragmentValidationHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var validationHistoryAdapter: ValidationHistoryAdapter
    private val validationService = ValidationService()
    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentValidationHistoryBinding.inflate(inflater, container, false)
        firebaseAuth = FirebaseAuth.getInstance()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        loadValidationHistory()
    }

    private fun setupRecyclerView() {
        validationHistoryAdapter = ValidationHistoryAdapter { record ->
            deleteValidationRecord(record)
        }
        binding.recyclerViewValidationHistory.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = validationHistoryAdapter
        }
    }

    private fun loadValidationHistory() {
        val userId = firebaseAuth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            updateVisibility(false)
            return
        }

        binding.progressBarHistory.visibility = View.VISIBLE
        binding.recyclerViewValidationHistory.visibility = View.GONE
        binding.textNoHistory.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val records = validationService.getValidationRecords(userId)
                Log.d("ValidationHistoryFragment", "Loaded ${records.size} records.")
                validationHistoryAdapter.submitList(records)
                updateSummaryReport(records)
                updateVisibility(records.isNotEmpty())
            } catch (e: Exception) {
                Log.e("ValidationHistoryFragment", "Error loading history", e)
                Toast.makeText(requireContext(), "Error loading history: ${e.message}", Toast.LENGTH_SHORT).show()
                updateVisibility(false)
            } finally {
                binding.progressBarHistory.visibility = View.GONE
            }
        }
    }

    private fun deleteValidationRecord(record: ValidationRecord) {
        lifecycleScope.launch {
            binding.progressBarHistory.visibility = View.VISIBLE
            try {
                val success = validationService.deleteValidationRecord(record.id)
                if (success) {
                    Toast.makeText(requireContext(), "Record deleted", Toast.LENGTH_SHORT).show()
                    loadValidationHistory() // Reload data after deletion
                } else {
                    Toast.makeText(requireContext(), "Failed to delete record", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error deleting record: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBarHistory.visibility = View.GONE
            }
        }
    }

    private fun updateSummaryReport(records: List<ValidationRecord>) {
        if (records.isEmpty()) {
            binding.textSummaryReport.text = "No validation data available."
            return
        }

        val totalValidations = records.size
        val correctValidations = records.count { it.isCorrect }
        val accuracy = if (totalValidations > 0) (correctValidations.toFloat() / totalValidations) * 100 else 0f

        binding.textSummaryReport.text = "Model Performance: %.2f%% accuracy across %d validations".format(accuracy, totalValidations)
    }

    private fun updateVisibility(hasData: Boolean) {
        binding.progressBarHistory.visibility = View.GONE
        if (hasData) {
            binding.recyclerViewValidationHistory.visibility = View.VISIBLE
            binding.textNoHistory.visibility = View.GONE
        } else {
            binding.recyclerViewValidationHistory.visibility = View.GONE
            binding.textNoHistory.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
