package com.knovik.skillvault.data.entity

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import java.util.*

/**
 * Represents a resume/career document in the local vector store.
 * ObjectBox entity optimized for edge device storage and semantic search.
 */
@Entity
data class Resume(
    @Id(assignable = true)
    var id: Long = 0,
    
    @Index
    var resumeId: String = "", // Unique identifier (can be UUID or Kaggle ID)
    
    var fullName: String = "",
    var email: String = "",
    var phoneNumber: String = "",
    
    // Raw resume text - stored for retrieval and re-embedding if needed
    var rawText: String = "",
    
    // Extracted key sections
    var summary: String = "",
    var skills: String = "", // Comma-separated or structured
    var experience: String = "", // Job history
    var education: String = "",
    var certifications: String = "",
    
    // Metadata
    var sourceFile: String = "", // Original filename (e.g., "kaggle_resume_123.pdf")
    var fileFormat: String = "text", // "text", "pdf", "docx"
    var fileSize: Long = 0, // Bytes
    
    // Timestamps
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var embeddedAt: Long = 0, // When vectors were generated
    
    // Processing status
    var isEmbedded: Boolean = false, // Whether embeddings have been generated
    var processingStatus: String = "pending", // pending, processing, completed, failed
    var errorMessage: String = "", // If processing failed
    
    // Storage efficiency
    var textHash: String = "", // SHA-256 of rawText to detect duplicates
)

/**
 * Represents vector embeddings for segments of a resume.
 * ObjectBox entity optimized for vector similarity search.
 */
@Entity
data class ResumeEmbedding(
    @Id(assignable = true)
    var id: Long = 0,
    
    @Index
    var resumeId: Long = 0, // Foreign key to Resume
    
    var segmentId: String = "", // Identifies which section (e.g., "experience_0", "skills_1")
    var segmentType: String = "", // "summary", "experience", "education", "skills", "certifications"
    var segmentText: String = "", // Original text segment
    
    // Vector embedding (384 dimensions for MobileBERT-based embeddings)
    var embedding: FloatArray = FloatArray(384),
    
    // Metadata
    var embeddingModel: String = "google-mediapipe-text-v1",
    var embeddingDimension: Int = 384,
    
    // Timestamps
    var createdAt: Long = System.currentTimeMillis(),
    
    // Quality metrics
    var confidenceScore: Float = 0f, // 0-1, how confident in this embedding
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ResumeEmbedding

        if (id != other.id) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
)

/**
 * Represents a search query with its embedding and results.
 * Used for caching and analytics.
 */
@Entity
data class SearchQuery(
    @Id(assignable = true)
    var id: Long = 0,
    
    @Index
    var queryText: String = "",
    var queryEmbedding: FloatArray = FloatArray(384),
    
    var executedAt: Long = System.currentTimeMillis(),
    var executionTimeMs: Long = 0,
    
    // Results metadata
    var resultCount: Int = 0,
    var topScoreValue: Float = 0f,
    
    // User interaction
    var wasUserSatisfied: Boolean = false,
    var feedbackText: String = "",
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SearchQuery

        if (id != other.id) return false
        if (!queryEmbedding.contentEquals(other.queryEmbedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + queryEmbedding.contentHashCode()
        return result
    }
)

/**
 * Represents performance metrics for benchmarking.
 * Used to track ingestion latency, retrieval speed, and battery impact.
 */
@Entity
data class PerformanceMetric(
    @Id(assignable = true)
    var id: Long = 0,
    
    @Index
    var metricType: String = "", // "ingestion", "retrieval", "storage", "battery"
    
    var metricName: String = "",
    var value: Double = 0.0,
    var unit: String = "", // "ms", "MB", "mAh"
    
    var resumeCount: Int = 0, // How many resumes were involved
    var embeddingCount: Int = 0,
    
    var timestamp: Long = System.currentTimeMillis(),
    var deviceInfo: String = "", // Device model, OS version for context
)

/**
 * Career timeline entry - for future "Lifelong Learning Portfolio" feature.
 * Represents a milestone in a person's career progression.
 */
@Entity
data class CareerEntry(
    @Id(assignable = true)
    var id: Long = 0,
    
    var userId: String = "",
    var timestamp: Long = 0,
    
    var entryType: String = "", // "job", "certification", "skill_acquisition", "project"
    var title: String = "",
    var description: String = "",
    var relevantSkills: String = "", // Comma-separated
    
    var createdAt: Long = System.currentTimeMillis(),
)
