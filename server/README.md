# 음식 인식 로컬 서버

첫 단계: 사진/음식명 → Gemini → 음식 목록과 추정 g → 앱 S14 확인 화면.
통합 앱에서는 영양·추천을 별도 `recommendation-server`에, 미래 비교를 `AvatarFutureService`에 연결한다. 새로운 음식은 영양 DB에 없으면 영양 확인 단계에서 매칭 실패할 수 있다.
Python 3.11 이상 표준 라이브러리만 사용하므로 pip 설치는 필요 없다.

## 실행

프로젝트 루트 PowerShell에서 서버 프로세스에만 환경변수를 설정한다. 키를 채팅이나 Git에 넣지 않는다.
아래 두 값은 본인 계정에서 발급/확인한 실제 값으로 바꾼다. `.env` 자동 로드는 하지 않는다.

```powershell
$env:GEMINI_API_KEY='YOUR_GEMINI_API_KEY'
$env:GEMINI_MODEL='gemini-4-hydrogen'
python server/food_server.py
```

기본 바인딩은 `127.0.0.1:8000`. 키/모델이 없으면 시작 시 오류를 내며 가짜 인식으로 대체하지 않는다.
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

공급자 대기 시간은 40초, 앱 응답 대기는 55초다. 자동 재시도로 유료 호출을 반복하지 않는다.
화면을 떠난 결과는 기존 요청 세대 검사로 반영하지 않지만 이미 시작한 공급자 호출 자체는 취소하지 않는다.

## 검증

```powershell
python -m unittest discover -s server -v
```

실제 localhost HTTP 통신과 Gemini 요청/응답 매핑, 이미지 전달, 비음식·잘못된 JSON·빈 결과·잘못된 g·한도 오류를 검증한다.
테스트는 Gemini 호출을 대체하므로 API 키나 비용이 필요 없으며 실제 인식 정확도를 검증하지 않는다.
Android `FlowInstrumentation`에도 응답 계약 검사를 추가했다. 테스트 APK 빌드와 기기 실행은 별개다.

2026-11-18 검증: Python 테스트 12개 통과, `foodApiUrl=http://10.0.2.2:8000` 모드의 앱·테스트 APK 빌드 성공, Lint 오류 0개/경고 16개. 연결된 기기/에뮬레이터가 없어 Instrumentation은 실행하지 않았다. Gemini 키·모델 설정이 없어 실제 공급자 호출과 음식 인식 정확도는 아직 검증하지 않았다.

추가 확인: 사용자 로컬 서버의 `/health` 정상 응답 및 실제 Gemini 음식명 입력 요청 성공(비빔밥 → 음식 구성/추정 g)을 확인했다. 사진 입력 정확도와 Android 기기에서의 전체 흐름은 아직 확인하지 않았다. 8000/8001 포트 충돌 시 `python server/food_server.py --port 8765`로 실행하고 앱 주소의 포트도 8765로 맞춘다.

참조: [Gemini generateContent REST](https://ai.google.dev/api/generate-content), [구조화 출력](https://ai.google.dev/gemini-api/docs/structured-output).
