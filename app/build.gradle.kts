plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ca.cleantvlauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "ca.cleantvlauncher"
        minSdk = 21
        targetSdk = 34
        versionCode = 15
        versionName = "2.10"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

