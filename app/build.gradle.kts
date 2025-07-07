plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
}

android {
    namespace = "cpp.test"
    compileSdk = 35
    ndkVersion = "25.2.9519653"

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(System.getenv("ORG_GRADLE_PROJECT_storeFile") ?: "app/release-key.jks")
            storePassword = System.getenv("ORG_GRADLE_PROJECT_storePassword")
            keyAlias = System.getenv("ORG_GRADLE_PROJECT_keyAlias")
            keyPassword = System.getenv("ORG_GRADLE_PROJECT_keyPassword")
        }
    }

    defaultConfig {
        applicationId = "cpp.test"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        
        ndk {
            abiFilters("armeabi-v7a", "arm64-v8a") 
        }

        resources {
            localeFilters.add("en") 
        }
    }
    
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "2.1.21"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.compose.ui:ui:1.9.0-beta02")
    implementation("androidx.compose.material3:material3:1.4.0-alpha17")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
}
