# 세 모듈 연동 가이드

## 연결 지점

현재 앱은 `MainActivity.onCreate()`에서 기본 `DemoServices`를 주입합니다. `foodApiUrl` 빌드 속성을 지정하면 음식 분석만 `RemoteFoodAnalysis`로 교체합니다. 이 구현체의 인식은 실제 서버를 사용하고 영양 분석은 아직 데모에 위임합니다. 나머지 담당자는 각각 `FeatureServices`의 인터페이스를 구현하고 해당 조합을 교체하면 됩니다. 인터페이스는 Java이며 외부 SDK나 UI 타입에 의존하지 않습니다.

```java
services = new FeatureServices(
    foodAnalysisImplementation,
    recommendationImplementation,
    futureAvatarImplementation
);
```

팀 공용 서버 URL·인증·JSON 명세는 아직 합의되지 않았습니다. 로컬 인식 API 초안과 실행 방법은 `server/README.md`에 있습니다. 인터넷 권한과 HttpURLConnection 어댑터를 추가했으며 로컬 HTTP는 debug의 loopback/에뮬레이터 호스트에만 허용합니다. API 키는 서버 환경변수에 두고 Android 소스에 넣지 않습니다. 실제 HTTP 응답 DTO는 각 구현체에서 `Models`의 타입으로 변환합니다.

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

기본 데모 모드의 사진 인식은 내용과 관계없이 돈가스 정식 예시를 반환합니다. `foodApiUrl`을 지정하면 실제 서버로 전송합니다. 텍스트의 돈가스·비빔밥·샐러드는 예시 식품 표로 연결하며, 알 수 없는 음식은 사용자 확인 뒤 영양 매칭 실패 화면으로 연결합니다.

사용자가 음식을 편집하면 `confirmed`, 영양 결과, 기존 추천을 무효화합니다. 확인되지 않은 데이터를 추천에 사용하지 않습니다.

## 2. 맞춤 식단 추천

```text
Meal(confirmed=true, nutrition) + Profile
 → recommend(meal, profile)
 → List<Alternative>
```

대안은 제목·실천 방법·구성·영양·추천 이유·점수를 포함합니다. 현재 세 대안은 양 조절 예시이고, 영양 표와 점수는 실제 DB 검증 결과가 아닙니다. 서버 구현체가 알레르기·예산·실천 조건을 검증하고 정렬해야 합니다. 후보가 부족하면 가짜 대안을 복제하는 대신 `SHORTAGE`를 반환합니다.

프로필의 목표·활동량·취향·피할 음식·알레르기·한 끼 예산·식사 환경을 전달할 수 있습니다. 실제 추천 가중치와 저장된 피드백을 반영하는 로직은 추천 모듈에서 구현해야 합니다.

## 3. 미래 아바타

```text
Meal + 선택 Alternative + Profile + months(1/3/6)
 → compare(...)
 → FutureComparison
```

현재·대안 체중과 `AvatarStage`를 반환합니다. UI가 1·3·6개월 결과를 작업 스레드에서 준비해 탭과 그래프에 반영합니다. 실제 BMR/TDEE 계산, 가정, 단계 경계는 이 구현체에 모아야 합니다. 현재 선형 예시 계산을 실제 예측처럼 사용하지 않습니다.

단계 리소스: `avatar_minus_two`, `avatar_minus_one`, `avatar_baseline`, `avatar_plus_one`, `avatar_plus_two`. PNG 원본과 동일한 숫자 플레이스홀더이므로 실물 캐릭터 5종을 받으면 `Ui.avatar()`의 이미지 연결 부분을 교체합니다.

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
