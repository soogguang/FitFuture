# 화면 목록과 이동 흐름

`../ui`의 56개 PNG 중 `03 Flow.png`는 참고 흐름도로만 사용합니다. 나머지 55개를 아래 화면·선택 상태에 대응시킵니다. PNG 파일 자체는 앱에 포함하지 않습니다.

## 주 흐름

시작 → 소개 3장 → 기본 정보 → 목표 → 취향·알레르기 → 예산 → 아바타 → 홈 → 입력 방식 → 촬영/갤러리/텍스트 → 인식 → 음식·양 확정 → 영양 결과 → 대안 3개 → 대안 상세/미래 비교 → 실제 식사·만족도·메모 → 기록 완료 → 홈·기록 상세

하단 내비게이션은 홈·기록·식단 입력·미래의 나·마이를 연결합니다. 미래 비교에서 1·3·6개월, 3개 대안, 가정 펼침을 각각 상태로 유지합니다.

## PNG 대응표

| 원본 PNG | 레이아웃 / 상태 |
| --- | --- |
| S22_History.png | `screen_s22.xml` |
| S23_History_Detail.png | `screen_s23.xml` |
| S24_Avatar_Progress.png | `screen_s24.xml` |
| S25_MyPage.png | `screen_s25.xml` |
| S26_Edit_Preference.png | `screen_s26.xml` |
| S27_Empty_Home.png | `screen_s27.xml` |
| S28_Network_Error.png | `screen_s28.xml` |
| S29_Toasts_Sheets.png | `screen_s29.xml` |
| S29_Toasts_Sheets__삭제 확인.png | `screen_s29_delete.xml` |
| S29_Toasts_Sheets__알레르기 주의.png | `screen_s29_allergy.xml` |
| S29_Toasts_Sheets__영양 DB 매칭 실패.png | `screen_s29_nutrition.xml` |
| S29_Toasts_Sheets__추천 부족.png | `screen_s29_shortage.xml` |
| S29_Toasts_Sheets__필수 정보 누락.png | `screen_s29_required.xml` |
| S01_Splash.png | `screen_s01.xml` |
| S02_Onboarding_Intro.png | `screen_s02.xml` · intro=0 |
| S03_Profile_Basic.png | `screen_s03.xml` |
| S04_Profile_Goal.png | `screen_s04.xml` |
| S05_Profile_Preference.png | `screen_s05.xml` |
| S06_Profile_Budget.png | `screen_s06.xml` |
| S07_Avatar_Create.png | `screen_s07.xml` |
| S16_Alternatives.png | `screen_s16.xml` |
| S17_Alternative_Detail.png | `screen_s17.xml` |
| S18_Future_Compare.png | `screen_s18.xml` |
| S19_Future_Tab_States.png | `screen_s18.xml` |
| S19_Future_Tab_States__3개월.png | `screen_s18.xml` · months=3 |
| S19_Future_Tab_States__6개월.png | `screen_s18.xml` · months=6 |
| S20_Choice_Feedback.png | `screen_s20.xml` |
| S21_Feedback_Done.png | `screen_s21.xml` |
| S02_Onboarding_Intro__02.png | `screen_s02.xml` · intro=1 |
| S02_Onboarding_Intro__03.png | `screen_s02.xml` · intro=2 |
| S14_Food_Confirm__Add.png | `screen_s14_add.xml` |
| S16_Alternatives__Loading.png | `screen_s16_loading.xml` |
| S16_Alternatives__대안2.png | `screen_s16.xml` · alternative=1 |
| S16_Alternatives__대안3.png | `screen_s16.xml` · alternative=2 |
| S17_Alternative_Detail__대안2.png | `screen_s17.xml` · alternative=1 |
| S17_Alternative_Detail__대안3.png | `screen_s17.xml` · alternative=2 |
| S18_Future_Compare__가정 펼침.png | `screen_s18.xml` · expanded=True |
| S18_Future_Compare__대안2.png | `screen_s18.xml` · alternative=1 |
| S18_Future_Compare__대안3.png | `screen_s18.xml` · alternative=2 |
| S19_Future_Tab_States__3개월__대안2.png | `screen_s18.xml` · months=3, alternative=1 |
| S19_Future_Tab_States__3개월__대안3.png | `screen_s18.xml` · months=3, alternative=2 |
| S19_Future_Tab_States__6개월__대안2.png | `screen_s18.xml` · months=6, alternative=1 |
| S19_Future_Tab_States__6개월__대안3.png | `screen_s18.xml` · months=6, alternative=2 |
| S20_Choice_Feedback__대안2.png | `screen_s20.xml` · alternative=1 |
| S20_Choice_Feedback__대안3.png | `screen_s20.xml` · alternative=2 |
| S21_Feedback_Done__대안2.png | `screen_s21.xml` · alternative=1 |
| S21_Feedback_Done__대안3.png | `screen_s21.xml` · alternative=2 |
| S08_Home.png | `screen_s08.xml` |
| S09_Input_Method.png | `screen_s09.xml` |
| S10_Input_Camera.png | `screen_s10.xml` |
| S11_Input_Text.png | `screen_s11.xml` |
| S12_Analyzing.png | `screen_s12.xml` |
| S13_Analysis_Result.png | `screen_s13.xml` |
| S14_Food_Confirm.png | `screen_s14.xml` |
| S15_Recognition_Error.png | `screen_s15.xml` |

S19는 별도 중복 화면을 만들지 않고 S18의 기간 선택 상태로 구현했습니다. S29 변형은 지정된 오류 화면·삭제 확인 다이얼로그·알레르기 바텀시트로 연결합니다.
