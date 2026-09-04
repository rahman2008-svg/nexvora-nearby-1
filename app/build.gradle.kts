plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.secrets)
}

android {
    namespace = "com.example"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.aistudio.nexvoranearby.vxnkpq"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePath =
                System.getenv("KEYSTORE_PATH")
                    ?: "${rootDir}/my-upload-key.jks"

            storeFile = file(keystorePath)
            storePassword = System.getenv("STORE_PASSWORD")
            keyAlias = "upload"
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isCrunchPngs = false
            isMinifyEnabled = false

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )

            signingConfig =
                signingConfigs.getByName("release")
        }

        debug {
            // Android's default debug signing is used automatically.
            // No custom debug.keystore is required.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }
}

/*
 * Secrets Gradle Plugin
 *
 * Reads:
 *   .env
 *
 * Falls back to:
 *   .env.example
 */
secrets {
    propertiesFileName = ".env"
    defaultPropertiesFileName = ".env.example"

    ignoreList.add(
        "FIREBASE_APPCHECK_DEBUG_TOKEN"
    )
}

dependencies {

    // --------------------------------------------------
    // Jetpack Compose
    // --------------------------------------------------

    implementation(
        platform(libs.androidx.compose.bom)
    )

    implementation(
        libs.androidx.activity.compose
    )

    implementation(
        libs.androidx.compose.material.icons.core
    )

    implementation(
        libs.androidx.compose.material.icons.extended
    )

    implementation(
        libs.androidx.compose.material3
    )

    implementation(
        libs.androidx.compose.ui
    )

    implementation(
        libs.androidx.compose.ui.graphics
    )

    implementation(
        libs.androidx.compose.ui.tooling.preview
    )


    // --------------------------------------------------
    // AndroidX Core
    // --------------------------------------------------

    implementation(
        libs.androidx.core.ktx
    )


    // --------------------------------------------------
    // Lifecycle
    // --------------------------------------------------

    implementation(
        libs.androidx.lifecycle.runtime.compose
    )

    implementation(
        libs.androidx.lifecycle.runtime.ktx
    )

    implementation(
        libs.androidx.lifecycle.viewmodel.compose
    )


    // --------------------------------------------------
    // Navigation
    // --------------------------------------------------

    implementation(
        libs.androidx.navigation.compose
    )


    // --------------------------------------------------
    // Kotlin Serialization
    // --------------------------------------------------

    implementation(
        libs.kotlinx.serialization.json
    )


    // --------------------------------------------------
    // Room Database
    // --------------------------------------------------

    implementation(
        libs.androidx.room.ktx
    )

    implementation(
        libs.androidx.room.runtime
    )


    // --------------------------------------------------
    // Networking / JSON
    // --------------------------------------------------

    implementation(
        libs.converter.moshi
    )

    implementation(
        libs.moshi.kotlin
    )

    implementation(
        libs.okhttp
    )

    implementation(
        libs.retrofit
    )

    implementation(
        libs.logging.interceptor
    )


    // --------------------------------------------------
    // Coroutines
    // --------------------------------------------------

    implementation(
        libs.kotlinx.coroutines.android
    )

    implementation(
        libs.kotlinx.coroutines.core
    )


    // --------------------------------------------------
    // Unit Tests
    // --------------------------------------------------

    testImplementation(
        libs.androidx.compose.ui.test.junit4
    )

    testImplementation(
        libs.androidx.core
    )

    testImplementation(
        libs.androidx.junit
    )

    testImplementation(
        libs.junit
    )

    testImplementation(
        libs.kotlinx.coroutines.test
    )

    testImplementation(
        libs.robolectric
    )

    testImplementation(
        libs.roborazzi
    )

    testImplementation(
        libs.roborazzi.compose
    )

    testImplementation(
        libs.roborazzi.junit.rule
    )


    // --------------------------------------------------
    // Android Instrumentation Tests
    // --------------------------------------------------

    androidTestImplementation(
        platform(libs.androidx.compose.bom)
    )

    androidTestImplementation(
        libs.androidx.compose.ui.test.junit4
    )

    androidTestImplementation(
        libs.androidx.espresso.core
    )

    androidTestImplementation(
        libs.androidx.junit
    )

    androidTestImplementation(
        libs.androidx.runner
    )


    // --------------------------------------------------
    // Debug Dependencies
    // --------------------------------------------------

    debugImplementation(
        libs.androidx.compose.ui.test.manifest
    )

    debugImplementation(
        libs.androidx.compose.ui.tooling
    )


    // --------------------------------------------------
    // KSP / Code Generation
    // --------------------------------------------------

    ksp(
        libs.androidx.room.compiler
    )

    ksp(
        libs.moshi.kotlin.codegen
    )
}
