plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val coreArchive = file("libs/libv2ray.aar")
val hasEngine: Boolean = coreArchive.exists()
val githubRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
val appVersionCode = githubRunNumber ?: 3
val appVersionName = githubRunNumber?.let { "2.1.0-$it" } ?: "2.1.0"

android {
    namespace = "app.nebulabox"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.nebulabox"
        minSdk = 24
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        resourceConfigurations += listOf("en", "fa")
        buildConfigField("boolean", "HAS_ENGINE", "$hasEngine")
    }

    signingConfigs {
        val localKeystore = file("../keystore/nebula.keystore")
        if (localKeystore.exists()) {
            create("nebula") {
                storeFile = localKeystore
                storePassword = (findProperty("javidtun.storePassword") as String?) ?: "nebulabox"
                keyAlias = (findProperty("javidtun.keyAlias") as String?) ?: "nebula"
                keyPassword = (findProperty("javidtun.keyPassword") as String?) ?: "nebulabox"
            }
        }
    }

    // One APK per architecture, so nobody downloads the cores of three other machines.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = false
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        // Free software build: no proprietary dependencies, sources buildable by F-Droid.
        create("fdroid") {
            dimension = "distribution"
            versionNameSuffix = ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (file("../keystore/nebula.keystore").exists()) {
                signingConfigs.getByName("nebula")
            } else {
                null
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
        implementation(files("libs/libv2ray.aar"))
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
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.google.zxing:core:3.5.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
