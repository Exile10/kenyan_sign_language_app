package com.jerry.ksl.gesturerecognizer

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.jerry.ksl.gesturerecognizer.databinding.ActivityLoginBinding

/**
 * Activity for user login
 */
class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private var loadingDialog: AlertDialog? = null // Changed ProgressDialog to AlertDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase Auth
        firebaseAuth = FirebaseAuth.getInstance()

        // Check if user is already signed in
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            // User already signed in, go directly to MainActivity
            navigateToMainActivity()
            return
        }

        setupListeners()
    }

    private fun setupListeners() {
        // Handle login button click
        binding.buttonLogin.setOnClickListener {
            val email = binding.editTextEmail.text.toString().trim()
            val password = binding.editTextPassword.text.toString().trim()

            if (validateInputs(email, password)) {
                performLogin(email, password)
            }
        }

        // Handle Google sign-in
        binding.buttonGoogleSignin.setOnClickListener {
            // Future implementation: Set up Google sign-in
            Toast.makeText(this, "Google Sign-In will be implemented soon", Toast.LENGTH_SHORT).show()
        }

        // Navigate to Registration
        binding.textRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Handle forgot password
        binding.textForgotPassword.setOnClickListener {
            handleForgotPassword()
        }
    }

    private fun validateInputs(email: String, password: String): Boolean {
        var isValid = true

        // Validate email
        if (email.isEmpty()) {
            binding.textInputEmail.error = "Email is required"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.textInputEmail.error = "Enter a valid email address"
            isValid = false
        } else {
            binding.textInputEmail.error = null
        }

        // Validate password
        if (password.isEmpty()) {
            binding.textInputPassword.error = "Password is required"
            isValid = false
        } else if (password.length < 8) {
            binding.textInputPassword.error = "Password must be at least 8 characters"
            isValid = false
        } else {
            binding.textInputPassword.error = null
        }

        return isValid
    }

    private fun performLogin(email: String, password: String) {
        showLoadingDialog("Login", "Signing you in...") // Show AlertDialog

        // Authenticate with Firebase
        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                dismissLoadingDialog() // Dismiss AlertDialog

                if (task.isSuccessful) {
                    // Sign in success
                    Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
                    navigateToMainActivity()
                } else {
                    // If sign in fails, display a message to the user.
                    Toast.makeText(this, "Authentication failed: ${task.exception?.message}",
                        Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun handleForgotPassword() {
        val email = binding.editTextEmail.text.toString().trim()

        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.textInputEmail.error = "Enter a valid email address for password reset"
            return
        }

        showLoadingDialog("Password Reset", "Sending password reset email...") // Show AlertDialog

        firebaseAuth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                dismissLoadingDialog() // Dismiss AlertDialog

                if (task.isSuccessful) {
                    Toast.makeText(this, "Password reset email sent to $email",
                        Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Failed to send reset email: ${task.exception?.message}",
                        Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun navigateToMainActivity() {
        // Start main activity and clear the activity stack
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // Method to show AlertDialog
    private fun showLoadingDialog(title: String, message: String) {
        if (loadingDialog == null) {
            val builder = AlertDialog.Builder(this)
            builder.setCancelable(false)
            loadingDialog = builder.create()
        }
        loadingDialog?.setTitle(title)
        loadingDialog?.setMessage(message)
        loadingDialog?.show()
    }

    // Method to dismiss AlertDialog
    private fun dismissLoadingDialog() {
        loadingDialog?.dismiss()
    }
}
