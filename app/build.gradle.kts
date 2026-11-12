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
        versionCode = 16
        versionName = "2.11"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("CLEAN_TV_KEYSTORE_PATH")
            val keystorePassword = System.getenv("CLEAN_TV_KEYSTORE_PASSWORD")
            val keyAliasValue = System.getenv("CLEAN_TV_KEY_ALIAS")
            val keyPasswordValue = System.getenv("CLEAN_TV_KEY_PASSWORD")

            if (!keystorePath.isNullOrBlank() &&
                !keystorePassword.isNullOrBlank() &&
                !keyAliasValue.isNullOrBlank() &&
                !keyPasswordValue.isNullOrBlank()
            ) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
