package com.jerry.ksl.gesturerecognizer.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jerry.ksl.gesturerecognizer.MainViewModel
import com.jerry.ksl.gesturerecognizer.R
import com.jerry.ksl.gesturerecognizer.databinding.FragmentUserPreferencesBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Fragment for managing user preferences and advanced settings
 * Includes notification settings, privacy preferences, and app customization
 */
class UserPreferencesFragment : Fragment() {

    companion object {
        private const val TAG = "UserPreferencesFragment"
    }

    private var _binding: FragmentUserPreferencesBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private val viewModel: MainViewModel by activityViewModels()

    // User preferences
    private var notificationsEnabled = true
    private var dataCollectionEnabled = true
    private var offlineModeEnabled = false
    private var autoSyncEnabled = true
    private var hapticFeedbackEnabled = true
    private var soundEffectsEnabled = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserPreferencesBinding.inflate(inflater, container, false)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadUserPreferences()
        setupPreferenceListeners()
    }

    /**
     * Load user preferences from Firestore
     */
    private fun loadUserPreferences() {
        val firebaseUser = auth.currentUser
        if (firebaseUser != null) {
            lifecycleScope.launch {
                try {
                    binding.progressLoading.visibility = View.VISIBLE

                    val preferencesDoc = firestore.collection("user_preferences")
                        .document(firebaseUser.uid)
                        .get()
                        .await()

                    if (preferencesDoc.exists()) {
                        val data = preferencesDoc.data ?: emptyMap()

                        // Load preferences with defaults
                        notificationsEnabled = data["notifications_enabled"] as? Boolean ?: true
                        dataCollectionEnabled = data["data_collection_enabled"] as? Boolean ?: true
                        offlineModeEnabled = data["offline_mode_enabled"] as? Boolean ?: false
                        autoSyncEnabled = data["auto_sync_enabled"] as? Boolean ?: true
                        hapticFeedbackEnabled = data["haptic_feedback_enabled"] as? Boolean ?: true
                        soundEffectsEnabled = data["sound_effects_enabled"] as? Boolean ?: true

                        updatePreferencesUI()
                        Log.d(TAG, "User preferences loaded successfully")
                    } else {
                        // Use defaults for new users
                        updatePreferencesUI()
                        saveUserPreferences() // Save defaults
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading user preferences", e)
                    showToast("Failed to load preferences: ${e.message}")
                    updatePreferencesUI() // Use defaults
                } finally {
                    binding.progressLoading.visibility = View.GONE
                }
            }
        }
    }

    /**
     * Update UI with current preference values
     */
    private fun updatePreferencesUI() {
        binding.apply {
            switchNotifications.isChecked = notificationsEnabled
            switchDataCollection.isChecked = dataCollectionEnabled
            switchOfflineMode.isChecked = offlineModeEnabled
            switchAutoSync.isChecked = autoSyncEnabled
            switchHapticFeedback.isChecked = hapticFeedbackEnabled
            switchSoundEffects.isChecked = soundEffectsEnabled

            // Update dependent settings visibility
            updateDependentSettings()
        }
    }

    /**
     * Setup preference change listeners
     */
    private fun setupPreferenceListeners() {
        binding.apply {
            // Notification preferences
            switchNotifications.setOnCheckedChangeListener { _, isChecked ->
                notificationsEnabled = isChecked
                updateDependentSettings()
                saveUserPreferences()
                showToast("Notification settings ${if (isChecked) "enabled" else "disabled"}")
            }

            // Data collection preferences
            switchDataCollection.setOnCheckedChangeListener { _, isChecked ->
                if (!isChecked) {
                    showDataCollectionWarning { confirmed ->
                        if (confirmed) {
                            dataCollectionEnabled = false
                            saveUserPreferences()
                            showToast("Data collection disabled")
                        } else {
                            switchDataCollection.isChecked = true // Revert
                        }
                    }
                } else {
                    dataCollectionEnabled = true
                    saveUserPreferences()
                    showToast("Data collection enabled")
                }
            }

            // Offline mode preferences
            switchOfflineMode.setOnCheckedChangeListener { _, isChecked ->
                offlineModeEnabled = isChecked
                updateDependentSettings()
                saveUserPreferences()
                showToast("Offline mode ${if (isChecked) "enabled" else "disabled"}")
            }

            // Auto sync preferences
            switchAutoSync.setOnCheckedChangeListener { _, isChecked ->
                autoSyncEnabled = isChecked
                saveUserPreferences()
                showToast("Auto sync ${if (isChecked) "enabled" else "disabled"}")
            }

            // Haptic feedback preferences
            switchHapticFeedback.setOnCheckedChangeListener { _, isChecked ->
                hapticFeedbackEnabled = isChecked
                saveUserPreferences()
                showToast("Haptic feedback ${if (isChecked) "enabled" else "disabled"}")
            }

            // Sound effects preferences
            switchSoundEffects.setOnCheckedChangeListener { _, isChecked ->
                soundEffectsEnabled = isChecked
                saveUserPreferences()
                showToast("Sound effects ${if (isChecked) "enabled" else "disabled"}")
            }

            // Clear cache button
            buttonClearCache.setOnClickListener {
                showClearCacheConfirmation()
            }

            // Reset preferences button
            buttonResetPreferences.setOnClickListener {
                showResetPreferencesConfirmation()
            }

            // Export data button
            buttonExportData.setOnClickListener {
                showToast("Data export feature coming soon")
            }
        }
    }

    /**
     * Update visibility of dependent settings
     */
    private fun updateDependentSettings() {
        binding.apply {
            // Auto sync depends on offline mode being disabled
            layoutAutoSync.alpha = if (offlineModeEnabled) 0.5f else 1.0f
            switchAutoSync.isEnabled = !offlineModeEnabled
        }
    }

    /**
     * Save user preferences to Firestore
     */
    private fun saveUserPreferences() {
        val firebaseUser = auth.currentUser
        if (firebaseUser != null) {
            lifecycleScope.launch {
                try {
                    val preferences = mapOf(
                        "notifications_enabled" to notificationsEnabled,
                        "data_collection_enabled" to dataCollectionEnabled,
                        "offline_mode_enabled" to offlineModeEnabled,
                        "auto_sync_enabled" to autoSyncEnabled,
                        "haptic_feedback_enabled" to hapticFeedbackEnabled,
                        "sound_effects_enabled" to soundEffectsEnabled,
                        "last_updated" to System.currentTimeMillis()
                    )

                    firestore.collection("user_preferences")
                        .document(firebaseUser.uid)
                        .set(preferences)
                        .await()

                    Log.d(TAG, "User preferences saved successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving user preferences", e)
                    showToast("Failed to save preferences: ${e.message}")
                }
            }
        }
    }

    /**
     * Show data collection warning dialog
     */
    private fun showDataCollectionWarning(callback: (Boolean) -> Unit) {
        AlertDialog.Builder(requireContext())
            .setTitle("Disable Data Collection")
            .setMessage("Disabling data collection will prevent the app from improving its recognition accuracy. Your previously submitted data will remain to help other users. Continue?")
            .setPositiveButton("Disable") { _, _ ->
                callback(true)
            }
            .setNegativeButton("Keep Enabled") { _, _ ->
                callback(false)
            }
            .show()
    }

    /**
     * Show clear cache confirmation dialog
     */
    private fun showClearCacheConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Clear Cache")
            .setMessage("This will clear all locally stored data including downloaded models. The app may need to re-download content. Continue?")
            .setPositiveButton("Clear") { _, _ ->
                performClearCache()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Show reset preferences confirmation dialog
     */
    private fun showResetPreferencesConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Reset Preferences")
            .setMessage("This will reset all preferences to their default values. Continue?")
            .setPositiveButton("Reset") { _, _ ->
                resetToDefaults()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Perform cache clearing operation
     */
    private fun performClearCache() {
        lifecycleScope.launch {
            try {
                binding.progressLoading.visibility = View.VISIBLE

                // Clear app cache
                val cacheDir = requireContext().cacheDir
                cacheDir.deleteRecursively()

                // Clear temporary files
                val tempDir = requireContext().getDir("temp", 0)
                tempDir.deleteRecursively()

                showToast("Cache cleared successfully")
                Log.d(TAG, "Cache cleared successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing cache", e)
                showToast("Failed to clear cache: ${e.message}")
            } finally {
                binding.progressLoading.visibility = View.GONE
            }
        }
    }

    /**
     * Reset all preferences to default values
     */
    private fun resetToDefaults() {
        notificationsEnabled = true
        dataCollectionEnabled = true
        offlineModeEnabled = false
        autoSyncEnabled = true
        hapticFeedbackEnabled = true
        soundEffectsEnabled = true

        updatePreferencesUI()
        saveUserPreferences()

        showToast("Preferences reset to defaults")
        Log.d(TAG, "Preferences reset to defaults")
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
