# SkillVault Architecture Documentation

## Overview

SkillVault is built following clean architecture principles with clear separation of concerns across three layers: Presentation (UI), Domain (Business Logic), and Data.

## Architecture Layers

### 1. Presentation Layer (UI)

**Location**: `ui/`

Responsible for displaying data and collecting user input. Uses MVVM pattern with Jetpack Compose and Material Design 3.

**Components**:
- **Activities**: Entry points for screens (MainActivity, SearchActivity, DetailActivity)
- **ViewModels**: Manage UI state and business logic coordination
- **UI State**: Sealed classes representing screen states (Loading, Success, Error)
- **Composables**: Reusable UI components (future Compose migration)

**Key ViewModels**:
- `ResumeListViewModel`: Manages resume list display
- `SearchViewModel`: Handles semantic search interaction
- `ImportDataViewModel`: Manages data import workflow

**Example StateFlow Usage**:
```kotlin
@HiltViewModel
class ResumeListViewModel @Inject constructor(
    private val resumeRepository: ResumeRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<ResumeListUIState>(ResumeListUIState.Loading)
    val uiState: StateFlow<ResumeListUIState> = _uiState.asStateFlow()
    
    fun loadResumes() {
        viewModelScope.launch {
            _uiState.value = ResumeListUIState.Loading
            // Load data...
            _uiState.value = ResumeListUIState.Success(resumes)
        }
    }
}
```

### 2. Domain Layer

**Location**: `domain/`

Contains business logic and use cases. Pure Kotlin without Android dependencies.

**Components**:

#### 2.1 Embedding Provider
**File**: `domain/embedding/MediaPipeEmbeddingProvider.kt`

Manages on-device text embedding generation using Google MediaPipe.

**Responsibilities**:
- Initialize MediaPipe TextEmbedder model
- Generate embeddings for text segments
- Batch processing of multiple texts
- Text preprocessing and segmentation
- Vector normalization

**Key Methods**:
```kotlin
suspend fun embedText(text: String): Result<FloatArray>
suspend fun embedTextBatch(texts: List<String>): Result<List<FloatArray>>
fun segmentText(text: String, segmentType: String): List<Pair<String, String>>
fun normalizeEmbedding(embedding: FloatArray): FloatArray
```

#### 2.2 Vector Search Engine
**File**: `domain/vector_search/VectorSearchEngine.kt`

Core semantic search engine using cosine similarity.

**Responsibilities**:
- Calculate cosine similarity between vectors
- Perform nearest neighbor search
- Top-K retrieval with filtering
- Performance metrics collection
- Similarity score ranking

**Algorithm Details**:
```
Cosine Similarity: sim(A, B) = (A · B) / (||A|| * ||B||)
- Range: [-1, 1]
- 1 = identical vectors
- 0 = orthogonal vectors
- -1 = opposite vectors
```

**Search Process**:
1. Calculate similarity score against all candidates
2. Filter by threshold
3. Sort by score (highest first)
4. Return top K results
5. Measure execution time

### 3. Data Layer

**Location**: `data/`

Handles all data operations including database access and external data sources.

#### 3.1 Entities
**File**: `data/entity/ResumeEntity.kt`

ObjectBox entities defining the data model:

**Resume Entity**:
- Resume metadata (name, email, skills, experience)
- Raw text storage for re-embedding
- Processing status tracking
- Hash for duplicate detection
- Timestamps for sorting/filtering

**ResumeEmbedding Entity**:
- References to parent Resume
- 384-dimensional vector
- Segment metadata (type: experience, skills, etc.)
- Confidence score
- Model version tracking

**SearchQuery Entity**:
- Query text and embedding
- Execution metrics
- User satisfaction feedback
- Analytics data

**PerformanceMetric Entity**:
- Benchmarking data
- Device context information
- Metric type classification

#### 3.2 Repository Pattern
**File**: `data/repository/ResumeRepository.kt`

Single source of truth for data access. Implements repository pattern for clean abstraction.

**Key Responsibilities**:
- CRUD operations for resumes and embeddings
- Query building and filtering
- Transaction management
- Storage statistics collection
- Data consistency

**Example Usage**:
```kotlin
// Insert
val resumeId = resumeRepository.insertOrUpdateResume(resume)

// Query
val embeddings = resumeRepository.getEmbeddingsForResume(resumeId)
val stats = resumeRepository.getStorageStats()

// Batch operations
resumeRepository.insertEmbeddingsBatch(embeddings)

// Cleanup
resumeRepository.deleteResume(resumeId)
```

#### 3.3 Database Layer
**Database**: ObjectBox (high-performance NoSQL for Android)

**Why ObjectBox**:
- Optimized for mobile devices
- Vector storage without additional overhead
- ACID transactions
- Queries are type-safe
- No reflection - compile-time code generation
- Superior performance vs Room for this use case

**Data Flow**:
```
User Input → ViewModel → Repository → ObjectBox → Local Storage
                ↓
            Domain Logic (Embedding/Search)
                ↓
            Repository (Query)
                ↓
            ViewModel (State Update)
                ↓
            UI (Display)
```

### 4. Supporting Components

#### 4.1 Dependency Injection (Hilt)
**File**: `di/Module.kt`

Provides singleton instances of repositories and services.

**Scopes**:
- `@Singleton`: Application-wide instances (BoxStore, Repository, SearchEngine)
- `@ViewModel`: ViewModel scope for UI state management

**Configuration**:
```kotlin
@Singleton
@Provides
fun provideBoxStore(@ApplicationContext context: Context): BoxStore = 
    ObjectBox.builder().androidContext(context).build()

@Singleton
@Provides
fun provideResumeRepository(box: Box<Resume>): ResumeRepository = 
    ResumeRepository(box)
```

#### 4.2 Background Services
**File**: `service/EmbeddingIngestionService.kt`

Handles batch embedding generation without blocking UI.

**Features**:
- WorkManager integration for reliable scheduling
- Progress callbacks
- Error handling and retry logic
- Battery optimization

#### 4.3 Data Importers
**File**: `data/importer/ResumeCSVImporter.kt`

Utilities for importing resume data from various formats.

**Supports**:
- CSV from Kaggle Dataset
- JSON (extensible)
- Duplicate detection via SHA-256
- Validation before import

#### 4.4 Benchmarking Utilities
**File**: `util/BenchmarkingUtil.kt`

Comprehensive performance monitoring.

**Metrics**:
- Execution time (milliseconds)
- Memory usage (heap, native, device)
- Battery consumption
- Storage efficiency
- Device information for context

## Data Flow Diagrams

### Resume Import & Embedding Flow

```
CSV File
   ↓
ResumeCSVImporter.importFromCSV()
   ↓
Resume entities (processed)
   ↓
ResumeRepository.insertOrUpdateResume()
   ↓
ObjectBox (Local Storage)
   ↓
EmbeddingIngestionService
   ↓
MediaPipeEmbeddingProvider.embedText()
   ↓
Float[384] vectors
   ↓
ResumeEmbedding entities
   ↓
ResumeRepository.insertEmbeddingsBatch()
   ↓
ObjectBox (Vector Store)
```

### Search Flow

```
User Query (text)
   ↓
SearchViewModel.search()
   ↓
MediaPipeEmbeddingProvider.embedText()
   ↓
Float[384] query embedding
   ↓
ResumeRepository.getAllEmbeddings()
   ↓
VectorSearchEngine.search(query, candidates)
   ↓
Cosine Similarity Calculations
   ↓
SearchResult[] (sorted by score)
   ↓
SearchViewModel (state update)
   ↓
UI (display results)
   ↓
ResumeRepository.recordSearchQuery() (analytics)
```

## Threading & Concurrency

**Dispatcher Usage**:
- `Dispatchers.Main`: UI updates
- `Dispatchers.IO`: Database & file operations
- `Dispatchers.Default`: CPU-intensive work (embeddings, similarity calculations)

**Example**:
```kotlin
suspend fun search(query: String) {
    viewModelScope.launch {
        // UI thread by default
        _uiState.value = SearchUIState.Loading
        
        // Move to IO for database
        val candidates = withContext(Dispatchers.IO) {
            resumeRepository.getAllEmbeddings()
        }
        
        // Move to Default for computation
        val results = withContext(Dispatchers.Default) {
            vectorSearchEngine.search(embedding, candidates)
        }
        
        // Back to Main for UI update
        _uiState.value = SearchUIState.Success(results)
    }
}
```

## Database Schema

### Resume Table
```
id: Long (primary key)
resumeId: String (unique external ID)
fullName: String
email: String
phoneNumber: String
rawText: String (full text)
summary: String
skills: String
experience: String
education: String
certifications: String
sourceFile: String
fileFormat: String
fileSize: Long
createdAt: Long (timestamp)
updatedAt: Long (timestamp)
embeddedAt: Long
isEmbedded: Boolean
processingStatus: String (pending, processing, completed, failed)
errorMessage: String
textHash: String (SHA-256 for deduplication)
```

### ResumeEmbedding Table
```
id: Long (primary key)
resumeId: Long (foreign key → Resume.id)
segmentId: String
segmentType: String (summary, experience, skills, education, certifications)
segmentText: String
embedding: FloatArray (384 dimensions)
embeddingModel: String
embeddingDimension: Int
createdAt: Long
confidenceScore: Float
```

### SearchQuery Table (Analytics)
```
id: Long (primary key)
queryText: String
queryEmbedding: FloatArray (384 dimensions)
executedAt: Long
executionTimeMs: Long
resultCount: Int
topScoreValue: Float
wasUserSatisfied: Boolean
feedbackText: String
```

## Performance Considerations

### Memory Optimization
- Store embeddings as FloatArray (efficient for 384-D vectors)
- Lazy loading of large text fields
- ObjectBox handles memory mapping efficiently
- Batch operations to reduce allocation

### Storage Optimization
- Compression ratio: Text → Vectors (≈15x compression)
- 384-D float embedding = 1,536 bytes
- 2,400 resumes × 1,536 bytes = 3.7 MB for embeddings
- vs. 50MB+ raw text

### Computation Optimization
- Pre-normalized embeddings for faster cosine similarity
- Batch vector operations
- Early termination in searches
- Reuse of candidates list (don't reload for each search)

## Error Handling Strategy

**Layered Approach**:
1. **Repository Layer**: Try-catch with logging
2. **Domain Layer**: Result<T> wrapper for boxing errors
3. **ViewModel Layer**: Convert to UI state (Error state)
4. **UI Layer**: Display user-friendly error messages

**Example**:
```kotlin
val embeddingResult = embeddingProvider.embedText(text)
if (embeddingResult.isFailure) {
    Timber.e(embeddingResult.exceptionOrNull(), "Embedding failed")
    _uiState.value = SearchUIState.Error("Failed to process query")
} else {
    // Process results
}
```

## Testing Strategy

### Unit Tests
- Vector search algorithm
- Similarity calculations
- Data import/validation
- Repository queries

### Integration Tests
- End-to-end search workflow
- Database operations
- Embedding generation

### UI Tests (Instrumented)
- Screen navigation
- User interactions
- State updates
- Error handling

## Future Extensibility

### Adding New Features
1. **Custom Embeddings**: Replace MediaPipe provider with fine-tuned model
2. **Advanced Search**: Add filters, date ranges, field-specific search
3. **Analytics Dashboard**: Visualize search patterns, popular queries
4. **Sync**: Add encrypted cloud backup with custom sync logic
5. **Offline-First Sync**: Eventually consistent data across devices

### Plugin Architecture
- Domain layer provides interfaces
- Implementations via dependency injection
- Easy to swap implementations (e.g., different search engines)

---

**Designed for**: Production-quality research implementation
**Maintainability**: Clean architecture, comprehensive documentation, test coverage
**Extensibility**: Plugin-based design, dependency injection throughout
