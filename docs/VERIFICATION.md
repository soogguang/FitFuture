# 구현 검증 기록

검증일: 2026-11-16

## 환경과 빌드

- macOS arm64, Android SDK 36, API 36 arm64 에뮬레이터
- Gradle Wrapper 9.5.0, Android Gradle Plugin 9.3.3
- OpenJDK 26 및 설치된 Android Studio 내장 JBR 25에서 debug 빌드·Lint 성공
- Android Studio 내장 JBR 25에서 debug·androidTest·release 빌드 성공
- Java 소스/바이트코드 호환성 17, minSdk 26

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:assembleRelease
```

Lint: **오류 0개, 경고 16개**. 남은 경고는 최신 SDK/Gradle 버전 안내, 플랫폼 ExifInterface 사용, 그래프의 draw 시 할당, 레이아웃 최적화·미사용 스타일 등의 항목입니다. 경고를 숨기는 baseline이나 `abortOnError=false`는 사용하지 않았습니다.

Release APK는 서명하지 않은 검증용 산출물입니다. 기기에 바로 설치할 수 있는 파일은 debug APK입니다.

## 기기에서 검증한 연결 흐름

`FlowInstrumentation`과 `scripts/verify_flow.py`가 최종 APK에서 PASS했습니다.

- 소개 → 필수 입력 누락 → 기본 정보 복구 → 목표·취향·예산 → 아바타 → 빈 홈
- 프로필 입력이 다음 단계와 저장 상태에 반영됨
- 텍스트 음식 인식 → 음식 확인 → 밥 200g을 150g으로 변경
- 음식 추가·삭제 후 영양 합계 **850 → 775 kcal** 반영
- 음식 이름 입력란이 실제 화면에 보이고 편집 가능한지 검사
- 대안 2 선택 → 상세 → 1·3·6개월 탭 → 피드백
- 계획은 대안 2, 실제 선택은 대안 1로 저장해 두 값을 구분하는지 검사
- 만족도·메모 입력 후 Activity 재생성 및 입력·선택 복원
- 로컬 JSON 저장 후 새 StateStore에서 기록·메모·만족도 복원
- 홈 아바타 라벨이 잘리지 않고 보이는지 검사
- 기록 상세 → 삭제 확인창 → 실제 삭제 및 로컬 데이터 반영
- AndroidX 뒤로가기 처리
- 갤러리·카메라 Intent와 취소 경로
- 실제 시스템 카메라 `com.android.camera2/...CaptureActivity`로 촬영 요청이 전달되는 것을 별도 확인
- 카메라 URI에 JPEG를 쓰고 성공 결과를 반환해 음식 확인 화면에 표시되는지 검사
- 갤러리 성공 결과의 URI가 동일 흐름으로 전달되는지 검사

촬영·갤러리 성공 자동 검사는 외부 앱 UI 조작 대신 Instrumentation 모니터가 결과를 돌려주는 방식입니다. 실제 카메라·사진 선택기의 제조사별 UI를 자동으로 검증했다는 의미는 아닙니다.

## 디자인과 화면 크기

- 제공된 PNG **56개 확인**: `03 Flow.png`는 흐름 참고용, 나머지 **55개는 화면·선택·기간·예외 상태**로 매핑
- 360×800dp 기준, 앱이 실제 전면에 있는지 확인하며 55개 상태 캡처
- 모든 상태의 캡처를 묶어 원본과 시각적으로 대조하고, 홈·음식 확인의 가로 배치 및 공통 간격 보정
- 홈 마지막 변경 후 별도 재캡처
- 320dp 너비·글꼴 배율 1.3, 960dp 가로 화면에서 S03·S08·S14·S16·S18·S20을 실행·캡처하고 런타임 예외가 없는지 확인
- 짧은 화면에서는 본문을 스크롤하고 주요 버튼과 하단 내비게이션을 유지
- 상태 바·시스템 내비게이션·키보드는 OS 렌더링이므로 PNG의 고정 시각·아이콘과 다를 수 있음

캡처·흐름 검사 로그는 `app/build/qa/`에 생성됩니다. README의 실행 화면은 실제 에뮬레이터 캡처를 축소한 것입니다.

## 아직 검증하거나 연결하지 않은 항목

- 실제 Gemini 인식, 식약처 영양 DB, 추천 서버, BMR/TDEE 기반 예측 모델
- 실제 캐릭터 5단계 이미지 — 현재는 원본 PNG의 번호 플레이스홀더
- 서버 피드백 저장과 개인화 학습
- API 26 실기기 및 다양한 제조사의 카메라·사진 선택기
- Android Studio GUI에서 Run을 누르는 과정 자체 — 동일 설치의 내장 JDK와 Wrapper로 빌드 검증
