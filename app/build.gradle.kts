plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    kotlin("plugin.serialization")
}

android {
    namespace = "com.example.hci_demo"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.hci_demo"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // --- 核心基础 ---
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.activity:activity-compose:1.9.0")

    // --- UI 设计模块 ---
    implementation(platform("androidx.compose:compose-bom:2024.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // --- 运动效果模块 ---
    implementation("androidx.compose.animation:animation:1.6.7")
    implementation("androidx.compose.foundation:foundation:1.6.7")

    // --- 后端逻辑模块  ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1") // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3") // JSON解析
    implementation("androidx.lifecycle:lifecycle-service:2.8.0") // 前台服务支持

    // 调试工具
    debugImplementation("androidx.compose.ui:ui-tooling")
}