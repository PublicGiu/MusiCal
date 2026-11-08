# Proguard rules for personal audio player (Android 16 / API 36)

# --- Room Database Rules ---
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class * extends androidx.room.RoomDatabase
-keep class * implements androidx.room.RoomOpenDelegate
-dontwarn androidx.room.paging.**

# --- Data Models and Room Entities ---
-keep class com.example.musicplayer.data.** { *; }
-keep class com.example.musicplayer.data.lastfm.cache.** { *; }

# --- AndroidX Media3 / ExoPlayer ---
-keep class androidx.media3.session.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.common.** { *; }
-dontwarn androidx.media3.**

# --- OkHttp and Okio ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# --- Coil Image Loading ---
-dontwarn coil.**
-keep class coil.** { *; }

# --- Kotlin Coroutines ---
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# --- Audio Tagging (jaudiotagger) ---
-dontwarn org.jaudiotagger.**
-keep class org.jaudiotagger.** { *; }
-keepclassmembers class org.jaudiotagger.** { *; }
