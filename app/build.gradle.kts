plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.hymate"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // 스토어 고유 식별자. Kotlin 패키지(namespace)와 달라도 됨.
        applicationId = "ai.hymate.app"
        minSdk = 28
        // Pleos Connect 플랫폼이 API 34라 런타임 동작을 34에 맞춰요. (compileSdk는 설치된 37 유지 — compileSdk ≥ targetSdk)
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    // AAOS에서 제공하는 라이브러리. 일반 개발 기기에도 설치할 수 있어요.
    useLibrary("android.car", false)
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Pleos Connect Vehicle SDK
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.tts)
    implementation(libs.pleos.stt)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
