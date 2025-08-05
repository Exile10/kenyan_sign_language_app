package com.jerry.ksl.gesturerecognizer.fragment

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jerry.ksl.gesturerecognizer.R
import com.jerry.ksl.gesturerecognizer.databinding.FragmentProfileBinding
import com.jerry.ksl.gesturerecognizer.model.User
import com.jerry.ksl.gesturerecognizer.model.UserRole
import com.jerry.ksl.gesturerecognizer.service.AccessControlService
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

/**
 * Dedicated fragment for comprehensive profile management
 * Provides detailed user information and profile editing capabilities
 */
class ProfileFragment : Fragment() {

    companion object {
        private const val TAG = "ProfileFragment"
    }

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var accessControlService: AccessControlService

    private var currentUser: User? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        accessControlService = AccessControlService()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadUserProfile()
        setupClickListeners()
    }

    /**
     * Load current user profile from Firebase
     */
    private fun loadUserProfile() {
        val firebaseUser = auth.currentUser
        if (firebaseUser != null) {
            lifecycleScope.launch {
                try {
                    binding.progressLoading.visibility = View.VISIBLE
                    binding.layoutProfileContent.visibility = View.GONE

                    val userDoc = firestore.collection("users")
                        .document(firebaseUser.uid)
                        .get()
                        .await()

                    if (userDoc.exists()) {
                        currentUser = User.fromMap(userDoc.data ?: emptyMap())
                        updateProfileUI()
                        Log.d(TAG, "User profile loaded successfully")
                    } else {
                        showError("User profile not found")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading user profile", e)
                    showError("Failed to load profile: ${e.message}")
                } finally {
                    binding.progressLoading.visibility = View.GONE
                    binding.layoutProfileContent.visibility = View.VISIBLE
                }
            }
        } else {
            showError("User not authenticated")
        }
    }

    /**
     * Update UI with current user profile data
     */
    private fun updateProfileUI() {
        currentUser?.let { user ->
            binding.apply {
                // Basic profile information
                textDisplayName.text = user.displayName.ifEmpty { "No display name set" }
                textEmail.text = user.email
                chipUserRole.text = user.role.name.lowercase().replaceFirstChar { it.uppercase() }

                // Account details
                val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                textAccountCreated.text = "Account created: ${dateFormat.format(user.createdAt)}"
                textLastLogin.text = "Last login: ${dateFormat.format(user.lastLoginDate)}"

                // Role-specific information
                when (user.role) {
                    UserRole.DEVELOPER -> {
                        textRoleDescription.text = "Full access to all features including model management and validation tools"
                        chipUserRole.setChipBackgroundColorResource(R.color.mp_color_primary)
                        radioGroupRoleProfile.visibility = View.GONE
                        buttonSaveRole.visibility = View.GONE
                    }
                    UserRole.VALIDATOR -> {
                        textRoleDescription.text = "Access to model validation tools and quality assurance features"
                        chipUserRole.setChipBackgroundColorResource(R.color.mp_color_accent)
                        radioButtonValidatorProfile.isChecked = true
                    }
                    UserRole.SIGNER -> {
                        textRoleDescription.text = "Access to core translation features and personal gallery"
                        chipUserRole.setChipBackgroundColorResource(R.color.mp_color_secondary)
                        radioButtonSignerProfile.isChecked = true
                    }
                }

                // Account status
                textAccountStatus.text = if (user.isActive) "Active" else "Inactive"
                textAccountStatus.setTextColor(
                    if (user.isActive)
                        resources.getColor(android.R.color.holo_green_dark, null)
                    else
                        resources.getColor(android.R.color.holo_red_dark, null)
                )
            }
        }
    }

    /**
     * Setup click listeners for profile actions
     */
    private fun setupClickListeners() {
        binding.apply {
            // Edit profile button
            buttonEditProfile.setOnClickListener {
                showEditProfileDialog()
            }

            // Save role button
            buttonSaveRole.setOnClickListener {
                val selectedRoleId = radioGroupRoleProfile.checkedRadioButtonId
                val newRole = if (selectedRoleId == radioButtonValidatorProfile.id) {
                    UserRole.VALIDATOR
                } else {
                    UserRole.SIGNER
                }
                updateUserRole(newRole)
            }

            // Account preferences
            cardPreferences.setOnClickListener {
                // For now, show a toast since navigation isn't set up
                showToast("User preferences feature coming soon")
            }

            // Account security
            cardSecurity.setOnClickListener {
                // For now, show a toast since navigation isn't set up
                showToast("Account security feature coming soon")
            }

            // Sign out
            buttonSignOut.setOnClickListener {
                showSignOutConfirmation()
            }

            // Delete account
            buttonDeleteAccount.setOnClickListener {
                showDeleteAccountConfirmation()
            }
        }
    }

    /**
     * Show edit profile dialog
     */
    private fun showEditProfileDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_profile, null)
        val displayNameInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_display_name)

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
                } else {
                    showToast("Display name cannot be empty")
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
                    binding.progressLoading.visibility = View.VISIBLE

                    val updatedUser = currentUser!!.copy(
                        displayName = newDisplayName,
                        lastLoginDate = Date()
                    )

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
                } finally {
                    binding.progressLoading.visibility = View.GONE
                }
            }
        }
    }

    private fun updateUserRole(newRole: UserRole) {
        val firebaseUser = auth.currentUser
        if (firebaseUser != null && currentUser != null) {
            if (currentUser!!.role == newRole) {
                showToast("You already have this role.")
                return
            }

            lifecycleScope.launch {
                try {
                    binding.progressLoading.visibility = View.VISIBLE

                    val updatedUser = currentUser!!.copy(role = newRole)

                    firestore.collection("users")
                        .document(firebaseUser.uid)
                        .set(updatedUser.toMap())
                        .await()

                    currentUser = updatedUser
                    updateProfileUI()
                    showToast("Role updated successfully")

                    Log.d(TAG, "Role updated for user: ${updatedUser.email} to ${newRole.name}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating role", e)
                    showToast("Error updating role: ${e.message}")
                } finally {
                    binding.progressLoading.visibility = View.GONE
                }
            }
        }
    }

    /**
     * Show sign out confirmation dialog
     */
    private fun showSignOutConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Sign Out")
            .setMessage("Are you sure you want to sign out?")
            .setPositiveButton("Sign Out") { _, _ ->
                performSignOut()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Perform user sign out
     */
    private fun performSignOut() {
        auth.signOut()
        showToast("Signed out successfully")

        // Navigate to LoginActivity
        try {
            val intent = Intent(requireActivity(), com.jerry.ksl.gesturerecognizer.LoginActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            requireActivity().finishAffinity()
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to login screen", e)
            showToast("Please restart the app to complete sign out")
        }
    }

    /**
     * Show delete account confirmation dialog
     */
    private fun showDeleteAccountConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Account")
            .setMessage("This action cannot be undone. All your data will be permanently deleted. Are you sure?")
            .setPositiveButton("Delete") { _, _ ->
                showFinalDeleteConfirmation()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Show final delete account confirmation
     */
    private fun showFinalDeleteConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Final Confirmation")
            .setMessage("This action cannot be undone. Type 'DELETE' to confirm account deletion.\n\nAccount deletion functionality will be implemented in a future update.")
            .setPositiveButton("Delete Account") { _, _ ->
                //  Implement account deletion functionality
                showToast("Account deletion feature will be implemented")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun showError(message: String) {
        Log.e(TAG, message)
        showToast(message)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
