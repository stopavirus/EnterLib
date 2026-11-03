plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "app.enterlib.tv"
    compileSdk = 34
    defaultConfig {
        applicationId = "app.enterlib.tv"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    buildTypes { release { isMinifyEnabled = false } }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}
dependencies { implementation(project(":ui-common")) }
