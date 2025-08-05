package com.jerry.ksl.gesturerecognizer.model

import com.google.firebase.Timestamp

data class TrainingImageItem(
    val id: String = "", // Firestore document ID
    val userId: String = "",
    val imageUrl: String = "",
    val label: String = "",
    val validated: Boolean = false,
    val modelId: String? = null,
    val timestamp: Timestamp? = null
)

