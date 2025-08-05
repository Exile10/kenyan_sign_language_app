package com.jerry.ksl.gesturerecognizer.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.CountDownTimer
import android.speech.tts.TextToSpeech
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
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import com.jerry.ksl.gesturerecognizer.GestureRecognizerHelper
import com.jerry.ksl.gesturerecognizer.MainViewModel
import com.jerry.ksl.gesturerecognizer.R
import com.jerry.ksl.gesturerecognizer.databinding.FragmentCameraBinding
import com.jerry.ksl.gesturerecognizer.service.GeminiService
import com.google.mediapipe.tasks.vision.core.RunningMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// Define an enum for UI modes
enum class UiMode {
    WORD_RECOGNITION,
    LIVE_TRANSLATION
}

class CameraFragment : Fragment(),
    GestureRecognizerHelper.GestureRecognizerListener {

    companion object {
        private const val TAG = "Hand gesture recognizer"
        private const val LLM_COLLECTION_TIME_MS = 5000L // 5 seconds for collecting words before LLM processing
        private const val COUNTDOWN_INTERVAL = 1000L // 1 second interval for timer updates
        private const val INACTIVITY_TIMEOUT_MS = 30000L // 30 seconds of no activity to clear recognized words
    }

    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!

    private lateinit var gestureRecognizerHelper: GestureRecognizerHelper
    private val viewModel: MainViewModel by activityViewModels()
    private var defaultNumResults = 1 // This might need to be re-evaluated based on new UI
    private val gestureRecognizerResultAdapter: GestureRecognizerResultsAdapter by lazy {
        GestureRecognizerResultsAdapter().apply {
            updateAdapterSize(defaultNumResults)
        }
    }
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var backgroundExecutor: ExecutorService

    // New state variables
    private var currentUiMode: UiMode = UiMode.WORD_RECOGNITION
    private var isTtsEnabled: Boolean = false
    private var lastRecognizedWord: String? = null
    private val recognizedWordsList = mutableListOf<String>() // For accumulating words in Word Recognition mode

    // Gemini service for translations
    private lateinit var geminiService: GeminiService

    // LLM translation related variables
    private val llmInputWords = mutableListOf<String>() // Words collected for LLM input
    private var isLlmTimerRunning = false
    private var llmCountDownTimer: CountDownTimer? = null
    private var llmTimeLeftMs = LLM_COLLECTION_TIME_MS

    // Text-to-Speech engine
    private var textToSpeech: TextToSpeech? = null

    // Removed word collection timer variables:
    // private val collectedWords = mutableSetOf<String>()
    // private var isCollecting = false
    // private var countDownTimer: CountDownTimer? = null
    // private var timeLeftMs = COLLECTION_TIME_MS

    // Inactivity handler
    private var inactivityHandler: CountDownTimer? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)

        return fragmentCameraBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Existing RecyclerView setup
        with(fragmentCameraBinding.recyclerviewResults) {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = gestureRecognizerResultAdapter
        }

        // Initialize Gemini service
        geminiService = GeminiService(requireContext())

        // Initialize background executor
        backgroundExecutor = Executors.newSingleThreadExecutor()

        // Initialize the gesture recognizer helper
        backgroundExecutor.execute {
            gestureRecognizerHelper = GestureRecognizerHelper(
                context = requireContext(),
                runningMode = RunningMode.LIVE_STREAM,
                minHandDetectionConfidence = viewModel.currentMinHandDetectionConfidence,
                minHandTrackingConfidence = viewModel.currentMinHandTrackingConfidence,
                minHandPresenceConfidence = viewModel.currentMinHandPresenceConfidence,
                currentDelegate = viewModel.currentDelegate,
                gestureRecognizerListener = this
            )
        }

        // Initialize Text-to-Speech engine
        initTextToSpeech()

        // Set up camera
        setUpCamera()

        // Set up new UI controls
        setupNewControls()

        // Update initial UI state
        updateUiModeText()

        // Set up clear button
        fragmentCameraBinding.clearTextButton.setOnClickListener {
            clearRecognizedText()
            Toast.makeText(requireContext(), "Text cleared", Toast.LENGTH_SHORT).show()
        }

        // The long-click listener is now redundant but kept for backwards compatibility
        fragmentCameraBinding.recognizedText.setOnLongClickListener {
            clearRecognizedText()
            Toast.makeText(requireContext(), "Text cleared", Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun setupNewControls() {
        fragmentCameraBinding.modeSwitch.setOnCheckedChangeListener { _, isChecked ->
            currentUiMode = if (isChecked) UiMode.LIVE_TRANSLATION else UiMode.WORD_RECOGNITION
            updateUiModeText()
            clearRecognizedText()

            // Cancel any running timer when switching modes
            llmCountDownTimer?.cancel()
            isLlmTimerRunning = false
            fragmentCameraBinding.timerText.visibility = View.GONE

            // Start timer if switching to Live Translation mode
            if (currentUiMode == UiMode.LIVE_TRANSLATION) {
                Toast.makeText(requireContext(), "Live Translation Mode", Toast.LENGTH_SHORT).show()
                startLlmTimer()
            } else {
                Toast.makeText(requireContext(), "Word Recognition Mode", Toast.LENGTH_SHORT).show()
            }
        }

        fragmentCameraBinding.ttsSwitch.setOnCheckedChangeListener { _, isChecked ->
            isTtsEnabled = isChecked
            if (isChecked) {
                Toast.makeText(requireContext(), "TTS Enabled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "TTS Disabled", Toast.LENGTH_SHORT).show()
                textToSpeech?.stop() // Stop any ongoing speech when TTS is disabled
            }
        }

        fragmentCameraBinding.cameraSwitchButton.setOnClickListener {
            val newFacing = if (viewModel.currentCameraFacing == CameraSelector.LENS_FACING_FRONT) {
                CameraSelector.LENS_FACING_BACK
            } else {
                CameraSelector.LENS_FACING_FRONT
            }
            viewModel.setCameraFacing(newFacing)
            bindCameraUseCases() // Rebind camera use cases with new facing
        }
    }

    private fun updateUiModeText() {
        fragmentCameraBinding.currentModeText.text = if (currentUiMode == UiMode.LIVE_TRANSLATION) {
            getString(R.string.mode_live_translation)
        } else {
            getString(R.string.mode_word_recognition)
        }
    }

    private fun clearRecognizedText() {
        recognizedWordsList.clear()
        llmInputWords.clear()
        lastRecognizedWord = null
        fragmentCameraBinding.recognizedText.text = ""
    }

    private fun startLlmTimer() {
        // Cancel existing timer if running
        llmCountDownTimer?.cancel()

        // Reset timer state
        isLlmTimerRunning = true
        llmTimeLeftMs = LLM_COLLECTION_TIME_MS
        llmInputWords.clear()

        // Make timer visible
        fragmentCameraBinding.timerText.visibility = View.VISIBLE
        updateLlmTimerDisplay()

        // Create and start new timer
        llmCountDownTimer = object : CountDownTimer(LLM_COLLECTION_TIME_MS, COUNTDOWN_INTERVAL) {
            override fun onTick(millisUntilFinished: Long) {
                llmTimeLeftMs = millisUntilFinished
                updateLlmTimerDisplay()
            }

            override fun onFinish() {
                isLlmTimerRunning = false
                processLlmTranslation()

                // Start a new timer after a short pause if still in Live Translation mode
                if (currentUiMode == UiMode.LIVE_TRANSLATION) {
                    view?.postDelayed({
                        if (view != null && isAdded && !isDetached && currentUiMode == UiMode.LIVE_TRANSLATION) {
                            startLlmTimer()
                        }
                    }, 1000) // 1 second pause
                }
            }
        }.start()
    }

    private fun updateLlmTimerDisplay() {
        val secondsLeft = (llmTimeLeftMs / 1000).toInt()
        fragmentCameraBinding.timerText.text = "Processing in ${secondsLeft}s..."
    }

    private fun processLlmTranslation() {
        if (llmInputWords.isEmpty()) {
            fragmentCameraBinding.recognizedText.text = "No signs detected"
            return
        }

        // Show loading state
        fragmentCameraBinding.recognizedText.text = "Translating with Gemini..."

        // Get the collected words
        val inputSigns = llmInputWords.toList() // Make a copy of the current list

        // Launch a coroutine to perform the translation in the background
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Call Gemini API to translate signs to natural language
                val translatedText = withContext(Dispatchers.IO) {
                    geminiService.translateSignsToText(inputSigns)
                }

                // Display the translated text if still attached to view
                if (_fragmentCameraBinding != null && isAdded && !isDetached) {
                    fragmentCameraBinding.recognizedText.text = translatedText

                    // Speak the translated result if TTS is enabled
                    if (isTtsEnabled) {
                        speakText(translatedText)
                    }

                    // Reset inactivity timer since we just received a translation
                    resetInactivityTimer()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during Gemini translation", e)
                if (_fragmentCameraBinding != null && isAdded && !isDetached) {
                    fragmentCameraBinding.recognizedText.text = "Translation error: ${e.message}"
                }
            }
        }

        // Clear the collected words for the next cycle
        llmInputWords.clear()
    }

    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(requireContext(), "Language not supported for TTS", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Text-to-Speech initialization failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun speakText(text: String) {
        if (isTtsEnabled) { // Only speak if TTS is enabled
            textToSpeech?.stop()
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
        }
    }

    /**
     * Starts or resets the inactivity timer that clears recognized words after a long period of no activity
     */
    private fun startInactivityTimer() {
        // Cancel any existing timer first
        inactivityHandler?.cancel()

        // Only start timer if we have words to clear
        if ((currentUiMode == UiMode.WORD_RECOGNITION && recognizedWordsList.isNotEmpty()) ||
            (currentUiMode == UiMode.LIVE_TRANSLATION && llmInputWords.isNotEmpty())) {

            inactivityHandler = object : CountDownTimer(INACTIVITY_TIMEOUT_MS, COUNTDOWN_INTERVAL) {
                override fun onTick(millisUntilFinished: Long) {
                    // Not showing any UI for this countdown
                }

                override fun onFinish() {
                    activity?.runOnUiThread {
                        if (isAdded && !isDetached) {
                            // If no activity detected for the timeout period, clear the recognized text
                            if (recognizedWordsList.isNotEmpty() || llmInputWords.isNotEmpty()) {
                                clearRecognizedText()
                                Toast.makeText(
                                    requireContext(),
                                    "Words cleared due to inactivity",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            }.start()
        }
    }

    /**
     * Resets the inactivity timer when activity is detected
     */
    private fun resetInactivityTimer() {
        // Reset the timer when any sign activity is detected
        startInactivityTimer()
    }

    override fun onResume() {
        super.onResume()
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(
                requireActivity(), R.id.fragment_container
            ).navigate(R.id.action_camera_to_permissions)
        }

        backgroundExecutor.execute {
            if (this::gestureRecognizerHelper.isInitialized && gestureRecognizerHelper.isClosed()) {
                gestureRecognizerHelper.setupGestureRecognizer()
            }
        }

        // Start inactivity monitoring
        startInactivityTimer()
    }

    override fun onPause() {
        super.onPause()
        if (this::gestureRecognizerHelper.isInitialized) {
            viewModel.setMinHandDetectionConfidence(gestureRecognizerHelper.minHandDetectionConfidence)
            viewModel.setMinHandTrackingConfidence(gestureRecognizerHelper.minHandTrackingConfidence)
            viewModel.setMinHandPresenceConfidence(gestureRecognizerHelper.minHandPresenceConfidence)
            viewModel.setDelegate(gestureRecognizerHelper.currentDelegate)
            backgroundExecutor.execute { gestureRecognizerHelper.clearGestureRecognizer() }
        }
        // Cancel timers
        llmCountDownTimer?.cancel()
        inactivityHandler?.cancel()
        isLlmTimerRunning = false
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()

        // Shut down our background executor
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(
            Long.MAX_VALUE, TimeUnit.NANOSECONDS
        )

        // Shutdown TTS
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }

    // Updated setUpCamera and bindCameraUseCases methods
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

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(viewModel.currentCameraFacing)
            .build()

        // Preview
        preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
            .build()

        // ImageAnalysis. Using RGBA 8888 to match how our models work
        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also {
                it.setAnalyzer(
                    backgroundExecutor
                ) { image ->
                    backgroundExecutor.execute {
                        if (::gestureRecognizerHelper.isInitialized) {
                            gestureRecognizerHelper.recognizeLiveStream(
                                imageProxy = image
                            )
                        }
                    }
                }
            }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    // Override the results callback to collect recognized words
    override fun onResults(resultBundle: GestureRecognizerHelper.ResultBundle) {
        activity?.runOnUiThread {
            if (_fragmentCameraBinding == null) { // Check if binding is still valid
                return@runOnUiThread
            }

            // Get all gesture categories from all detected hands
            val allGestureCategories = resultBundle.results.firstOrNull()?.gestures()

            if (allGestureCategories != null && allGestureCategories.isNotEmpty()) {
                // Process gestures from all detected hands
                val detectedHandsCount = allGestureCategories.size

                if (detectedHandsCount > 0) {
                    // For single hand, process as before
                    val sortedCategoriesFirstHand = allGestureCategories[0].sortedByDescending { it.score() }
                    val topCategoryFirstHand = sortedCategoriesFirstHand.firstOrNull()

                    // For two hands, look at the second hand if available
                    val topCategorySecondHand = if (detectedHandsCount > 1 && allGestureCategories[1].isNotEmpty()) {
                        allGestureCategories[1].sortedByDescending { it.score() }.firstOrNull()
                    } else null

                    // Log the detected hands for debugging
                    Log.d(TAG, "Detected $detectedHandsCount hands")

                    // Process the gestures based on what's detected
                    if (topCategoryFirstHand != null && topCategoryFirstHand.score() > 0.9) {
                        val currentWordFirstHand = topCategoryFirstHand.categoryName().trim()

                        // Two-handed gesture processing
                        if (topCategorySecondHand != null && topCategorySecondHand.score() > 0.9) {
                            val currentWordSecondHand = topCategorySecondHand.categoryName().trim()
                            Log.d(TAG, "Two-hand gesture: $currentWordFirstHand + $currentWordSecondHand")

                            // Combine the gestures based on your application's logic
                            // For example, you might have specific combinations that form special signs
                            // Or just concatenate them for now
                            processTwoHandedGesture(currentWordFirstHand, currentWordSecondHand)
                        } else {
                            // Process single-handed gesture as before
                            processSingleHandedGesture(currentWordFirstHand)
                        }
                    }
                }
            } else { // No gestures detected or empty categories
                // Optionally, reset lastRecognizedWord if no gesture is detected for a while
                // to allow re-recognition of the same word if needed after a pause.
                // For now, we don't reset it here to strictly prevent immediate repetition.
            }

            // Update existing UI components as well (if recyclerview_results is still used)
            resultBundle.results.firstOrNull()?.let {
                gestureRecognizerResultAdapter.updateResults(it)
            }

            // Get the current result from the bundle (might be null)
            val result = resultBundle.results.firstOrNull()

            // Only call setResults if we have a valid result
            if (result != null) {
                fragmentCameraBinding.overlay.setResults(
                    result,
                    resultBundle.inputImageHeight,
                    resultBundle.inputImageWidth,
                    RunningMode.LIVE_STREAM
                )
                fragmentCameraBinding.overlay.invalidate()
            }
        }
    }

    /**
     * Process a single-handed gesture (original logic)
     */
    private fun processSingleHandedGesture(word: String) {
        if (word.isNotEmpty() && word != lastRecognizedWord) {
            lastRecognizedWord = word

            when (currentUiMode) {
                UiMode.WORD_RECOGNITION -> {
                    // Check if this word is the same as the last one in the list
                    val lastWord = recognizedWordsList.lastOrNull()
                    if (lastWord != word) {
                        // Only add the word if it's different from the last one
                        recognizedWordsList.add(word)
                        val sentence = recognizedWordsList.joinToString(" ")
                        fragmentCameraBinding.recognizedText.text = sentence
                        speakText(word) // Speak individual word

                        // Reset the inactivity timer since we detected a new sign
                        resetInactivityTimer()
                    }
                }
                UiMode.LIVE_TRANSLATION -> {
                    // Check if this word is the same as the last one in the list
                    val lastWord = llmInputWords.lastOrNull()
                    if (lastWord != word) {
                        // Only add the word if it's different from the last one
                        llmInputWords.add(word)

                        // Show collected words so far
                        val collectedWords = llmInputWords.joinToString(" ")
                        fragmentCameraBinding.recognizedText.text = "Collecting: $collectedWords"

                        // Reset the inactivity timer since we detected a new sign
                        resetInactivityTimer()
                    }
                    // No immediate TTS - will speak after translation
                }
            }
        }
    }

    /**
     * Process a two-handed gesture
     */
    private fun processTwoHandedGesture(leftHandWord: String, rightHandWord: String) {
        // Create a combined gesture identifier
        // You can customize this based on your application's needs
        val combinedGesture = "$leftHandWord+$rightHandWord"

        // Check for specific two-handed gestures
        val processedWord = when {
            // Add specific two-handed gesture mappings here
            // Example: "A+B" -> "Hello"
            // leftHandWord == "A" && rightHandWord == "B" -> "Hello"

            // For now, just combine them with a plus sign
            else -> combinedGesture
        }

        // If this is a new gesture, process it
        if (processedWord != lastRecognizedWord) {
            lastRecognizedWord = processedWord

            when (currentUiMode) {
                UiMode.WORD_RECOGNITION -> {
                    // Check if this word is the same as the last one in the list
                    val lastWord = recognizedWordsList.lastOrNull()
                    if (lastWord != processedWord) {
                        recognizedWordsList.add(processedWord)
                        val sentence = recognizedWordsList.joinToString(" ")
                        fragmentCameraBinding.recognizedText.text = sentence
                        speakText(processedWord) // Speak individual word

                        // Reset the inactivity timer since we detected a new sign
                        resetInactivityTimer()
                    }
                }
                UiMode.LIVE_TRANSLATION -> {
                    // Check if this word is the same as the last one in the list
                    val lastWord = llmInputWords.lastOrNull()
                    if (lastWord != processedWord) {
                        llmInputWords.add(processedWord)

                        // Show collected words so far
                        val collectedWords = llmInputWords.joinToString(" ")
                        fragmentCameraBinding.recognizedText.text = "Collecting: $collectedWords"

                        // Reset the inactivity timer since we detected a new sign
                        resetInactivityTimer()
                    }
                    // No immediate TTS - will speak after translation
                }
            }
        }
    }

    override fun onError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            if (_fragmentCameraBinding == null) return@runOnUiThread
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            // Display error in the recognized text view for feedback
            val errorText = "Error: $error"
            fragmentCameraBinding.recognizedText.text = errorText
            // Consider if TTS should speak errors
            // speakText(errorText)
        }
    }
}
