package com.knovik.skillvault

import android.app.Application
import com.knovik.skillvault.data.entity.ResumeEmbedding_
import com.knovik.skillvault.data.entity.SearchQuery_
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Application entry point with Hilt dependency injection support.
 */
@HiltAndroidApp
class SkillVaultApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.d("SkillVault Application initialized in DEBUG mode")
        } else {
            // In production, you might want custom crash reporting
            Timber.plant(CrashReportingTree())
            Timber.d("SkillVault Application initialized in RELEASE mode")
        }
        
        // Initialize analytics, crash reporting, etc.
        setupCrashReporting()
    }

    /**
     * Setup crash reporting for production builds.
     */
    private fun setupCrashReporting() {
        // TODO: Integrate Firebase Crashlytics or similar
        Timber.d("Crash reporting setup completed")
    }

    /**
     * Custom Timber tree for production crash reporting.
     */
    private class CrashReportingTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority >= android.util.Log.ERROR) {
                // Send to crash reporting service
                // Crashlytics.crashlyticsKit.recordException(t)
                super.log(priority, tag, message, t)
            }
        }
    }
}
