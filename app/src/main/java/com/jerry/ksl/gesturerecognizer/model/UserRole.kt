package com.jerry.ksl.gesturerecognizer.model

/**
 * Enum representing the different user roles in the KSL app
 */
enum class UserRole {
    /**
     * End users who use the app to translate sign language
     * Permissions: Access Home, Gallery, Profile management
     */
    SIGNER,

    /**
     * Maintain and update ML models, app functionality
     * Permissions: Full access including Validate and Submit Data features
     */
    DEVELOPER,

    /**
     * Quality assurance for ML model accuracy
     * Permissions: Access Validate feature, view submission data
     */
    VALIDATOR;

    companion object {
        /**
         * Convert string to UserRole, with fallback to SIGNER
         */
        fun fromString(role: String?): UserRole {
            return when (role?.uppercase()) {
                "DEVELOPER" -> DEVELOPER
                "VALIDATOR" -> VALIDATOR
                "SIGNER" -> SIGNER
                else -> SIGNER // Default role
            }
        }
    }

    /**
     * Check if this role has access to validation features
     */
    fun canAccessValidation(): Boolean {
        return this == DEVELOPER || this == VALIDATOR
    }

    /**
     * Check if this role has access to submit data features
     */
    fun canSubmitData(): Boolean {
        return true // All users can submit training data
    }

    /**
     * Check if this role has developer access
     */
    fun isDeveloper(): Boolean {
        return this == DEVELOPER
    }

    /**
     * Check if this role can validate models
     */
    fun canValidate(): Boolean {
        return this == DEVELOPER || this == VALIDATOR
    }
}
