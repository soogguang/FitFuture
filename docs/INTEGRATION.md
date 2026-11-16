# 세 모듈 연동 가이드

## 연결 지점

현재 앱은 `MainActivity.onCreate()`에서 `new DemoServices().services()`를 주입합니다. 세 담당자가 각각 `FeatureServices`의 인터페이스를 구현하고 해당 조합을 교체하면 됩니다. 인터페이스는 Java이며 외부 SDK나 UI 타입에 의존하지 않습니다.

```java
services = new FeatureServices(
    foodAnalysisImplementation,
    recommendationImplementation,
    futureAvatarImplementation
);
```

서버 URL·인증·JSON 명세는 아직 합의되지 않았습니다. 실제 HTTP 응답 DTO는 각 구현체에서 `Models`의 타입으로 변환합니다. API 키를 Android 소스에 넣지 않습니다. 서버 연동 시 manifest 인터넷 권한과 HTTP 클라이언트 설정을 추가해야 합니다.

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

사진 URI는 기기 내부 참조입니다. 서버가 직접 접근할 수 없으므로 어댑터가 `ContentResolver`로 내용을 읽어 업로드해야 합니다. 갤러리는 `ACTION_OPEN_DOCUMENT`와 읽기 권한 유지, 카메라는 시스템 촬영 Intent와 grant-only `PhotoProvider`를 사용합니다. 카메라 앱을 열기 위해 앱 자체에 CAMERA 권한을 요청하지 않습니다.

현재 사진 인식은 내용과 관계없이 돈가스 정식 예시를 반환합니다. 텍스트의 돈가스·비빔밥·샐러드는 예시 식품 표로 연결하며, 알 수 없는 음식은 사용자 확인 뒤 영양 매칭 실패 화면으로 연결합니다.

사용자가 음식을 편집하면 `confirmed`, 영양 결과, 기존 추천을 무효화합니다. 확인되지 않은 데이터를 추천에 사용하지 않습니다.

## 2. 맞춤 식단 추천

```text
Meal(confirmed=true, nutrition) + Profile
 → recommend(meal, profile)
 → List<Alternative>
```

대안은 제목·실천 방법·구성·영양·추천 이유·점수를 포함합니다. 현재 세 대안은 양 조절 예시이고, 영양 표와 점수는 실제 DB 검증 결과가 아닙니다. 서버 구현체가 알레르기·예산·실천 조건을 검증하고 정렬해야 합니다. 후보가 부족하면 가짜 대안을 복제하는 대신 `SHORTAGE`를 반환합니다.

프로필의 목표·활동량·취향·피할 음식·알레르기·한 끼 예산·식사 환경을 전달할 수 있습니다. 실제 추천 가중치와 저장된 피드백을 반영하는 로직은 추천 모듈에서 구현해야 합니다.

## 3. 미래 아바타 (구현됨 · 장수인)

```text
Meal + 선택 Alternative + Profile + months(1/3/6)
 → AvatarFutureService.compare(...)
 → FutureComparison (체중·체형 칸·볼 윤곽·예상 kg·감량 경고·가정 문구)
```

- 구현: `app/.../data/AvatarFutureService.java` + `avatar` 모듈(`com.fitfuture.avatar`). `MainActivity`에서 `DemoServices` 대신 주입.
- 계산: Mifflin–St Jeor BMR × 활동 계수 = TDEE, 체지방 1kg ≈ 7,700kcal, ±20% 범위.
- 하루 섭취 A: 아침·점심·저녁이 모두 기록된 날 3일 이상이면 평균, 아니면 "TDEE만큼 먹되 한 끼를 이 메뉴로 매일" 가정. B = A − (원래 메뉴 − 대안). 쓴 가정은 `assumptions`에 문장으로 들어감.
- 체형: BMI 7칸(경계 18.5/20.5/23/25/27.5/30, `MainActivity.BODY_CELLS`). 현재 칸은 사용자가 S07에서 확정, 아니면 BMI 초안. 미래 칸은 변화율 2% 미만 0칸, 2~5% 1칸, 5% 이상 2칸, 1~7칸 제한. 실제로 2칸 이동할 때만 볼 윤곽 변경.
- 렌더링: `Ui.figure()` → `AvatarLayerView`. S07 편집 미리보기, S18 A/B, 홈, S21 완료, S24 주차별이 모두 같은 렌더러.
- 파츠: Paper Cut v4 앱용 PNG 127개 `app/src/main/assets/avatar/` (모두 320×790, (0,0)에 겹침). 파츠 이름·순서·헤어 짝은 `avatar/.../core/AvatarCatalog.java`. 파츠 수정·재생성은 파츠 원본 패키지(`FitFuture_PaperCut_v2/rebuild.swift`)에서 한다.
- 자세한 사용법: [`docs/avatar/AVATAR_GUIDE.md`](avatar/AVATAR_GUIDE.md) (얼굴형 vs 볼 윤곽, 헤어 앞뒤 짝, 저장 형식, 문제 해결).
- 앞머리 선택은 `withHairStyle()`로 맞는 뒷머리까지 함께 고른다.
- 저장: 아바타 구성은 `AvatarStore`(SharedPreferences, Firestore용 JSON 변환 포함). 원본 사진은 저장하지 않음.
- `FutureComparison.currentStage/alternativeStage`는 실제 이동한 칸 수(−2~+2)로 계속 채워 기존 코드와 호환.
- 다음: 셀카 기반 파츠 추천(ML Kit 얼굴 윤곽 → 가장 가까운 파츠).

## 실제 식사와 피드백

`MealRecord`는 다음 내용을 로컬 JSON으로 저장합니다.

- 원래 음식·양·영양·사진 참조
- 먹기 전에 선택한 `plannedAlternative`
- 실제 먹은 `actualChoice`와 `actualTitle`
- 실제 선택의 `actualNutrition` — 기타를 선택하면 알 수 없는 열량을 만들지 않고 `null`
- 만족도 1~5, 메모, 기록 시각

현재 피드백은 `StateStore`에만 저장합니다. 서버 저장·추천 개인화가 필요하면 별도 기록 저장소 인터페이스를 추가하고 저장 성공 후 완료 화면을 표시하도록 확장하세요. 동일한 식사 ID를 반복 저장하면 중복 기록 대신 기존 기록을 교체합니다.

## 통합 때 확인할 사항

- 인식 실패, 알 수 없는 음식, 유효 후보 부족, 네트워크 오류 처리
- 확정된 음식량과 서버 계산의 기준량 일치
- 빠른 대안·기간 변경 및 화면을 떠난 요청 처리
- 실제 선택과 계획한 선택이 다른 경우의 영양·피드백 저장
- 회전·재생성·재실행 후 입력과 사진 접근 권한 유지
- 추천 모델, 영양 DB, 미래 계산의 예시 구현을 각각 교체했는지 확인
