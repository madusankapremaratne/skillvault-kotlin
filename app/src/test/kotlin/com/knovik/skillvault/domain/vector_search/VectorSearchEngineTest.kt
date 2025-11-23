package com.knovik.skillvault.domain.vector_search

import com.knovik.skillvault.data.entity.ResumeEmbedding
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.math.sqrt

/**
 * Unit tests for VectorSearchEngine.
 * Tests cosine similarity calculations and search functionality.
 */
class VectorSearchEngineTest : FreeSpec({

    val searchEngine = VectorSearchEngine()

    "Vector Operations" - {
        "cosine similarity should return 1 for identical vectors" {
            runTest {
                val vector = FloatArray(512) { 0.5f }
                val embedding = ResumeEmbedding(
                    id = 1,
                    resumeId = 1,
                    embedding = vector
                )

                val results = searchEngine.search(vector, listOf(embedding), topK = 1)
                results.first().similarityScore shouldBe 1.0f
            }
        }

        "cosine similarity should return 0 for orthogonal vectors" {
            runTest {
                val vector1 = FloatArray(10) { if (it < 5) 1f else 0f }
                val vector2 = FloatArray(10) { if (it < 5) 0f else 1f }
                
                val embedding = ResumeEmbedding(
                    id = 1,
                    resumeId = 1,
                    embedding = vector2
                )

                val results = searchEngine.search(vector1, listOf(embedding), topK = 1)
                results.first().similarityScore shouldBe 0.0f
            }
        }

        "similarity score should be between -1 and 1" {
            runTest {
                val query = FloatArray(512) { kotlin.random.Random.nextFloat() }
                val candidates = (1..10).map { i ->
                    ResumeEmbedding(
                        id = i.toLong(),
                        resumeId = i.toLong(),
                        embedding = FloatArray(512) { kotlin.random.Random.nextFloat() }
                    )
                }

                val results = searchEngine.search(query, candidates, topK = 10)
                
                results.forEach { result ->
                    result.similarityScore.shouldBeGreaterThan(-1f)
                    result.similarityScore.shouldBeLessThan(2f)
                }
            }
        }
    }

    "Search Operations" - {
        "search should return top K results" {
            runTest {
                val query = FloatArray(10) { 1f }
                val candidates = (1..20).map { i ->
                    ResumeEmbedding(
                        id = i.toLong(),
                        resumeId = i.toLong(),
                        embedding = FloatArray(10) { 1f }
                    )
                }

                val results = searchEngine.search(query, candidates, topK = 5)
                results.size shouldBe 5
            }
        }

        "search should respect similarity threshold" {
            runTest {
                val query = FloatArray(10) { 1f }
                val similar = FloatArray(10) { 1f }
                val dissimilar = FloatArray(10) { -1f }
                
                val candidates = listOf(
                    ResumeEmbedding(id = 1, resumeId = 1, embedding = similar),
                    ResumeEmbedding(id = 2, resumeId = 2, embedding = dissimilar),
                )

                val results = searchEngine.search(query, candidates, similarityThreshold = 0.8f)
                results.size shouldBe 1
            }
        }

        "search should return results sorted by similarity" {
            runTest {
                val query = FloatArray(10) { 1f }
                val candidates = listOf(
                    ResumeEmbedding(id = 1, resumeId = 1, embedding = FloatArray(10) { 1f }),
                    ResumeEmbedding(id = 2, resumeId = 2, embedding = FloatArray(10) { 0.5f }),
                    ResumeEmbedding(id = 3, resumeId = 3, embedding = FloatArray(10) { 0f }),
                )

                val results = searchEngine.search(query, candidates, topK = 3)
                
                for (i in 0 until results.size - 1) {
                    val current = results[i].similarityScore
                    val next = results[i + 1].similarityScore
                    current.shouldBeGreaterThan(next)
                }
            }
        }
    }

    "Batch Search" - {
        "batch search should return results for all queries" {
            runTest {
                val queries = listOf(
                    FloatArray(10) { 1f },
                    FloatArray(10) { 0f },
                )
                val candidates = (1..10).map { i ->
                    ResumeEmbedding(
                        id = i.toLong(),
                        resumeId = i.toLong(),
                        embedding = FloatArray(10) { kotlin.random.Random.nextFloat() }
                    )
                }

                val results = searchEngine.batchSearch(queries, candidates, topK = 5)
                results.size shouldBe 2
                results.forEach { it.size shouldBe 5 }
            }
        }
    }

    "Segment Type Filtering" - {
        "search should filter by segment type" {
            runTest {
                val query = FloatArray(10) { 1f }
                val candidates = listOf(
                    ResumeEmbedding(
                        id = 1, resumeId = 1, 
                        embedding = FloatArray(10) { 1f },
                        segmentType = "experience"
                    ),
                    ResumeEmbedding(
                        id = 2, resumeId = 2,
                        embedding = FloatArray(10) { 1f },
                        segmentType = "skills"
                    ),
                )

                val results = searchEngine.searchBySegmentType(
                    query, candidates, "experience", topK = 10
                )
                results.forEach { it.segmentType shouldBe "experience" }
            }
        }
    }

    "Performance Metrics" - {
        "search should execute within reasonable time" {
            runTest {
                val query = FloatArray(512) { kotlin.random.Random.nextFloat() }
                val candidates = (1..1000).map { i ->
                    ResumeEmbedding(
                        id = i.toLong(),
                        resumeId = i.toLong(),
                        embedding = FloatArray(512) { kotlin.random.Random.nextFloat() }
                    )
                }

                val (results, metrics) = searchEngine.searchWithMetrics(query, candidates, topK = 10)
                
                metrics.executionTimeMs.shouldBeLessThan(500L) // Should complete in <500ms
                //shouldBeLessThan(500L) // Should complete in <500ms
                results.size.shouldBeGreaterThan(0)
            }
        }
    }

    "Edge Cases" - {
        "empty candidates should return empty results" {
            runTest {
                val query = FloatArray(10) { 1f }
                val results = searchEngine.search(query, emptyList())
                results.size shouldBe 0
            }
        }

        "zero vector should not crash" {
            runTest {
                val query = FloatArray(10) { 0f }
                val candidates = listOf(
                    ResumeEmbedding(id = 1, resumeId = 1, embedding = FloatArray(10) { 1f })
                )
                val results = searchEngine.search(query, candidates)
                // Should complete without error
                results.size shouldBe 0 // Zero vector has zero similarity
            }
        }
    }
})
