package com.knovik.skillvault.di

import android.content.Context
import com.knovik.skillvault.data.entity.Resume
import com.knovik.skillvault.data.entity.ResumeEmbedding
import com.knovik.skillvault.data.entity.SearchQuery
import com.knovik.skillvault.data.entity.PerformanceMetric
import io.objectbox.Box
import io.objectbox.BoxStore
import com.knovik.skillvault.data.entity.MyObjectBox

import io.objectbox.kotlin.boxFor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import javax.inject.Singleton

/**
 * Dependency injection module for data layer.
 * Provides singleton instances of repositories and databases.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    /**
     * Provide ObjectBox BoxStore singleton.
     * BoxStore is the entry point to ObjectBox database.
     */
    @Singleton
    @Provides
    fun provideBoxStore(@ApplicationContext context: Context): BoxStore {
        val boxStore = MyObjectBox.builder()
            .androidContext(context)
            .build()
        
        Timber.d("ObjectBox initialized: $boxStore")
        return boxStore
    }

    /**
     * Provide Box<Resume> for Resume entity operations.
     */
    @Singleton
    @Provides
    fun provideResumeBox(boxStore: BoxStore): Box<Resume> {
        return boxStore.boxFor()
    }

    /**
     * Provide Box<ResumeEmbedding> for embedding operations.
     */
    @Singleton
    @Provides
    fun provideResumeEmbeddingBox(boxStore: BoxStore): Box<ResumeEmbedding> {
        return boxStore.boxFor()
    }

    /**
     * Provide Box<SearchQuery> for search analytics.
     */
    @Singleton
    @Provides
    fun provideSearchQueryBox(boxStore: BoxStore): Box<SearchQuery> {
        return boxStore.boxFor()
    }

    /**
     * Provide Box<PerformanceMetric> for benchmarking.
     */
    @Singleton
    @Provides
    fun providePerformanceMetricBox(boxStore: BoxStore): Box<PerformanceMetric> {
        return boxStore.boxFor()
    }

    /**
     * Provide OkHttpClient for network operations.
     */
    @Singleton
    @Provides
    fun provideOkHttpClient(): okhttp3.OkHttpClient {
        return okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    /**
     * Provide ResumeCSVImporter for CSV import operations.
     */
    @Singleton
    @Provides
    fun provideResumeCSVImporter(
        @ApplicationContext context: Context,
        okHttpClient: okhttp3.OkHttpClient
    ): com.knovik.skillvault.data.importer.ResumeCSVImporter {
        return com.knovik.skillvault.data.importer.ResumeCSVImporter(context, okHttpClient)
    }
}

/**
 * Dependency injection module for domain layer.
 * Provides use case and service implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    // Domain services are automatically provided via constructor injection
    // No additional configuration needed as they're injected into repositories
}

/**
 * Dependency injection module for UI layer.
 * Provides ViewModels and UI-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object UIModule {

    // ViewModels are provided via ViewModelFactory or directly through HiltViewModel annotation
}


