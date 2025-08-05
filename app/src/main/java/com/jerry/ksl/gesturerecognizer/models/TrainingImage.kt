package com.jerry.ksl.gesturerecognizer.models

/**
 * Data class matching the TRAINING_IMAGES schema in Firestore
 */
data class TrainingImage(
    val id: String = "",
    val user_id: String = "",
    val image_url: String = "",
    val label: String = "",
    val validated: Boolean = false,
    val model_id: String = ""
)
