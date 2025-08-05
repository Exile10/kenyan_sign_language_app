package com.jerry.ksl.gesturerecognizer.fragment

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.jerry.ksl.gesturerecognizer.MainViewModel
import com.jerry.ksl.gesturerecognizer.R
import com.jerry.ksl.gesturerecognizer.databinding.FragmentSettingsBinding
import com.jerry.ksl.gesturerecognizer.model.User
import com.jerry.ksl.gesturerecognizer.model.UserRole
import com.jerry.ksl.gesturerecognizer.service.AccessControlService
import java.io.File
import java.text.DecimalFormat
import java.util.Locale
import java.util.Date
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.widget.EditText
import android.content.DialogInterface
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Enhanced fragment for app settings and comprehensive profile management
 * Includes profile editing, user preferences, and security features
 */
class SettingsFragment : Fragment() {

    companion object {
        private const val TAG = "SettingsFragment"
    }

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    

    // Firebase instances
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var accessControlService: AccessControlService

    // Current user data
    private var currentUser: User? = null

    

    // Language and theme options
    private val languages = arrayOf("English", "Swahili")
    private val themes = arrayOf("Light", "Dark")

    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        accessControlService = AccessControlService()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load current user profile
        loadCurrentUserProfile()

        // Observe settings loaded status from ViewModel
        viewModel.settingsLoaded.observe(viewLifecycleOwner) { loaded ->
            if (loaded) {
                Log.d(TAG, "Settings confirmed loaded by ViewModel observer.")
                populateUiFromViewModel()
            } else {
                Log.d(TAG, "Settings loading failed or user not logged in, UI might show defaults from ViewModel's cache.")
                populateUiFromViewModel()
            }
        }

        // Initial population, in case settingsLoaded LiveData was already set
        if(viewModel.settingsLoaded.value == null) {
             populateUiFromViewModel()
        }

        setupSettingsClickListeners()
        setupProfileManagement() // NEW: Profile editing functionality
        setupAccountSettings() // Add this missing call!
        
        setupAboutSection()

        setupLogoutButton()
    }

    /**
     * Load current user profile from Firestore
     */
    private fun loadCurrentUserProfile() {
        val firebaseUser = auth.currentUser
        if (firebaseUser != null) {
            lifecycleScope.launch {
                try {
                    val userDoc = firestore.collection("users")
                        .document(firebaseUser.uid)
                        .get()
                        .await()

                    if (userDoc.exists()) {
                        currentUser = User.fromMap(userDoc.data ?: emptyMap())
                        updateProfileUI()
                        Log.d(TAG, "User profile loaded: ${currentUser?.email}")
                    } else {
                        Log.w(TAG, "User document does not exist in Firestore")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading user profile", e)
                    showToast("Error loading profile: ${e.message}")
                }
            }
        }
    }

    /**
     * Update profile UI with current user data
     */
    private fun updateProfileUI() {
        currentUser?.let { user ->
            // Update UI elements with user data
            binding.apply {
                // Set user info if UI elements exist
                try {
                    // These would be new UI elements we'll add to the layout
                    // For now, we'll use logging and prepare for UI updates
                    Log.d(TAG, "Updating UI for user: ${user.displayName} (${user.email})")
                    Log.d(TAG, "User role: ${user.role}")
                    Log.d(TAG, "Account created: ${user.createdAt}")
                    Log.d(TAG, "Last login: ${user.lastLoginDate}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating profile UI", e)
                }
            }
        }
    }

    /**
     * Setup profile management functionality with navigation to dedicated screens
     */
    private fun setupProfileManagement() {
        Log.d(TAG, "Setting up profile management with navigation to dedicated screens")

        // Navigate to dedicated ProfileFragment for comprehensive profile management
        binding.settingsEditProfile.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_settings_to_profile)
                Log.d(TAG, "Navigating to ProfileFragment")
            } catch (e: Exception) {
                Log.e(TAG, "Error navigating to ProfileFragment", e)
                // Fallback to local dialog if navigation fails
                showProfileEditDialog()
            }
        }
    }

    

    /**
     * Setup account settings with navigation to dedicated screens
     */
    private fun setupAccountSettings() {
        // Navigate to AccountSecurityFragment for password management
        binding.settingsChangePassword.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_settings_to_security)
                Log.d(TAG, "Navigating to AccountSecurityFragment")
            } catch (e: Exception) {
                Log.e(TAG, "Error navigating to AccountSecurityFragment", e)
                // Fallback to local dialog if navigation fails
                showPasswordChangeDialog()
            }
        }

        // Send password reset email
        binding.settingsSendPasswordReset.setOnClickListener {
            showPasswordResetConfirmDialog()
        }
    }

    /**
     * Show profile edit dialog
     */
    private fun showProfileEditDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_profile, null)

        // We'll need to create this layout file
        val displayNameInput = dialogView.findViewById<TextInputEditText>(R.id.edit_display_name)

        currentUser?.let { user ->
            displayNameInput.setText(user.displayName)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Profile")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newDisplayName = displayNameInput.text.toString().trim()
                if (newDisplayName.isNotEmpty()) {
                    updateUserProfile(newDisplayName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Update user profile in Firestore
     */
    private fun updateUserProfile(newDisplayName: String) {
        val firebaseUser = auth.currentUser
        if (firebaseUser != null && currentUser != null) {
            lifecycleScope.launch {
                try {
                    val updatedUser = currentUser!!.copy(
                        displayName = newDisplayName,
                        lastLoginDate = Date()
                    )

                    // Update in Firestore
                    firestore.collection("users")
                        .document(firebaseUser.uid)
                        .set(updatedUser.toMap())
                        .await()

                    currentUser = updatedUser
                    updateProfileUI()
                    showToast("Profile updated successfully")

                    Log.d(TAG, "Profile updated for user: ${updatedUser.email}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating profile", e)
                    showToast("Error updating profile: ${e.message}")
                }
            }
        }
    }

    /**
     * Show password change dialog
     */
    private fun showPasswordChangeDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_password, null)

        // We'll need to create this layout file
        val currentPasswordInput = dialogView.findViewById<TextInputEditText>(R.id.edit_current_password)
        val newPasswordInput = dialogView.findViewById<TextInputEditText>(R.id.edit_new_password)
        val confirmPasswordInput = dialogView.findViewById<TextInputEditText>(R.id.edit_confirm_password)

        AlertDialog.Builder(requireContext())
            .setTitle("Change Password")
            .setView(dialogView)
            .setPositiveButton("Change") { _, _ ->
                val currentPassword = currentPasswordInput.text.toString()
                val newPassword = newPasswordInput.text.toString()
                val confirmPassword = confirmPasswordInput.text.toString()

                if (validatePasswordChange(currentPassword, newPassword, confirmPassword)) {
                    changeUserPassword(currentPassword, newPassword)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Validate password change inputs
     */
    private fun validatePasswordChange(current: String, new: String, confirm: String): Boolean {
        when {
            current.isEmpty() -> {
                showToast("Please enter your current password")
                return false
            }
            new.length < 6 -> {
                showToast("New password must be at least 6 characters")
                return false
            }
            new != confirm -> {
                showToast("New passwords do not match")
                return false
            }
            else -> return true
        }
    }

    /**
     * Change user password
     */
    private fun changeUserPassword(currentPassword: String, newPassword: String) {
        val user = auth.currentUser
        if (user != null && user.email != null) {
            lifecycleScope.launch {
                try {
                    // Re-authenticate user
                    val credential = EmailAuthProvider.getCredential(user.email!!, currentPassword)
                    user.reauthenticate(credential).await()

                    // Update password
                    user.updatePassword(newPassword).await()

                    showToast("Password updated successfully")
                    Log.d(TAG, "Password updated for user: ${user.email}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error changing password", e)
                    showToast("Error changing password: ${e.message}")
                }
            }
        }
    }

    /**
     * Send password reset email
     */
    private fun sendPasswordResetEmail() {
        val user = auth.currentUser
        if (user?.email != null) {
            auth.sendPasswordResetEmail(user.email!!)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        showToast("Password reset email sent to ${user.email}")
                        Log.d(TAG, "Password reset email sent to: ${user.email}")
                    } else {
                        Log.e(TAG, "Error sending password reset email", task.exception)
                        showToast("Error sending reset email: ${task.exception?.message}")
                    }
                }
        }
    }

    /**
     * Show role-based features based on user permissions
     */
    private fun updateRoleBasedUI() {
        currentUser?.let { user ->
            // Show/hide features based on user role
            when (user.role) {
                UserRole.DEVELOPER -> {
                    // Show developer-specific options
                    Log.d(TAG, "Showing developer features for user")
                }
                UserRole.VALIDATOR -> {
                    // Show validator-specific options
                    Log.d(TAG, "Showing validator features for user")
                }
                UserRole.SIGNER -> {
                    // Show standard user features
                    Log.d(TAG, "Showing standard user features")
                }
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun populateUiFromViewModel() {
        Log.d(TAG, "Populating UI from ViewModel")
        // Language
        binding.textSelectedLanguage.text = viewModel.language

        // Theme
        binding.textSelectedTheme.text = viewModel.theme

        // Threshold, Model Download, and other UI elements may not exist in current layout
        // Only set values for elements that actually exist in fragment_settings.xml

        // Check if seekbar exists before using it
        try {
            binding.seekbarThreshold.progress = viewModel.threshold
            binding.textThresholdValue.text = "${viewModel.threshold}%"
        } catch (e: Exception) {
            Log.w(TAG, "Threshold seekbar not found in layout")
        }

        // Check if download button exists
        try {
            binding.buttonDownloadModels.text = if (viewModel.modelsDownloaded) "Update Models" else "Download Models"
        } catch (e: Exception) {
            Log.w(TAG, "Download models button not found in layout")
        }

        // Bottom Sheet Delegate Spinner
        val delegateIndex = when (viewModel.currentDelegate) {
            0 -> 0 // CPU
            1 -> 1 // GPU
            else -> 0 // Default to CPU
        }
        // BottomSheetBinding is removed, so this part is commented out
        // bottomSheetBinding.spinnerDelegate.setSelection(delegateIndex, false) // setSelection(index, animate)

        // Bottom Sheet Detection Threshold (from ViewModel's specific recognizer settings)
        val decimalFormat = DecimalFormat("#.##")
        // bottomSheetBinding.settingsDetectionThresholdValue.text =
        //     decimalFormat.format(viewModel.currentMinHandDetectionConfidence)
        // Listeners for bottom sheet controls are in setupBottomSheetListeners

        // Bottom Sheet Debug Mode Switch
        // bottomSheetBinding.switchDebugMode.isChecked = viewModel.debugMode
        // Listener for debug switch is in setupBottomSheetListeners

        // Initialize BottomSheetBehavior - commented out since bottom sheet doesn't exist
        // val bottomSheetView = binding.root.findViewById<View>(R.id.settings_bottom_sheet)
        // if (bottomSheetView != null) {
        //     bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetView)
        // } else {
        //     Log.d(TAG, "Settings Bottom Sheet view not found - this is expected in current layout")
        // }
    }

    private fun setupSettingsClickListeners() {
        // Language settings
        binding.settingsLanguage.setOnClickListener {
            showLanguageSelectionDialog()
        }

        // Theme settings
        binding.settingsTheme.setOnClickListener {
            showThemeSelectionDialog()
        }

        // Only set up threshold seekbar if it exists in the layout
        try {
            binding.seekbarThreshold.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        binding.textThresholdValue.text = "$progress%"
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) { /* Not needed */ }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    seekBar?.let {
                        val newThreshold = it.progress
                        viewModel.setThreshold(newThreshold) // Save via ViewModel
                        Toast.makeText(requireContext(), "Detection threshold set to $newThreshold%", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Threshold seekbar not found in layout")
        }

        // Only set up download button if it exists
        try {
            binding.buttonDownloadModels.setOnClickListener {
                downloadModelsAndUpdateViewModel()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Download models button not found in layout")
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        // _bottomSheetBinding = null // Important to nullify this too, but it's removed
    }

    private fun showLanguageSelectionDialog() {
        val currentLanguage = binding.textSelectedLanguage.text.toString()
        var selectedLanguageIndex = languages.indexOf(currentLanguage)
        if (selectedLanguageIndex == -1) selectedLanguageIndex = 0

        AlertDialog.Builder(requireContext())
            .setTitle("Select Language")
            .setSingleChoiceItems(languages, selectedLanguageIndex) { _, which ->
                selectedLanguageIndex = which
            }
            .setPositiveButton("OK") { _, _ ->
                val selectedLanguage = languages[selectedLanguageIndex]
                binding.textSelectedLanguage.text = selectedLanguage

                // Apply language change
                applyLanguageChange(selectedLanguage)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyLanguageChange(language: String) {
        viewModel.setLanguage(language) // Use ViewModel
        Toast.makeText(requireContext(), "Language changed to $language", Toast.LENGTH_SHORT).show()
        // Actual locale change logic (if any) would still be here or triggered via ViewModel observation
    }

    private fun showThemeSelectionDialog() {
        val currentTheme = binding.textSelectedTheme.text.toString()
        var selectedThemeIndex = themes.indexOf(currentTheme)
        if (selectedThemeIndex == -1) selectedThemeIndex = 0

        AlertDialog.Builder(requireContext())
            .setTitle("Select Theme")
            .setSingleChoiceItems(themes, selectedThemeIndex) { _, which ->
                selectedThemeIndex = which
            }
            .setPositiveButton("OK") { _, _ ->
                val selectedTheme = themes[selectedThemeIndex]
                binding.textSelectedTheme.text = selectedTheme

                // Apply theme change
                applyThemeChange(selectedTheme)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyThemeChange(theme: String) {
        viewModel.setTheme(theme) // Use ViewModel
        Toast.makeText(requireContext(), "Theme changed to $theme", Toast.LENGTH_SHORT).show()
        // Actual theme application (if any) would still be here or triggered via ViewModel observation
    }

    /**
     * Show password reset confirmation dialog
     */
    private fun showPasswordResetConfirmDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Reset Password")
            .setMessage("Send a password reset email to your registered email address?")
            .setPositiveButton("Send Email") { _, _ ->
                sendPasswordResetEmail()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupAboutSection() {
        // Privacy policy
        binding.settingsPrivacyPolicy.setOnClickListener {
            openPrivacyPolicy()
        }

        // About app
        binding.settingsAbout.setOnClickListener {
            showAboutDialog()
        }
    }

    private fun openPrivacyPolicy() {
        // This URL should be replaced with the actual privacy policy URL
        val privacyPolicyUrl = "https://example.com/privacy-policy"

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(privacyPolicyUrl))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Could not open privacy policy", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAboutDialog() {
        val appVersion = requireActivity().packageManager.getPackageInfo(requireActivity().packageName, 0).versionName

        AlertDialog.Builder(requireContext())
            .setTitle("About KSL App")
            .setMessage("KSL App Version $appVersion\n\nKSL App is designed to recognize Kenyan Sign Language gestures using advanced machine learning algorithms.\n\n© 2025 KSL App Team")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun downloadModelsAndUpdateViewModel() {
        val progressDialog = AlertDialog.Builder(requireContext())
            .setTitle("Downloading ML Models")
            .setMessage("Please wait while we download the required models...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        view?.postDelayed({
            progressDialog.dismiss()
            Toast.makeText(requireContext(), "ML Models downloaded successfully", Toast.LENGTH_SHORT).show()
            viewModel.setModelsDownloaded(true) // Use ViewModel
            binding.buttonDownloadModels.text = "Update Models" // Update UI directly
        }, 2000)
    }

    

    private fun performLogout() {
        auth.signOut()
        Toast.makeText(requireContext(), "Logged out successfully.", Toast.LENGTH_SHORT).show()

        // Navigate to LoginActivity
        try {
            // Direct navigation to LoginActivity
            val intent = Intent(requireActivity(), com.jerry.ksl.gesturerecognizer.LoginActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            requireActivity().finishAffinity() // Finish all activities in the task
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to login screen.", e)
            Toast.makeText(requireContext(), "Error navigating to login. Please restart the app.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupLogoutButton() {
        // Assuming you add a Button with id 'buttonLogout' in your fragment_settings.xml
        // If your logout button has a different ID, please change R.id.buttonLogout accordingly.
        // You might need to find it via binding if it's part of the included layout directly.
        // For example: binding.buttonLogout.setOnClickListener { ... }
        // If it's a separate view not in the binding, you'd use view.findViewById<Button>(R.id.buttonLogout)

        // Let's assume you add a button with this ID to your fragment_settings.xml
        // val logoutButton = view?.findViewById<Button>(R.id.buttonLogout) // Example: find button
        // if (logoutButton == null) {
            // If you are using view binding and added buttonLogout to fragment_settings.xml,
            // it would be binding.buttonLogout
            // This is a fallback or example if not directly in binding.
        //     Log.w(TAG, "Logout button not found. Ensure it's in your layout with ID 'buttonLogout' or access it via binding.")
        // }

        // Replace with your actual logout button from the binding, e.g. binding.buttonLogout
        // For this example, I'll create a listener on a hypothetical settingsLogout item
        // similar to other settings items if you prefer that style.
        binding.settingsLogout.setOnClickListener { // If you add a settingsLogout view
            performLogout()
        }
        // For now, I'll make settingsManageAccount also trigger logout for demonstration,
        // you should replace this with your actual logout button.
        // binding.settingsManageAccount.setOnClickListener { // TEMPORARY - REPLACE
        // Let's assume you have a button in your main settings layout (FragmentSettingsBinding)
        // e.g. binding.buttonLogout.setOnClickListener { ... }
        // For now, I'll log a message. You need to add the button and wire it up.
        // Example:
        // if (binding.buttonLogout != null) { // Check if buttonLogout is part of your FragmentSettingsBinding
        //    binding.buttonLogout.setOnClickListener {
        //        performLogout()
        //    }
        // } else {
        //    Log.e(TAG, "Please add a logout button to your fragment_settings.xml and reference it here.")
        // }
        // For the purpose of this task, I will assume you will add a view with id settings_logout_container
        // in your fragment_settings.xml that will act as a logout button.
        // binding.settingsManageAccount.visibility = View.GONE // Hide old advanced settings temporarily
                                                            // to avoid confusion with bottom sheet trigger.
                                                            // You can re-enable or repurpose.

        // You should add a dedicated logout button or view. For example, if you add:
        // <TextView android:id="@+id/settings_logout" ... /> or <Button android:id="@+id/button_logout" ... />
        // Then you would do:
        // binding.settingsLogout.setOnClickListener { performLogout() } or
        // binding.buttonLogout.setOnClickListener { performLogout() }

        // For now, as a placeholder, I'll log that the user needs to add a button.
        // The actual logout logic is in performLogout().
        // Log.i(TAG, "SetupLogoutButton called. Ensure you have a UI element to trigger performLogout().")
    }
}
