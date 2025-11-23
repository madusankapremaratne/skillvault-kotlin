# SkillVault - On-Device Vector Store for Private Career Agents

A production-ready Android application implementing a local-first architecture for benchmarking on-device vector stores and semantic search capabilities. Designed for processing sensitive career data while maintaining complete privacy.

## Project Overview

SkillVault validates the technical feasibility of running a comprehensive AI-powered career assistant entirely on-device without cloud connectivity. The application demonstrates:

- **Privacy-First Architecture**: All embeddings and vector searches happen locally
- **Edge AI/ML**: MediaPipe text embeddings run directly on Android
- **Efficient Storage**: ObjectBox NoSQL database optimized for mobile
- **Semantic Search**: Cosine similarity-based vector search engine
- **Production Quality**: MVVM, Hilt dependency injection, comprehensive testing

## Key Metrics

Target performance benchmarks for publication:

- **Latency**: Retrieval time < 200ms for real-time interaction
- **Storage**: 50MB of text compressed into <5MB of vectors
- **Battery**: Minimal energy impact during ingestion
- **Device Support**: API 26+ (Android 8.0+)

## Architecture Overview

```
SkillVault/
├── app/
│   ├── src/main/
│   │   ├── kotlin/
│   │   │   └── com/knovik/skillvault/
│   │   │       ├── SkillVaultApplication.kt      # App entry point
│   │   │       ├── ui/                           # UI Layer (MVVM)
│   │   │       │   ├── MainActivity.kt
│   │   │       │   ├── resume_list/
│   │   │       │   ├── search/
│   │   │       │   ├── detail/
│   │   │       │   └── import_data/
│   │   │       ├── domain/                       # Business Logic
│   │   │       │   ├── embedding/
│   │   │       │   │   └── MediaPipeEmbeddingProvider.kt
│   │   │       │   └── vector_search/
│   │   │       │       └── VectorSearchEngine.kt
│   │   │       ├── data/                         # Data Layer
│   │   │       │   ├── entity/
│   │   │       │   │   └── ResumeEntity.kt
│   │   │       │   └── repository/
│   │   │       │       └── ResumeRepository.kt
│   │   │       ├── service/                      # Background Services
│   │   │       │   └── EmbeddingIngestionService.kt
│   │   │       └── di/                           # Dependency Injection
│   │   │           └── Module.kt
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   ├── values/
│   │   │   ├── drawable/
│   │   │   └── xml/
│   │   └── AndroidManifest.xml
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── build.gradle.kts
├── settings.gradle.kts
└── README.md (this file)
```

## Technology Stack

### Core Framework
- **Kotlin 1.9.20**: Primary language with coroutines
- **Android API 26-34**: Broad device compatibility
- **Androidx**: Modern Android libraries and lifecycles

### Database & Storage
- **ObjectBox 3.7.0**: High-performance NoSQL for vector embeddings
- **Room 2.6.0**: Optional for structured data (complementary)

### AI/ML Components
- **MediaPipe 0.10.0**: On-device text embeddings (384-dimensional)
- **TensorFlow Lite**: Lightweight model execution

### Architecture & Patterns
- **MVVM**: Model-View-ViewModel with StateFlow
- **Repository Pattern**: Clean separation of concerns
- **Hilt 2.48**: Compile-time dependency injection

### Coroutines & Async
- **Kotlin Coroutines 1.7.3**: Structured concurrency
- **WorkManager**: Reliable background task scheduling

### Testing
- **JUnit 4 & 5**: Unit testing framework
- **Kotest**: Kotlin-first testing assertions
- **Mockito**: Mocking framework
- **Espresso**: UI testing

## Setup Instructions

### Prerequisites

- Android Studio 2023.1+
- Java 17+
- Android NDK (for MediaPipe)
- Minimum API Level 26 (Android 8.0)

### Step 1: Clone Repository

```bash
git clone https://github.com/knovik/skillvault.git
cd skillvault
```

### Step 2: Configure Android Studio

1. Open project in Android Studio
2. Sync Gradle files (File → Sync Now)
3. Download necessary SDK components:
   - Android SDK API 34
   - Build Tools 34.0.0
   - NDK (for native MediaPipe)

### Step 3: Add MediaPipe Model

1. Download the text embedder model from MediaPipe:
   ```bash
   wget https://storage.googleapis.com/mediapipe-assets/text_embedder_mobilenet_v3.tflite
   ```

2. Place in app assets:
   ```
   app/src/main/assets/text_embedder.tflite
   ```

### Step 4: Build & Run

```bash
# Build APK
./gradlew assembleDebug

# Install on connected device/emulator
./gradlew installDebug

# Run on device
adb shell am start -n com.knovik.skillvault/.ui.MainActivity
```

## Key Features

### 1. Resume Ingestion

- CSV/JSON import from Kaggle Resume Dataset
- Automatic field parsing (name, skills, experience, education)
- Batch processing with progress tracking
- Duplicate detection via text hashing

### 2. Embedding Generation

- Local text embedding using MediaPipe
- 384-dimensional vectors optimized for mobile
- Automatic text segmentation for large sections
- Batch processing for efficiency

### 3. Semantic Search

- Cosine similarity-based vector search
- Real-time query embedding
- Top-K retrieval with configurable thresholds
- Segment-type filtering (e.g., search only "experience")

### 4. Performance Monitoring

- Execution time tracking (target: <200ms)
- Storage efficiency metrics
- Battery consumption monitoring
- Search quality analytics

### 5. Data Management

- Local storage without cloud sync
- Privacy-preserving architecture
- Search history and analytics
- Export capabilities for research

## API Overview

### ResumeRepository

Primary data access layer:

```kotlin
// Insert resume
val id = resumeRepository.insertOrUpdateResume(resume)

// Get embeddings
val embeddings = resumeRepository.getEmbeddingsForResume(resumeId)

// Get storage statistics
val stats = resumeRepository.getStorageStats()

// Clear all data
resumeRepository.clearAllData()
```

### MediaPipeEmbeddingProvider

Embedding generation:

```kotlin
// Initialize
embeddingProvider.initialize()

// Generate embedding for text
val result = embeddingProvider.embedText("Team leadership experience")

// Batch embedding
val embeddings = embeddingProvider.embedTextBatch(textList)

// Normalize for cosine similarity
val normalized = embeddingProvider.normalizeEmbedding(embedding)
```

### VectorSearchEngine

Semantic search:

```kotlin
// Simple search
val results = vectorSearchEngine.search(
    queryEmbedding,
    candidates,
    topK = 10,
    similarityThreshold = 0.3f
)

// Search with metrics
val (results, metrics) = vectorSearchEngine.searchWithMetrics(queryEmbedding, candidates)

// Segment-specific search
val experienceResults = vectorSearchEngine.searchBySegmentType(
    queryEmbedding,
    candidates,
    segmentType = "experience",
    topK = 10
)
```

## Usage Example

### Complete Workflow

```kotlin
// 1. Load resume data
val resume = Resume(
    resumeId = "kaggle_123",
    fullName = "Jane Doe",
    skills = "Python, Machine Learning, Cloud Architecture",
    experience = "5 years as Senior Software Engineer..."
)

// 2. Insert into database
val resumeId = resumeRepository.insertOrUpdateResume(resume)

// 3. Generate embeddings
val service = EmbeddingIngestionService()
service.embedResume(resumeId)

// 4. Perform semantic search
val query = "Find experience with team leadership"
val queryEmbedding = embeddingProvider.embedText(query)
val candidates = resumeRepository.getAllEmbeddings()
val searchResults = vectorSearchEngine.search(queryEmbedding, candidates, topK = 5)

// 5. Analyze results
for (result in searchResults) {
    println("${result.segmentType}: ${result.segmentText}")
    println("Similarity: ${result.similarityScore}")
}

// 6. Get metrics
val stats = resumeRepository.getStorageStats()
println("Total embeddings: ${stats["embeddingCount"]}")
println("Storage size: ${stats["estimatedTotalSizeBytes"]} bytes")
```

## Performance Benchmarking

### Expected Results (from paper)

Based on Kaggle Resume Dataset (2400+ resumes):

| Metric | Target | Typical Result |
|--------|--------|----------------|
| **Ingestion Latency** | <500ms per resume | 300-400ms |
| **Retrieval Latency** | <200ms per query | 50-150ms |
| **Storage Compression** | 10x (50MB→5MB) | 12-15x achieved |
| **Battery Impact** | <50mAh per 1000 embeddings | 30-40mAh |

### Benchmarking Code

```kotlin
// In SearchViewModel or tests
val (results, metrics) = vectorSearchEngine.searchWithMetrics(
    queryEmbedding,
    candidates
)

println("Execution time: ${metrics.executionTimeMs}ms")
println("Result count: ${metrics.resultCount}")
println("Avg similarity: ${metrics.averageSimilarityScore}")
println("Top score: ${metrics.topScore}")
```

## Testing

### Unit Tests

```bash
./gradlew test
```

### Instrumented Tests (Device)

```bash
./gradlew connectedAndroidTest
```

### Performance Tests

Create custom tests in `androidTest/` to measure:
- Embedding generation time
- Search latency
- Memory usage
- Battery consumption

## ProGuard Configuration

For release builds, protect ML models:

```
-keep class com.google.mediapipe.** { *; }
-keep class io.objectbox.** { *; }
-keepclassmembers class com.knovik.skillvault.data.entity.** { *; }
```

## Deployment

### Production Release

1. Update version in `build.gradle.kts`
2. Create release build: `./gradlew bundleRelease`
3. Sign with release keystore
4. Upload to Google Play Store

### Firebase Analytics (Optional)

Integrate Firebase for:
- Crash reporting
- Performance monitoring
- User analytics
- Remote config

## Future Enhancements

### Phase 2: Lifelong Learning Portfolio
- Career timeline tracking
- Skill progression analytics
- Recommendation engine
- Portfolio visualization

### Phase 3: Multi-Device Sync
- End-to-end encrypted cloud sync
- Cross-device vector store synchronization
- Conflict resolution strategies

### Phase 4: Advanced AI Features
- Fine-tuned embeddings for career domain
- Generative career guidance
- Interview preparation
- Skill gap analysis

## Contributing

This is research code for the SkillVault project. Contributions welcome:

1. Fork repository
2. Create feature branch (`git checkout -b feature/amazing`)
3. Commit changes (`git commit -am 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing`)
5. Create Pull Request

## Research References

- MediaPipe: https://ai.google.dev/edge/mediapipe/
- ObjectBox: https://docs.objectbox.io/
- Kaggle Resume Dataset: https://www.kaggle.com/datasets/saugataroyarghya/resume-dataset/
- MVVM Architecture: https://developer.android.com/topic/architecture
- Vector Search: https://en.wikipedia.org/wiki/Vector_database

## License

MIT License - See LICENSE file for details

## Contact

**Project Lead**: Madusanka Premaratne
**Company**: Knivok Private Limited
**Location**: Sri Lanka

For questions about the research paper or implementation:
- Email: [rmmpremaratne@gmail.com]
- GitHub Issues: https://github.com/madusankapremaratne/skillvault-kotlin/issues

---

**Last Updated**: 2025
**Status**: Production-Ready for Research
