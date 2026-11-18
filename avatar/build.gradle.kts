plugins { id("com.android.library") }

android {
    namespace = "com.fitfuture.avatar"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // 셀카 얼굴 윤곽 (모델 내장형: 설치 즉시 오프라인 동작, 사진이 기기 밖으로 나가지 않음)
    implementation("com.google.mlkit:face-detection:16.1.7")
    testImplementation("junit:junit:4.13.2")
}
