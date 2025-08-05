package com.jerry.ksl.gesturerecognizer.fragment

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import com.jerry.ksl.gesturerecognizer.ModelValidationViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jerry.ksl.gesturerecognizer.service.AccessControlService
import com.jerry.ksl.gesturerecognizer.MainViewModel
import com.jerry.ksl.gesturerecognizer.databinding.FragmentModelValidationBinding
import com.jerry.ksl.gesturerecognizer.R

/**
 * Fragment for validating the KSL recognition model by capturing images and analyzing predictions
 * Access restricted to VALIDATOR and DEVELOPER roles only
 */
class ModelValidationFragment : Fragment() {
    private var _binding: FragmentModelValidationBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var modelValidationViewModel: ModelValidationViewModel

    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_FRONT

    /** Blocking operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService

    // List of common KSL signs for data annotation
    private val kslSigns = listOf(
        "Hello", "Thank You", "Yes", "No", "Please", "Sorry", "Love", "Friend",
        "Family", "Home", "Work", "Eat", "Drink", "Sleep", "Walk", "Run",
        "Happy", "Sad", "Angry", "Excited", "Good", "Bad", "More", "Finish",
        "Help", "Learn", "Teacher", "Student", "Book", "Computer", "Phone",
        "Car", "Bus", "Train", "Airplane", "Time", "Day", "Night", "Morning",
        "Evening", "Week", "Month", "Year", "Today", "Tomorrow", "Yesterday",
        "Question", "Answer", "Understand", "Don't Understand", "Again", "Slow",
        "Fast", "Big", "Small", "Hot", "Cold", "New", "Old", "Open", "Close"
    )

    companion object {
        private const val TAG = "ModelValidationFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentModelValidationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        modelValidationViewModel = ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)).get(ModelValidationViewModel::class.java)
        modelValidationViewModel.initGestureRecognizerHelper(requireContext(), mainViewModel)

        // Initialize camera executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Check access permissions first - restrict to VALIDATOR and DEVELOPER roles only
        // This logic remains in the Fragment as it's UI/navigation related
        lifecycleScope.launch {
            val hasAccess = AccessControlService().canAccessValidation()
            if (!hasAccess) {
                Toast.makeText(
                    requireContext(),
                    "Access Denied: Validation features are restricted to Validators and Developers only",
                    Toast.LENGTH_LONG
                ).show()
                try {
                    findNavController().navigate(R.id.camera_fragment)
                } catch (e: Exception) {
                    Log.e(TAG, "Navigation failed", e)
                    activity?.onBackPressed()
                }
                return@launch
            }

            // Remove old AutoCompleteTextView setup for expected label
            // No adapter needed for EditText

            setupButtonListeners()
            setUpCamera()
            setupObservers()
        }

        binding.buttonStartTimer.setOnClickListener {
            if (isTimerRunning) stopTimedCapture() else startTimedCapture()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            findNavController().navigate(R.id.action_camera_to_permissions)
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
        cameraExecutor.shutdown()
        try {
            if (!cameraExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                cameraExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            cameraExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    private var correctCount = 0
    private var totalCount = 0
    private var lastPromptedAt: Int? = null

    private fun updateAccuracyAndPromptIfNeeded(isCorrect: Boolean) {
        totalCount++
        if (isCorrect) correctCount++
        val accuracy = if (totalCount > 0) correctCount.toFloat() / totalCount else 0f
        // Prompt if accuracy is around 70% (68-72%) and not already prompted at this count
        if (accuracy in 0.68f..0.72f && lastPromptedAt != totalCount) {
            lastPromptedAt = totalCount
            showAccuracyPrompt(accuracy)
        }
    }

    private fun showAccuracyPrompt(accuracy: Float) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Model Accuracy Alert")
            .setMessage("Validation accuracy is about ${(accuracy * 100).toInt()}%. Please consider sending this data for annotation.")
            .setPositiveButton("Annotate & Send") { dialog, _ ->
                val imageUri = modelValidationViewModel.capturedImageUri.value
                val expectedLabel = binding.editTextExpectedLabel.text.toString().trim()
                if (imageUri != null && expectedLabel.isNotEmpty()) {
                    modelValidationViewModel.uploadValidationData(imageUri, expectedLabel)
                } else {
                    Toast.makeText(requireContext(), "Cannot send data: image not captured or expected label not set.", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun setupButtonListeners() {
        binding.buttonCapture.setOnClickListener {
            if (modelValidationViewModel.capturedImageUri.value == null) {
                captureImage()
            } else {
                modelValidationViewModel.resetState()
                binding.editTextExpectedLabel.setText("")
                binding.buttonCapture.text = "Capture Image"
                binding.buttonValidate.text = "Validate"
                binding.buttonValidate.isEnabled = false
                binding.buttonSendData.visibility = View.GONE
                binding.textPreviewPlaceholder.visibility = View.VISIBLE
            }
        }

        binding.buttonValidate.setOnClickListener {
            val expectedLabel = binding.editTextExpectedLabel.text.toString().trim()
            if (expectedLabel.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter an expected label.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            modelValidationViewModel.capturedImageUri.value?.let { uri ->
                modelValidationViewModel.analyzeAndValidateImage(uri, expectedLabel)
                // After validation, check accuracy and prompt if needed
                val prediction = modelValidationViewModel.prediction.value ?: ""
                val isCorrect = prediction == expectedLabel
                updateAccuracyAndPromptIfNeeded(isCorrect)
            } ?: Toast.makeText(requireContext(), "No image captured to validate", Toast.LENGTH_SHORT).show()
        }

        binding.buttonViewHistory.setOnClickListener {
            findNavController().navigate(R.id.action_validate_to_history)
        }

        binding.buttonSendData.setOnClickListener {
            promptAndSendData()
        }
    }

    private var timerJob: Job? = null
    private var secondsLeft = 10
    private var isTimerRunning = false

    private fun startTimedCapture() {
        if (isTimerRunning) return
        isTimerRunning = true
        timerJob = viewLifecycleOwner.lifecycleScope.launch {
            secondsLeft = 10
            while (isActive && isTimerRunning) {
                binding.textTimer.text = "Timer: $secondsLeft"
                delay(1000)
                secondsLeft--
                if (secondsLeft <= 0) {
                    val expectedLabel = binding.editTextExpectedLabel.text.toString().trim()
                    if (expectedLabel.isNotEmpty()) {
                        captureImage() // Use your existing capture logic
                        // Optionally, run gesture identification here if not automatic
                    } else {
                        Toast.makeText(requireContext(), "Please enter an expected label before timed capture.", Toast.LENGTH_SHORT).show()
                        stopTimedCapture()
                        break
                    }
                    secondsLeft = 10 // Reset timer for next capture
                }
            }
        }
        binding.buttonStartTimer.text = "Stop Timed Capture"
    }

    private fun stopTimedCapture() {
        isTimerRunning = false
        timerJob?.cancel()
        binding.textTimer.text = "Timer: --"
        binding.buttonStartTimer.text = "Start Timed Capture"
    }

    private fun captureImage() {
        val imageCapture = this.imageCapture ?: return

        val photoFile = File(
            requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "KSL_VALIDATION_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        binding.buttonCapture.isEnabled = false

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    Toast.makeText(requireContext(), "Photo capture failed: ${exc.message}", Toast.LENGTH_LONG).show()
                    modelValidationViewModel.resetState()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    modelValidationViewModel.setCapturedImageUri(Uri.fromFile(photoFile))
                    val msg = "Photo captured for validation"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "Photo captured: ${modelValidationViewModel.capturedImageUri.value.toString()}")

                    binding.buttonCapture.text = "Retake Image"
                    binding.buttonCapture.isEnabled = true
                    binding.buttonValidate.isEnabled = true
                    binding.textPreviewPlaceholder.visibility = View.GONE
                }
            }
        )
    }

    @SuppressLint("MissingPermission")
    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            },
            ContextCompat.getMainExecutor(requireContext())
        )
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        val rotation = binding.previewContainer.display.rotation

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(cameraFacing)
            .build()

        preview = Preview.Builder()
            .setTargetRotation(rotation)
            .build()

        imageCapture = ImageCapture.Builder()
            .setTargetRotation(rotation)
            .build()

        cameraProvider.unbindAll()

        try {
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageCapture
            )

            binding.previewContainer.removeAllViews()
            val previewView = androidx.camera.view.PreviewView(requireContext())
            previewView.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            binding.previewContainer.addView(previewView)
            preview?.setSurfaceProvider(previewView.surfaceProvider)

        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
            Toast.makeText(requireContext(), "Failed to set up camera: ${exc.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupObservers() {
        modelValidationViewModel.confidenceScore.observe(viewLifecycleOwner) { score ->
            binding.textConfidenceScore.text = "Confidence Score: ${String.format("%.2f", score)}"
        }

        modelValidationViewModel.inferenceTime.observe(viewLifecycleOwner) { time ->
            binding.textInferenceTime.text = "Inference Time: $time ms"
        }

        modelValidationViewModel.prediction.observe(viewLifecycleOwner) { prediction ->
            binding.textPrediction.text = "Prediction: $prediction"
        }

        modelValidationViewModel.validationResultText.observe(viewLifecycleOwner) { text ->
            binding.textValidationResult.text = text
        }

        

        modelValidationViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.buttonValidate.isEnabled = !isLoading && modelValidationViewModel.capturedImageUri.value != null
            binding.buttonCapture.isEnabled = !isLoading
            binding.buttonSendData.isEnabled = !isLoading && modelValidationViewModel.capturedImageUri.value != null && !modelValidationViewModel.prediction.value.isNullOrEmpty()
        }

        modelValidationViewModel.toastMessage.observe(viewLifecycleOwner) { message ->
            message?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun promptAndSendData() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Send Data for Training?")
            .setMessage("The model's prediction confidence is high. Do you want to send this image and its prediction for further model training?")
            .setPositiveButton("Send") { dialog, _ ->
                val imageUri = modelValidationViewModel.capturedImageUri.value
                val expectedLabel = binding.editTextExpectedLabel.text.toString().trim()
                if (imageUri != null && expectedLabel.isNotEmpty()) {
                    modelValidationViewModel.uploadValidationData(imageUri, expectedLabel)
                } else {
                    Toast.makeText(requireContext(), "Cannot send data: image not captured or expected label not set.", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}