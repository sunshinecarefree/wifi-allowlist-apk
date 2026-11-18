plugins {
    id("com.android.application")
}

android {
    namespace = "com.local.wifiallowlist"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.local.wifiallowlist"
        minSdk = 28
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
    }
}
