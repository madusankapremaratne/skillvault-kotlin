package com.knovik.skillvault.di

import android.content.Context
import com.knovik.skillvault.data.entity.Resume
import com.knovik.skillvault.data.entity.ResumeEmbedding
import com.knovik.skillvault.data.entity.SearchQuery
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.ObjectBox
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
        val boxStore = ObjectBox.builder()
            .androidContext(context)
            .build()
        
        Timber.d("ObjectBox initialized at: ${boxStore.dbFile}")
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

/**
 * Provides logging setup for Timber.
 */
@Module
@InstallIn(SingletonComponent::class)
object LoggingModule {

    @Singleton
    @Provides
    fun provideTimberTree(): Unit {
        if (!::isTimberInitialized.isInitialized) {
            Timber.plant(Timber.DebugTree())
            isTimberInitialized = true
        }
    }

    private lateinit var isTimberInitialized: Boolean
}
