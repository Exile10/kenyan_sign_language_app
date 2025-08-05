package com.jerry.ksl.gesturerecognizer.fragment

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.jerry.ksl.gesturerecognizer.R
import com.jerry.ksl.gesturerecognizer.databinding.FragmentSubmitTrainingDataBinding
import com.jerry.ksl.gesturerecognizer.service.AccessControlService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.UploadTask
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Enhanced fragment for submitting training data with quality validation and progress tracking
 * Supports batch uploads and secure data submission pipeline
 */
class SubmitTrainingDataFragment : Fragment() {
    private var _binding: FragmentSubmitTrainingDataBinding? = null
    private val binding get() = _binding!!

    private val categoryOptions = arrayOf("Alphabet", "Numbers", "Common Phrases")
    private var signOptions = arrayOf<String>()
    private var capturedImageUri: Uri? = null

    // Enhanced batch upload support
    private val capturedImages = mutableListOf<CapturedImageData>()
    private var isSubmittingBatch = false
    private var currentUploadProgress = 0
    private var totalUploadItems = 0

    // Enhanced quality validation
    private var lastQualityCheckResult: QualityCheckResult? = null

    private var preview: Preview? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_FRONT

    /** Blocking operations are performed using this executor */
    private lateinit var backgroundExecutor: ExecutorService

    /** Firebase instances */
    private lateinit var auth: FirebaseAuth
    private lateinit var storage: FirebaseStorage
    private lateinit var firestore: FirebaseFirestore

    /** Access control for submission permissions */
    private val accessControlService = AccessControlService()

    /** Capturing video/frames functionality */
    private var imageCapture: ImageCapture? = null
    private var currentSelectedCategory: String = ""
    private var currentSelectedSign: String = ""

    companion object {
        private const val TAG = "SubmitTrainingDataFragment"
        private const val MIN_IMAGE_SIZE = 224 // Minimum image dimension
        private const val MAX_FILE_SIZE = 5 * 1024 * 1024 // 5MB max file size
        private const val MIN_BRIGHTNESS = 50 // Minimum brightness threshold
    }

    data class CapturedImageData(
        val uri: Uri,
        val category: String,
        val sign: String,
        val timestamp: Date,
        val isValidated: Boolean = false
    )

    data class QualityCheckResult(
        val isValid: Boolean,
        val issues: List<String>,
        val score: Float
    )

    enum class UploadStatus {
        READY, VALIDATING, UPLOADING, COMPLETED, FAILED
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSubmitTrainingDataBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize background executor
        backgroundExecutor = Executors.newSingleThreadExecutor()

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        storage = FirebaseStorage.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Setup flip camera button
        binding.buttonFlipCamera.setOnClickListener {
            cameraFacing = if (cameraFacing == CameraSelector.LENS_FACING_FRONT) {
                CameraSelector.LENS_FACING_BACK
            } else {
                CameraSelector.LENS_FACING_FRONT
            }
            // Rebind use cases to switch camera
            bindCameraUseCases()
        }

        setupCategorySpinner()
        setupButtonListeners()
        binding.buttonSubmit.isEnabled = false // Initially disable submit

        // Set up camera when view is created
        setUpCamera()
    }

    override fun onResume() {
        super.onResume()

        // Make sure we have camera permissions
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            findNavController().navigate(R.id.action_camera_to_permissions) // Updated navigation call
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()

        // Shut down our background executor
        backgroundExecutor.shutdown()
        try {
            if (!backgroundExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                backgroundExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            backgroundExecutor.shutdownNow()
            Thread.currentThread().interrupt()
            Log.e(TAG, "Background executor termination interrupted", e)
        }
    }

    private fun setupCategorySpinner() {
        val categoryAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            categoryOptions
        )

        binding.spinnerCategories.adapter = categoryAdapter
        binding.spinnerCategories.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentSelectedCategory = categoryOptions[position]
                updateSignSpinner(position)
                resetCaptureState() // Reset if category changes
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }
    }

    private fun updateSignSpinner(categoryPosition: Int) {
        signOptions = when (categoryPosition) {
            0 -> resources.getStringArray(R.array.alphabet_signs)
            1 -> resources.getStringArray(R.array.number_signs)
            2 -> resources.getStringArray(R.array.phrase_signs)
            else -> arrayOf()
        }

        val signAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            signOptions
        )

        binding.spinnerSigns.adapter = signAdapter
        binding.spinnerSigns.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position >= 0 && position < signOptions.size) {
                    currentSelectedSign = signOptions[position]
                } else if (signOptions.isNotEmpty()) {
                    currentSelectedSign = signOptions[0] // Default to first if selection is somehow invalid
                } else {
                    currentSelectedSign = ""
                }
                resetCaptureState() // Reset if sign changes
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {
                currentSelectedSign = "" // Clear if nothing selected
            }
        }
        // Ensure a sign is selected if options are available
        if (signOptions.isNotEmpty()) {
            currentSelectedSign = signOptions[0]
            binding.spinnerSigns.setSelection(0)
        } else {
            currentSelectedSign = ""
        }
    }

    private fun setupButtonListeners() {
        binding.buttonRecord.text = getString(R.string.capture_image)
        binding.buttonRecord.setOnClickListener {
            if (capturedImageUri == null) {
                takePicture()
            } else {
                resetCaptureState()
            }
        }

        // Enhanced batch upload functionality
        binding.buttonSubmit.setOnClickListener {
            if (capturedImages.isNotEmpty()) {
                startBatchUpload()
            } else {
                uploadImageAndSubmitData()
            }
        }

        // Clear batch functionality
        binding.buttonClearBatch.setOnClickListener {
            clearBatchImages()
        }

        // Add to batch functionality (modified capture behavior)
        binding.buttonRecord.setOnLongClickListener {
            if (capturedImageUri != null) {
                addToBatch()
                true
            } else {
                false
            }
        }

        binding.buttonViewHistory.setOnClickListener {
            findNavController().navigate(R.id.action_submit_to_history)
        }
    }

    private fun takePicture() {
        if (currentSelectedSign.isEmpty() || currentSelectedCategory.isEmpty()) {
            Toast.makeText(requireContext(), "Please select a category and sign first", Toast.LENGTH_SHORT).show()
            return
        }

        val imageCapture = this.imageCapture ?: return

        // Create time-stamped output file to hold the image
        val photoFile = File(
            requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "KSL_TRAIN_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        binding.buttonRecord.isEnabled = false // Disable while capturing

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    Toast.makeText(requireContext(), "Photo capture failed: ${exc.message}", Toast.LENGTH_LONG).show()
                    resetCaptureState()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturedImageUri = Uri.fromFile(photoFile)
                    val msg = "Photo capture succeeded: ${capturedImageUri.toString()}"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)

                    binding.buttonRecord.text = getString(R.string.retake_image)
                    binding.buttonRecord.isEnabled = true
                    binding.buttonSubmit.isEnabled = true
                    binding.textPreviewPlaceholder.visibility = View.GONE

                    // Automatically validate the captured image quality
                    capturedImageUri?.let { uri ->
                        try {
                            val file = File(uri.path!!)
                            val qualityResult = performQualityCheck(file)
                            updateQualityUI(qualityResult)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error validating image quality", e)
                        }
                    }
                }
            }
        )
    }

    /**
     * Enhanced batch upload functionality
     */
    private fun addToBatch() {
        if (capturedImageUri == null || currentSelectedSign.isEmpty() || currentSelectedCategory.isEmpty()) {
            Toast.makeText(requireContext(), "Please capture an image and select category/sign", Toast.LENGTH_SHORT).show()
            return
        }

        // Validate image quality before adding to batch
        val file = File(capturedImageUri!!.path ?: return)
        val qualityResult = performQualityCheck(file)

        if (!qualityResult.isValid) {
            updateQualityUI(qualityResult)
            Toast.makeText(requireContext(), "Image quality insufficient: ${qualityResult.issues.joinToString(", ")}", Toast.LENGTH_LONG).show()
            return
        }

        val capturedData = CapturedImageData(
            uri = capturedImageUri!!,
            category = currentSelectedCategory,
            sign = currentSelectedSign,
            timestamp = Date(),
            isValidated = true
        )

        capturedImages.add(capturedData)
        updateBatchUI()
        updateQualityUI(qualityResult)

        Toast.makeText(requireContext(), "Image added to batch (${capturedImages.size})", Toast.LENGTH_SHORT).show()
        resetCaptureState()
    }

    private fun clearBatchImages() {
        capturedImages.clear()
        updateBatchUI()
        resetProgressUI()
        Toast.makeText(requireContext(), "Batch cleared", Toast.LENGTH_SHORT).show()
    }

    private fun startBatchUpload() {
        if (capturedImages.isEmpty()) {
            Toast.makeText(requireContext(), "No images in batch to upload", Toast.LENGTH_SHORT).show()
            return
        }

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        // Check access control
        accessControlService.checkSubmissionAccess(currentUser.uid) { hasAccess ->
            if (!hasAccess) {
                Toast.makeText(requireContext(), "You do not have permission to submit data", Toast.LENGTH_SHORT).show()
                return@checkSubmissionAccess
            }

            lifecycleScope.launch {
                performBatchUpload(currentUser.uid)
            }
        }
    }

    private suspend fun performBatchUpload(userId: String) {
        isSubmittingBatch = true
        totalUploadItems = capturedImages.size
        currentUploadProgress = 0

        updateUploadStatus(UploadStatus.UPLOADING)
        updateProgressUI()

        try {
            for ((index, imageData) in capturedImages.withIndex()) {
                updateUploadStatus(UploadStatus.UPLOADING, "Uploading image ${index + 1}/${totalUploadItems}")

                val success = uploadSingleImage(userId, imageData)
                if (success) {
                    currentUploadProgress++
                    updateProgressUI()
                } else {
                    throw Exception("Failed to upload image ${index + 1}")
                }
            }

            // All uploads successful
            updateUploadStatus(UploadStatus.COMPLETED, "All ${totalUploadItems} images uploaded successfully!")
            clearBatchImages()
            Toast.makeText(requireContext(), "Batch upload completed successfully!", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Log.e(TAG, "Batch upload failed", e)
            updateUploadStatus(UploadStatus.FAILED, "Upload failed: ${e.message}")
            Toast.makeText(requireContext(), "Batch upload failed: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            isSubmittingBatch = false
        }
    }

    private suspend fun uploadSingleImage(userId: String, imageData: CapturedImageData): Boolean {
        return try {
            val file = File(imageData.uri.path ?: return false)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(imageData.timestamp)
            val filename = "${imageData.sign.replace(" ", "_")}_${timestamp}_${UUID.randomUUID()}.jpg"
            val imageRef = storage.reference.child("training_images/$userId/${imageData.sign}/$filename")

            // Upload file
            val uploadTask = imageRef.putFile(imageData.uri)
            val snapshot = uploadTask.await()
            val downloadUrl = snapshot.storage.downloadUrl.await()

            // Save to Firestore
            val trainingData = hashMapOf(
                "user_id" to userId,
                "image_url" to downloadUrl.toString(),
                "label" to imageData.sign,
                "category" to imageData.category,
                "validated" to imageData.isValidated,
                "timestamp" to com.google.firebase.Timestamp(imageData.timestamp)
            )

            firestore.collection("TRAINING_IMAGES").add(trainingData).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload single image", e)
            false
        }
    }

    /**
     * Enhanced quality validation
     */
    private fun performQualityCheck(file: File): QualityCheckResult {
        val issues = mutableListOf<String>()
        var score = 100f

        // Check file size
        if (file.length() > MAX_FILE_SIZE) {
            issues.add("File too large")
            score -= 30f
        }

        // Check image dimensions and brightness
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        if (bitmap == null) {
            issues.add("Invalid image file")
            return QualityCheckResult(false, issues, 0f)
        }

        val (width, height) = bitmap.run { Pair(this.width, this.height) }
        if (width < MIN_IMAGE_SIZE || height < MIN_IMAGE_SIZE) {
            issues.add("Image too small (${width}x${height})")
            score -= 40f
        }

        // Check brightness
        if (!bitmap.isBrightEnough(MIN_BRIGHTNESS)) {
            issues.add("Image too dark")
            score -= 25f
        }

        // Check if image is blurry (basic edge detection)
        val blurScore = calculateBlurScore(bitmap)
        if (blurScore < 50) {
            issues.add("Image appears blurry")
            score -= 20f
        }

        val isValid = issues.isEmpty() && score >= 60f
        return QualityCheckResult(isValid, issues, score)
    }

    private fun calculateBlurScore(bitmap: Bitmap): Float {
        // Simple edge detection for blur estimation
        // This is a basic implementation - in production you might want something more sophisticated
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var edgeCount = 0
        for (i in 1 until pixels.size - bitmap.width - 1) {
            val current = pixels[i]
            val right = pixels[i + 1]
            val bottom = pixels[i + bitmap.width]

            val rightDiff = Math.abs((current and 0xFF) - (right and 0xFF))
            val bottomDiff = Math.abs((current and 0xFF) - (bottom and 0xFF))

            if (rightDiff > 30 || bottomDiff > 30) {
                edgeCount++
            }
        }

        return (edgeCount.toFloat() / pixels.size) * 1000
    }

    /**
     * UI Update Methods
     */
    private fun updateBatchUI() {
        binding.textBatchCount.text = "Captured: ${capturedImages.size} images"
        binding.buttonSubmit.text = if (capturedImages.isNotEmpty()) {
            "Upload Batch (${capturedImages.size})"
        } else {
            "Submit Data"
        }
        binding.buttonClearBatch.isEnabled = capturedImages.isNotEmpty()
    }

    private fun updateProgressUI() {
        if (totalUploadItems > 0) {
            val progressPercentage = (currentUploadProgress.toFloat() / totalUploadItems * 100).toInt()
            binding.progressUpload.progress = progressPercentage
            binding.textProgressPercentage.text = "$progressPercentage%"
        } else {
            binding.progressUpload.progress = 0
            binding.textProgressPercentage.text = "0%"
        }
    }

    private fun resetProgressUI() {
        binding.progressUpload.progress = 0
        binding.textProgressPercentage.text = "0%"
        binding.textUploadStatus.text = "Ready to upload"
    }

    private fun updateUploadStatus(status: UploadStatus, message: String? = null) {
        val statusText = message ?: when (status) {
            UploadStatus.READY -> "Ready to upload"
            UploadStatus.VALIDATING -> "Validating images..."
            UploadStatus.UPLOADING -> "Uploading..."
            UploadStatus.COMPLETED -> "Upload completed"
            UploadStatus.FAILED -> "Upload failed"
        }
        binding.textUploadStatus.text = statusText
    }

    private fun updateQualityUI(qualityResult: QualityCheckResult) {
        lastQualityCheckResult = qualityResult

        if (qualityResult.isValid) {
            binding.iconQualityStatus.setImageResource(R.drawable.ic_check_circle)
            binding.iconQualityStatus.setColorFilter(ContextCompat.getColor(requireContext(), R.color.mp_color_primary))
            binding.textQualityStatus.text = "Quality check passed (${qualityResult.score.toInt()}%)"
        } else {
            binding.iconQualityStatus.setImageResource(R.drawable.ic_error)
            binding.iconQualityStatus.setColorFilter(ContextCompat.getColor(requireContext(), android.R.color.holo_red_light))
            binding.textQualityStatus.text = "Quality issues: ${qualityResult.issues.joinToString(", ")}"
        }
    }

    private fun resetCaptureState() {
        capturedImageUri = null
        binding.buttonRecord.text = getString(R.string.capture_image) // Use string resource
        binding.buttonRecord.isEnabled = true
        binding.buttonSubmit.isEnabled = false
        // Consider showing placeholder again if you have one for the preview area
        // binding.textPreviewPlaceholder.visibility = View.VISIBLE
    }

    private fun uploadImageAndSubmitData() {
        if (capturedImageUri == null) {
            Toast.makeText(requireContext(), "No image captured to submit.", Toast.LENGTH_SHORT).show()
            return
        }
        if (currentSelectedSign.isEmpty() || currentSelectedCategory.isEmpty()) {
            Toast.makeText(requireContext(), "Category or Sign not selected.", Toast.LENGTH_SHORT).show()
            return
        }
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), "User not logged in.", Toast.LENGTH_SHORT).show()
            // Optionally, navigate to login screen
            return
        }

        // Check access control before allowing submission
        accessControlService.checkSubmissionAccess(currentUser.uid) { hasAccess ->
            if (!hasAccess) {
                Toast.makeText(requireContext(), "You do not have permission to submit data.", Toast.LENGTH_SHORT).show()
                return@checkSubmissionAccess
            }

            binding.buttonSubmit.isEnabled = false
            binding.buttonRecord.isEnabled = false // Disable retake during submission
            Toast.makeText(requireContext(), "Submitting image...", Toast.LENGTH_LONG).show()

            val userId = currentUser.uid
            val label = currentSelectedSign
            val file = capturedImageUri!! // Already checked for null

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val filename = "${label.replace(" ", "_")}_${timestamp}_${UUID.randomUUID()}.jpg"
            val imageRef = storage.reference.child("training_images/$userId/$label/$filename")

            // Validate image quality before upload
            val imageFile = File(capturedImageUri!!.path!!)
            if (!validateImageQuality(imageFile)) {
                Toast.makeText(requireContext(), "Image does not meet quality standards.", Toast.LENGTH_LONG).show()
                resetUiAfterSubmissionAttempt()
                return@checkSubmissionAccess
            }

            imageRef.putFile(file)
                .addOnSuccessListener { taskSnapshot ->
                    taskSnapshot.storage.downloadUrl.addOnSuccessListener { downloadUrl ->
                        val imageUrl = downloadUrl.toString()
                        saveTrainingDataToFirestore(userId, imageUrl, label)
                    }.addOnFailureListener { e ->
                        Log.e(TAG, "Failed to get download URL", e)
                        Toast.makeText(requireContext(), "Failed to get image URL: ${e.message}", Toast.LENGTH_LONG).show()
                        resetUiAfterSubmissionAttempt()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Image upload failed", e)
                    Toast.makeText(requireContext(), "Image upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                    resetUiAfterSubmissionAttempt()
                }
                .addOnProgressListener {
                    // Optional: Update UI with progress
                    // val progress = (100.0 * it.bytesTransferred) / it.totalByteCount
                    // Log.d(TAG, "Upload is $progress% done")
                }
        }
    }

    private fun validateImageQuality(file: File): Boolean {
        // Check file size
        if (file.length() > MAX_FILE_SIZE) {
            Log.e(TAG, "Image file size exceeds the maximum limit.")
            return false
        }

        // Check image dimensions and brightness
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        val (width, height) = bitmap.run { Pair(this.width, this.height) }
        if (width < MIN_IMAGE_SIZE || height < MIN_IMAGE_SIZE) {
            Log.e(TAG, "Image dimensions are below the minimum required size.")
            return false
        }

        // Check image brightness
        val isBrightEnough = bitmap.isBrightEnough(MIN_BRIGHTNESS)
        if (!isBrightEnough) {
            Log.e(TAG, "Image brightness is below the minimum threshold.")
            return false
        }

        return true
    }

    private fun Bitmap.isBrightEnough(minBrightness: Int): Boolean {
        val totalPixels = this.width * this.height
        var brightPixels = 0

        for (x in 0 until this.width) {
            for (y in 0 until this.height) {
                val pixel = this.getPixel(x, y)
                val brightness = (pixel shr 16 and 0xff) * 0.299 + // R
                        (pixel shr 8 and 0xff) * 0.587 + // G
                        (pixel and 0xff) * 0.114 // B
                if (brightness > minBrightness) {
                    brightPixels++
                }
            }
        }

        val brightnessRatio = brightPixels / totalPixels.toFloat()
        return brightnessRatio > 0.5 // More than 50% of the image should be bright enough
    }

    private fun saveTrainingDataToFirestore(userId: String, imageUrl: String, label: String) {
        val trainingData = hashMapOf(
            "user_id" to userId,
            "image_url" to imageUrl,
            "label" to label,
            "validated" to false, // Default to false
            "model_id" to null, // Or a relevant default/placeholder
            "timestamp" to com.google.firebase.Timestamp.now() // Optional: add a server timestamp
        )

        firestore.collection("TRAINING_IMAGES")
            .add(trainingData)
            .addOnSuccessListener { documentReference ->
                Log.d(TAG, "Training data saved to Firestore with ID: ${documentReference.id}")
                Toast.makeText(requireContext(), "Image submitted successfully!", Toast.LENGTH_LONG).show()
                resetCaptureState() // Fully reset for next capture
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error saving training data to Firestore", e)
                Toast.makeText(requireContext(), "Firestore submission failed: ${e.message}", Toast.LENGTH_LONG).show()
                // Decide if you want to allow resubmission of Firestore data or delete uploaded image
                resetUiAfterSubmissionAttempt()
            }
    }

    private fun resetUiAfterSubmissionAttempt() {
        // Re-enable buttons to allow user to try again or retake
        binding.buttonSubmit.isEnabled = capturedImageUri != null // Enable if image still there
        binding.buttonRecord.isEnabled = true
    }

    @SuppressLint("MissingPermission")
    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                bindCameraUseCases()
            },
            ContextCompat.getMainExecutor(requireContext())
        )
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        val rotation = binding.previewContainer.display.rotation

        // CameraSelector
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(cameraFacing)
            .build()

        // Preview
        preview = Preview.Builder()
            .setTargetRotation(rotation)
            .build()

        // ImageCapture
        imageCapture = ImageCapture.Builder()
            .setTargetRotation(rotation)
            // Consider setting capture mode if specific quality/latency is needed.
            // .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        // Remove ImageAnalysis if not used, or ensure its analyzer is not doing gesture recognition
        // For this task, ImageAnalysis for live gesture recognition is removed.
        // If you still need ImageAnalysis for other purposes (e.g., a different kind of live preview processing),
        // ensure its analyzer is configured correctly or removed if not needed.
        // Here, we assume it's not needed for the primary task of capturing a single image.

        cameraProvider.unbindAll()

        try {
            // Bind only Preview and ImageCapture. Remove imageAnalyzer if it was solely for gesture recognition.
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageCapture // Only bind imageCapture, remove imageAnalyzer if not needed
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
}
