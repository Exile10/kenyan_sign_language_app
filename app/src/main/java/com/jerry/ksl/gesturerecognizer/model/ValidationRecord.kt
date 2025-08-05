package com.jerry.ksl.gesturerecognizer.model

import com.google.firebase.Timestamp

data class ValidationRecord(
    val id: String = "", // Firestore document ID
    val userId: String = "",
    val imageUrl: String = "",
    val expectedLabel: String = "", // The sign the user was trying to make
    val predictedLabel: String = "", // What the model predicted
    val confidenceScore: Float = 0f, // Confidence level of the prediction
    val inferenceTimeMs: Long = 0, // How long it took to process
    val isCorrect: Boolean = false, // Whether the prediction matched the expected label
    val modelVersion: String = "", // Version of the model used
    val deviceInfo: Map<String, Any> = mapOf(), // Device-specific information
    val timestamp: Timestamp? = null
)
