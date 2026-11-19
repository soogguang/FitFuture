# FitFuture 식단 대안 추천 서버

FastAPI + **Gemini 후보 생성 + 규칙 기반 점수화** 구현입니다. 새 AI 모델을 학습하지 않습니다.
`real`이 기본이며 키/식약처 데이터가 없을 때 fixture로 자동 전환하지 않습니다.
음식 인식은 `foodApiUrl` 지정 시 별도 Gemini 인식 서버를 사용하고 미지정 시 Android DemoServices를 사용합니다. 사용자가 확정한 음식과 g만 이 서버에 전달합니다. 두 서버의 연결 설정은 [통합 가이드](../docs/INTEGRATION.md)를 참고하세요.

## 실행

```bash
cd recommendation-server
python3 -m venv .venv
.venv/bin/pip install -r requirements.lock.txt
cp .env.example .env
# .env의 키, 데이터 경로 등을 입력 (Git에서 제외됨)
.venv/bin/python scripts/server.py start
.venv/bin/python scripts/server.py status
.venv/bin/python scripts/server.py stop
```

start는 별도 프로세스로 실행되며 Codex/터미널 창을 닫아도 서버가 실행됩니다.
macOS에서는 서버가 살아 있는 동안 `caffeinate -i`로 유휴 절전을 방지하며 디스플레이는 꺼질 수 있습니다.
이 스크립트가 계속 실행하는 것은 **추천 서버**입니다. Codex의 코드 작성 세션을 재시작하거나 대신 수행하지 않습니다.
전원 종료, 강제 절전/노트북 덮개 닫기, 로그아웃 후 실행은 보장하지 않습니다. 이 경우 별도 상시 서버가 필요합니다.
로그는 `.run/server.log`, PID는 `.run/server.pid`입니다. 시스템 절전 설정과 로그인 항목은 변경하지 않습니다.

기존 가상환경의 파일 읽기가 멈추는 경우에는 동일한 고정 버전으로 별도 `.runtime-venv` 환경을 만들 수 있습니다.

```bash
python3 -m venv .runtime-venv
.runtime-venv/bin/python -m pip install -r requirements.lock.txt
.runtime-venv/bin/python scripts/server.py start
```

키 없이 검증용 서버를 실행하려면 다음을 **명시적으로** 선택합니다.

```bash
RECOMMENDATION_MODE=fixture .venv/bin/python scripts/server.py start
# 실행 중 모드를 바꾸려면 먼저 stop
curl -s http://127.0.0.1:8000/health
curl -s http://127.0.0.1:8000/recommend -H 'Content-Type: application/json' --data-binary @examples/request.json
```

`fixture`는 실제 Gemini/식약처 응답이 아닙니다. 응답과 Android 화면에 출처를 표시합니다.
`examples/response.fixture.json`은 Python 파이프라인으로 만든 **검증용 응답**이며 Android 계약 테스트도 동일 파일을 읽습니다.
직접 foreground 실행할 때는 환경변수를 export한 후 `.venv/bin/uvicorn fitfuture.app:app --host 127.0.0.1 --port 8000`을 사용합니다 (`.env` 자동 로드는 scripts/server.py에서 수행).

## 공식 식약처 데이터 확보와 검증 근거

공식 인터페이스: `NutritionRepository`가 식약처에서 가져와 검토한 스냅샷을 읽고,
`import_mfds.py`가 식품안전나라 I0750 JSON을 받아 정규화합니다.

```bash
export MFDS_API_KEY='발급받은키'
.venv/bin/python -m fitfuture.import_mfds --start 1 --end 1000 --output data/mfds.json
# 또는 공식 서비스에서 다운로드한 I0750 JSON
.venv/bin/python -m fitfuture.import_mfds --input /path/to/official-export.json --output data/mfds.json
```

구독한 공식 서비스가 I0750와 다른 필드 구조라면 변환 어댑터를 해당 명세에 맞게 추가해야 합니다.
HTTPS 접근만 시도하며 HTTP로 키를 보내는 fallback은 없습니다. **실제 키로 호출한 검증은 하지 않았습니다.**
누락/N/A/음수/불명확한 기준량은 0으로 채우지 않고 제외합니다.
`SERVING_WT` 기준 중량과 `NUTR_CONT1/2/3/4`를 사용합니다. 같은 코드의 여러 연도는 검토해서 한 버전을 선택해야 합니다.

공식 영양 DB 자체에는 식당의 실제 레시피·교차접촉·현재 판매 가격 정보가 없습니다.
정규화 데이터만 가져오면 **추천은 아직 SHORTAGE**가 될 수 있습니다. 아래 근거를 운영자가 확보해야 합니다.

- 음식별 `aliases`: 사용자 음식명과 DB 항목의 검토된 정확한 매핑. 퍼지 검색으로 임의 선택하지 않습니다.
- `allergens`, `allergen_source`, `allergen_valid_until`: 실제 레시피의 성분 확인 근거. `null`은 모름, `[]`은 근거 있는 미포함 정보입니다.
- `ingredients`, `ingredient_source`, `ingredient_valid_until`: 기피 재료 확인용 근거.
- `offers`: 환경, 실제 음식별 최대 제공 중량 `max_grams`, **분량을 줄여도 실제 청구되는 한 끼 가격** `price_won`, `source`, `valid_until`, 허용 변경 유형 `allowed_kinds`, 추가 구매/조리 필요 여부, 제공 메뉴와 교차접촉을 반영한 `allergens`/`allergen_source`.

앱의 갑각류/유제품/견과류는 `fitfuture/allergens.py`의 보수적 용어 집합으로 성분 표시와 대조합니다.
지원하지 않는 사용자 알레르기나 근거의 성분 표시는 검증 불가로 제외합니다. 용어 매핑만으로 실제 재료를 추론하지 않습니다.

스키마는 `fitfuture/nutrition.py`를 참고하세요. 검증용 구조 예시는 `tests/fixtures/catalog.json`에 있지만,
이 파일의 합성 영양값/가격/알레르기 정보를 실제 서비스로 복사하면 안 됩니다.
실제 모드는 `provenance=mfds`, 모든 식품 `source=mfds`만 수용합니다. 출처 표기만 바꾸는 것은 데이터 확보가 아닙니다.
현재 레포에는 공식 영양/레시피/가격 데이터가 포함되어 있지 않습니다.

## API와 오류

- `POST /nutrition`: 확정할 음식명/g → DB 기준 Nutrition, 출처. Android는 `foodApiUrl` 미지정 시 이 API를 사용하고, 지정 시 앱 내 식약처 DB로 S13 영양을 계산합니다. 추천 요청 시에는 이 서버 DB로 원래 식사의 영양을 다시 계산합니다.
- `POST /recommend`: `examples/request.json`의 meal/profile/feedback → 상위 3개, 원래 영양 재계산, 출처, 제외 사유 집계.
- `GET /health`: 준비 여부와 후보 생성 모드. `/docs`: OpenAPI 명세.
- 오류: `{ "error": { "code": "SHORTAGE", "message": "…", "available": 2, "rejected": {…} } }`.
- NUTRITION: 정확히 매칭 불가. SHORTAGE: 검증된 후보 없음. CONFIGURATION: 키/DB 설정 불가.
- AI_RATE_LIMIT: Gemini 요청 한도, AI_AUTH: API 키 권한, AI_MODEL: 사용 불가 모델, INVALID_AI_REQUEST: AI 요청 형식 오류.
- UPSTREAM/INVALID_AI_RESPONSE: Gemini 일시 오류/응답 스키마 오류. 연결·일시 서버 오류는 한 번만 자동 재시도합니다. 인증·요청 형식·한도 오류는 자동 재시도하지 않습니다.
- TIMEOUT: Gemini 각 시도 최대 25초, 재시도 포함 최대 30초, 전체 요청 35초 제한. 진단 로그에는 HTTP 상태·연결 오류 종류만 남기며 키와 요청 본문은 기록하지 않습니다.
- 잘못된 요청은 422 `VALIDATION` 오류로, 나이·체중 등 수정할 필드와 허용 범위를 안내합니다.
- 요청 본문/키를 로그에 기록하지 않습니다. 외부에 공개하려면 HTTPS·인증·요청 제한을 별도 구성하세요. 기본은 loopback 개발 서버입니다.

## 처리 과정과 점수

1. 입력 검증 후 이름을 정확한 식품 ID로 매칭하고 **원래 식사부터 DB로 재계산**합니다.
2. Gemini에 프로필·식품 ID·검증된 판매 조건을 보내 5~10개 JSON 후보를 요청합니다.
   AI가 제안할 수 있는 값은 제목/변경 유형/식품 ID/g뿐입니다. 영양·가격·알레르기 값은 허용하지 않습니다.
3. 없는 소스, 모순된 변경 유형, 미확인 재료/알레르기/가격/환경, 예산 초과, 과도한 감소를 제외합니다.
   영양은 `기준 영양 × 실제 g / 기준 g`로 합산합니다. 최소 300/최대 1800kcal, 메뉴 교체 외에는 원래 열량의 60% 이상을 유지하는 초기 보수적 정책입니다 (의료 처방 아님, 성인 대상).
4. 거의 같은 구성은 전체 중량 대비 차이가 5% 미만이면 중복으로 제외합니다.
   설명은 검증된 중량 변화에서 서버가 작성해 AI의 근거 없는 실천/안전 문구가 노출되지 않게 합니다.
5. 0~100점 요소와 종합 점수로 정렬하고 최대 3개를 반환합니다. 1~2개만 통과해도 그대로 보여주며, 0개일 때만 SHORTAGE 안내 화면으로 이동합니다. 없는 후보를 채우거나 조건을 완화하지 않습니다.

후보 생성은 이번 요청의 실제 음식별 중량을 기준으로 하며, 고정 예시 중량을 기준으로 삼지 않도록 Gemini에 지시합니다. 추천 이유에는 DB로 검증한 중량·열량 변화, 예산과 실천 조건을 반영합니다. PPT의 열량 수치는 예시이므로 고정하지 않습니다.

음식 인식·영양 모듈을 담당하는 다른 팀원의 설정을 유지하고 **추천 후보 생성만 Gemini로 연결**하려면 기존 `RECOMMENDATION_MODE`에 `CANDIDATE_GENERATOR=gemini`, `GEMINI_API_KEY`를 추가합니다. 영양 모드가 fixture이면 영양 데이터는 계속 합성 예시로 표시됩니다. 실제 모드에서 fixture 후보 생성으로 전환하는 것은 허용하지 않습니다. 키가 없을 때 Gemini 호출은 CONFIGURATION 오류를 반환합니다.

`fitfuture/scoring.json`에서 가중치와 안전 정책을 수정할 수 있습니다. 초기 가중치는 N .35 / P .30 / C .20 / F .15입니다.

- N = 목표 한 끼 열량과의 거리 점수 65% + 탄단지 에너지 비율(50/20/30) 거리 점수 35%.
  명시 목표가 없으면 신체/활동량의 BMR/TDEE 추정과 감량 −300/증량 +200kcal의 하루 조정으로 한 끼 목표를 구합니다. BMR 미만 목표를 만들지 않습니다. 체중 변화량은 계산하지 않습니다.
- P = 40 + 원래 음식 중량 유지율×25 + 선호 음식 중량 비율×25 (선호 없음 12.5) + 기피 검증 통과 10.
  `기름진 음식`은 재료 제외가 아닌 취향으로, DB 기준 원래 식사 대비 지방 감소율을 반영해 0~25점을 차감합니다 (많이 줄일수록 차감 감소).
  재료에 `기름`이 있다는 이유만으로 모든 양 조절 후보를 제외하지 않습니다. `기름` 등 명시적 기피 재료와 알레르기는 계속 제외합니다.
  실제 먹은 음식 집합과 과거 음식의 Jaccard 유사도×(만족도−3)×5를 평균해 보정합니다. 0~100으로 제한합니다.
- C = 예산 대비 실제 비용에 따른 점수 60% + 환경 접근 확인 100점의 40%. 예산 초과는 채점 전 제외합니다.
- F = 100 − 추가 구매 25 − 조리 30 − 첫 변경 이후 음식별 변경 5점. 0~100으로 제한합니다.

`RuleScorer`는 후보 생성/검증/아바타 계산과 분리되어 있어 피드백 확보 후 `Scorer` 계약의 다른 구현으로 교체할 수 있습니다.
현재 학습 모델은 없습니다. 서버는 피드백을 저장하지 않습니다. Android `StateStore`의 실제 섭취 구성/계획·실제 ID/만족도를 요청마다 최대 최근 100개 전달합니다. 메모는 로컬에 유지합니다.

## Android 설정과 검증

```bash
# 에뮬레이터 기본 URL: http://10.0.2.2:8000
./gradlew :app:assembleDebug
# USB 실기기 (adb reverse 후 localhost로 빌드)
adb reverse tcp:8000 tcp:8000
./gradlew :app:assembleDebug -PrecommendationBaseUrl=http://127.0.0.1:8000
# 배포 APK에는 HTTPS 주소 지정. HTTP 허용은 debug manifest에만 존재합니다.
./gradlew :app:assembleRelease -PrecommendationBaseUrl=https://your-recommendation-server.example
```

```bash
cd recommendation-server
.venv/bin/python -m pytest -q
cd ..
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :avatar:testDebugUnitTest
python3 scripts/verify_flow.py --adb /path/to/adb  # 일회용 에뮬레이터에서만; FitFuture 로컬 기록 초기화
# fixture 서버가 실행 중이면 Android → FastAPI 전체 연결도 검증
python3 scripts/verify_flow.py --adb /path/to/adb --recommendation-url http://10.0.2.2:8000
```

현재 저장된 확정 식사·프로필을 초기화하지 않고 **실제 Gemini 요청 → 추천 카드 → 상세 → 미래 비교**만 확인하려면 서버에 키를 설정하고 아래 검증을 명시적으로 실행합니다. 실제 API 사용량이 발생하며, 받아온 추천 결과는 앱에 저장됩니다.

```bash
adb shell am instrument -w -e liveRecommendationOnly true com.fitfuture.app.test/com.fitfuture.app.FlowInstrumentation
```

계약 테스트는 실제 HTTP adapter/JSON parser/S17 상세/서버 실패 화면/추천 선택 변경/1·3·6개월 아바타 계산/로컬 피드백 저장을 검증합니다.
기존 전체 UI 회귀 테스트는 별도로 명시적 DemoServices를 주입합니다. live Gemini/식약처 호출을 테스트 성공으로 표시하지 않습니다.
