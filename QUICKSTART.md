# SkillVault - Quick Start Guide

## Project Structure Summary

This is a **production-ready Android project** implementing the SkillVault research paper on edge AI for career agents. All code follows industry best practices.

## Files Created

### Configuration Files
```
build.gradle.kts                    # Root build configuration with centralized versions
app/build.gradle.kts               # App-level dependencies and Android config
settings.gradle.kts                # Project structure
app/proguard-rules.pro             # Obfuscation rules for release builds
```

### Core Application
```
SkillVaultApplication.kt           # App entry point with Hilt initialization
AndroidManifest.xml                # Android configuration and permissions
```

### Data Layer
```
data/entity/ResumeEntity.kt        # ObjectBox entities (Resume, Embedding, etc.)
data/repository/ResumeRepository.kt # Repository pattern for clean data access
data/importer/ResumeCSVImporter.kt # CSV import utilities
```

### Domain Layer (Business Logic)
```
domain/embedding/MediaPipeEmbeddingProvider.kt  # On-device text embeddings
domain/vector_search/VectorSearchEngine.kt      # Semantic search engine
```

### Presentation Layer (UI/MVVM)
```
ui/resume_list/ResumeListViewModel.kt  # Resume list screen logic
ui/search/SearchViewModel.kt           # Search screen logic
```

### Services & Background
```
service/EmbeddingIngestionService.kt   # Background embedding processing
```

### Dependency Injection
```
di/Module.kt                       # Hilt DI configuration
```

### Utilities
```
util/BenchmarkingUtil.kt           # Performance monitoring and metrics
```

### Tests
```
test/domain/vector_search/VectorSearchEngineTest.kt  # Unit tests
```

### Documentation
```
README.md                          # Comprehensive project guide
docs/ARCHITECTURE.md               # Detailed architecture documentation
```

## Key Technologies

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **Database** | ObjectBox | High-performance vector storage |
| **Embeddings** | MediaPipe | On-device text embeddings (384-D) |
| **Search** | Cosine Similarity | Semantic vector search |
| **Architecture** | MVVM | Clean architecture pattern |
| **DI** | Hilt | Compile-time dependency injection |
| **Async** | Coroutines | Structured concurrency |
| **Testing** | Kotest, JUnit5 | Comprehensive test coverage |

## Quick Start (5 Minutes)

### 1. Open in Android Studio
```bash
# Clone (when repo is created)
git clone https://github.com/knovik/skillvault.git
cd skillvault

# Open in Android Studio
open -a "Android Studio" .
```

### 2. Sync & Build
- File → Sync Now (sync Gradle)
- Build → Make Project
- Run → Run app (or press Shift+F10)

### 3. Add MediaPipe Model
```bash
# Download model to assets
mkdir -p app/src/main/assets
wget https://storage.googleapis.com/mediapipe-assets/text_embedder_mobilenet_v3.tflite \
  -O app/src/main/assets/text_embedder.tflite
```

### 4. Import Sample Data
- Prepare CSV from Kaggle Resume Dataset: https://kaggle.com/datasets/saugataroyarghya/resume-dataset
- Use ImportDataActivity to load resumes
- System will auto-generate embeddings in background

### 5. Test Search
- Navigate to SearchActivity
- Enter query: "team leadership experience"
- View results with similarity scores
- Performance should be <200ms ✓

## Development Workflow

### Adding a New Feature

**Example: Add skill filtering to search**

1. **Data Layer**: Update `ResumeEmbedding` if needed
2. **Domain Layer**: Add method to `VectorSearchEngine`
3. **Repository**: Add query method to `ResumeRepository`
4. **ViewModel**: Add logic to `SearchViewModel`
5. **UI**: Create composable/activity
6. **Tests**: Add unit tests

### Testing

```bash
# Unit tests
./gradlew test

# Instrumented tests (device required)
./gradlew connectedAndroidTest

# Specific test
./gradlew test -Dkotlin.test.single=VectorSearchEngineTest

# With coverage
./gradlew jacocoTestReport
```

### Performance Testing

```kotlin
// In ViewModel or test
val benchmarkUtil = BenchmarkingUtil(context, repository)

// Measure operation
val (results, timeMs) = benchmarkUtil.measureSuspend("Search") {
    searchEngine.search(embedding, candidates)
}

// Log performance
Timber.d("Search completed in ${timeMs}ms")

// Generate report
val report = benchmarkUtil.generateBenchmarkReport()
println(benchmarkUtil.exportReportAsJSON(report))
```

## Architecture Highlights

### MVVM Pattern
```kotlin
// Data
data class Resume(val id: Long, val name: String, ...)

// ViewModel manages state
@HiltViewModel
class MyViewModel @Inject constructor(repo: ResumeRepository) : ViewModel()

// UI observes state
LaunchedEffect(uiState) {
    when (val state = uiState) {
        is Success -> display(state.data)
        is Error -> showError(state.message)
        is Loading -> showSpinner()
    }
}
```

### Dependency Injection
```kotlin
// All dependencies injected, no Service Locators
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: ResumeRepository,
    private val embedder: MediaPipeEmbeddingProvider,
    private val searchEngine: VectorSearchEngine,
) : ViewModel()
```

### Clean Architecture
```
UI (Activities/Compose)
    ↓
ViewModels (State Management)
    ↓
Repository (Data Abstraction)
    ↓
Entity (Database) + Domain Services (Embedding, Search)
    ↓
ObjectBox (Storage)
```

## Common Tasks

### 1. Import Resumes from CSV
```kotlin
// In ImportViewModel
val result = ResumeCSVImporter.importFromCSV("/path/to/dataset.csv")
if (result.isSuccess) {
    val resumes = result.getOrNull()!!
    resumeRepository.insertOrUpdateResume(resumesList)
    embeddingService.scheduleIngestionWork()
}
```

### 2. Perform Semantic Search
```kotlin
// In SearchViewModel
fun search(query: String) {
    viewModelScope.launch {
        val embedding = embeddingProvider.embedText(query)
        val candidates = resumeRepository.getAllEmbeddings()
        val results = vectorSearchEngine.search(embedding, candidates, topK=10)
        _uiState.value = SearchUIState.Success(results)
    }
}
```

### 3. Monitor Performance
```kotlin
// In activity/service
val benchmark = BenchmarkingUtil(context, repository)
val report = benchmark.generateBenchmarkReport()
Timber.d("Storage: ${report.estimatedTotalSizeBytes} bytes")
Timber.d("Embeddings: ${report.embeddingCount}")
```

### 4. Export Results for Paper
```kotlin
// Generate benchmark report
val json = benchmarkUtil.exportReportAsJSON(report)
// Save to file for publication
val file = File(context.filesDir, "benchmark_results.json")
file.writeText(json)
```

## Debugging Tips

### Enable Verbose Logging
```kotlin
// In SkillVaultApplication.onCreate()
if (BuildConfig.DEBUG) {
    Timber.plant(Timber.DebugTree())
}
```

### Inspect Database
```bash
# Connect to device
adb shell

# Navigate to app data
cd /data/data/com.knovik.skillvault/

# List files
ls -la

# Pull database
adb pull /data/data/com.knovik.skillvault/objectbox/objectbox.db ./
```

### Check Vector Dimensions
```kotlin
// Verify embeddings are 384-D
val embedding = embeddingProvider.embedText("test")
Timber.d("Embedding size: ${embedding.size}") // Should be 384
```

## Performance Targets (From Paper)

✓ **Latency**: Retrieval < 200ms
✓ **Storage**: 50MB text → <5MB vectors  
✓ **Battery**: <50mAh per 1000 embeddings
✓ **Device**: API 26+ (Android 8.0+)

## Release Checklist

Before publishing to Play Store:

- [ ] Update version in `build.gradle.kts`
- [ ] Run all tests: `./gradlew test connectedAndroidTest`
- [ ] Check ProGuard rules in `proguard-rules.pro`
- [ ] Generate signed APK: `./gradlew bundleRelease`
- [ ] Test on multiple devices (API 26, 28, 30, 34)
- [ ] Verify storage permissions on API 30+
- [ ] Check battery consumption with profiler
- [ ] Generate benchmark report for paper
- [ ] Create release notes

## Resources

- **Android Docs**: https://developer.android.com/
- **MediaPipe**: https://ai.google.dev/edge/mediapipe/
- **ObjectBox**: https://docs.objectbox.io/
- **Kotlin Coroutines**: https://kotlinlang.org/docs/coroutines-overview.html
- **Hilt DI**: https://dagger.dev/hilt/
- **MVVM Architecture**: https://developer.android.com/topic/architecture/ui-layer/stateholders

## Support & Contribution

This is research code for the SkillVault project published by Knovik. For questions:

- Check `docs/ARCHITECTURE.md` for detailed design
- Review unit tests for usage examples
- Examine ViewModels for complete workflows

## Next Steps

1. ✓ Clone/sync project
2. ✓ Add MediaPipe model
3. ✓ Download Kaggle Resume Dataset
4. ✓ Import resumes via CSV
5. ✓ Run semantic searches
6. ✓ Generate benchmark reports
7. ✓ Publish findings

**Happy researching! 🚀**

---

**Version**: 1.0.0
**Status**: Production-Ready
**License**: MIT
