plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// The sing-box native engine (libbox.aar) is produced separately by
// scripts/build-libbox.sh because it needs a Go + NDK toolchain.
// When it is present the real tunnel implementation is compiled in;
// when it is absent the app still builds and runs with an inert engine
// so the UI can be developed and shipped independently.
val libboxAar = file("libs/libbox.aar")
val hasEngine: Boolean = libboxAar.exists()

android {
    namespace = "app.nebulabox"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.nebulabox"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        resourceConfigurations += listOf("en", "fa")
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
        buildConfigField("boolean", "HAS_ENGINE", "$hasEngine")
    }

    signingConfigs {
        create("nebula") {
            storeFile = file("../keystore/nebula.keystore")
            storePassword = "nebulabox"
            keyAlias = "nebula"
            keyPassword = "nebulabox"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = if (file("../keystore/nebula.keystore").exists()) {
                signingConfigs.getByName("nebula")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    sourceSets {
        getByName("main") {
            if (hasEngine) {
                java.srcDir("src/engine/java")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += listOf("/META-INF/{AL2.0,LGPL2.1}")
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    if (hasEngine) {
        implementation(files("libs/libbox.aar"))
    }

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.google.zxing:core:3.5.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
