# 음식 인식 담당 개발 준비

## 구현 진행 (2026-11-18)

첫 단계의 서버/앱 연동 코드를 추가했다. `server/food_server.py`가 Gemini를 호출하고 `RemoteFoodAnalysis`가 사진을 업로드해 기존 S14에 음식 목록을 표시한다. 기본 모드는 데모이며 `foodApiUrl` 설정 시 서버를 사용한다. 영양 계산은 아직 데모다. 키와 모델 환경변수가 없어 실제 Gemini 호출은 검증하지 않았다. 실행법과 API 계약은 [서버 안내](../server/README.md)를 참고한다.

이후 사용자가 서버 키와 모델을 설정해 실제 음식명 입력 요청 성공을 확인했다. 사진 인식과 앱 전체 흐름 확인은 남아 있다.

## 최초 조사 상태 (2026-11-18, 연동 구현 전)

- 앱은 Java + Android XML이며 Compose/Kotlin 앱이 아니다.
- 로컬 Git 기준 origin: https://github.com/soogguang/FitFuture.git, 확인한 HEAD: cfd64fb. 원격 최신 상태를 fetch한 것은 아니다.
- 촬영 Intent, 갤러리 선택, 사진 표시, 음식 추가·삭제·이름·그램 수정, 확정 후 영양 분석 UI가 있다.
- `DemoServices.recognize()`는 어떤 사진이든 돈가스 정식으로 처리한다. `analyze()`도 소수 음식의 예시 표만 사용한다.
- 실제 서버, Gemini 호출, 식약처 DB 연동, HTTP 클라이언트, INTERNET 권한은 없다.
- Figma 원본은 접근하지 못했다. 화면 범위는 저장소의 `SCREEN_MAP.md`, XML, Java UI 코드 기준이며 최신 Figma와 대조가 필요하다.

## PC 환경과 실행

설치 확인: Android Studio JBR 25.0.3, 시스템 JDK 17, Android SDK 36, build-tools 36.0.0, adb.
이 PC에서 debug APK·androidTest APK 빌드 성공, Lint 성공(오류 0, 경고 15). 기기 실행/Instrumentation 실행과 실제 AI 인식 검증은 수행하지 않았다. 최초 Lint의 SDK 경로 이스케이프 오류를 수정하고 `:app:lintDebug --rerun-tasks`로 재검증했다.
프로젝트 설정: Gradle 9.5.0, AGP 9.3.3, Gradle daemon JVM 25, Java 소스 호환성 17, minSdk 26.
Java 소스 버전 17과 Gradle 실행 JVM 25는 별개다. 전역 JAVA_HOME을 바꾸지 않고 내장 JBR을 사용한다.

1. Android Studio에서 이 `FitFuture` 폴더를 연다.
2. Settings → Build, Execution, Deployment → Build Tools → Gradle에서 JBR 25/기존 daemon JVM 25 설정을 확인한다.
3. SDK Manager에서 Android SDK Platform 36과 Build-Tools 36.0.0을 확인한다.
4. 로컬 `local.properties`에 실제 `sdk.dir`을 지정한다. 이 파일은 Git에서 제외된다.
5. PowerShell에서 실행한다.

```powershell
.\scripts\android-build.ps1
.\scripts\android-build.ps1 :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

이 PC처럼 PowerShell 실행 정책이 ps1 실행을 막으면 시스템 정책을 변경하는 대신 해당 프로세스에만 허용한다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\android-build.ps1 :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

스크립트는 해당 실행 동안만 JBR을 선택하며 한글 경로 검사를 우회한다. 한글 경로로 인한 실제 도구 오류가 나면 ASCII 경로의 별도 checkout에서 확인한다.
Android Studio Sync에는 스크립트의 우회 옵션이 적용되지 않는다. 동일 경로 검사 오류가 보이면 로컬 개발용 `gradle.properties`에 `android.overridePathCheck=true`를 추가하거나 ASCII 경로 checkout을 사용한다. 이 옵션은 실험적이며 실제 경로 호환성을 보장하지 않는다.
초기 실행에는 Gradle과 의존성 다운로드가 필요하다. APK: `app/build/outputs/apk/debug/app-debug.apk`.
앱 실행은 Android Studio에서 API 26 이상 기기/에뮬레이터를 선택하고 app Run.
`scripts/verify_flow.py`는 테스트 기기의 앱 데이터를 바꾸므로 개인 기록이 있는 기기에 무심코 실행하지 않는다.

## 담당 범위와 연결 지점

담당: 음식 입력 → 인식 → 사용자 확인/분량 수정 → 영양 계산 → 확정 식사 반환.
추천 순위·대안 생성과 아바타 계산은 각 담당 모듈에서 수행한다.

| 코드 | 역할 |
| --- | --- |
| `domain/FeatureServices.java` | `FoodAnalysisService.recognize(text, localPhotoUri)`, `analyze(confirmedFoods)` 계약 |
| `domain/Models.java` | `Meal`, `FoodPortion(name, grams)`, `Nutrition` 공통 모델 |
| `data/DemoServices.java` | 교체할 데모 구현 |
| `MainActivity.java` | 서비스 주입, worker 실행, `recognize()`, `confirmMeal()` |
| `ui/MealScreens.java` | 입력·진행·확인·영양 결과 화면 |
| `data/PhotoProvider.java` | 촬영 이미지 URI 제공 |

화면: S09 입력 방식, S10 촬영, S11 텍스트, S12 진행, S14 음식/양 확인, S13 영양 결과, S15 인식 실패, S28 연결 오류, S29 영양 매칭 실패.
인식 후 S14에서 확인하고 `analyze()` 성공 시 S13으로 이동한다. 번호 순서와 실제 이동 순서는 다르다.

새 `FoodAnalysisService` 구현체를 주입하고 나머지 두 서비스는 데모로 유지할 수 있다.

```java
DemoServices demo = new DemoServices();
services = new FeatureServices(realFoodAnalysis, demo, demo);
```

단, 데모 추천은 내부에서 자체 예시 영양표의 `analyze()`를 호출하므로 새 음식의 인식·영양 분석이 성공해도 추천 단계에서 실패할 수 있다. 추천 담당자와 함께 교체하거나 음식 모듈 검증은 S13까지 분리한다.
`onCreate()`에서 추천을 직접 호출하는 기존 경로도 있어 실제 네트워크 추천으로 교체할 때 worker로 이동해야 한다.

## 권장 구현 순서

1. **현재 데모 실행:** 텍스트 돈가스 → 음식/양 수정 → 영양 결과까지 확인. 촬영/갤러리 취소와 성공 경로도 확인.
2. **팀 계약 확정:** 서버 담당·주소, 요청/응답, 영양 데이터 출처·기준량, 오류 코드, 음식 식별자를 정한다. 아직 확정된 서버 명세는 없다.
3. **인식 한 경로 완성:** 이미지 업로드 → 서버의 Gemini 이미지 인식 → JSON 검증 → `Meal` 변환 → 기존 S14 화면. 직접 모델 학습은 초기 MVP 범위에서 제외하는 것을 제안한다.
4. **영양 매칭:** 인식 음식명과 DB 식품을 매칭하고 사용자가 확정한 g로 kcal/탄수화물/단백질/지방을 계산한다. 100g 기준이면 `영양값 × 확정g / 100` 후 음식별 합산. 1회 제공량 기준 데이터는 먼저 기준량을 정규화한다.
5. **실패/수정 경로:** 비음식 사진, 빈 결과, 알 수 없는 음식, 응답 지연, 오프라인, 잘못된 JSON, 없는 DB 식품, 분량 수정 후 재계산을 확인한다.
6. **모듈 통합:** `Meal.confirmed=true`와 `nutrition`이 준비된 뒤 추천으로 전달한다. 기존 화면 흐름 검증을 다시 실행한다.

## 제안 아키텍처와 주의할 계약

Android → 팀 서버 → Gemini(음식 후보/구성) + 영양 DB(수치) → Android 확인 화면.
Gemini는 이미지 입력을 지원한다. 모델 ID/가격/이용 한도는 구현 시 계정에서 확인하고 서버 설정으로 관리한다.
API 키는 서버 환경변수에 둔다. Android BuildConfig나 APK에 공급자 비밀 키를 넣지 않는다.
이미지에서 추정한 g는 초깃값으로 취급하고 사용자가 확정한다. 초기 범위를 정해진 한식/학식 메뉴로 제한하면 평가하기 쉽다.

서버 API 제안(미합의):

- `POST /food/recognize`: multipart 이미지 또는 텍스트 → 식사 이름, 음식 목록, 추정 분량, 매칭 후보.
- `POST /food/analyze`: 확정 음식 식별자와 g 목록 → 음식별/총 영양, 출처, 기준량.
- `content://` URI 문자열은 서버에서 열 수 없다. `ContentResolver`로 바이트를 읽어 MIME 검증·방향 보정·크기 제한 후 전송한다.
- 현재 모델에는 식품 ID·DB 출처·후보·신뢰도 필드가 없다. 서버 DTO는 어댑터 안에서 변환하고 공통 모델 확장은 팀원과 합의한다. 추정 신뢰도를 검증된 확률처럼 표시하지 않는다.
- `RECOGNITION` → S15, `NUTRITION` → S29 영양 매칭 실패, 그 외 연결 예외 → S28. 빈 결과나 모르는 식품을 임의의 성공값/0 kcal로 만들지 않는다.
- 현재 인터페이스는 동기 반환이며 호출부가 worker를 사용한다. 타임아웃을 설정하고 화면 전환 뒤 오래된 결과가 덮어쓰지 않도록 기존 요청 세대 검사를 유지한다.
- 서버 연결 단계에서 INTERNET 권한과 HTTP 구현을 추가한다. 현재 시스템 카메라 Intent를 유지한다면 별도 CameraX나 CAMERA 권한은 필요하지 않다.

## 첫 완료 기준

- 실제로 서로 다른 음식 사진을 넣었을 때 내용에 맞는 결과가 나오는지, 비음식 사진을 거절하는지 확인한다.
- 음식명 수정·항목 추가/삭제·g 변경 후 영양 합계가 재계산된다.
- DB 출처·기준량이 확인되고 후보가 없으면 선택/재입력 경로가 있다.
- 작은 한국 음식 평가 세트를 만들고 음식명 정답률, 매칭 성공률, 실패 유형과 응답 시간을 기록한다. 목표치는 팀이 합의한다.
- 취소/오프라인/재시도/회전/빠른 재입력에서 상태가 꼬이지 않는다.
- 추천 담당자가 확정된 `Meal`을 받을 수 있다.

참고: [모듈 계약](INTEGRATION.md), [화면 지도](SCREEN_MAP.md), [기존 검증](VERIFICATION.md), [Gemini 이미지 입력](https://ai.google.dev/gemini-api/docs/image-understanding), [Android JDK 설정](https://developer.android.com/build/jdks).
