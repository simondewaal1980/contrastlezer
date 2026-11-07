plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "nl.contrastlezer"
    compileSdk = 36

    defaultConfig {
        applicationId = "nl.contrastlezer"
        minSdk = 33
        targetSdk = 36
        versionCode = 2
        versionName = "0.02 Alpha"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // ML Kit levert een grote native bibliotheek per ABI. Telefoons met
            // Android 13+ zijn 64-bit; alleen debug houdt alle ABI's voor de emulator.
            ndk { abiFilters += "arm64-v8a" }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        aidl = false
        buildConfig = false
        shaders = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.exifinterface)

    implementation(libs.mlkit.text.recognition)

    testImplementation(libs.junit)
}
