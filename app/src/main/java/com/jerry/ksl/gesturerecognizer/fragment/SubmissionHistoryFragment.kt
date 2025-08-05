package com.jerry.ksl.gesturerecognizer.fragment

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.jerry.ksl.gesturerecognizer.adapter.SubmissionHistoryAdapter
import com.jerry.ksl.gesturerecognizer.databinding.FragmentSubmissionHistoryBinding
import com.jerry.ksl.gesturerecognizer.model.TrainingImageItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import androidx.fragment.app.activityViewModels
import com.jerry.ksl.gesturerecognizer.R
import com.jerry.ksl.gesturerecognizer.SharedViewModel

class SubmissionHistoryFragment : Fragment() {

    private var _binding: FragmentSubmissionHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var submissionHistoryAdapter: SubmissionHistoryAdapter

    private val submissionList = mutableListOf<TrainingImageItem>()
    private val sharedViewModel: SharedViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSubmissionHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        setupRecyclerView()
        loadSubmissionHistory()
    }

    private fun setupRecyclerView() {
        submissionHistoryAdapter = SubmissionHistoryAdapter(
            onDeleteClicked = { item ->
                confirmDeleteItem(item)
            },
            onSubmissionItemClicked = { item ->
                sharedViewModel.selectedTrainingImageItem.value = item
                findNavController().navigate(R.id.action_submission_history_to_training_image_detail)
            },
            lifecycleScope = viewLifecycleOwner.lifecycleScope
        )

        binding.recyclerViewSubmissionHistory.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = submissionHistoryAdapter
        }
    }

    private fun loadSubmissionHistory() {
        binding.progressBarHistory.visibility = View.VISIBLE
        binding.textNoHistory.visibility = View.GONE
        binding.textSummaryReport.visibility = View.GONE

        val userId = auth.currentUser?.uid
        if (userId == null) {
            handleNoUser()
            return
        }

        firestore.collection("TRAINING_IMAGES")
            .whereEqualTo("user_id", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                binding.progressBarHistory.visibility = View.GONE
                if (documents.isEmpty) {
                    handleEmptyHistory()
                } else {
                    handleSuccessfulLoad(documents.toObjects(TrainingImageItem::class.java))
                }
            }
            .addOnFailureListener { e ->
                handleLoadFailure(e)
            }
    }

    private fun handleNoUser() {
        binding.progressBarHistory.visibility = View.GONE
        binding.textNoHistory.visibility = View.VISIBLE
        binding.textNoHistory.text = "Please login to see your submission history."
        binding.textSummaryReport.visibility = View.GONE
    }

    private fun handleEmptyHistory() {
        binding.textNoHistory.visibility = View.VISIBLE
        binding.textNoHistory.text = "No submissions found. Try submitting some training data first."
        binding.textSummaryReport.visibility = View.GONE
    }

    private fun handleSuccessfulLoad(items: List<TrainingImageItem>) {
        submissionList.clear()
        submissionList.addAll(items)
        submissionHistoryAdapter.submitList(submissionList)
        updateSummaryReport(submissionList)
        binding.textSummaryReport.visibility = View.VISIBLE
        binding.textNoHistory.visibility = View.GONE
    }

    private fun handleLoadFailure(e: Exception) {
        binding.progressBarHistory.visibility = View.GONE
        binding.textNoHistory.visibility = View.VISIBLE
        binding.textNoHistory.text = "Error loading history: ${e.message}"
        binding.textSummaryReport.visibility = View.GONE
        Log.e(TAG, "Error loading submission history", e)
        Toast.makeText(context, "Error loading history: ${e.message}", Toast.LENGTH_LONG).show()
    }

    private fun updateSummaryReport(items: List<TrainingImageItem>) {
        val totalSubmissions = items.size
        val uniqueSigns = items.map { it.label }.distinct().size
        binding.textSummaryReport.text = "Summary: $totalSubmissions images submitted for $uniqueSigns unique signs."
    }

    private fun confirmDeleteItem(item: TrainingImageItem) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Submission")
            .setMessage("Are you sure you want to delete this submission? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteSubmission(item)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSubmission(item: TrainingImageItem) {
        binding.progressBarHistory.visibility = View.VISIBLE // Show progress during deletion

        // 1. Delete from Firestore
        firestore.collection("TRAINING_IMAGES").document(item.id)
            .delete()
            .addOnSuccessListener {
                // 2. Delete from Firebase Storage
                if (item.imageUrl.isNotEmpty()) {
                    try {
                        val imageRef = storage.getReferenceFromUrl(item.imageUrl)
                        imageRef.delete()
                            .addOnSuccessListener {
                                handleDeleteSuccess()
                            }
                            .addOnFailureListener { e ->
                                handleImageDeleteFailure(e)
                            }
                    } catch (e: IllegalArgumentException) {
                        // Handle invalid URL format
                        Log.e(TAG, "Invalid image URL format: ${item.imageUrl}", e)
                        handleDeleteSuccess() // Still consider successful since document was deleted
                    }
                } else {
                    handleDeleteSuccess()
                }
            }
            .addOnFailureListener { e ->
                handleDocumentDeleteFailure(e)
            }
    }

    private fun handleDeleteSuccess() {
        binding.progressBarHistory.visibility = View.GONE
        Toast.makeText(context, "Submission deleted successfully.", Toast.LENGTH_SHORT).show()
        // Refresh list from Firestore to ensure consistency
        loadSubmissionHistory()
    }

    private fun handleImageDeleteFailure(e: Exception) {
        binding.progressBarHistory.visibility = View.GONE
        Log.e(TAG, "Error deleting image from Storage", e)
        Toast.makeText(context, "Document deleted but error removing image file: ${e.message}", Toast.LENGTH_LONG).show()
        // Even if storage deletion fails, the Firestore entry is gone.
        // Consider how to handle this - perhaps a cleanup job later or log for manual check.
        loadSubmissionHistory()
    }

    private fun handleDocumentDeleteFailure(e: Exception) {
        binding.progressBarHistory.visibility = View.GONE
        Log.e(TAG, "Error deleting document from Firestore", e)
        Toast.makeText(context, "Error deleting submission data: ${e.message}", Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "SubmissionHistoryFrag"
    }
}
