plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.autoclicker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.autoclicker"
        minSdk = 24 // dispatchGesture() exige API 24+
        targetSdk = 34
        versionCode = 3
        versionName = "1.2.0"
    }

    // Keystore fixa: todas as builds (inclusive as do GitHub Actions) usam a mesma
    // assinatura, então dá para instalar uma versão nova por cima da anterior.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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
    implementation("androidx.core:core-ktx:1.13.1")
}
