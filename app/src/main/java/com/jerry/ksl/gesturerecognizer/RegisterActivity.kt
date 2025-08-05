package com.jerry.ksl.gesturerecognizer

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import android.text.TextWatcher
import android.text.Editable
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.jerry.ksl.gesturerecognizer.databinding.ActivityRegisterBinding
import com.jerry.ksl.gesturerecognizer.model.User
import com.jerry.ksl.gesturerecognizer.model.UserRole
import com.jerry.ksl.gesturerecognizer.service.AccessControlService

/**
 * Activity for user registration
 * Automatically assigns SIGNER role to new users
 */
class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private val accessControlService = AccessControlService()
    private var loadingDialog: AlertDialog? = null // Changed ProgressDialog to AlertDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase Auth
        firebaseAuth = FirebaseAuth.getInstance()

        setupListeners()

        binding.editTextPasswordRegister.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val password = s.toString()
                val strength = calculatePasswordStrength(password)
                updatePasswordStrengthUI(strength)
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun calculatePasswordStrength(password: String): Int {
        var strength = 0
        if (password.length >= 8) strength += 20
        if (password.matches(".*[A-Z].*".toRegex())) strength += 20
        if (password.matches(".*[a-z].*".toRegex())) strength += 20
        if (password.matches(".*[0-9].*".toRegex())) strength += 20
        if (password.matches(".*[!@#$%^&*()_+].*".toRegex())) strength += 20
        return strength.coerceAtMost(100)
    }

    private fun updatePasswordStrengthUI(strength: Int) {
        binding.progressBarPasswordStrength.progress = strength
        binding.textViewPasswordStrength.text = when {
            strength < 40 -> "Password Strength: Weak"
            strength < 70 -> "Password Strength: Medium"
            else -> "Password Strength: Strong"
        }

        val color = when {
            strength < 40 -> resources.getColor(android.R.color.holo_red_dark, theme)
            strength < 70 -> resources.getColor(android.R.color.holo_orange_dark, theme)
            else -> resources.getColor(android.R.color.holo_green_dark, theme)
        }
        binding.textViewPasswordStrength.setTextColor(color)
        binding.progressBarPasswordStrength.progressTintList = android.content.res.ColorStateList.valueOf(color)
    }

    private fun setupListeners() {
        // Handle register button click
        binding.buttonRegister.setOnClickListener {
            if (validateInputs()) {
                performRegistration()
            }
        }

        // Navigate to Login screen
        binding.textLogin.setOnClickListener {
            finish() // Simply finish this activity as the login should be underneath
        }

        // Handle terms and conditions click
        binding.textTerms.setOnClickListener {
            // Future implementation: Show terms and conditions
            Toast.makeText(this, "Terms & conditions will be displayed soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun validateInputs(): Boolean {
        var isValid = true
        val fullName = binding.editTextFullName.text.toString().trim()
        val email = binding.editTextEmailRegister.text.toString().trim()
        val phone = binding.editTextPhone.text.toString().trim()
        val password = binding.editTextPasswordRegister.text.toString().trim()
        val confirmPassword = binding.editTextConfirmPassword.text.toString().trim()

        // Validate full name
        if (fullName.isEmpty()) {
            binding.textInputFullName.error = "Full name is required"
            isValid = false
        } else {
            binding.textInputFullName.error = null
        }

        // Validate email
        if (email.isEmpty()) {
            binding.textInputEmailRegister.error = "Email is required"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.textInputEmailRegister.error = "Enter a valid email address"
            isValid = false
        } else {
            binding.textInputEmailRegister.error = null
        }

        // Validate phone (optional)
        if (phone.isNotEmpty() && !android.util.Patterns.PHONE.matcher(phone).matches()) {
            binding.textInputPhone.error = "Enter a valid phone number"
            isValid = false
        } else {
            binding.textInputPhone.error = null
        }

        // Validate password
        if (password.isEmpty()) {
            binding.textInputPasswordRegister.error = "Password is required"
            isValid = false
        } else if (password.length < 8) {
            binding.textInputPasswordRegister.error = "Password must be at least 8 characters"
            isValid = false
        } else if (!password.matches(".*[A-Za-z].*".toRegex()) || !password.matches(".*[0-9].*".toRegex())) {
            binding.textInputPasswordRegister.error = "Password must contain both letters and numbers"
            isValid = false
        } else {
            binding.textInputPasswordRegister.error = null
        }

        // Validate password confirmation
        if (confirmPassword.isEmpty()) {
            binding.textInputConfirmPassword.error = "Please confirm your password"
            isValid = false
        } else if (confirmPassword != password) {
            binding.textInputConfirmPassword.error = "Passwords do not match"
            isValid = false
        } else {
            binding.textInputConfirmPassword.error = null
        }

        // Check terms agreement
        if (!binding.checkboxTerms.isChecked) {
            Toast.makeText(this, "You must agree to the Terms and Conditions", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        return isValid
    }

    private fun performRegistration() {
        val email = binding.editTextEmailRegister.text.toString().trim()
        val password = binding.editTextPasswordRegister.text.toString().trim()
        val fullName = binding.editTextFullName.text.toString().trim()
        val selectedRoleId = binding.radioGroupRole.checkedRadioButtonId
        val userRole = if (selectedRoleId == binding.radioButtonValidator.id) {
            UserRole.VALIDATOR
        } else {
            UserRole.SIGNER
        }

        showLoadingDialog() // Show AlertDialog

        // Create user with email and password in Firebase
        firebaseAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Update user profile with full name
                    val user = firebaseAuth.currentUser
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(fullName)
                        .build()

                    user?.updateProfile(profileUpdates)
                        ?.addOnCompleteListener { profileTask ->
                            dismissLoadingDialog() // Dismiss AlertDialog

                            if (profileTask.isSuccessful) {
                                // Assign the selected role to the new user
                                assignUserRole(user.uid, userRole)

                                Toast.makeText(this, "Registration successful", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Registration successful, but could not update profile: ${profileTask.exception?.message}",
                                    Toast.LENGTH_LONG).show() // Changed to LENGTH_LONG for potentially longer message
                            }
                            // Proceed to MainActivity even if profile update failed, as account creation was successful
                            val intent = Intent(this, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        }
                } else {
                    dismissLoadingDialog() // Dismiss AlertDialog
                    // Handle registration failure
                    Toast.makeText(this, "Registration failed: ${task.exception?.message}",
                        Toast.LENGTH_SHORT).show()
                }
            }
    }

    // Method to show AlertDialog
    private fun showLoadingDialog() {
        if (loadingDialog == null) {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Registration")
            builder.setMessage("Creating your account...")
            builder.setCancelable(false)
            loadingDialog = builder.create()
        }
        loadingDialog?.show()
    }

    // Method to dismiss AlertDialog
    private fun dismissLoadingDialog() {
        loadingDialog?.dismiss()
    }

    // Method to assign role to user
    private fun assignUserRole(userId: String, role: UserRole) {
        lifecycleScope.launch {
            try {
                accessControlService.assignRoleToUser(userId, role)
            } catch (e: Exception) {
                // Handle potential errors here, e.g., log the error or show a message
                Toast.makeText(this@RegisterActivity, "Error assigning role: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
