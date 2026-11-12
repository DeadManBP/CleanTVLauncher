plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ca.cleantvlauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "ca.cleantvlauncher2"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "2.0"
    }

    signingConfigs {
        create("stable") {
            // Supplied only by GitHub Actions secrets. Never commit the keystore.
            val storeFilePath = System.getenv("CLEANTV_KEYSTORE_FILE")
            if (!storeFilePath.isNullOrBlank()) {
                storeFile = file(storeFilePath)
            }
            storePassword = System.getenv("CLEANTV_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("CLEANTV_KEY_ALIAS")
            keyPassword = System.getenv("CLEANTV_KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            // CI assigns the permanent signing key; local builds retain the
            // standard Android debug key until the permanent key is configured.
            if (!System.getenv("CLEANTV_KEYSTORE_FILE").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("stable")
            }
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
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
