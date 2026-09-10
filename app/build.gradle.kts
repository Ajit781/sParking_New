plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.innovus.sparkingnew"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.innovus.sparkingnew"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("armeabi-v7a", "armeabi")
        }

        buildConfigField("String", "BASE_URL", "\"https://vigpl.com/SParkingRestAPI/api/\"")
        // Staging commented out - Production currently uses same working endpoint
        // buildConfigField("String", "BASE_URL_STAGING", "\"https://vigpl.com/SParkingRestAPI/api/\"")
        buildConfigField("String", "BASE_URL_PROD", "\"https://vigpl.com/SParkingRestAPI/api/\"")
        buildConfigField("Boolean", "IS_ENCRYPTION_ENABLED", "false")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
    implementation("com.google.zxing:core:3.5.3")

    // CameraX and ML Kit Text Recognition (OCR)
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("com.google.mlkit:text-recognition:16.0.1")

    testImplementation(libs.junit)
    testImplementation("org.json:json:20231013")
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}