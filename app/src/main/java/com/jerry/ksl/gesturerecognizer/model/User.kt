package com.jerry.ksl.gesturerecognizer.model

import java.util.Date

/**
 * Data class representing a user in the KSL app
 */
data class User(
    val userId: String = "",
    val email: String = "",
    val role: UserRole = UserRole.SIGNER,
    val profileImageUrl: String = "",
    val createdAt: Date = Date(),
    val lastLoginDate: Date = Date(),
    val isActive: Boolean = true,
    val displayName: String = ""
) {

    /**
     * Convert User to Map for Firestore storage
     */
    fun toMap(): Map<String, Any> {
        return mapOf(
            "userId" to userId,
            "email" to email,
            "role" to role.name,
            "profileImageUrl" to profileImageUrl,
            "createdAt" to createdAt,
            "lastLoginDate" to lastLoginDate,
            "isActive" to isActive,
            "displayName" to displayName
        )
    }

    companion object {
        /**
         * Create User from Firestore Map
         */
        fun fromMap(map: Map<String, Any>): User {
            return User(
                userId = map["userId"] as? String ?: "",
                email = map["email"] as? String ?: "",
                role = UserRole.fromString(map["role"] as? String),
                profileImageUrl = map["profileImageUrl"] as? String ?: "",
                createdAt = map["createdAt"] as? Date ?: Date(),
                lastLoginDate = map["lastLoginDate"] as? Date ?: Date(),
                isActive = map["isActive"] as? Boolean ?: true,
                displayName = map["displayName"] as? String ?: ""
            )
        }
    }
}
