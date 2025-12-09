// app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.licznikusmiechow"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.licznikusmiechow"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // mniejsze APK – tylko popularne ABI
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures { viewBinding = true }

    // Java 21 + desugaring
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        isCoreLibraryDesugaringEnabled = true
    }
}
kotlin { jvmToolchain(21) }

dependencies {
    // face landmarker
    implementation ("com.google.mediapipe:tasks-vision:0.10.29")

    // Desugaring (dla nowszych API Javy na starszych Androidach)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    implementation("com.google.android.material:material:1.12.0")
    // CameraX
    val camerax = "1.4.0"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    // TensorFlow Lite
    implementation("org.tensorflow:tensorflow-lite:2.17.0")

    // OpenCV jako moduł (z logów: :opencv)
    implementation(project(":opencv"))
    // Testy jednostkowe (JUnit4)
    testImplementation("junit:junit:4.13.2")

    // Testy instrumentacyjne (AndroidX Test + JUnit4)
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
