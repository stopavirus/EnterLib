plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "app.enterlib.ui"
    compileSdk = 34
    defaultConfig { minSdk = 24 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}
dependencies {
    api(project(":data"))
    api(platform("androidx.compose:compose-bom:2024.06.00"))
    api("androidx.compose.ui:ui")
    api("androidx.compose.foundation:foundation")
    api("androidx.compose.material3:material3")
    api("androidx.compose.material:material-icons-core")
    api("androidx.core:core-ktx:1.13.1")
    api("androidx.activity:activity-compose:1.9.0")
    api("androidx.navigation:navigation-compose:2.7.7")
    api("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    api("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    api("io.coil-kt:coil-compose:2.6.0")
    api("androidx.media3:media3-exoplayer:1.3.1")
    api("androidx.media3:media3-exoplayer-hls:1.3.1")
    api("androidx.media3:media3-exoplayer-dash:1.3.1")
    api("androidx.media3:media3-ui:1.3.1")
}
