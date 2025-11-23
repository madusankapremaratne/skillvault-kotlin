// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("com.android.library") version "8.2.0" apply false
    kotlin("android") version "1.9.20" apply false
    kotlin("kapt") version "1.9.20" apply false
    id("io.objectbox") version "4.1.0" apply false
}

dependencies {
    classpath("io.objectbox:objectbox-gradle-plugin:4.1.0") // Check version compatibility
}

// Centralized dependency versions
ext {
    set("kotlin_version", "1.9.20")
    set("android_min_sdk", 26)
    set("android_target_sdk", 34)
    set("android_compile_sdk", 34)
    
    // Core Android
    set("androidx_core_version", "1.12.0")
    set("androidx_appcompat_version", "1.6.1")
    set("androidx_lifecycle_version", "2.6.2")
    set("androidx_activity_version", "1.8.0")
    
    // UI
    set("material_version", "1.10.0")
    set("compose_version", "1.5.4")
    set("constraintlayout_version", "2.1.4")
    
    // Database & Storage
    set("objectbox_version", "5.0.1")
    set("room_version", "2.6.0")
    
    // Networking & Data
    set("retrofit_version", "2.10.0")
    set("okhttp_version", "4.11.0")
    set("moshi_version", "1.15.0")
    
    // Machine Learning / MediaPipe
    set("mediapipe_version", "0.10.0")
    
    // Coroutines & Async
    set("coroutines_version", "1.7.3")
    
    // Dependency Injection
    set("dagger_version", "2.48")
    set("hilt_version", "2.48")
    
    // Testing
    set("junit_version", "4.13.2")
    set("junit5_version", "5.9.3")
    set("androidx_test_version", "1.5.0")
    set("espresso_version", "3.5.1")
    set("mockito_version", "5.5.1")
    set("kotest_version", "5.7.2")
    
    // Logging & Analytics
    set("timber_version", "5.0.1")
    
    // JSON Processing
    set("gson_version", "2.10.1")
}
