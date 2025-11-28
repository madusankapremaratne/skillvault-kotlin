package com.knovik.skillvault.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.BackoffPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.knovik.skillvault.data.entity.Resume
import com.knovik.skillvault.data.entity.ResumeEmbedding
import com.knovik.skillvault.data.repository.ResumeRepository
import com.knovik.skillvault.domain.embedding.MediaPipeEmbeddingProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import androidx.work.ListenableWorker
import kotlinx.coroutines.cancel
import javax.inject.Inject

/**
 * Service for background ingestion of resumes and embeddings.
 * Runs embeddings generation asynchronously to avoid blocking the main thread.
 */
@AndroidEntryPoint
class EmbeddingIngestionService : Service() {

    @Inject lateinit var resumeRepository: ResumeRepository
    @Inject lateinit var embeddingProvider: MediaPipeEmbeddingProvider

    private val binder = EmbeddingBinder()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    inner class EmbeddingBinder : Binder() {
        fun getService(): EmbeddingIngestionService = this@EmbeddingIngestionService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("EmbeddingIngestionService created")
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.coroutineContext.cancel()
        Timber.d("EmbeddingIngestionService destroyed")
    }

    /**
     * Schedule background embedding ingestion work.
     * Uses WorkManager for reliable background processing.
     */
    fun scheduleIngestionWork() {
        val ingestionWork = OneTimeWorkRequestBuilder<EmbeddingIngestionWorker>()
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                1, TimeUnit.MINUTES
            )
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "embedding_ingestion",
            androidx.work.ExistingWorkPolicy.KEEP,
            ingestionWork as androidx.work.OneTimeWorkRequest
        )

        Timber.d("Embedding ingestion work scheduled")
    }

    /**
     * Perform synchronous embedding ingestion for a single resume.
     */
    fun embedResume(resumeId: Long, onProgress: (Int, Int) -> Unit = { _, _ -> }) {
        scope.launch {
            try {
                val resume = resumeRepository.getResume(resumeId) ?: run {
                    Timber.w("Resume $resumeId not found")
                    return@launch
                }

                // Update status
                resumeRepository.insertOrUpdateResume(
                    resume.copy(processingStatus = "processing")
                )

                val embeddings = generateEmbeddings(resume)
                resumeRepository.insertEmbeddingsBatch(embeddings)

                // Mark as embedded
                resumeRepository.markResumeAsEmbedded(resumeId, true)

                Timber.d("Resume $resumeId embedding completed: ${embeddings.size} embeddings")
            } catch (e: Exception) {
                Timber.e(e, "Failed to embed resume $resumeId")
                resumeRepository.insertOrUpdateResume(
                    resumeRepository.getResume(resumeId)?.copy(
                        processingStatus = "failed",
                        errorMessage = e.message ?: "Unknown error"
                    ) ?: return@launch
                )
            }
        }
    }

    /**
     * Batch embed multiple resumes.
     */
    fun embedResumes(resumeIds: List<Long>, onProgress: (Int, Int) -> Unit = { _, _ -> }) {
        scope.launch {
            try {
                Timber.d("Starting batch embedding for ${resumeIds.size} resumes")
                
                for ((index, resumeId) in resumeIds.withIndex()) {
                    embedResume(resumeId) { current, total ->
                        onProgress(current, total)
                    }
                    onProgress(index + 1, resumeIds.size)
                }

                Timber.d("Batch embedding completed")
            } catch (e: Exception) {
                Timber.e(e, "Batch embedding failed")
            }
        }
    }

    /**
     * Generate embeddings for all sections of a resume.
     */
    private suspend fun generateEmbeddings(resume: Resume): List<ResumeEmbedding> {
        val startTime = System.currentTimeMillis()
        val embeddings = mutableListOf<ResumeEmbedding>()
        var segmentId = 0

        // Segment and embed each section
        val sections = mapOf(
            "summary" to resume.summary,
            "skills" to resume.skills,
            "experience" to resume.experience,
            "education" to resume.education,
            "certifications" to resume.certifications,
        )

        for ((sectionType, sectionText) in sections) {
            if (sectionText.isEmpty()) continue

            // Segment text into chunks
            val segments = embeddingProvider.segmentText(sectionText, sectionType)

            for (segment in segments) {
                // Prepend section type to text for better semantic context
                val textToEmbed = "${sectionType}: ${segment.first}"
                val embeddingResult = embeddingProvider.embedText(textToEmbed)
                
                if (embeddingResult.isSuccess) {
                    embeddings.add(
                        ResumeEmbedding(
                            resumeId = resume.id,
                            segmentId = "${sectionType}_${segmentId}",
                            segmentType = sectionType,
                            segmentText = segment.first, // Store original text
                            embedding = embeddingResult.getOrThrow(),
                        )
                    )
                    segmentId++
                } else {
                    Timber.w("Failed to embed segment: ${segment.first.take(50)}...")
                }
            }
        }

        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        android.util.Log.d("EdgeScoutExperiment", "EMBEDDING_GENERATION_TIME, ${resume.id}, $duration")

        Timber.d("Generated ${embeddings.size} embeddings for resume ${resume.id}")
        return embeddings
    }
}

/**
 * WorkManager worker for background embedding ingestion.
 * Allows scheduling of embedding tasks without keeping service alive.
 */
@HiltWorker
class EmbeddingIngestionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val resumeRepository: ResumeRepository,
    private val embeddingProvider: MediaPipeEmbeddingProvider,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): ListenableWorker.Result {
        return try {
            Timber.d("EmbeddingIngestionWorker started")

            // Initialize embedding provider
            val initResult = embeddingProvider.initialize()
            if (initResult.isFailure) {
                Timber.e("Failed to initialize embedding provider")
                return ListenableWorker.Result.retry()
            }

            // Get unembedded resumes
            val unembeddedResumes = resumeRepository.getUnembeddedResumes(limit = 100)
            if (unembeddedResumes.isEmpty()) {
                Timber.d("No unembedded resumes found")
                return ListenableWorker.Result.success()
            }

            Timber.d("Processing ${unembeddedResumes.size} unembedded resumes")

            for (resume in unembeddedResumes) {
                try {
                    resumeRepository.insertOrUpdateResume(
                        resume.copy(processingStatus = "processing")
                    )

                    val embeddings = generateEmbeddings(resume)
                    resumeRepository.insertEmbeddingsBatch(embeddings)
                    resumeRepository.markResumeAsEmbedded(resume.id, true)

                    Timber.d("Processed resume ${resume.id}")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to process resume ${resume.id}")
                    resumeRepository.insertOrUpdateResume(
                        resume.copy(
                            processingStatus = "failed",
                            errorMessage = e.message ?: "Unknown error"
                        )
                    )
                }
            }

            Timber.d("EmbeddingIngestionWorker completed")
            return ListenableWorker.Result.success()
        } catch (e: Exception) {
            Timber.e(e, "EmbeddingIngestionWorker failed")
            return ListenableWorker.Result.retry()
        }
    }

    private suspend fun generateEmbeddings(resume: Resume): List<ResumeEmbedding> {
        val startTime = System.currentTimeMillis()
        val embeddings = mutableListOf<ResumeEmbedding>()
        var segmentId = 0

        val sections = mapOf(
            "summary" to resume.summary,
            "skills" to resume.skills,
            "experience" to resume.experience,
            "education" to resume.education,
            "certifications" to resume.certifications,
        )

        for ((sectionType, sectionText) in sections) {
            if (sectionText.isEmpty()) continue

            val segments = embeddingProvider.segmentText(sectionText, sectionType)
            for (segment in segments) {
                // Prepend section type to text for better semantic context
                val textToEmbed = "${sectionType}: ${segment.first}"
                val embeddingResult = embeddingProvider.embedText(textToEmbed)
                
                if (embeddingResult.isSuccess) {
                    embeddings.add(
                        ResumeEmbedding(
                            resumeId = resume.id,
                            segmentId = "${sectionType}_${segmentId}",
                            segmentType = sectionType,
                            segmentText = segment.first, // Store original text
                            embedding = embeddingResult.getOrThrow(),
                        )
                    )
                    segmentId++
                }
            }
        }

        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        android.util.Log.d("EdgeScoutExperiment", "EMBEDDING_GENERATION_TIME_WORKER, ${resume.id}, $duration")

        return embeddings
    }
}
