# SkillVault ProGuard Rules
# Keep all code needed for production while optimizing size

# Keep MediaPipe classes
-keep class com.google.mediapipe.** { *; }
-keep class org.tensorflow.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn org.tensorflow.**

# Keep ObjectBox generated classes
-keep class io.objectbox.** { *; }
-keep interface io.objectbox.** { *; }
-keep class com.knovik.skillvault.data.entity.** { *; }
-keep class com.knovik.skillvault.data.entity.**_ { *; }
-keepclasseswithmembernames class * {
    @io.objectbox.annotation.* <fields>;
    @io.objectbox.annotation.* <methods>;
}

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep interface dagger.hilt.** { *; }
-keep class **_HiltComponents { *; }
-keep class **_HiltModules { *; }
-keep class hilt_aggregated_deps.** { *; }

# Keep Kotlin classes
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.**

# Keep Android Lifecycle classes
-keep class androidx.lifecycle.** { *; }
-keep interface androidx.lifecycle.** { *; }

# Keep Serialization classes
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-keep class * implements com.squareup.moshi.JsonAdapter { <init>(...); }

# Keep OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# Keep Retrofit
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**

# Keep Timber
-keep class timber.log.** { *; }

# Generic rules
-keep class com.knovik.skillvault.** { *; }
-keep interface com.knovik.skillvault.** { *; }

# Preserve line numbers for crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Remove logging in release builds
-assumenosideeffects class timber.log.Timber {
    public static *** v(...);
    public static *** d(...);
}

# Allow optimization
-optimizationpasses 5
-allowaccessmodification
-dontusemixedcaseclassnames
