package com.jerry.ksl.gesturerecognizer

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import java.util.Locale
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.jerry.ksl.gesturerecognizer.model.ValidationRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID
import kotlin.system.measureTimeMillis

class ModelValidationViewModel(application: Application) : AndroidViewModel(application), GestureRecognizerHelper.GestureRecognizerListener {

    private val _confidenceScore = MutableLiveData<Float>()
    val confidenceScore: LiveData<Float> = _confidenceScore

    private val _inferenceTime = MutableLiveData<Long>()
    val inferenceTime: LiveData<Long> = _inferenceTime

    private val _prediction = MutableLiveData<String>()
    val prediction: LiveData<String> = _prediction

    private val _validationResultText = MutableLiveData<String>()
    val validationResultText: LiveData<String> = _validationResultText

    private val _showSendDataButton = MutableLiveData<Boolean>()
    val showSendDataButton: LiveData<Boolean> = _showSendDataButton

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _toastMessage = MutableLiveData<String>()
    val toastMessage: LiveData<String> = _toastMessage

    private val _capturedImageUri = MutableLiveData<Uri?>()
    val capturedImageUri: LiveData<Uri?> = _capturedImageUri

    // Model selection logic
    private val _modelList = MutableLiveData<List<String>>()
    val modelList: LiveData<List<String>> = _modelList

    private val _selectedModel = MutableLiveData<String?>()
    val selectedModel: LiveData<String?> = _selectedModel

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private var gestureRecognizerHelper: GestureRecognizerHelper? = null
    private var currentExpectedLabel: String = ""

    // Initialize GestureRecognizerHelper
    fun initGestureRecognizerHelper(context: android.content.Context, mainViewModel: MainViewModel) {
        gestureRecognizerHelper = GestureRecognizerHelper(
            context = context,
            runningMode = RunningMode.IMAGE,
            minHandDetectionConfidence = mainViewModel.currentMinHandDetectionConfidence,
            minHandTrackingConfidence = mainViewModel.currentMinHandTrackingConfidence,
            minHandPresenceConfidence = mainViewModel.currentMinHandPresenceConfidence,
            currentDelegate = mainViewModel.currentDelegate,
            gestureRecognizerListener = this
        )
    }

    fun setCapturedImageUri(uri: Uri?) {
        _capturedImageUri.value = uri
    }

    fun analyzeAndValidateImage(imageUri: Uri, expectedLabel: String) {
        currentExpectedLabel = expectedLabel
        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = getApplication<Application>().contentResolver.openInputStream(imageUri)
                val bitmap = BitmapFactory.decodeStream(inputStream)

                var resultBundle: GestureRecognizerHelper.ResultBundle? = null
                val time = measureTimeMillis {
                    resultBundle = gestureRecognizerHelper?.recognizeImage(bitmap)
                }
                _inferenceTime.postValue(time)

                withContext(Dispatchers.Main) {
                    if (resultBundle != null &&
                        !resultBundle!!.results.first().gestures().isEmpty() &&
                        resultBundle!!.results.first().gestures()[0].isNotEmpty()
                    ) {
                        val topGesture = resultBundle!!.results.first().gestures()[0][0]
                        _confidenceScore.postValue(topGesture.score())
                        _prediction.postValue(topGesture.categoryName())

                        val allPredictions = mutableListOf<Pair<String, Float>>()
                        if (resultBundle!!.results.first().gestures().isNotEmpty()) {
                            val gestures = resultBundle!!.results.first().gestures()[0]
                            for (i in 0 until minOf(3, gestures.size)) {
                                allPredictions.add(Pair(gestures[i].categoryName(), gestures[i].score()))
                            }
                        }
                        updateMetricsDisplay(allPredictions)

                        if (topGesture.score() >= 0.70f) {
                            _showSendDataButton.postValue(true)
                            _toastMessage.postValue("Prediction confidence is high. Consider sending data for training!")
                        } else {
                            _showSendDataButton.postValue(false)
                        }

                    } else {
                        handleEmptyPrediction()
                    }
                }
            } catch (e: Exception) {
                handlePredictionError(e)
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun uploadValidationData(imageUri: Uri, expectedLabel: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _toastMessage.postValue("User not logged in.")
            return
        }

        _isLoading.value = true
        _toastMessage.postValue("Submitting validation data...")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val userId = currentUser.uid
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val filename = "validation_${expectedLabel}_${timestamp}_${UUID.randomUUID()}.jpg"
                val imageRef = storage.reference.child("validation_images/$userId/${expectedLabel}/$filename")

                imageRef.putFile(imageUri)
                    .addOnSuccessListener { taskSnapshot ->
                        taskSnapshot.storage.downloadUrl.addOnSuccessListener { downloadUrl ->
                            val imageUrl = downloadUrl.toString()
                            saveValidationDataToFirestore(userId, imageUrl, expectedLabel)
                        }.addOnFailureListener { e ->
                            Log.e(TAG, "Failed to get download URL", e)
                            _toastMessage.postValue("Failed to get image URL: ${e.message}")
                            _isLoading.postValue(false)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Image upload failed", e)
                        _toastMessage.postValue("Image upload failed: ${e.message}")
                        _isLoading.postValue(false)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading image", e)
                _toastMessage.postValue("Error uploading image: ${e.message}")
                _isLoading.postValue(false)
            }
        }
    }

    private fun saveValidationDataToFirestore(userId: String, imageUrl: String, expectedLabel: String) {
        val deviceInfo = hashMapOf(
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "os_version" to Build.VERSION.SDK_INT,
            "device" to Build.DEVICE
        )

        val modelVersion = "1.0"

        val validationData = ValidationRecord(
            userId = userId,
            imageUrl = imageUrl,
            expectedLabel = expectedLabel,
            predictedLabel = _prediction.value ?: "",
            confidenceScore = _confidenceScore.value ?: 0f,
            inferenceTimeMs = _inferenceTime.value ?: 0L,
            isCorrect = (expectedLabel == (_prediction.value ?: "")),
            modelVersion = modelVersion,
            deviceInfo = deviceInfo,
            timestamp = com.google.firebase.Timestamp.now()
        )

        firestore.collection("MODEL_VALIDATIONS")
            .add(validationData)
            .addOnSuccessListener {
                _toastMessage.postValue("Validation data submitted successfully!")
                resetState()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error saving validation data to Firestore", e)
                _toastMessage.postValue("Firestore submission failed: ${e.message}")
                _isLoading.postValue(false)
            }
    }

    private fun updateMetricsDisplay(allPredictions: List<Pair<String, Float>>) {
        val confidence = _confidenceScore.value ?: 0f
        val predictionText = _prediction.value ?: ""
        val inference = _inferenceTime.value ?: 0L

        val validationText = StringBuilder("Validation Status: Sign identified as '$predictionText' with ${String.format("%.2f", confidence * 100)}% confidence")

        if (allPredictions.size > 1) {
            validationText.append("\n\nAlternative predictions:")
            for (i in 1 until allPredictions.size) {
                val (sign, score) = allPredictions[i]
                validationText.append("\n$sign: ${String.format("%.2f", score * 100)}%")
            }
        }
        _validationResultText.postValue(validationText.toString())
    }

    private fun handleEmptyPrediction() {
        _toastMessage.postValue("Could not identify any sign in the image")
        _confidenceScore.postValue(0f)
        _inferenceTime.postValue(0L)
        _prediction.postValue("No sign detected")
        _validationResultText.postValue("Validation Status: Failed to detect sign")
        _showSendDataButton.postValue(false)
    }

    private fun handlePredictionError(e: Exception) {
        Log.e(TAG, "Error analyzing image", e)
        _toastMessage.postValue("Error analyzing image: ${e.message}")
        _validationResultText.postValue("Validation Status: Error analyzing image")
        _showSendDataButton.postValue(false)
    }

    fun resetState() {
        _capturedImageUri.postValue(null)
        _confidenceScore.postValue(0f)
        _inferenceTime.postValue(0L)
        _prediction.postValue("")
        _validationResultText.postValue("Validation Status: Not validated yet")
        _showSendDataButton.postValue(false)
        _isLoading.postValue(false)
    }

    /**
     * Fetches available model names from Firestore 'models' collection
     */
    fun fetchAvailableModels() {
        val db = FirebaseFirestore.getInstance()
        db.collection("models").get()
            .addOnSuccessListener { result ->
                val models = result.documents.mapNotNull { it.getString("name") }
                _modelList.value = models
                // Optionally select the first model by default
                if (_selectedModel.value == null && models.isNotEmpty()) {
                    _selectedModel.value = models[0]
                }
            }
            .addOnFailureListener { e ->
                Log.e("ModelValidationVM", "Failed to fetch models", e)
                _toastMessage.value = "Failed to load models."
            }
    }

    fun setSelectedModel(modelName: String) {
        _selectedModel.value = modelName
    }

    override fun onError(error: String, errorCode: Int) {
        _toastMessage.postValue(error)
        _validationResultText.postValue("Validation Status: Error: $error")
        _isLoading.postValue(false)
    }

    override fun onResults(resultBundle: GestureRecognizerHelper.ResultBundle) {
        // Not used in IMAGE mode
    }

    override fun onCleared() {
        super.onCleared()
        gestureRecognizerHelper?.clearGestureRecognizer()
    }

    companion object {
        private const val TAG = "ModelValidationViewModel"
    }
}