package com.jerry.ksl.gesturerecognizer

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.camera.core.CameraSelector
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * Manager for handling persistent settings storage for the KSL app
 */
class SettingsManager(private val context: Context) {

    companion object {
        private const val TAG = "SettingsManager"
        private const val PREFS_NAME = "ksl_app_settings"
        private const val FIRESTORE_COLLECTION_USERS = "users"
        private const val FIRESTORE_DOCUMENT_SETTINGS = "settings"

        // Keys for storing settings (SharedPreferences and Firestore)
        // Keys from SettingsFragment (ensure consistency)
        const val KEY_LANGUAGE = "language"
        const val KEY_THEME = "theme"
        const val KEY_THRESHOLD = "threshold" // Corresponds to handDetectionConfidence for this manager
        const val KEY_MODELS_DOWNLOADED = "models_downloaded"
        const val KEY_DEBUG_MODE = "debug_mode"

        // Keys specific to GestureRecognizerHelper and Camera
        const val KEY_DELEGATE = "delegate"
        const val KEY_HAND_DETECTION_CONFIDENCE = "hand_detection_confidence"
        const val KEY_HAND_TRACKING_CONFIDENCE = "hand_tracking_confidence"
        const val KEY_HAND_PRESENCE_CONFIDENCE = "hand_presence_confidence"
        const val KEY_CAMERA_FACING = "camera_facing"

        // Default values
        private const val DEFAULT_LANGUAGE = "English"
        private const val DEFAULT_THEME = "Light"
        private const val DEFAULT_THRESHOLD = 70 // Default for the general threshold in SettingsFragment
        private const val DEFAULT_MODELS_DOWNLOADED = false
        private const val DEFAULT_DEBUG_MODE = false

        private const val DEFAULT_DELEGATE = GestureRecognizerHelper.DELEGATE_CPU
        private const val DEFAULT_HAND_DETECTION_CONFIDENCE = GestureRecognizerHelper.DEFAULT_HAND_DETECTION_CONFIDENCE
        private const val DEFAULT_HAND_TRACKING_CONFIDENCE = GestureRecognizerHelper.DEFAULT_HAND_TRACKING_CONFIDENCE
        private const val DEFAULT_HAND_PRESENCE_CONFIDENCE = GestureRecognizerHelper.DEFAULT_HAND_PRESENCE_CONFIDENCE
        private const val DEFAULT_CAMERA_FACING = CameraSelector.LENS_FACING_FRONT
    }

    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var firestore: FirebaseFirestore? = null
    private var auth: FirebaseAuth? = null
    private var userId: String? = null

    init {
        try {
            firestore = FirebaseFirestore.getInstance()
            auth = FirebaseAuth.getInstance()
            userId = auth?.currentUser?.uid
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Firebase not initialized: ${e.message}")
            // Firebase might not be initialized if this manager is accessed too early
            // or in a context where Firebase is not available (e.g. unit tests without mocking)
        }
    }

    private fun getUserId(): String? {
        if (userId == null) {
            userId = auth?.currentUser?.uid
        }
        return userId
    }

    private fun <T> saveToFirestore(key: String, value: T) {
        getUserId()?.let { uid ->
            firestore?.collection(FIRESTORE_COLLECTION_USERS)?.document(uid)?.collection(FIRESTORE_DOCUMENT_SETTINGS)?.document("user_settings")
                ?.set(hashMapOf(key to value), SetOptions.merge())
                ?.addOnSuccessListener { Log.d(TAG, "$key saved to Firestore.") }
                ?.addOnFailureListener { e -> Log.e(TAG, "Error saving $key to Firestore", e) }
        }
    }

    // SettingsFragment specific settings
    var language: String
        get() = sharedPrefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
        set(value) {
            sharedPrefs.edit().putString(KEY_LANGUAGE, value).apply()
            saveToFirestore(KEY_LANGUAGE, value)
        }

    var theme: String
        get() = sharedPrefs.getString(KEY_THEME, DEFAULT_THEME) ?: DEFAULT_THEME
        set(value) {
            sharedPrefs.edit().putString(KEY_THEME, value).apply()
            saveToFirestore(KEY_THEME, value)
        }

    // This 'threshold' is the general one from SettingsFragment, mapped to handDetectionConfidence for consistency here
    var threshold: Int
        get() = sharedPrefs.getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD)
        set(value) {
            sharedPrefs.edit().putInt(KEY_THRESHOLD, value).apply()
            saveToFirestore(KEY_THRESHOLD, value)
            // Also update handDetectionConfidence if this is the primary threshold control
            handDetectionConfidence = value / 100f // Assuming threshold is 0-100
        }

    var modelsDownloaded: Boolean
        get() = sharedPrefs.getBoolean(KEY_MODELS_DOWNLOADED, DEFAULT_MODELS_DOWNLOADED)
        set(value) {
            sharedPrefs.edit().putBoolean(KEY_MODELS_DOWNLOADED, value).apply()
            saveToFirestore(KEY_MODELS_DOWNLOADED, value)
        }

    var debugMode: Boolean
        get() = sharedPrefs.getBoolean(KEY_DEBUG_MODE, DEFAULT_DEBUG_MODE)
        set(value) {
            sharedPrefs.edit().putBoolean(KEY_DEBUG_MODE, value).apply()
            saveToFirestore(KEY_DEBUG_MODE, value)
        }

    // GestureRecognizerHelper and Camera specific settings
    var delegate: Int
        get() = sharedPrefs.getInt(KEY_DELEGATE, DEFAULT_DELEGATE)
        set(value) {
            sharedPrefs.edit().putInt(KEY_DELEGATE, value).apply()
            saveToFirestore(KEY_DELEGATE, value)
        }

    var handDetectionConfidence: Float
        get() = sharedPrefs.getFloat(KEY_HAND_DETECTION_CONFIDENCE, DEFAULT_HAND_DETECTION_CONFIDENCE)
        set(value) {
            sharedPrefs.edit().putFloat(KEY_HAND_DETECTION_CONFIDENCE, value).apply()
            saveToFirestore(KEY_HAND_DETECTION_CONFIDENCE, value)
        }

    var handTrackingConfidence: Float
        get() = sharedPrefs.getFloat(KEY_HAND_TRACKING_CONFIDENCE, DEFAULT_HAND_TRACKING_CONFIDENCE)
        set(value) {
            sharedPrefs.edit().putFloat(KEY_HAND_TRACKING_CONFIDENCE, value).apply()
            saveToFirestore(KEY_HAND_TRACKING_CONFIDENCE, value)
        }

    var handPresenceConfidence: Float
        get() = sharedPrefs.getFloat(KEY_HAND_PRESENCE_CONFIDENCE, DEFAULT_HAND_PRESENCE_CONFIDENCE)
        set(value) {
            sharedPrefs.edit().putFloat(KEY_HAND_PRESENCE_CONFIDENCE, value).apply()
            saveToFirestore(KEY_HAND_PRESENCE_CONFIDENCE, value)
        }

    var cameraFacing: Int
        get() = sharedPrefs.getInt(KEY_CAMERA_FACING, DEFAULT_CAMERA_FACING)
        set(value) {
            sharedPrefs.edit().putInt(KEY_CAMERA_FACING, value).apply()
            saveToFirestore(KEY_CAMERA_FACING, value)
        }

    fun loadSettingsFromFirestore(onComplete: ((Boolean) -> Unit)? = null) {
        getUserId()?.let { uid ->
            firestore?.collection(FIRESTORE_COLLECTION_USERS)?.document(uid)?.collection(FIRESTORE_DOCUMENT_SETTINGS)?.document("user_settings")?.get()
                ?.addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        Log.d(TAG, "Settings loaded from Firestore.")
                        val editor = sharedPrefs.edit()
                        document.getString(KEY_LANGUAGE)?.let { editor.putString(KEY_LANGUAGE, it) }
                        document.getString(KEY_THEME)?.let { editor.putString(KEY_THEME, it) }
                        document.getLong(KEY_THRESHOLD)?.let { editor.putInt(KEY_THRESHOLD, it.toInt()) } // Firestore stores numbers as Long
                        document.getBoolean(KEY_MODELS_DOWNLOADED)?.let { editor.putBoolean(KEY_MODELS_DOWNLOADED, it) }
                        document.getBoolean(KEY_DEBUG_MODE)?.let { editor.putBoolean(KEY_DEBUG_MODE, it) }

                        document.getLong(KEY_DELEGATE)?.let { editor.putInt(KEY_DELEGATE, it.toInt()) }
                        document.getDouble(KEY_HAND_DETECTION_CONFIDENCE)?.let { editor.putFloat(KEY_HAND_DETECTION_CONFIDENCE, it.toFloat()) }
                        document.getDouble(KEY_HAND_TRACKING_CONFIDENCE)?.let { editor.putFloat(KEY_HAND_TRACKING_CONFIDENCE, it.toFloat()) }
                        document.getDouble(KEY_HAND_PRESENCE_CONFIDENCE)?.let { editor.putFloat(KEY_HAND_PRESENCE_CONFIDENCE, it.toFloat()) }
                        document.getLong(KEY_CAMERA_FACING)?.let { editor.putInt(KEY_CAMERA_FACING, it.toInt()) }
                        editor.apply()
                        onComplete?.invoke(true)
                    } else {
                        Log.d(TAG, "No settings document found in Firestore for user $uid. Using local/defaults.")
                        // Optionally, save local/default SharedPreferences to Firestore if no document exists
                         saveAllSharedPreferencesToFirestore()
                        onComplete?.invoke(false)
                    }
                }
                ?.addOnFailureListener { e ->
                    Log.e(TAG, "Error loading settings from Firestore", e)
                    onComplete?.invoke(false)
                }
        } ?: run {
            Log.d(TAG, "User not logged in. Using local/default settings.")
            onComplete?.invoke(false)
        }
    }

    private fun saveAllSharedPreferencesToFirestore() {
        getUserId()?.let { uid ->
            val settingsMap = mutableMapOf<String, Any>()
            sharedPrefs.all.forEach { (key, value) ->
                value?.let { settingsMap[key] = it }
            }
            if (settingsMap.isNotEmpty()) {
                firestore?.collection(FIRESTORE_COLLECTION_USERS)?.document(uid)?.collection(FIRESTORE_DOCUMENT_SETTINGS)?.document("user_settings")
                    ?.set(settingsMap, SetOptions.merge())
                    ?.addOnSuccessListener { Log.d(TAG, "All SharedPreferences saved to Firestore.") }
                    ?.addOnFailureListener { e -> Log.e(TAG, "Error saving SharedPreferences to Firestore", e) }
            }
        }
    }

    fun resetToDefaults() {
        sharedPrefs.edit().apply {
            putString(KEY_LANGUAGE, DEFAULT_LANGUAGE)
            putString(KEY_THEME, DEFAULT_THEME)
            putInt(KEY_THRESHOLD, DEFAULT_THRESHOLD)
            putBoolean(KEY_MODELS_DOWNLOADED, DEFAULT_MODELS_DOWNLOADED)
            putBoolean(KEY_DEBUG_MODE, DEFAULT_DEBUG_MODE)

            putInt(KEY_DELEGATE, DEFAULT_DELEGATE)
            putFloat(KEY_HAND_DETECTION_CONFIDENCE, DEFAULT_HAND_DETECTION_CONFIDENCE)
            putFloat(KEY_HAND_TRACKING_CONFIDENCE, DEFAULT_HAND_TRACKING_CONFIDENCE)
            putFloat(KEY_HAND_PRESENCE_CONFIDENCE, DEFAULT_HAND_PRESENCE_CONFIDENCE)
            putInt(KEY_CAMERA_FACING, DEFAULT_CAMERA_FACING)
            apply()
        }
        // After resetting SharedPreferences, also update Firestore with these defaults
        saveAllSharedPreferencesToFirestore()
        Log.d(TAG, "All settings reset to defaults in SharedPreferences and pushed to Firestore.")
    }
}
