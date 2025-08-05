package com.jerry.ksl.gesturerecognizer

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsManager = SettingsManager(application.applicationContext)

    // LiveData for observing settings changes if needed by UI components directly
    private val _settingsLoaded = MutableLiveData<Boolean>()
    val settingsLoaded: LiveData<Boolean> = _settingsLoaded

    // General Settings
    val language: String get() = settingsManager.language
    val theme: String get() = settingsManager.theme
    val threshold: Int get() = settingsManager.threshold
    val modelsDownloaded: Boolean get() = settingsManager.modelsDownloaded
    val debugMode: Boolean get() = settingsManager.debugMode

    // Gesture Recognizer & Camera Settings
    val currentDelegate: Int get() = settingsManager.delegate
    val currentMinHandDetectionConfidence: Float get() = settingsManager.handDetectionConfidence
    val currentMinHandTrackingConfidence: Float get() = settingsManager.handTrackingConfidence
    val currentMinHandPresenceConfidence: Float get() = settingsManager.handPresenceConfidence
    val currentCameraFacing: Int get() = settingsManager.cameraFacing

    fun refreshSettingsFromFirestore() {
        settingsManager.loadSettingsFromFirestore { success ->
            if (success) {
                Log.d("MainViewModel", "Settings successfully loaded from Firestore and cached.")
                // Optionally, trigger updates for any observers if using LiveData for each setting
            } else {
                Log.d("MainViewModel", "Failed to load settings from Firestore or no settings found, using local/defaults.")
            }
            _settingsLoaded.postValue(success) // Notify observers that loading attempt is complete
        }
    }

    // Setters for General Settings
    fun setLanguage(language: String) {
        settingsManager.language = language
    }

    fun setTheme(theme: String) {
        settingsManager.theme = theme
    }

    fun setThreshold(threshold: Int) {
        settingsManager.threshold = threshold
        // The setter in SettingsManager already updates handDetectionConfidence
    }

    fun setModelsDownloaded(downloaded: Boolean) {
        settingsManager.modelsDownloaded = downloaded
    }

    fun setDebugMode(debug: Boolean) {
        settingsManager.debugMode = debug
    }

    // Setters for Gesture Recognizer & Camera Settings
    fun setDelegate(delegate: Int) {
        settingsManager.delegate = delegate
    }

    fun setMinHandDetectionConfidence(confidence: Float) {
        settingsManager.handDetectionConfidence = confidence
    }

    fun setMinHandTrackingConfidence(confidence: Float) {
        settingsManager.handTrackingConfidence = confidence
    }

    fun setMinHandPresenceConfidence(confidence: Float) {
        settingsManager.handPresenceConfidence = confidence
    }

    fun setCameraFacing(cameraFacing: Int) {
        settingsManager.cameraFacing = cameraFacing
    }

    fun resetAllSettings() {
        settingsManager.resetToDefaults()
        // Optionally, refresh LiveData if individual settings have observers
        _settingsLoaded.postValue(true) // Indicate settings have been reset (and thus 'loaded')
    }
}
