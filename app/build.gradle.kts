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
        versionCode = 10
        versionName = "2.5"
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

dependencies {
    // App drawer grid. A RecyclerView, not a GridView — GridView swallows
    // short DPAD_CENTER presses (the v2.0–v2.2 long-press bug).
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
