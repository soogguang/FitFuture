# 음식 인식 로컬 서버

첫 단계: 사진/음식명 → Gemini → 음식 목록과 추정 g → 앱 S14 확인 화면.
`foodApiUrl`을 지정한 앱에서는 확정한 음식의 영양을 앱에 포함된 식약처 음식 DB로 계산한다. 미지정 시 음식 인식은 데모이며 영양은 별도 `recommendation-server`에서 계산한다. 대안 추천은 `recommendation-server`, 미래 비교는 `AvatarFutureService`에 연결한다. 추천 서버는 원래 식사의 영양도 자체 DB에서 다시 계산하므로 새 음식은 서버 DB에도 매칭되어야 한다.
DB 범위·출처·갱신 방법은 [영양 DB 설명](../app/src/main/assets/nutrition/SOURCE.md)을 참고한다.
Python 3.11 이상 표준 라이브러리만 사용하므로 pip 설치는 필요 없다.

## 실행

프로젝트 루트 PowerShell에서 서버 프로세스에만 환경변수를 설정한다. 키를 채팅이나 Git에 넣지 않는다.
아래 두 값은 본인 계정에서 발급/확인한 실제 값으로 바꾼다. `.env` 자동 로드는 하지 않는다.

```powershell
$env:GEMINI_API_KEY='YOUR_GEMINI_API_KEY'
$env:GEMINI_MODEL='gemini-3.5-flash-lite'
python server/food_server.py
```

기본 바인딩은 `127.0.0.1:8000`. `GEMINI_MODEL`을 지정하지 않으면 `gemini-3.5-flash-lite`를 쓴다 (추천 서버와 같은 기본 모델). 키가 없으면 시작 시 오류를 내며 가짜 인식으로 대체하지 않는다.
실제 API 호출은 공급자 계정의 요금/한도를 사용한다. `/health`는 프로세스 상태만 확인하며 키 유효성 검사는 아니다.

앱의 음식 인식은 기본 데모 모드다. 아래처럼 서버 주소를 넣어 APK를 빌드하면 실제 인식 서버를 사용한다. 영양·추천 서버와 함께 실행할 때는 포트가 겹치지 않도록 인식 서버를 `python server/food_server.py --port 8765`로 시작하고 `-PfoodApiUrl=http://10.0.2.2:8765`를 지정한다. 영양·추천 서버 주소는 `recommendationBaseUrl`로 별도 설정한다. [통합 가이드](../docs/INTEGRATION.md)를 참고한다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\android-build.ps1 :app:assembleDebug -PfoodApiUrl=http://10.0.2.2:8000
```

Android Emulator의 `10.0.2.2`는 호스트 PC를 가리킨다. Android Studio Sync/Run에도 적용하려면 로컬 `gradle.properties`에 `foodApiUrl=http://10.0.2.2:8000`을 추가한다. 개인 서버 주소 설정은 커밋하지 않는다.
한글 경로 검사 오류가 나면 개발 준비 문서의 `android.overridePathCheck` 안내도 적용한다.
빌드 속성은 URL만 포함하며 Gemini 키는 APK에 포함하지 않는다.

USB 실기기는 `adb reverse tcp:8000 tcp:8000` 후 `-PfoodApiUrl=http://127.0.0.1:8000`로 빌드한다.
HTTP는 debug에서 `10.0.2.2`, `127.0.0.1`, `localhost`에만 허용한다. release는 HTTPS가 필요하다.
서버는 인증·사용자별 호출 제한이 없는 로컬 개발 도구다. 외부 공개 전에는 팀 서버에 인증·한도·HTTPS를 갖춰 통합한다.

앱에서 음식 입력 → 촬영 또는 갤러리 → 인식 → 음식과 양 확인하기를 누르면 S14에서 실제 결과를 확인한다.
입력 화면에 전송 안내가 있으며 서버는 이미지/음식명/모델 응답을 파일로 저장하거나 로그에 남기지 않는다.
Gemini로의 전송과 공급자 측 데이터 처리는 계정 정책에 따른다.

## API 계약

`POST /food/recognize`, `Content-Type: application/json`, `Content-Length` 필수.

```json
{"text":"","image":{"mimeType":"image/jpeg","data":"BASE64_IMAGE_BYTES"}}
```

`image` 없이 `{"text":"비빔밥"}`도 지원한다. 사진이 있으면 이미지를 주 근거로 사용한다.
앱은 ContentResolver로 사진을 읽고 최대 변 1600px 이하로 샘플링, EXIF 방향 보정, JPEG 재인코딩 후 전송한다.
서버는 최대 요청 6MB, 이미지 4MB, 음식 20개, 음식별 추정량 0 초과 5000g 이하를 검사한다.
이미지 시그니처 검사는 완전한 이미지 디코딩 검증은 아니며 실제 해석은 공급자가 수행한다.

```json
{"name":"비빔밥","foods":[{"name":"비빔밥","grams":400}],"portionEstimated":true}
```

400g은 예시이며 결과는 음식에 따라 달라진다. 아직 확정된 음식량이나 DB 영양값이 아니다.
오류는 `{"error":{"code":"RECOGNITION","message":"..."}}` 형식이다.

| HTTP | 의미 | 앱 동작 |
| --- | --- | --- |
| 400/413/415 | 입력 형식/크기 오류 | 연결 오류 화면 |
| 422 | 비음식/인식 불가 | 기존 S15 인식 실패 |
| 502/503/504 | 공급자 오류/한도/시간 초과 | 기존 S28 및 재시도 |

공급자의 HTTP 503은 `MODEL_BUSY`, 429는 `RATE_LIMIT`, 401/403은 `API_AUTH`, 404는 `MODEL_NOT_FOUND`로 구분한다. 앱은 이 코드에 맞는 안내를 표시한다. 서버가 정상이어도 Gemini가 혼잡하면 인식 요청은 실패할 수 있다. `/health`와 브라우저의 기본 경로 응답만으로 Gemini 호출 성공을 판단하지 않는다.

공급자 요청의 소켓 대기 시간은 최대 40초, 앱 응답 대기는 55초다. HTTP 503만 1초·2초 대기 후 최대 총 3회 시도하고, 재시도 시작은 45초 예산 안으로 제한한다. 400·401·403·404·429는 자동 재시도하지 않는다. 재시도로 혼잡 해소를 보장하지 않으며 성공한 요청을 반복하지 않는다.
화면을 떠난 결과는 기존 요청 세대 검사로 반영하지 않지만 이미 시작한 공급자 호출 자체는 취소하지 않는다.

## 검증

에뮬레이터를 사용하기 전에 PC에서 실제 공급자 호출을 분리해 확인할 수 있다.

```cmd
python server\diagnose_gemini.py
python server\diagnose_gemini.py --image "C:\path\food.jpg"
```

진단은 현재 프로세스 환경변수, 없으면 Windows의 `setx`로 저장한 사용자 환경변수에서 키·모델을 읽으며 키는 출력/저장하지 않는다. 첫 단계는 앱·로컬 HTTP 서버·음식 프롬프트·JSON 스키마를 제외한 최소 REST 요청이다. 성공한 경우에만 실제 음식 인식 JSON, 선택적으로 음식 사진을 확인한다. 실제 API를 호출하므로 공급자 한도를 사용한다. 최소 요청이 실패하면 음식 인식 단계로 진행하지 않는다.

2026-11-19 PC 실호출 확인: 기존 `gemini-4-hydrogen`은 최소 REST/.NET 호출에서도 503을 반환했다. 같은 계정의 `gemini-3.5-flash-lite`에서는 최소 요청, 음식명 JSON 인식, 공개 바나나 사진 인식(바나나·추정 118g)이 성공했다. 실제 `POST /food/recognize`를 통해 바나나 사진은 200, 단색 비음식 PNG는 422로 처리되는 것도 확인했다. 모델의 향후 가용성을 보장하는 결과는 아니며 실제 사용자 음식 사진의 정확도는 별도 평가한다. 테스트 사진 원본: https://upload.wikimedia.org/wikipedia/commons/8/8a/Banana-Single.jpg (테스트 산출물은 Git에 포함하지 않음).

cmd에서 이미 실행 중인 서버에 모델 변경을 적용하려면 Ctrl+C 후 다음과 같이 실행한다. `setx`는 새 창에 적용되므로 기존 cmd에는 `set`도 필요하다.

```cmd
set "GEMINI_MODEL=gemini-3.5-flash-lite"
python server\food_server.py --port 8765
```

```powershell
python -m unittest discover -s server -v
```

실제 localhost HTTP 통신과 Gemini 요청/응답 매핑, 이미지 전달, 비음식·잘못된 JSON·빈 결과·잘못된 g·한도 오류를 검증한다.
테스트는 Gemini 호출을 대체하므로 API 키나 비용이 필요 없으며 실제 인식 정확도를 검증하지 않는다.
Android `FlowInstrumentation`에도 응답 계약 검사를 추가했다. 테스트 APK 빌드와 기기 실행은 별개다.

2026-11-18 검증: Python 테스트 12개 통과, `foodApiUrl=http://10.0.2.2:8000` 모드의 앱·테스트 APK 빌드 성공, Lint 오류 0개/경고 16개. 연결된 기기/에뮬레이터가 없어 Instrumentation은 실행하지 않았다. Gemini 키·모델 설정이 없어 실제 공급자 호출과 음식 인식 정확도는 아직 검증하지 않았다.

추가 확인: 사용자 로컬 서버의 `/health` 정상 응답 및 실제 Gemini 음식명 입력 요청 성공(비빔밥 → 음식 구성/추정 g)을 확인했다. 사진 입력 정확도와 Android 기기에서의 전체 흐름은 아직 확인하지 않았다. 8000/8001 포트 충돌 시 `python server/food_server.py --port 8765`로 실행하고 앱 주소의 포트도 8765로 맞춘다.

참조: [Gemini generateContent REST](https://ai.google.dev/api/generate-content), [구조화 출력](https://ai.google.dev/gemini-api/docs/structured-output).
