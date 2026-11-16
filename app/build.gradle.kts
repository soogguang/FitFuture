plugins { id("com.android.application") }
android {
    namespace = "com.fitfuture.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.fitfuture.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "com.fitfuture.app.FlowInstrumentation"
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies { implementation("androidx.activity:activity:1.13.0"); coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5") }
