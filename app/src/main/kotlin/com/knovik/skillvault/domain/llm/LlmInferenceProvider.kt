package com.knovik.skillvault.domain.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for on-device LLM inference using MediaPipe and Gemma 2.
 */
@Singleton
class LlmInferenceProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var llmInference: LlmInference? = null
    private val modelFileName = "gemma-2b-it-cpu-int4.bin"

    /**
     * Initialize the LLM engine.
     * This is a heavy operation and should be done in background.
     * 
     * @return Result indicating success or failure
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (llmInference != null) return@withContext Result.success(Unit)

            // 1. Check if model exists in filesDir (where user pushed it)
            val sourceFile = File(context.filesDir, modelFileName)
            
            if (!sourceFile.exists()) {
                 return@withContext Result.failure(
                    IllegalStateException("Model file not found. Please push '$modelFileName' to ${context.filesDir.absolutePath}")
                )
            }

            // 2. Ensure a writable cache directory exists for the engine to use for its internal artifacts
            // We don't move the model there, we just make sure the dir exists.
            val cacheDir = File(context.cacheDir, "llm_cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            // 3. Clean up any stale cache artifacts that might have bad permissions from previous runs
            val cacheArtifact = File(context.cacheDir, "$modelFileName.cache")
            if (cacheArtifact.exists()) {
                try {
                    cacheArtifact.delete()
                } catch (e: Exception) {
                    Timber.w("Could not delete cache artifact: ${e.message}")
                }
            }

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(sourceFile.absolutePath) // Point directly to filesDir
                .setMaxTokens(256)
                .setTopK(40)
                .setTemperature(0.7f)
                .setRandomSeed(42)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            Timber.d("LLM Inference initialized successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize LLM Inference")
            Result.failure(e)
        }
    }

    /**
     * Generate a response for the given prompt.
     * 
     * @param prompt The input prompt
     * @return The generated text response
     */
    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            if (llmInference == null) {
                val initResult = initialize()
                if (initResult.isFailure) throw initResult.exceptionOrNull()!!
            }
            
            llmInference?.generateResponse(prompt) ?: throw IllegalStateException("LLM not initialized")
        } catch (e: Exception) {
            Timber.e(e, "LLM generation failed")
            throw e
        }
    }

    /**
     * Check if the model file exists on device.
     */
    fun isModelAvailable(): Boolean {
        return File(context.filesDir, modelFileName).exists()
    }
    
    fun getModelPath(): String {
        return File(context.filesDir, modelFileName).absolutePath
    }
}
