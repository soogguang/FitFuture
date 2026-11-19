plugins { id("com.android.application") }
val foodApiUrl = providers.gradleProperty("foodApiUrl").orElse("").get()
require(foodApiUrl.isEmpty() || foodApiUrl.matches(Regex("https?://[A-Za-z0-9.\\-:]+/?"))) {
    "foodApiUrl must be an http(s) origin without a path"
}
android {
    namespace = "com.fitfuture.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.fitfuture.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "FOOD_API_URL", "\"$foodApiUrl\"")
        testInstrumentationRunner = "com.fitfuture.app.FlowInstrumentation"
        buildConfigField("String", "RECOMMENDATION_BASE_URL", "\"" +
            providers.gradleProperty("recommendationBaseUrl").orElse("http://10.0.2.2:8000").get()
                .replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
    }
    buildFeatures { buildConfig = true }
    sourceSets.getByName("androidTest").assets.srcDir("../recommendation-server/examples")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    implementation(project(":avatar"))
    implementation("androidx.activity:activity:1.13.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
