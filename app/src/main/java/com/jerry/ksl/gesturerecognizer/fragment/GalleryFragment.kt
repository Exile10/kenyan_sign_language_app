package com.jerry.ksl.gesturerecognizer.fragment

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.jerry.ksl.gesturerecognizer.GestureRecognizerHelper
import com.jerry.ksl.gesturerecognizer.MainViewModel
import com.jerry.ksl.gesturerecognizer.databinding.FragmentGalleryBinding
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class GalleryFragment : Fragment(),
    GestureRecognizerHelper.GestureRecognizerListener {

    enum class MediaType {
        IMAGE,
        VIDEO,
        UNKNOWN
    }

    private var _fragmentGalleryBinding: FragmentGalleryBinding? = null
    private val fragmentGalleryBinding
        get() = _fragmentGalleryBinding!!
    private lateinit var gestureRecognizerHelper: GestureRecognizerHelper
    private val viewModel: MainViewModel by activityViewModels()
    private var defaultNumResults = 1
    private val gestureRecognizerResultsAdapter by lazy {
        GestureRecognizerResultsAdapter().apply {
            updateAdapterSize(defaultNumResults)
        }
    }

    /** Blocking ML operations are performed using this executor */
    private lateinit var backgroundExecutor: ScheduledExecutorService

    // Firebase components for cloud gallery functionality
    private lateinit var auth: FirebaseAuth
    private lateinit var storage: FirebaseStorage
    private lateinit var firestore: FirebaseFirestore

    companion object {
        private const val TAG = "GalleryFragment"
        private const val STORAGE_PATH_GALLERY = "user_gallery"
        private const val VIDEO_INTERVAL_MS = 300L // 300ms interval for video processing
    }

    private val getContent =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            // Handle the returned Uri
            uri?.let { mediaUri ->
                when (val mediaType = loadMediaType(mediaUri)) {
                    MediaType.IMAGE -> runGestureRecognitionOnImage(mediaUri)
                    MediaType.VIDEO -> runGestureRecognitionOnVideo(mediaUri)
                    MediaType.UNKNOWN -> {
                        updateDisplayView(mediaType)
                        Toast.makeText(
                            requireContext(),
                            "Unsupported data type.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentGalleryBinding =
            FragmentGalleryBinding.inflate(inflater, container, false)

        return fragmentGalleryBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentGalleryBinding.fabGetContent.setOnClickListener {
            getContent.launch(arrayOf("image/*", "video/*"))
        }

        with(fragmentGalleryBinding.recyclerviewResults) {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = gestureRecognizerResultsAdapter
        }
        initBottomSheetControls()
    }

    override fun onPause() {
        fragmentGalleryBinding.overlay.clear()
        if (fragmentGalleryBinding.videoView.isPlaying) {
            fragmentGalleryBinding.videoView.stopPlayback()
        }
        fragmentGalleryBinding.videoView.visibility = View.GONE
        super.onPause()
    }

    private fun initBottomSheetControls() {
        // init bottom sheet settings
        updateControlsUi()

        // When clicked, lower detection score threshold floor
        fragmentGalleryBinding.bottomSheetLayout.detectionThresholdMinus.setOnClickListener {
            if (viewModel.currentMinHandDetectionConfidence >= 0.2) {
                viewModel.setMinHandDetectionConfidence(viewModel.currentMinHandDetectionConfidence - 0.1f)
                updateControlsUi()
            }
        }

        // When clicked, raise detection score threshold floor
        fragmentGalleryBinding.bottomSheetLayout.detectionThresholdPlus.setOnClickListener {
            if (viewModel.currentMinHandDetectionConfidence <= 0.8) {
                viewModel.setMinHandDetectionConfidence(viewModel.currentMinHandDetectionConfidence + 0.1f)
                updateControlsUi()
            }
        }

        // When clicked, lower hand tracking score threshold floor
        fragmentGalleryBinding.bottomSheetLayout.trackingThresholdMinus.setOnClickListener {
            if (viewModel.currentMinHandTrackingConfidence >= 0.2) {
                viewModel.setMinHandTrackingConfidence(
                    viewModel.currentMinHandTrackingConfidence - 0.1f
                )
                updateControlsUi()
            }
        }

        // When clicked, raise hand tracking score threshold floor
        fragmentGalleryBinding.bottomSheetLayout.trackingThresholdPlus.setOnClickListener {
            if (viewModel.currentMinHandTrackingConfidence <= 0.8) {
                viewModel.setMinHandTrackingConfidence(
                    viewModel.currentMinHandTrackingConfidence + 0.1f
                )
                updateControlsUi()
            }
        }

        // When clicked, lower hand presence score threshold floor
        fragmentGalleryBinding.bottomSheetLayout.presenceThresholdMinus.setOnClickListener {
            if (viewModel.currentMinHandPresenceConfidence >= 0.2) {
                viewModel.setMinHandPresenceConfidence(
                    viewModel.currentMinHandPresenceConfidence - 0.1f
                )
                updateControlsUi()
            }
        }

        // When clicked, raise hand presence score threshold floor
        fragmentGalleryBinding.bottomSheetLayout.presenceThresholdPlus.setOnClickListener {
            if (viewModel.currentMinHandPresenceConfidence <= 0.8) {
                viewModel.setMinHandPresenceConfidence(
                    viewModel.currentMinHandPresenceConfidence + 0.1f
                )
                updateControlsUi()
            }
        }

        // When clicked, change the underlying hardware used for inference. Current options are CPU
        // GPU, and NNAPI
        fragmentGalleryBinding.bottomSheetLayout.spinnerDelegate.setSelection(
            viewModel.currentDelegate,
            false
        )
        fragmentGalleryBinding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    p0: AdapterView<*>?,
                    p1: View?,
                    p2: Int,
                    p3: Long
                ) {

                    viewModel.setDelegate(p2)
                    updateControlsUi()
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                    /* no op */
                }
            }
    }

    // Update the values displayed in the bottom sheet. Reset detector.
    @SuppressLint("NotifyDataSetChanged")
    private fun updateControlsUi() {
        if (fragmentGalleryBinding.videoView.isPlaying) {
            fragmentGalleryBinding.videoView.stopPlayback()
        }
        fragmentGalleryBinding.videoView.visibility = View.GONE
        fragmentGalleryBinding.imageResult.visibility = View.GONE
        fragmentGalleryBinding.overlay.clear()
        fragmentGalleryBinding.bottomSheetLayout.detectionThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinHandDetectionConfidence
            )
        fragmentGalleryBinding.bottomSheetLayout.trackingThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinHandTrackingConfidence
            )
        fragmentGalleryBinding.bottomSheetLayout.presenceThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinHandPresenceConfidence
            )

        fragmentGalleryBinding.overlay.clear()
        fragmentGalleryBinding.tvPlaceholder.visibility = View.VISIBLE
        gestureRecognizerResultsAdapter.updateResults(null as com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult?)
        gestureRecognizerResultsAdapter.notifyDataSetChanged()
    }

    // Load and display the image.
    private fun runGestureRecognitionOnImage(uri: Uri) {
        setUiEnabled(false)
        backgroundExecutor = Executors.newSingleThreadScheduledExecutor()
        updateDisplayView(MediaType.IMAGE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(
                requireActivity().contentResolver,
                uri
            )
            ImageDecoder.decodeBitmap(source)
        } else {
            MediaStore.Images.Media.getBitmap(
                requireActivity().contentResolver,
                uri
            )
        }
            .copy(Bitmap.Config.ARGB_8888, true)
            ?.let { bitmap ->
                fragmentGalleryBinding.imageResult.setImageBitmap(bitmap)

                // Run gesture recognizer on the input image
                backgroundExecutor.execute {

                    gestureRecognizerHelper =
                        GestureRecognizerHelper(
                            context = requireContext(),
                            runningMode = RunningMode.IMAGE,
                            minHandDetectionConfidence = viewModel.currentMinHandDetectionConfidence,
                            minHandTrackingConfidence = viewModel.currentMinHandTrackingConfidence,
                            minHandPresenceConfidence = viewModel.currentMinHandPresenceConfidence,
                            currentDelegate = viewModel.currentDelegate
                        )

                    gestureRecognizerHelper.recognizeImage(bitmap)
                        ?.let { resultBundle ->
                            activity?.runOnUiThread {

                                    fragmentGalleryBinding.overlay.setResults(
                                        resultBundle.results[0],
                                        bitmap.height,
                                        bitmap.width,
                                        RunningMode.IMAGE
                                    )

                                    // This will return an empty list if there are no gestures detected
                                    if(!resultBundle.results.first().gestures().isEmpty()) {
                                        gestureRecognizerResultsAdapter.updateResults(
                                            resultBundle.results.first()
                                                .gestures().first()
                                        )
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Hands not detected",
                                            Toast.LENGTH_SHORT).show()
                                    }

                                setUiEnabled(true)
                                fragmentGalleryBinding.bottomSheetLayout.inferenceTimeVal.text =
                                    String.format(
                                        "%d ms",
                                        resultBundle.inferenceTime
                                    )
                            }
                        } ?: run {
                        Log.e(
                            TAG, "Error running gesture recognizer."
                        )
                    }

                    gestureRecognizerHelper.clearGestureRecognizer()
                }
            }
    }

    // Load and display the video.
    private fun runGestureRecognitionOnVideo(uri: Uri) {
        setUiEnabled(false)
        updateDisplayView(MediaType.VIDEO)
        gestureRecognizerResultsAdapter.updateResults(null as com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult?)
        gestureRecognizerResultsAdapter.notifyDataSetChanged()

        with(fragmentGalleryBinding.videoView) {
            setVideoURI(uri)
            // mute the audio
            setOnPreparedListener { it.setVolume(0f, 0f) }
            requestFocus()
        }

        backgroundExecutor = Executors.newSingleThreadScheduledExecutor()
        backgroundExecutor.execute {

            gestureRecognizerHelper =
                GestureRecognizerHelper(
                    context = requireContext(),
                    runningMode = RunningMode.VIDEO,
                    minHandDetectionConfidence = viewModel.currentMinHandDetectionConfidence,
                    minHandTrackingConfidence = viewModel.currentMinHandTrackingConfidence,
                    minHandPresenceConfidence = viewModel.currentMinHandPresenceConfidence,
                    currentDelegate = viewModel.currentDelegate
                )

            activity?.runOnUiThread {
                fragmentGalleryBinding.videoView.visibility = View.GONE
                fragmentGalleryBinding.progress.visibility = View.VISIBLE
            }

            gestureRecognizerHelper.recognizeVideoFile(uri, VIDEO_INTERVAL_MS)
                ?.let { resultBundle ->
                    activity?.runOnUiThread { displayVideoResult(resultBundle) }
                }
                ?: run {
                    activity?.runOnUiThread {
                        fragmentGalleryBinding.progress.visibility =
                            View.GONE
                    }
                    Log.e(TAG, "Error running gesture recognizer.")
                }

            gestureRecognizerHelper.clearGestureRecognizer()
        }
    }

    // Setup and display the video.
    private fun displayVideoResult(result: GestureRecognizerHelper.ResultBundle) {

        fragmentGalleryBinding.videoView.visibility = View.VISIBLE
        fragmentGalleryBinding.progress.visibility = View.GONE

        fragmentGalleryBinding.videoView.start()
        val videoStartTimeMs = SystemClock.uptimeMillis()

        backgroundExecutor.scheduleAtFixedRate(
            {
                activity?.runOnUiThread {
                    val videoElapsedTimeMs =
                        SystemClock.uptimeMillis() - videoStartTimeMs
                    val resultIndex =
                        videoElapsedTimeMs.div(VIDEO_INTERVAL_MS).toInt()

                    if (resultIndex >= result.results.size || fragmentGalleryBinding.videoView.visibility == View.GONE) {
                        // The video playback has finished so we stop drawing bounding boxes
                        setUiEnabled(true)
                        backgroundExecutor.shutdown()
                    } else {
                        fragmentGalleryBinding.overlay.setResults(
                            result.results[resultIndex],
                            result.inputImageHeight,
                            result.inputImageWidth,
                            RunningMode.VIDEO
                        )
                        val categories = result.results[resultIndex].gestures()
                        if (categories.isNotEmpty()) {
                            gestureRecognizerResultsAdapter.updateResults(
                                categories.first()
                            )
                        }

                        setUiEnabled(false)

                        fragmentGalleryBinding.bottomSheetLayout.inferenceTimeVal.text =
                            String.format("%d ms", result.inferenceTime)
                    }
                }
            },
            0,
            VIDEO_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )
    }

    private fun updateDisplayView(mediaType: MediaType) {
        fragmentGalleryBinding.imageResult.visibility =
            if (mediaType == MediaType.IMAGE) View.VISIBLE else View.GONE
        fragmentGalleryBinding.videoView.visibility =
            if (mediaType == MediaType.VIDEO) View.VISIBLE else View.GONE
        fragmentGalleryBinding.tvPlaceholder.visibility =
            if (mediaType == MediaType.UNKNOWN) View.VISIBLE else View.GONE
    }

    // Check the type of media that user selected.
    private fun loadMediaType(uri: Uri): MediaType {
        val mimeType = context?.contentResolver?.getType(uri)
        mimeType?.let {
            if (mimeType.startsWith("image")) return MediaType.IMAGE
            if (mimeType.startsWith("video")) return MediaType.VIDEO
        }

        return MediaType.UNKNOWN
    }

    private fun setUiEnabled(enabled: Boolean) {
        fragmentGalleryBinding.fabGetContent.isEnabled = enabled
        fragmentGalleryBinding.bottomSheetLayout.detectionThresholdMinus.isEnabled =
            enabled
        fragmentGalleryBinding.bottomSheetLayout.detectionThresholdPlus.isEnabled =
            enabled
        fragmentGalleryBinding.bottomSheetLayout.trackingThresholdMinus.isEnabled =
            enabled
        fragmentGalleryBinding.bottomSheetLayout.trackingThresholdPlus.isEnabled =
            enabled
        fragmentGalleryBinding.bottomSheetLayout.presenceThresholdMinus.isEnabled =
            enabled
        fragmentGalleryBinding.bottomSheetLayout.presenceThresholdPlus.isEnabled =
            enabled
        fragmentGalleryBinding.bottomSheetLayout.spinnerDelegate.isEnabled =
            enabled
    }

    private fun recognitionError() {
        activity?.runOnUiThread {
            fragmentGalleryBinding.progress.visibility = View.GONE
            setUiEnabled(true)
            updateDisplayView(MediaType.UNKNOWN)
        }
    }

    override fun onError(error: String, errorCode: Int) {
        recognitionError()
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            if (errorCode == GestureRecognizerHelper.GPU_ERROR) {
                fragmentGalleryBinding.bottomSheetLayout.spinnerDelegate.setSelection(
                    GestureRecognizerHelper.DELEGATE_CPU,
                    false
                )
            }
        }
    }

    override fun onResults(resultBundle: GestureRecognizerHelper.ResultBundle) {
        // no-op
    }

    private fun uploadImageToFirebase(uri: Uri) {
        // Get a reference to the storage service using the default Firebase App
        val storage = FirebaseStorage.getInstance()

        // Create a storage reference from our app
        val storageRef = storage.reference

        // Create a reference to "images/mountains.jpg"
        val mountainsRef = storageRef.child("images/mountains.jpg")

        // Upload the file to the path "images/mountains.jpg"
        val uploadTask = mountainsRef.putFile(uri)

        // Register observers to listen for when the download is done or if it fails
        uploadTask.addOnSuccessListener {
            // Get the download URL
            mountainsRef.downloadUrl.addOnSuccessListener { uri ->
                Log.d(TAG, "File uploaded successfully: $uri")
            }
        }.addOnFailureListener {
            // Handle unsuccessful uploads
            Log.e(TAG, "Error uploading file")
        }
    }

    private fun uploadVideoToFirebase(uri: Uri) {
        // Get a reference to the storage service using the default Firebase App
        val storage = FirebaseStorage.getInstance()

        // Create a storage reference from our app
        val storageRef = storage.reference

        // Create a reference to "videos/video.mp4"
        val videoRef = storageRef.child("videos/video.mp4")

        // Upload the file to the path "videos/video.mp4"
        val uploadTask = videoRef.putFile(uri)

        // Register observers to listen for when the download is done or if it fails
        uploadTask.addOnSuccessListener {
            // Get the download URL
            videoRef.downloadUrl.addOnSuccessListener { uri ->
                Log.d(TAG, "File uploaded successfully: $uri")
            }
        }.addOnFailureListener {
            // Handle unsuccessful uploads
            Log.e(TAG, "Error uploading file")
        }
    }

    private fun saveFileInfoToFirestore(uri: Uri, fileType: String) {
        // Get a reference to the Firestore service using the default Firebase App
        val firestore = FirebaseFirestore.getInstance()

        // Create a reference to the "files" collection
        val filesRef = firestore.collection("files")

        // Create a document with a generated ID
        val newFileRef = filesRef.document()

        // Set the file information
        val fileInfo = hashMapOf(
            "filePath" to uri.toString(),
            "fileType" to fileType,
            "timestamp" to Date()
        )

        // Save the file information to Firestore
        newFileRef.set(fileInfo)
            .addOnSuccessListener {
                Log.d(TAG, "File information saved successfully")
            }
            .addOnFailureListener {
                Log.e(TAG, "Error saving file information")
            }
    }

    private fun fetchGalleryFromFirestore() {
        // Get a reference to the Firestore service using the default Firebase App
        val firestore = FirebaseFirestore.getInstance()

        // Create a reference to the "files" collection
        val filesRef = firestore.collection("files")

        // Get all documents in the collection
        filesRef.get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val filePath = document.getString("filePath")
                    val fileType = document.getString("fileType")

                    Log.d(TAG, "File: $filePath, Type: $fileType")

                    // Here you can also download the file using the filePath
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error getting documents: ", exception)
            }
    }

    private fun deleteFileFromFirebase(filePath: String) {
        // Get a reference to the storage service using the default Firebase App
        val storage = FirebaseStorage.getInstance()

        // Create a storage reference from our app
        val storageRef = storage.reference

        // Create a reference to the file to delete
        val fileRef = storageRef.child(filePath)

        // Delete the file
        fileRef.delete()
            .addOnSuccessListener {
                Log.d(TAG, "File deleted successfully")
            }
            .addOnFailureListener {
                Log.e(TAG, "Error deleting file")
            }
    }

    private fun updateFileInFirebase(uri: Uri, filePath: String) {
        // Get a reference to the storage service using the default Firebase App
        val storage = FirebaseStorage.getInstance()

        // Create a storage reference from our app
        val storageRef = storage.reference

        // Create a reference to the file to update
        val fileRef = storageRef.child(filePath)

        // Upload the new file to the same path
        val uploadTask = fileRef.putFile(uri)

        // Register observers to listen for when the download is done or if it fails
        uploadTask.addOnSuccessListener {
            // Get the download URL
            fileRef.downloadUrl.addOnSuccessListener { uri ->
                Log.d(TAG, "File updated successfully: $uri")
            }
        }.addOnFailureListener {
            // Handle unsuccessful uploads
            Log.e(TAG, "Error updating file")
        }
    }

    private fun downloadFileFromFirebase(filePath: String, destinationUri: Uri) {
        // Get a reference to the storage service using the default Firebase App
        val storage = FirebaseStorage.getInstance()

        // Create a storage reference from our app
        val storageRef = storage.reference

        // Create a reference to the file to download
        val fileRef = storageRef.child(filePath)

        // Download the file to the specified URI
        fileRef.getFile(destinationUri)
            .addOnSuccessListener {
                Log.d(TAG, "File downloaded successfully")
            }
            .addOnFailureListener {
                Log.e(TAG, "Error downloading file")
            }
    }
}
