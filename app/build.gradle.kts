import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.aariz.freshtrack"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aariz.freshtrack"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Load Cloudinary credentials from local.properties
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { localProperties.load(it) }
        }

        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", "\"${localProperties.getProperty("CLOUDINARY_CLOUD_NAME", "")}\"")
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
        buildConfig = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // Firebase BOM — manages all Firebase versions automatically
    implementation(platform("com.google.firebase:firebase-bom:34.3.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")

    // Google Sign-In / Credentials
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("androidx.credentials:credentials:1.6.0-beta03")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0-beta03")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // UI
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // Lifecycle (Java-friendly, no ktx)
    implementation("androidx.lifecycle:lifecycle-runtime:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.7")

    // WorkManager (Java-friendly, no ktx)
    implementation("androidx.work:work-runtime:2.9.1")

    // Camera & MLKit
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")
    implementation("androidx.camera:camera-core:1.4.0")
    implementation("androidx.camera:camera-camera2:1.4.0")
    implementation("androidx.camera:camera-lifecycle:1.4.0")
    implementation("androidx.camera:camera-view:1.4.0")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Image loading & compression
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("id.zelory:compressor:3.0.1")

    // Cloudinary
    implementation("com.cloudinary:cloudinary-android:3.0.2")
    implementation("com.cloudinary:cloudinary-core:1.38.0")

    // Charts & Animations
    implementation("com.airbnb.android:lottie:6.1.0")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Ads
    implementation("com.google.android.gms:play-services-ads:24.7.0")
}