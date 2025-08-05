package com.jerry.ksl.gesturerecognizer.service

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.jerry.ksl.gesturerecognizer.model.ValidationRecord
import kotlinx.coroutines.tasks.await

class ValidationService {

    private val db = FirebaseFirestore.getInstance()
    private val validationCollection = db.collection("MODEL_VALIDATIONS")

    suspend fun getValidationRecords(userId: String): List<ValidationRecord> {
        return try {
            val querySnapshot = validationCollection
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()
            val records = querySnapshot.documents.mapNotNull { document ->
                document.toObject(ValidationRecord::class.java)?.copy(id = document.id)
            }
            Log.d("ValidationService", "Fetched ${records.size} validation records for user $userId")
            records
        } catch (e: Exception) {
            Log.e("ValidationService", "Error fetching validation records", e)
            emptyList()
        }
    }

    suspend fun deleteValidationRecord(recordId: String): Boolean {
        return try {
            validationCollection.document(recordId).delete().await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}