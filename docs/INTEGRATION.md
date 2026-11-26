# 세 모듈 연동 가이드

## 연결 지점

현재 앱은 `MainActivity.onCreate()`에서 아래 어댑터를 주입합니다. 인터페이스는 기존 Java `FeatureServices`를 재사용합니다.

- 음식 인식: `foodApiUrl` 지정 시 `RemoteFoodAnalysis` → `POST /food/recognize`, 미지정 시 `DemoServices` (개발용 예시)
- 영양 분석: `foodApiUrl` 지정 시 `RemoteFoodAnalysis` → 앱 내 `NutritionDatabase` (식약처 음식 DB), 미지정 시 `ServerFoodAnalysisService` → `POST /nutrition`
- 추천: `HttpMealRecommendationService` → `POST /recommend`
- 미래 비교: 기존 `AvatarFutureService` (보존)

```java
services = new FeatureServices(
    foodAnalysisImplementation,
    recommendationImplementation,
    futureAvatarImplementation
);
```

서버 명세와 실행 방법은 [음식 인식 서버](../server/README.md)와 [영양·추천 서버](../recommendation-server/README.md)를 참고하세요. `foodApiUrl` 지정 시 `RemoteFoodAnalysis`가 인식 서버 호출과 로컬 식약처 영양 계산을 담당합니다. 미지정 시 `ServerFoodAnalysisService`가 예시 인식을 사용하고 영양은 추천 서버에서 계산합니다. `RecommendationJson`이 HTTP 응답을 `Models.Alternative`로 변환합니다. Android에는 서버 URL만 있고 Gemini/식약처 API 키는 없습니다. HTTP는 debug의 loopback/에뮬레이터 호스트에만 허용합니다.

두 로컬 서버는 서로 다른 포트로 실행합니다. 예를 들어 음식 인식은 `python3 server/food_server.py --port 8765`, 영양·추천은 기본 8000 포트를 사용하고, 앱은 `./gradlew :app:assembleDebug -PfoodApiUrl=http://10.0.2.2:8765 -PrecommendationBaseUrl=http://10.0.2.2:8000`으로 빌드합니다.

## 공통 규칙

- 열량 kcal, 음식·영양소 g, 체중 kg, 키 cm, 예산 원, 비교 기간 개월
- 각 인터페이스는 동기 반환 계약이며 **앱이 별도 Executor에서 호출**합니다. 서버 구현체도 UI 스레드에서 직접 호출하지 않습니다.
- 실패는 `ModuleException(code, message)`로 반환합니다. `RECOGNITION`, `NUTRITION`, `SHORTAGE`는 각각 제공된 예외 화면으로 이동하며 기타 예외는 연결 오류 화면으로 연결합니다.
- 화면 전환 시 요청 세대 번호를 바꿔 이전 작업이 새 선택을 덮어쓰지 않도록 합니다. 실제 네트워크 어댑터에는 취소와 타임아웃을 추가할 수 있습니다.
- 원래 식사, 계획한 대안, 실제 먹은 식사를 구분합니다. 기록 저장 시 식사의 사본을 만들어 이후 편집이 과거 기록을 바꾸지 않도록 합니다.

## 1. 음식 인식

```text
음식 이름 또는 사진 URI
 → recognize(text, localPhotoUri)
 → Meal(name, foods, photoUri)
 → 사용자 이름·섭취량 확인/수정
 → analyze(confirmedFoods)
 → Nutrition
```

사진 URI는 기기 내부 참조입니다. 서버가 직접 접근할 수 없으므로 어댑터가 `ContentResolver`로 내용을 읽어 업로드합니다. 갤러리는 `ACTION_OPEN_DOCUMENT`와 읽기 권한 유지를 사용합니다. 촬영 화면은 `MealCamera`의 CameraX `PreviewView`로 실시간 영상을 표시하며 CAMERA 런타임 권한을 요청합니다. 화면의 촬영 버튼은 `ImageCapture`로 내부 파일에 직접 JPEG를 저장하고 `PhotoProvider` URI를 음식 인식에 전달합니다. 외부 카메라 앱은 열지 않습니다. 화면을 떠나면 use case를 해제하고 이전 콜백을 무효화합니다.

`foodApiUrl`을 지정하면 사진·음식명을 Gemini 서버에 보내 음식 구성과 추정량을 받습니다. 사용자가 확정한 음식명과 중량은 앱에 포함된 [식약처 음식 DB](../app/src/main/assets/nutrition/SOURCE.md)의 100g당 값으로 계산합니다. 미지정 시 사진 인식은 돈가스 정식 예시를 반환하고 텍스트 음식 분해도 예시이며, 확정한 음식의 영양은 `ServerFoodAnalysisService`로 추천 서버에서 계산합니다. 어느 경로든 알 수 없는 음식은 영양 매칭 실패 화면으로 연결합니다. 서버 fixture 모드를 명시적으로 선택하면 합성 DB를 사용하고 화면에 출처를 표시합니다. 추천 요청 시 원래 식사의 영양은 추천 서버 DB에서 다시 계산하므로, 앱 DB에서 인식한 음식도 추천 서버 DB에 별도로 매칭되어야 합니다.

사용자가 음식을 편집하면 `confirmed`, 영양 결과, 기존 추천을 무효화합니다. 확인되지 않은 데이터를 추천에 사용하지 않습니다.

## 2. 맞춤 식단 추천

```text
Meal(confirmed=true, nutrition) + Profile
 → recommend(meal, profile)
 → List<Alternative>
```

대안은 서버가 만든 안정적 `id`, 제목·실천 방법·구성/g·재계산 영양·열량 차이·추천 이유·요소별 실제 점수/가중치·검증 근거·가격·출처를 포함합니다.
Gemini는 5~10개 구성 변경 후보만 제안하고, 서버가 DB 재계산과 알레르기/기피/가격/환경/과도한 제한/중복 검증 후 N/P/C/F 가중 점수로 상위 3개를 반환합니다.
검증 가능한 후보가 1~2개이면 남은 후보를 그대로 반환하고, 0개일 때만 `SHORTAGE`입니다. 공식 데이터/키가 없으면 실제 모드는 실패하며 fixture로 몰래 대체하지 않습니다.
음식 인식·영양 모듈의 설정을 유지하면서 후보 생성만 연결하려면 `CANDIDATE_GENERATOR=gemini`와 `GEMINI_API_KEY`를 설정합니다. 영양이 fixture인 경우에는 계속 합성 데이터 출처가 표시됩니다.

- Android `RecommendationHttpClient`는 별도 Executor에서 HTTP를 실행합니다 (연결 8초/읽기 40초, 응답 크기 제한).
- S16/S17은 실제 영양·이유·점수·서버 가중치·출처를 표시합니다. 번호는 리스트 위치이며 서버 id/index를 배열 첨자로 사용하지 않습니다.
- 원래 식사의 영양도 서버가 다시 계산해 같은 DB 기준으로 비교합니다.
- 앱 생성 시 동기 네트워크 호출을 제거했습니다. 응답은 `StateStore`에 저장하고 재생성 시 보존합니다.
- 기록의 실제 음식 구성과 만족도를 최근 100개까지 전달하여 간단한 선호도 보정에 사용합니다. 메모는 로컬에 남깁니다. 학습 모델은 아직 없습니다.

## 3. 미래 아바타 (구현됨 · 장수인)

```text
Meal + 선택 Alternative + Profile + months(1/3/6)
 → AvatarFutureService.compare(...)
 → FutureComparison (체중·체형 칸·볼 윤곽·예상 kg·감량 경고·가정 문구)
```

- 구현: `app/.../data/AvatarFutureService.java` + `avatar` 모듈(`com.fitfuture.avatar`). `MainActivity`에서 `DemoServices` 대신 주입.
- 계산: Mifflin–St Jeor BMR × 활동 계수 = TDEE, 체지방 1kg ≈ 7,700kcal, ±20% 범위.
- 하루 섭취 A: 아침·점심·저녁이 모두 기록된 날 3일 이상이면 평균, 아니면 "TDEE만큼 먹되 한 끼를 이 메뉴로 매일" 가정. B = A − (원래 메뉴 − 대안). 쓴 가정은 `assumptions`에 문장으로 들어감.
- 체형: BMI 7칸(경계 18.5/20.5/23/25/27.5/30, `MainActivity.BODY_CELLS`). 현재 칸은 사용자가 S07에서 확정, 아니면 BMI 초안. 미래 칸은 변화율 2% 미만 0칸, 2~5% 1칸, 5% 이상 2칸, 1~7칸 제한.
- 볼 윤곽: 체형 칸 구간을 따름 (1~2칸 갸름, 3~5칸 기본, 6~7칸 도톰, `BodyCellConfig.cheekForCell`). 얼굴형은 그대로, 편집·미래 비교·홈·주차별 어디서든 체형이 바뀌면 볼도 바뀜.
- 셀카 추천: S07의 "셀카 찍기 / 사진 고르기" → ML Kit 얼굴 윤곽(기기 안) → 비율 측정 → 평균 대비 편차로 얼굴형·눈·눈썹·코·입 초안. 헤어·옷·체형은 유지. 사진은 저장하지 않고 촬영 파일은 분석 뒤 삭제. 평균·표준편차(`FaceNorms.DEFAULT`)는 추정치라 실제 사진으로 보정 필요 (디버그 빌드에서 측정 z값 표시).
- 렌더링: `Ui.figure()` → `AvatarLayerView`. S07 편집 미리보기, S18 A/B, 홈, S21 완료, S24 주차별이 모두 같은 렌더러.
- 파츠: Paper Cut v4 앱용 PNG 127개 `app/src/main/assets/avatar/` (모두 320×790, (0,0)에 겹침). 파츠 이름·순서·헤어 짝은 `avatar/.../core/AvatarCatalog.java`. 파츠 수정·재생성은 파츠 원본 패키지(`FitFuture_PaperCut_v2/rebuild.swift`)에서 한다.
- 자세한 사용법: [`docs/avatar/AVATAR_GUIDE.md`](avatar/AVATAR_GUIDE.md) (얼굴형 vs 볼 윤곽, 헤어 앞뒤 짝, 저장 형식, 문제 해결).
- 편집 화면(S07): 부위 탭 → 그림 타일(지금 아바타에 후보 파츠를 입힌 모습, `PartThumbView`)로 종류를 고르고, 크기·굵기·두께는 같은 종류 안에서 3단계(`PartGroups`). 저장 ID는 그대로.
- 헤어: "헤어스타일" 하나로 고른다. 뒷머리 레이어는 스타일에 맞춰 `AvatarSpec`이 항상 자동으로 정한다 (따로 고르지 않음, 예전 저장값도 불러올 때 바로잡음).
- 저장: 아바타 구성은 `AvatarStore`(SharedPreferences, Firestore용 JSON 변환 포함). 원본 사진은 저장하지 않음.
- `FutureComparison.currentStage/alternativeStage`는 실제 이동한 칸 수(−2~+2)로 계속 채워 기존 코드와 호환.
- 추천 대안 변경 시 ID/열량을 비교 캐시 키에 포함하여 1·3·6개월을 다시 계산합니다. 서버/추천 모델은 체중 변화량을 계산하지 않습니다.
- 다음: 셀카 기반 파츠 추천(ML Kit 얼굴 윤곽 → 가장 가까운 파츠).

## 실제 식사와 피드백

`MealRecord`는 다음 내용을 로컬 JSON으로 저장합니다.

- 원래 음식·양·영양·사진 참조
- 먹기 전에 선택한 `plannedAlternative` 위치와 `plannedSnapshot` (안정적 ID/영양/구성 포함)
- 실제 먹은 `actualChoice`, `actualTitle`, `actualSnapshot`, `actualFoods`
- 실제 선택의 `actualNutrition` — 기타를 선택하면 알 수 없는 열량을 만들지 않고 `null`
- 만족도 1~5, 메모, 기록 시각

피드백은 `StateStore`에 저장합니다. 계획/실제 대안 스냅샷과 실제 음식/g도 함께 보존하고 다음 추천 요청에 선호도 보정 자료로 전달합니다. 기존 기록에서 음식 구성을 모르면 임의 복원하지 않습니다. 별도 서버 영구 저장은 구현하지 않았습니다. 동일한 식사 ID를 반복 저장하면 중복 기록 대신 기존 기록을 교체합니다.

## 통합 때 확인할 사항

- 인식 실패, 알 수 없는 음식, 유효 후보 부족, 네트워크 오류 처리
- 확정된 음식량과 서버 계산의 기준량 일치
- 빠른 대안·기간 변경 및 화면을 떠난 요청 처리
- 실제 선택과 계획한 선택이 다른 경우의 영양·피드백 저장
- 회전·재생성·재실행 후 입력과 사진 접근 권한 유지
- 추천 모델, 영양 DB, 미래 계산의 예시 구현을 각각 교체했는지 확인
