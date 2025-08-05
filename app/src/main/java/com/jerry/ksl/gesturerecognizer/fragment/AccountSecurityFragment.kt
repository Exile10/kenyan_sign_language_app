package com.jerry.ksl.gesturerecognizer.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jerry.ksl.gesturerecognizer.R
import com.jerry.ksl.gesturerecognizer.databinding.FragmentAccountSecurityBinding
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

/**
 * Fragment for managing account security features
 * Includes password management, session control, and security monitoring
 */
class AccountSecurityFragment : Fragment() {

    companion object {
        private const val TAG = "AccountSecurityFragment"
    }

    private var _binding: FragmentAccountSecurityBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private var lastPasswordChange: Date? = null
    private var activeSessions: Int = 1
    private var twoFactorEnabled: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountSecurityBinding.inflate(inflater, container, false)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadSecurityInfo()
        setupSecurityActions()
    }

    /**
     * Load security information from Firebase
     */
    private fun loadSecurityInfo() {
        val firebaseUser = auth.currentUser
        if (firebaseUser != null) {
            lifecycleScope.launch {
                try {
                    binding.progressLoading.visibility = View.VISIBLE

                    // Load security info from Firestore
                    val securityDoc = firestore.collection("user_security")
                        .document(firebaseUser.uid)
                        .get()
                        .await()

                    if (securityDoc.exists()) {
                        val data = securityDoc.data ?: emptyMap()

                        lastPasswordChange = (data["last_password_change"] as? com.google.firebase.Timestamp)?.toDate()
                        activeSessions = (data["active_sessions"] as? Long)?.toInt() ?: 1
                        twoFactorEnabled = data["two_factor_enabled"] as? Boolean ?: false
                    }

                    updateSecurityUI()
                    Log.d(TAG, "Security information loaded successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading security information", e)
                    showToast("Failed to load security info: ${e.message}")
                    updateSecurityUI() // Show defaults
                } finally {
                    binding.progressLoading.visibility = View.GONE
                }
            }
        }
    }

    /**
     * Update UI with current security information
     */
    private fun updateSecurityUI() {
        binding.apply {
            // Account email
            textAccountEmail.text = auth.currentUser?.email ?: "Not available"

            // Last password change
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            textLastPasswordChange.text = lastPasswordChange?.let {
                "Last changed: ${dateFormat.format(it)}"
            } ?: "Never changed"

            // Password strength indicator
            updatePasswordStrengthIndicator()

            // Active sessions
            textActiveSessions.text = "$activeSessions active session${if (activeSessions != 1) "s" else ""}"

            // Two-factor authentication status
            binding.switch2fa.isChecked = twoFactorEnabled
            binding.text2faStatus.text = if (twoFactorEnabled) "Enabled" else "Disabled"
            binding.text2faStatus.setTextColor(
                if (twoFactorEnabled)
                    resources.getColor(android.R.color.holo_green_dark, null)
                else
                    resources.getColor(android.R.color.holo_orange_dark, null)
            )

            // Account verification status
            val isEmailVerified = auth.currentUser?.isEmailVerified == true
            textEmailVerification.text = if (isEmailVerified) "Verified" else "Not Verified"
            textEmailVerification.setTextColor(
                if (isEmailVerified)
                    resources.getColor(android.R.color.holo_green_dark, null)
                else
                    resources.getColor(android.R.color.holo_red_dark, null)
            )

            buttonVerifyEmail.visibility = if (isEmailVerified) View.GONE else View.VISIBLE
        }
    }

    /**
     * Update password strength indicator
     */
    private fun updatePasswordStrengthIndicator() {
        // Calculate days since last password change
        val daysSinceChange = lastPasswordChange?.let {
            val diff = Date().time - it.time
            (diff / (1000 * 60 * 60 * 24)).toInt()
        } ?: Int.MAX_VALUE

        binding.apply {
            when {
                daysSinceChange > 365 -> {
                    textPasswordStrength.text = "Weak (Very Old)"
                    textPasswordStrength.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
                    progressPasswordStrength.progress = 25
                }
                daysSinceChange > 180 -> {
                    textPasswordStrength.text = "Fair (Old)"
                    textPasswordStrength.setTextColor(resources.getColor(android.R.color.holo_orange_dark, null))
                    progressPasswordStrength.progress = 50
                }
                daysSinceChange > 90 -> {
                    textPasswordStrength.text = "Good"
                    textPasswordStrength.setTextColor(resources.getColor(android.R.color.holo_blue_dark, null))
                    progressPasswordStrength.progress = 75
                }
                else -> {
                    textPasswordStrength.text = "Strong (Recent)"
                    textPasswordStrength.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
                    progressPasswordStrength.progress = 100
                }
            }
        }
    }

    /**
     * Setup security action listeners
     */
    private fun setupSecurityActions() {
        binding.apply {
            // Change password
            buttonChangePassword.setOnClickListener {
                showChangePasswordDialog()
            }

            // Send password reset email
            buttonPasswordReset.setOnClickListener {
                showPasswordResetConfirmation()
            }

            // Two-factor authentication toggle
            switch2fa.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked && !twoFactorEnabled) {
                    showToast("Two-factor authentication setup coming soon")
                    switch2fa.isChecked = false // Revert for now
                } else if (!isChecked && twoFactorEnabled) {
                    showDisable2FAConfirmation()
                }
            }

            // Verify email
            buttonVerifyEmail.setOnClickListener {
                sendEmailVerification()
            }

            // View active sessions
            buttonViewSessions.setOnClickListener {
                showActiveSessionsDialog()
            }

            // Security checkup
            buttonSecurityCheckup.setOnClickListener {
                performSecurityCheckup()
            }

            // Download security report
            buttonDownloadReport.setOnClickListener {
                showToast("Security report download coming soon")
            }
        }
    }

    /**
     * Show change password dialog
     */
    private fun showChangePasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_password, null)
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
            new.length < 8 -> {
                showToast("New password must be at least 8 characters")
                return false
            }
            !new.matches(Regex(".*[A-Z].*")) -> {
                showToast("Password must contain at least one uppercase letter")
                return false
            }
            !new.matches(Regex(".*[0-9].*")) -> {
                showToast("Password must contain at least one number")
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
     * Change user password with enhanced security
     */
    private fun changeUserPassword(currentPassword: String, newPassword: String) {
        val user = auth.currentUser
        if (user != null && user.email != null) {
            lifecycleScope.launch {
                try {
                    binding.progressLoading.visibility = View.VISIBLE

                    // Re-authenticate user
                    val credential = EmailAuthProvider.getCredential(user.email!!, currentPassword)
                    user.reauthenticate(credential).await()

                    // Update password
                    user.updatePassword(newPassword).await()

                    // Update security information
                    updateSecurityInfo(mapOf(
                        "last_password_change" to Date(),
                        "password_change_count" to com.google.firebase.firestore.FieldValue.increment(1)
                    ))

                    lastPasswordChange = Date()
                    updateSecurityUI()

                    showToast("Password updated successfully")
                    Log.d(TAG, "Password updated for user: ${user.email}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error changing password", e)
                    showToast("Error changing password: ${e.message}")
                } finally {
                    binding.progressLoading.visibility = View.GONE
                }
            }
        }
    }

    /**
     * Show password reset confirmation
     */
    private fun showPasswordResetConfirmation() {
        val email = auth.currentUser?.email
        if (email != null) {
            AlertDialog.Builder(requireContext())
                .setTitle("Reset Password")
                .setMessage("Send a password reset email to $email?")
                .setPositiveButton("Send Email") { _, _ ->
                    sendPasswordResetEmail()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    /**
     * Send password reset email
     */
    private fun sendPasswordResetEmail() {
        val email = auth.currentUser?.email
        if (email != null) {
            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        showToast("Password reset email sent to $email")
                        Log.d(TAG, "Password reset email sent to: $email")
                    } else {
                        Log.e(TAG, "Error sending password reset email", task.exception)
                        showToast("Error sending reset email: ${task.exception?.message}")
                    }
                }
        }
    }

    /**
     * Send email verification
     */
    private fun sendEmailVerification() {
        val user = auth.currentUser
        if (user != null) {
            user.sendEmailVerification()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        showToast("Verification email sent")
                        Log.d(TAG, "Email verification sent")
                    } else {
                        Log.e(TAG, "Error sending verification email", task.exception)
                        showToast("Error sending verification email: ${task.exception?.message}")
                    }
                }
        }
    }

    /**
     * Show disable 2FA confirmation
     */
    private fun showDisable2FAConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Disable Two-Factor Authentication")
            .setMessage("This will make your account less secure. Are you sure?")
            .setPositiveButton("Disable") { _, _ ->
//                 Implement 2FA disable
                showToast("Two-factor authentication disabled")
                twoFactorEnabled = false
                updateSecurityUI()
            }
            .setNegativeButton("Cancel") { _, _ ->
                binding.switch2fa.isChecked = true // Revert
            }
            .show()
    }

    /**
     * Show active sessions dialog
     */
    private fun showActiveSessionsDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Active Sessions")
            .setMessage("Current device: Android App\nSessions: $activeSessions\n\nOther sessions management coming soon.")
            .setPositiveButton("OK", null)
            .show()
    }

    /**
     * Perform security checkup
     */
    private fun performSecurityCheckup() {
        lifecycleScope.launch {
            try {
                binding.progressLoading.visibility = View.VISIBLE

                val issues = mutableListOf<String>()

                // Check email verification
                if (auth.currentUser?.isEmailVerified != true) {
                    issues.add("Email not verified")
                }

                // Check password age
                val daysSinceChange = lastPasswordChange?.let {
                    val diff = Date().time - it.time
                    (diff / (1000 * 60 * 60 * 24)).toInt()
                } ?: Int.MAX_VALUE

                if (daysSinceChange > 180) {
                    issues.add("Password is old (${daysSinceChange} days)")
                }

                // Check 2FA
                if (!twoFactorEnabled) {
                    issues.add("Two-factor authentication disabled")
                }

                val message = if (issues.isEmpty()) {
                    "✅ Your account security looks good!"
                } else {
                    "⚠️ Security issues found:\n\n" + issues.joinToString("\n• ", "• ")
                }

                AlertDialog.Builder(requireContext())
                    .setTitle("Security Checkup")
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show()

            } catch (e: Exception) {
                Log.e(TAG, "Error performing security checkup", e)
                showToast("Error performing checkup: ${e.message}")
            } finally {
                binding.progressLoading.visibility = View.GONE
            }
        }
    }

    /**
     * Update security information in Firestore
     */
    private fun updateSecurityInfo(updates: Map<String, Any>) {
        val firebaseUser = auth.currentUser
        if (firebaseUser != null) {
            lifecycleScope.launch {
                try {
                    firestore.collection("user_security")
                        .document(firebaseUser.uid)
                        .update(updates)
                        .await()

                    Log.d(TAG, "Security information updated")
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating security information", e)
                }
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
