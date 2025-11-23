package com.knovik.skillvault.domain.embedding

import android.content.Context
import com.google.mediapipe.tasks.components.containers.EmbeddingResult
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wrapper for MediaPipe Text Embedder.
 * Handles on-device text embedding generation for semantic search.
 * 
 * This is the core "Brain" component for SkillVault.
 */
@Singleton
class MediaPipeEmbeddingProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var textEmbedder: TextEmbedder? = null
    private val lock = Any()

    companion object {
        private const val MODEL_ASSET_PATH = "text_embedder.tflite"
        private const val EMBEDDING_DIMENSION = 512
    }

    /**
     * Initialize the MediaPipe TextEmbedder.
     * Must be called before using embeddings.
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            synchronized(lock) {
                if (textEmbedder != null) {
                    Timber.d("TextEmbedder already initialized")
                    return@withContext Result.success(Unit)
                }

                val options = TextEmbedder.TextEmbedderOptions.builder()
                    .setBaseOptions(
                        com.google.mediapipe.tasks.core.BaseOptions.builder()
                            .setModelAssetPath(MODEL_ASSET_PATH)
                            .build()
                    )
                    .build()

                textEmbedder = TextEmbedder.createFromOptions(context, options)
                Timber.d("TextEmbedder initialized successfully")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize TextEmbedder")
            Result.failure(e)
        }
    }

    /**
     * Generate embedding for a single text segment.
     *
     * @param text The text to embed
     * @return FloatArray embedding vector or empty array on error
     */
    suspend fun embedText(text: String): Result<FloatArray> = withContext(Dispatchers.Default) {
        return@withContext try {
            val embedder = synchronized(lock) {
                textEmbedder ?: throw IllegalStateException("TextEmbedder not initialized. Call initialize() first.")
            }

            val textEmbedderResult = embedder.embed(text)
            val embeddingResult = textEmbedderResult.embeddingResult()
            
            // Extract the embedding vector from the result
            val embedding = embeddingResult.embeddings().firstOrNull()?.floatEmbedding()
                ?: throw IllegalStateException("No embedding generated")

            Timber.d("Embedded text of length ${text.length} -> ${embedding.size}D vector")
            
            Result.success(embedding)
        } catch (e: Exception) {
            Timber.e(e, "Failed to embed text")
            Result.failure(e)
        }
    }

    /**
     * Generate embeddings for multiple text segments in batch.
     * More efficient than individual calls when embedding many segments.
     *
     * @param texts List of text segments to embed
     * @return List of embeddings (same order as input), or empty list on error
     */
    suspend fun embedTextBatch(texts: List<String>): Result<List<FloatArray>> = withContext(Dispatchers.Default) {
        return@withContext try {
            val results = mutableListOf<FloatArray>()
            
            for ((index, text) in texts.withIndex()) {
                val embeddingResult = embedText(text)
                if (embeddingResult.isSuccess) {
                    results.add(embeddingResult.getOrThrow())
                } else {
                    Timber.w("Failed to embed text at index $index")
                    // Add zero vector to maintain order
                    results.add(FloatArray(EMBEDDING_DIMENSION))
                }
            }
            
            Timber.d("Batch embedded ${texts.size} texts -> ${results.size} embeddings")
            Result.success(results)
        } catch (e: Exception) {
            Timber.e(e, "Failed in batch embedding")
            Result.failure(e)
        }
    }

    /**
     * Check if embedder is initialized.
     */
    fun isInitialized(): Boolean = synchronized(lock) {
        textEmbedder != null
    }

    /**
     * Get the embedding dimension.
     */
    fun getEmbeddingDimension(): Int = EMBEDDING_DIMENSION

    /**
     * Release resources.
     * Call this when done with embeddings.
     */
    fun release() {
        synchronized(lock) {
            textEmbedder?.close()
            textEmbedder = null
            Timber.d("TextEmbedder released")
        }
    }

    /**
     * Normalize embedding vector to unit length.
     * Useful for cosine similarity calculations.
     */
    fun normalizeEmbedding(embedding: FloatArray): FloatArray {
        var magnitude = 0.0
        for (value in embedding) {
            magnitude += (value * value).toDouble()
        }
        magnitude = kotlin.math.sqrt(magnitude)
        
        if (magnitude == 0.0) {
            return embedding
        }

        val normalized = FloatArray(embedding.size)
        for (i in embedding.indices) {
            normalized[i] = (embedding[i] / magnitude).toFloat()
        }
        return normalized
    }

    /**
     * Segment resume text into optimal chunks for embedding.
     * Handles preprocessing for better embeddings.
     */
    fun segmentText(
        text: String,
        segmentType: String,
        maxSegmentLength: Int = 512,
    ): List<Pair<String, String>> {
        // Remove extra whitespace
        val cleaned = text.trim().replace(Regex("\\s+"), " ")
        
        if (cleaned.length <= maxSegmentLength) {
            return listOf(Pair(cleaned, segmentType))
        }

        // Split into sentences or chunks
        val sentences = cleaned.split(Regex("[.!?]"))
        val segments = mutableListOf<String>()
        var currentSegment = StringBuilder()

        for (sentence in sentences) {
            val trimmed = sentence.trim()
            if (trimmed.isEmpty()) continue

            val withPunctuation = "$trimmed."
            val newLength = currentSegment.length + withPunctuation.length

            if (newLength > maxSegmentLength && currentSegment.isNotEmpty()) {
                segments.add(currentSegment.toString())
                currentSegment = StringBuilder(withPunctuation)
            } else {
                if (currentSegment.isNotEmpty()) {
                    currentSegment.append(" ")
                }
                currentSegment.append(withPunctuation)
            }
        }

        if (currentSegment.isNotEmpty()) {
            segments.add(currentSegment.toString())
        }

        return segments.map { Pair(it, segmentType) }
    }
}

/**
 * Data class for embedding operation results.
 */
data class EmbeddingMetrics(
    val textLength: Int,
    val embeddingDimension: Int,
    val generationTimeMs: Long,
)
