plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.ambientcoccoc"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.ambientcoccoc"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "0.2"
    }
}
