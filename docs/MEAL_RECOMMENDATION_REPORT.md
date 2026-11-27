# 식단 대안 추천 구현 보고

작업 브랜치: `meal-recommendation`, 기준: `origin/avatar-integration`.
시작 시 로컬 `main`은 미커밋 변경이 없었습니다. `avatar-integration`을 fetch한 뒤 새 브랜치에서 작업했습니다.
`main`/`avatar-integration`에는 push/merge하지 않았습니다. 작업 브랜치의 원격 추적도 해제해 기존 원격 아바타 브랜치로 잘못 push하는 일을 방지했습니다.

## 1. 기존 파일 수정

| 파일 | 변경 내용 |
| --- | --- |
| `app/.../MainActivity.java` | 서버 서비스 주입, 시작 시 UI 스레드 네트워크 제거, 원래 영양 동기화, 기록 피드백 전달, 대안별 아바타 캐시 무효화, 서버 오류 메시지 |
| `domain/FeatureServices.java` | 기존 recommend 계약 유지, 기록 전달용 default overload |
| `domain/Models.java` | 대안 ID/출처/검증 근거/실제 요소 점수, 영양 출처, 계획·실제 대안 스냅샷 및 실제 섭취 구성 |
| `data/AppState.java` | 선택 위치 범위 보정, 분석 무효화 시 출처 초기화 |
| `data/StateStore.java` | 기존 저장소에 대안과 실제 음식/g·피드백 스냅샷 추가, 기존 필드/기록 유지 |
| `ui/RecommendationScreens.java` | S16/S17 실제 결과·점수·가중치·근거·출처 표시, 배열 index 의존과 고정 설명 제거 |
| `ui/MealScreens.java` | 영양 출처 및 음식 인식 예시 상태 표시 |
| `ui/AccountScreens.java` | 기존 S28 화면에 실제 서버/네트워크 실패 메시지 표시 |
| `app/build.gradle.kts` | 서버 URL BuildConfig 설정, Android 계약 테스트에 서버 응답 fixture 공유 |
| `app/src/main/AndroidManifest.xml` | INTERNET 권한 |
| `app/src/androidTest/.../FlowInstrumentation.java` | 추천 계약 검사 추가, 기존 UI 회귀 검사는 명시적 demo 주입 |
| `scripts/verify_flow.py` | 실행 중 FastAPI까지 검증하는 선택적 URL 인자 |
| `docs/INTEGRATION.md` | 실제 연결 구조·피드백·오류·아바타 연결 문서 갱신 |
| `.gitignore` | venv/백그라운드 로그/로컬 데이터 제외, 빈 `.env.example` 허용 |

`DemoServices.java`, `AvatarFutureService.java`, `avatar/` 모듈·자산은 변경하지 않았습니다.

## 2. 새 파일

- Android `data/`: `RecommendationHttpClient.java`, `RecommendationJson.java`, `HttpMealRecommendationService.java`, `ServerFoodAnalysisService.java`.
- `app/src/debug/AndroidManifest.xml`: 로컬 개발용 HTTP 허용. Release에는 적용되지 않습니다.
- `app/src/androidTest/.../RecommendationChecks.java`: 실제 HTTP·파서·UI 오류·아바타·피드백 검사.
- `recommendation-server/fitfuture/`: `app.py`, `schemas.py`, `generator.py`, `nutrition.py`, `import_mfds.py`, `allergens.py`, `pipeline.py`, `scoring.py`, `scoring.json`, `errors.py`, `__init__.py`.
- 서버 실행/설정: `README.md`, `.env.example`, `requirements.txt`, 검증된 의존성 `requirements.lock.txt`, `scripts/server.py`.
- 예제/테스트: `examples/request.json`, `examples/response.fixture.json`, `tests/test_recommendations.py`, `tests/fixtures/catalog.json`, `tests/fixtures/candidates.json`.
- 이 보고서: `docs/MEAL_RECOMMENDATION_REPORT.md`.

## 3. 실제 알고리즘

입력 검증 → 정확한 DB 식품 ID 매칭 → 원래 영양 재계산 → Gemini 최대 10개 JSON 후보(0개면 SHORTAGE) → 식품/변경 가능성/성분/알레르기/가격/환경/영양 제한 검증 → DB 중량 비례 재계산 → 유사 구성 중복 제거 → N/P/C/F 점수 → 상위 3개.

`Score = .35N + .30P + .20C + .15F`이며 각 요소는 0~100점입니다. 가중치는 `scoring.json`에서 수정합니다.
목표 열량/탄단지 균형, 선호 메뉴 중량과 원래 구성 유지율, 실제 예산·접근성, 추가 구매/조리/변경 개수를 계산합니다.
후보가 3개 미만이면 `SHORTAGE`이며 결과를 복제하지 않습니다.
미확인 영양/알레르기/성분/가격/환경/기한이 지난 근거는 추측으로 통과시키지 않습니다.
갑각류/유제품/견과류는 표시 성분의 보수적 용어 매핑을 적용하고, 지원하지 않는 알레르기는 검증 불가로 제외합니다.

영양은 Gemini 수치를 사용하지 않으며 `기준 영양 × 실제 g / 기준 g`를 합산합니다.
실천 방법과 설명은 검증된 중량 변화로 작성합니다. 새 모델을 학습하지 않았고, 규칙 점수 모듈은 교체 가능합니다.

## 4. Gemini·식약처 연결 상태

| 항목 | 상태 |
| --- | --- |
| Gemini 실제 REST 어댑터/JSON Schema/타임아웃/응답 검증 | 구현, mock HTTP 성공·실패 테스트 통과 |
| 실제 Gemini 키로 생성 | 미실행: 키 없음 |
| 공식 식약처 I0750 HTTPS 가져오기·JSON 정규화 인터페이스 | 구현, 기준 중량/누락/중복 연도 처리 테스트 통과 |
| 실제 식약처 데이터 확보·매칭 검토 | 미완료: 공식 데이터/키 필요 |
| 실제 메뉴 가격·레시피·교차접촉·환경 근거 | 미확보: 별도 운영 자료 필요 |
| 검증용 후보/합성 영양 DB | `tests/fixtures`에 분리, 명시적 fixture 모드에서만 사용 |

실제 모드가 기본입니다. 키나 데이터가 부족할 때 fixture로 자동 대체하지 않습니다.
공식 데이터 연동 참고: [식품안전나라 I0750](https://www.foodsafetykorea.go.kr/api/openApiInfo.do?menu_grp=MENU_GRP31&menu_no=656&show_cnt=10&start_idx=1&svc_no=I0750&svc_type_cd=API_TYPE06), [공식 영양 DB API 안내](https://various.foodsafetykorea.go.kr/nutrient/industry/openApi/info.do).
Gemini 구조화 출력 계약: [Google 공식 문서](https://ai.google.dev/gemini-api/docs/structured-output).

## 5. Android 수신 과정

음식명/중량 확정 → `/nutrition` → S13 → 기존 `loadRecommendations()`의 작업 스레드 → `/recommend` → `RecommendationJson` → `List<Alternative>` → S16/S17.
안정적 서버 ID와 리스트 위치를 구분하고 3개 개수/중복 ID/양수 중량/유한 영양/요소 점수·가중 합/열량 차이를 확인합니다.
네트워크·서버 오류는 S28, 영양 매칭 실패는 기존 영양 오류 화면, 추천 부족은 기존 SHORTAGE 화면입니다.
연결/읽기 제한은 8/40초입니다. Gemini는 25초, 서버 전체 추천은 35초입니다.

## 6. 미래 아바타와 피드백

선택한 실제 `Alternative.nutrition`을 기존 `AvatarFutureService.compare()`에 전달합니다.
1·3·6개월 계산 책임과 `WeightSimulator`는 그대로 유지합니다. 대안 ID/열량이 변경되면 비교 캐시가 다시 계산됩니다.
테스트에서는 서로 다른 625/730/745kcal 대안이 각 기간에 서로 다른 비교 결과를 만드는 것을 확인했습니다 (fixture 기준).

계획한 대안 스냅샷과 실제 선택 스냅샷, 실제 음식/g, 만족도 1~5, 메모를 같은 로컬 기록에 저장합니다.
다음 요청은 실제 구성과 만족도를 최근 100개까지 받아 선호도 점수를 간단히 보정합니다. 기존 기록에서 모르는 구성은 만들어내지 않습니다.
메모는 로컬에 남고 서버에는 영구 저장하지 않습니다. 학습 기반 개인화는 아직 없습니다.

## 7. 실행·환경변수

자세한 절차는 [서버 README](../recommendation-server/README.md)에 있습니다.

```bash
cd recommendation-server
python3 -m venv .venv
.venv/bin/pip install -r requirements.lock.txt
cp .env.example .env
# 실제 모드: GEMINI_API_KEY, GEMINI_MODEL, MFDS_API_KEY, MFDS_CATALOG_PATH 설정
# 검토한 영양·레시피·알레르기·판매 근거 확보 후 real로 실행
.venv/bin/python scripts/server.py start
```

키 없는 검증: `RECOMMENDATION_MODE=fixture .venv/bin/python scripts/server.py start`.
상태/중지는 `scripts/server.py status` / `stop`입니다. 실행 중 모드 변경은 stop 후 start입니다.
Android 기본 개발 URL은 `http://10.0.2.2:8000`. 변경은 `-PrecommendationBaseUrl=...`로 지정합니다.
Release에는 HTTPS 주소를 지정해야 합니다. 앱에는 API 키가 없습니다.

## 8. 실행한 검증

- 서버 pytest **37개 통과**. Starlette의 httpx TestClient 사용 중단 예고 경고 1개가 있으며 실패는 없습니다.
- Android Debug 앱·테스트 APK **빌드 성공**.
- 기존 아바타 단위 테스트 **24개 통과** (6+4+4+10), 아바타 코드 변경 없음.
- API 36 에뮬레이터: 실제 HTTP 파싱/인덱스 정규화/점수 파싱/S17/SHORTAGE·서버 오류 화면/대안별 1·3·6개월 비교/계획과 실제 피드백·로컬 보존 검사 **통과**.
- Android → 실행 중인 FastAPI fixture → S13 → S16 → S17 → S18 → 기록 저장 **통과**.
- 기존 온보딩·수량 수정·음식 추가/삭제·기간 탭·만족도·재생성·로컬 기록/삭제·사진 인텐트 회귀 검사 **통과**.
- 실제 로컬 HTTP `/recommend`: 200, 서로 다른 대안 3개, 625/730/745kcal, fixture 출처 확인.
- `git diff --check` 통과. 아바타 모듈/서비스의 기준 브랜치 대비 diff 없음.

환경의 기본 JDK는 26이었고 Gradle 캐시에는 일부 Library 플러그인이 없었습니다.
프로젝트 설정을 바꾸지 않고 Android Studio 내 JDK 25를 지정해 온라인 최초 빌드 후 오프라인 증분 빌드도 통과했습니다.

```bash
env JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew -Dorg.gradle.java.installations.paths='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  :app:assembleDebug :app:assembleDebugAndroidTest :avatar:testDebugUnitTest
python3 scripts/verify_flow.py --adb /Users/hee/Library/Android/sdk/platform-tools/adb \
  --recommendation-url http://10.0.2.2:8000
```

## 9. 추가 필요한 설정

Gemini 키와 사용 가능한 모델명, 공식 영양 DB API 구독/키 또는 공식 내보내기 파일, 검토된 정확한 음식명 매핑,
실제 레시피/알레르기/교차접촉·가격·식사 환경 근거와 갱신 기한이 필요합니다.
외부 서버 배포·HTTPS·서비스 인증/요청 제한은 아직 설정하지 않았습니다. 기본 서버는 loopback 개발용입니다.

## 10. 남은 예시와 백그라운드 동작 범위

현재 실행한 서버는 명시적 **fixture 모드**입니다. Gemini와 공식 DB가 실제 호출된 것으로 표시하지 않습니다.
음식 사진 인식/메뉴 분해, 개발 preview, 기존 DemoServices 추천 예시는 유지했습니다.
실제 사용자 데이터로 학습하는 모델·클라우드 피드백 저장은 구현하지 않았습니다.

추천 서버는 창/터미널과 독립된 프로세스이며 macOS `caffeinate -i`가 서버 실행 동안 유휴 절전을 방지합니다.
디스플레이는 꺼져도 됩니다. 시스템 설정이나 자동 로그인 항목은 바꾸지 않았습니다.
이 기능은 Codex 코드 작성 세션을 앱 완전 종료 후에도 계속 실행해 주는 장치가 아닙니다.
컴퓨터 전원 종료/강제 절전/로그아웃 이후에는 로컬 실행을 보장할 수 없으며 상시 서버가 필요합니다.
Codex의 로컬 작업 유지 설정은 [공식 Settings](https://learn.chatgpt.com/docs/reference/settings)의 Prevent sleep while running을 참고하세요.
