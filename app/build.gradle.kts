import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

val excludeLastFmKeys = project.findProperty("excludeLastFmKeys")?.toString()?.toBoolean() == true

val lastFmApiKey: String = if (excludeLastFmKeys) "" else (
    System.getenv("LASTFM_API_KEY")
        ?: localProperties.getProperty("lastfm.apiKey")
        ?: ""
)

val lastFmSharedSecret: String = if (excludeLastFmKeys) "" else (
    System.getenv("LASTFM_SHARED_SECRET")
        ?: localProperties.getProperty("lastfm.sharedSecret")
        ?: ""
)

android {
    namespace = "com.example.musicplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.musicplayer"
        minSdk = 36
        targetSdk = 36
        versionCode = 6
        versionName = "0.1.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "LASTFM_API_KEY", "\"$lastFmApiKey\"")
        buildConfigField("String", "LASTFM_SHARED_SECRET", "\"$lastFmSharedSecret\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // AndroidX Media3
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)

    // Coil Compose
    implementation(libs.coil.compose)

    // AndroidX Palette
    implementation(libs.androidx.palette.ktx)

    // OkHttp client for Last.fm API
    implementation(libs.okhttp)

    // Audio Tagging (MP3, FLAC, M4A, OGG)
    implementation(libs.jaudiotagger)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.json)
}
