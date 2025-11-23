plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    id("dagger.hilt.android.plugin")
    id("io.objectbox")
}

android {
    namespace = "com.knovik.skillvault"
    compileSdk = rootProject.ext.get("android_compile_sdk") as Int
    
    defaultConfig {
        applicationId = "com.knovik.skillvault"
        minSdk = rootProject.ext.get("android_min_sdk") as Int
        targetSdk = rootProject.ext.get("android_target_sdk") as Int
        versionCode = 1
        versionName = "1.0.0"
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // MediaPipe GPU support (optional)
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
        }
    }
    
    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
    }
    
    buildFeatures {
        compose = true
        viewBinding = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:${rootProject.ext.get("androidx_core_version")}")
    implementation("androidx.appcompat:appcompat:${rootProject.ext.get("androidx_appcompat_version")}")
    implementation("androidx.activity:activity-ktx:${rootProject.ext.get("androidx_activity_version")}")
    
    // Lifecycle & MVVM
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:${rootProject.ext.get("androidx_lifecycle_version")}")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:${rootProject.ext.get("androidx_lifecycle_version")}")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:${rootProject.ext.get("androidx_lifecycle_version")}")
    
    // Compose UI
    implementation("androidx.compose.ui:ui:${rootProject.ext.get("compose_version")}")
    implementation("androidx.compose.material3:material3:1.1.2")
    implementation("androidx.compose.ui:ui-tooling-preview:${rootProject.ext.get("compose_version")}")
    implementation("androidx.activity:activity-compose:${rootProject.ext.get("androidx_activity_version")}")
    debugImplementation("androidx.compose.ui:ui-tooling:${rootProject.ext.get("compose_version")}")
    
    // Material Design
    implementation("com.google.android.material:material:${rootProject.ext.get("material_version")}")
    implementation("androidx.constraintlayout:constraintlayout:${rootProject.ext.get("constraintlayout_version")}")
    
    // ObjectBox - Vector Database
    implementation("io.objectbox:objectbox-kotlin:${rootProject.ext.get("objectbox_version")}")
    implementation("io.objectbox:objectbox-android:${rootProject.ext.get("objectbox_version")}")
    
    // Room Database (complementary to ObjectBox)
    implementation("androidx.room:room-runtime:${rootProject.ext.get("room_version")}")
    kapt("androidx.room:room-compiler:${rootProject.ext.get("room_version")}")
    implementation("androidx.room:room-ktx:${rootProject.ext.get("room_version")}")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${rootProject.ext.get("coroutines_version")}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${rootProject.ext.get("coroutines_version")}")
    
    // Dependency Injection - Hilt
    implementation("com.google.dagger:hilt-android:${rootProject.ext.get("hilt_version")}")
    kapt("com.google.dagger:hilt-compiler:${rootProject.ext.get("hilt_version")}")
    
    // Networking
    implementation("com.squareup.retrofit2:retrofit:${rootProject.ext.get("retrofit_version")}")
    implementation("com.squareup.okhttp3:okhttp:${rootProject.ext.get("okhttp_version")}")
    implementation("com.squareup.okhttp3:logging-interceptor:${rootProject.ext.get("okhttp_version")}")
    
    // Serialization
    implementation("com.squareup.moshi:moshi-kotlin:${rootProject.ext.get("moshi_version")}")
    kapt("com.squareup.moshi:moshi-kotlin-codegen:${rootProject.ext.get("moshi_version")}")
    implementation("com.google.code.gson:gson:${rootProject.ext.get("gson_version")}")
    
    // MediaPipe - On-Device ML (Text Embeddings)
    implementation("com.google.mediapipe:mediapipe-tasks-text:${rootProject.ext.get("mediapipe_version")}")
    
    // Logging
    implementation("com.jakewharton.timber:timber:${rootProject.ext.get("timber_version")}")
    
    // Testing - Unit Tests
    testImplementation("junit:junit:${rootProject.ext.get("junit_version")}")
    testImplementation("org.mockito.kotlin:mockito-kotlin:${rootProject.ext.get("mockito_version")}")
    testImplementation("org.mockito:mockito-inline:${rootProject.ext.get("mockito_version")}")
    testImplementation("io.kotest:kotest-runner-junit5:${rootProject.ext.get("kotest_version")}")
    testImplementation("io.kotest:kotest-assertions-core:${rootProject.ext.get("kotest_version")}")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${rootProject.ext.get("coroutines_version")}")
    
    // Testing - Instrumented Tests
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:${rootProject.ext.get("androidx_test_version")}")
    androidTestImplementation("androidx.test.espresso:espresso-core:${rootProject.ext.get("espresso_version")}")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:${rootProject.ext.get("compose_version")}")
    androidTestDebugImplementation("androidx.compose.ui:ui-test-manifest:${rootProject.ext.get("compose_version")}")
}

// Hilt plugin configuration
hilt {
    enableExperimentalClasspathAggregation = true
}
