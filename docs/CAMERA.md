# 앱 내부 음식 촬영

S10의 안내용 카메라 그림을 CameraX 실시간 미리보기로 교체했다.

1. 음식 입력 → 사진 촬영을 선택한다.
2. 처음 실행할 때 카메라 권한을 허용한다.
3. 화면의 가이드 안에서 실제 카메라 영상을 확인한다.
4. 하단 사진 촬영 버튼을 누르면 외부 앱 전환 없이 JPEG를 저장한다.
5. 기존 음식 인식 → 음식/추정량 확인으로 이동한다.

`MealCamera`가 `PreviewView`, `Preview`, `ImageCapture`를 관리한다. 후면 카메라를 우선하고 후면이 없으면 전면을 사용한다. 미리보기와 사진에 같은 viewport를 적용한다. 이미지 저장은 앱 내부 `files/photos` 폴더이며 `PhotoProvider` URI로 기존 업로드 어댑터와 연결한다. 갤러리 사진 선택은 이전과 같다.

권한을 거부하면 안내와 허용 버튼을 표시하고, 재요청이 불가능하면 앱 권한 설정으로 연결한다. 미리보기 준비/촬영 중에는 중복 촬영을 막는다. 화면을 벗어나면 카메라 use case와 관찰자를 해제하며 이전 화면의 바인딩·촬영 콜백은 반영하지 않는다. 권한 설정에서 돌아오면 카메라를 다시 준비한다.

## 실행 확인

카메라 의존성을 추가했으므로 Android Studio에서 Gradle Sync 후 app을 다시 실행한다. 단순 Apply Changes 대신 Run으로 새 APK를 설치한다.
실제 음식이 보이는지는 기기의 카메라 입력에 달려 있다. 에뮬레이터는 가상 장면/테스트 영상을 표시할 수 있고 실제 휴대폰에서는 휴대폰 카메라 영상이 나온다. 음식 인식 정확도는 음식 사진으로 별도 확인한다.
촬영 성공은 `foodApiUrl` 설정과 별개다. 서버 모드에서는 촬영한 사진을 실제 서버에 보내고, URL 미설정이면 기존 데모 인식을 사용한다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\android-build.ps1 :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
python scripts/verify_flow.py --camera-only
```

카메라 단독 검사는 앱·테스트 APK를 설치하고 CAMERA 권한을 부여한다. 저장되지 않는 debug 미리보기 모드와 데모 인식을 사용해 기존 식사 기록을 유지하며 미리보기 → 직접 JPEG 촬영 → 인식 전달 → 음식 확인 화면 → 카메라 재진입을 검증한다. Gemini 인식 정확도 검사는 아니다.
옵션 없는 전체 흐름 검사는 기존과 같이 앱 데이터를 초기화하므로 일회용 에뮬레이터에서 데모 모드 빌드로만 실행한다.

참고: [CameraX 미리보기](https://developer.android.com/media/camera/camerax/preview), [사진 촬영](https://developer.android.com/media/camera/camerax/take-photo).

검증 기록 (2026-11-19): debug 앱·테스트 APK 빌드 성공, Lint 오류 0개/경고 19개. 에뮬레이터에 APK 설치는 성공했으나 단독 촬영 검사 도중 기기 연결이 끊겨 검사 결과를 얻지 못했다. 이후 테스트용 재부팅도 부팅 완료에 도달하지 않아 실제 미리보기/촬영 성공을 검증했다고 판단하지 않는다. Android Studio에서 정상 부팅한 기기 또는 실기기에서 카메라 단독 검사를 다시 실행해야 한다.

후속 검증: 정상 연결된 에뮬레이터에서 `python scripts/verify_flow.py --camera-only`가 통과했다. 실제 CameraX 미리보기 STREAMING 상태, 외부 카메라 앱 없이 JPEG 저장, 인식 어댑터로 URI 전달, 음식 확인 화면의 사진 표시, 카메라 화면 재진입을 확인했다. 이 검사는 데모 인식을 사용하며 실제 음식 사진의 Gemini 인식 정확도 검사는 아니다. 서버 모드에서도 debug 화면 미리보기 초기화가 네트워크를 호출하지 않도록 예시 데이터 생성도 분리했다.
