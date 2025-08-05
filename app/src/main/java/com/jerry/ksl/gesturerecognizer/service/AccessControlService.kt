package com.jerry.ksl.gesturerecognizer.service

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jerry.ksl.gesturerecognizer.model.User
import com.jerry.ksl.gesturerecognizer.model.UserRole
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Service for managing user access control and role-based permissions
 */
class AccessControlService {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "AccessControlService"
        private const val USERS_COLLECTION = "users"
    }

    /**
     * Get current user with role information
     */
    suspend fun getCurrentUser(): User? {
        return try {
            val currentUser = auth.currentUser ?: return null
            val userDoc = firestore.collection(USERS_COLLECTION)
                .document(currentUser.uid)
                .get()
                .await()

            if (userDoc.exists()) {
                val userData = userDoc.data ?: return null
                User.fromMap(userData)
            } else {
                // Create default user profile if none exists
                val newUser = User(
                    userId = currentUser.uid,
                    email = currentUser.email ?: "",
                    role = UserRole.SIGNER, // Default role
                    displayName = currentUser.displayName ?: ""
                )
                saveUserProfile(newUser)
                newUser
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current user", e)
            null
        }
    }

    /**
     * Save user profile to Firestore
     */
    suspend fun saveUserProfile(user: User): Boolean {
        return try {
            firestore.collection(USERS_COLLECTION)
                .document(user.userId)
                .set(user.toMap())
                .await()
            Log.d(TAG, "User profile saved successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user profile", e)
            false
        }
    }

    /**
     * Check if current user can access validation features
     */
    suspend fun canAccessValidation(): Boolean {
        val user = getCurrentUser()
        return user?.role?.canAccessValidation() ?: false
    }

    /**
     * Check if current user can submit training data
     */
    suspend fun canSubmitData(): Boolean {
        val user = getCurrentUser()
        return user?.role?.canSubmitData() ?: true // Default allow for backward compatibility
    }

    /**
     * Check if current user is a developer
     */
    suspend fun isDeveloper(): Boolean {
        val user = getCurrentUser()
        return user?.role?.isDeveloper() ?: false
    }

    /**
     * Update user role (only for developers)
     */
    suspend fun updateUserRole(userId: String, newRole: UserRole): Boolean {
        return try {
            // Check if current user is developer
            if (!isDeveloper()) {
                Log.w(TAG, "Unauthorized attempt to update user role")
                return false
            }

            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .update("role", newRole.name)
                .await()
            Log.d(TAG, "User role updated successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user role", e)
            false
        }
    }

    /**
     * Assign role to a user (creates user profile if it doesn't exist)
     */
    suspend fun assignRoleToUser(userId: String, role: UserRole): Boolean {
        return try {
            val currentUser = auth.currentUser
            val user = User(
                userId = userId,
                email = currentUser?.email ?: "",
                role = role,
                displayName = currentUser?.displayName ?: "",
                createdAt = Date(),
                lastLoginDate = Date(),
                isActive = true
            )
            saveUserProfile(user)
        } catch (e: Exception) {
            Log.e(TAG, "Error assigning role to user", e)
            false
        }
    }

    /**
     * Check if user can submit training data (callback-based for UI compatibility)
     */
    fun checkSubmissionAccess(userId: String, callback: (Boolean) -> Unit) {
        // For now, all authenticated users can submit data
        // This can be enhanced later if submission restrictions are needed
        auth.currentUser?.let { user ->
            callback(true) // All authenticated users can submit
        } ?: callback(false) // Not authenticated
    }

    /**
     * Get user by ID
     */
    suspend fun getUserById(userId: String): User? {
        return try {
            val userDoc = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .get()
                .await()

            if (userDoc.exists()) {
                val userData = userDoc.data ?: return null
                User.fromMap(userData)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user by ID", e)
            null
        }
    }
}
