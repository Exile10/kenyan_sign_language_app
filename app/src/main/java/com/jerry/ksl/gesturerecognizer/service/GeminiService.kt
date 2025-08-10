package com.jerry.ksl.gesturerecognizer.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Service for interacting with the Gemini API using direct HTTP requests
 */
class GeminiService(private val context: Context) {

    companion object {
        private const val TAG = "GeminiService"
        private const val GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"

        // Direct API key - using this directly since BuildConfig field isn't working
        private const val API_KEY = "INSERT_HERE"
    }

    // Initialize OkHttpClient for HTTP requests
    private val client = OkHttpClient()

    // JSON media type for the request
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Translate a sequence of recognized signs to natural language using Gemini API via HTTP
     * @param recognizedSigns List of recognized sign language gestures
     * @return Translated natural language text
     */
    suspend fun translateSignsToText(recognizedSigns: List<String>): String = withContext(Dispatchers.IO) {
        if (recognizedSigns.isEmpty()) return@withContext "No signs detected"

        val inputText = recognizedSigns.joinToString(" ")
        Log.d(TAG, "Sending to Gemini API: $inputText")

        // Create the prompt for Gemini
        val prompt = """
            Act as a sign language interpreter. The following words represent individual signs in Korean Sign Language (KSL) captured in sequence: 

            $inputText
            
            Please convert these individual signs into a natural, fluent sentence in English, maintaining the original meaning.
            Focus only on the translation, without explanations or additional text.
        """.trimIndent()

        try {
            // Build the request JSON
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.2)
                    put("topP", 1.0)
                    put("topK", 32)
                    put("maxOutputTokens", 1000)
                })
            }

            // Create the request body
            val requestBody = requestJson.toString().toRequestBody(jsonMediaType)

            // Build the HTTP request
            val request = Request.Builder()
                .url("$GEMINI_API_URL?key=$API_KEY")
                .post(requestBody)
                .build()

            // Execute the request and parse the response
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                throw IOException("API request failed: ${response.code} - ${response}")
            }

            // Parse the JSON response
            val responseBody = response.body?.string() ?: throw IOException("Empty response body")
            Log.d(TAG, "Raw API response: $responseBody")

            val jsonResponse = JSONObject(responseBody)

            // Extract the generated text from the response
            val candidates = jsonResponse.getJSONArray("candidates")
            if (candidates.length() > 0) {
                val content = candidates.getJSONObject(0).getJSONObject("content")
                val parts = content.getJSONArray("parts")

                // Find and return the first text part
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    if (part.has("text")) {
                        val translatedText = part.getString("text").trim()
                        Log.d(TAG, "Gemini translation: $translatedText")
                        return@withContext translatedText
                    }
                }
            }

            return@withContext "Translation error: Could not parse API response"

        } catch (e: Exception) {
            Log.e(TAG, "Error translating with Gemini API", e)
            return@withContext "Translation error: ${e.message ?: "Unknown error"}"
        }
    }
}
