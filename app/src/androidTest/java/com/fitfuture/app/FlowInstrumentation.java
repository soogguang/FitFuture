package com.fitfuture.app;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import com.fitfuture.app.data.*;
import com.fitfuture.app.domain.Models.*;

/** Dependency-free device tests of the complete connected flow and persisted data. */
public final class FlowInstrumentation extends Instrumentation {
  private MainActivity activity;

  @Override
  public void onCreate(Bundle args) {
    super.onCreate(args);
    start();
  }

  @Override
  public void onStart() {
    Bundle result = new Bundle();
    try {
      checkFoodResponseContract();
      runFlow();
      result.putString(
          "stream",
          "PASS: onboarding, validation, recognition, quantity editing, add/remove food,"
              + " recommendation selection, detail, 1/3/6-month tabs, actual-vs-planned feedback,"
              + " local persistence, recreation, native back, deletion, camera/gallery intents\n");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable error) {
      result.putString(
          "stream", "FAIL: " + error + "\n" + android.util.Log.getStackTraceString(error));
      finish(Activity.RESULT_CANCELED, result);
    }
  }

  private void checkFoodResponseContract() throws Exception {
    Meal meal = RemoteFoodAnalysis.parse(new org.json.JSONObject(
        "{\"name\":\"비빔밥\",\"foods\":[{\"name\":\"비빔밥\",\"grams\":400}]}"));
    require(meal.name.equals("비빔밥") && meal.foods.get(0).grams == 400 && !meal.confirmed,
        "Remote recognition must preserve food and keep estimates unconfirmed");
    for (String invalid : new String[] {
        "{\"name\":\"밥\",\"foods\":[]}",
        "{\"name\":\"밥\",\"foods\":[{\"name\":\"밥\",\"grams\":0}]}",
        "{\"name\":\"밥\",\"foods\":[{\"name\":\"밥\",\"grams\":\"100\"}]}"}) {
      boolean rejected = false;
      try { RemoteFoodAnalysis.parse(new org.json.JSONObject(invalid)); }
      catch (ModuleException | org.json.JSONException expected) { rejected = true; }
      require(rejected, "Invalid remote response must be rejected");
    }
  }

  private void onMain(Runnable action) {
    java.util.concurrent.atomic.AtomicReference<Throwable> error =
        new java.util.concurrent.atomic.AtomicReference<>();
    super.runOnMainSync(
        () -> {
          try {
            action.run();
          } catch (Throwable t) {
            error.set(t);
          }
        });
    if (error.get() != null) throw new AssertionError("UI action failed", error.get());
  }

  private void require(boolean b, String message) {
    if (!b) throw new AssertionError(message);
  }

  private View find(View root, String text) {
    if (root instanceof TextView && ((TextView) root).getText().toString().equals(text))
      return root;
    if (text.equals(root.getContentDescription())) return root;
    if (root instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) root;
      for (int i = 0; i < group.getChildCount(); i++) {
        View v = find(group.getChildAt(i), text);
        if (v != null) return v;
      }
    }
    return null;
  }

  private View clickable(View root, String text) {
    if ((root instanceof TextView && ((TextView) root).getText().toString().equals(text))
        || text.equals(root.getContentDescription())) {
      View candidate = root;
      while (candidate != null) {
        if (candidate.isClickable()) return candidate;
        candidate = candidate.getParent() instanceof View ? (View) candidate.getParent() : null;
      }
    }
    if (root instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) root;
      for (int i = 0; i < group.getChildCount(); i++) {
        View v = clickable(group.getChildAt(i), text);
        if (v != null) return v;
      }
    }
    return null;
  }

  private void click(String text) {
    onMain(
        () -> {
          View v = clickable(activity.getWindow().getDecorView(), text);
          require(v != null, "Missing clickable control: " + text + " on " + activity.screen);
          require(v.isEnabled(), "Disabled control: " + text);
          v.performClick();
        });
    waitForIdleSync();
  }

  private View findInput(View root, String description) {
    if (root instanceof EditText && description.equals(root.getContentDescription())) return root;
    if (root instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) root;
      for (int i = 0; i < group.getChildCount(); i++) {
        View found = findInput(group.getChildAt(i), description);
        if (found != null) return found;
      }
    }
    return null;
  }

  private void input(String description, String value) {
    onMain(
        () -> {
          View v = findInput(activity.getWindow().getDecorView(), description);
          require(v instanceof EditText, "Missing input: " + description);
          ((EditText) v).setText(value);
        });
  }

  private void screen(String name) {
    require(activity.screen.equals(name), "Expected " + name + ", got " + activity.screen);
  }

  private void waitUntil(java.util.function.BooleanSupplier check) throws Exception {
    long until = System.currentTimeMillis() + 15000;
    while (!check.getAsBoolean() && System.currentTimeMillis() < until) {
      Thread.sleep(80);
      waitForIdleSync();
    }
    require(check.getAsBoolean(), "Timed out on " + activity.screen);
  }

  private void runFlow() throws Exception {
    getTargetContext()
        .getSharedPreferences("fitfuture.v1", Context.MODE_PRIVATE)
        .edit()
        .clear()
        .commit();
    Intent intent = new Intent(getTargetContext(), MainActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    activity = (MainActivity) startActivitySync(intent);
    waitForIdleSync();
    screen("s01");
    click("시작하기");
    click("건너뛰기");
    screen("s03");
    input("나이", "");
    click("다음");
    screen("s29_required");
    click("기본 정보 입력");
    input("나이", "23");
    input("키", "170");
    input("현재 체중", "65");
    click("다음");
    screen("s04");
    input("목표 체중", "60");
    click("다음");
    click("비빔밥");
    click("다음");
    screen("s06");
    click("설정 완료");
    click("기본 아바타로 시작");
    screen("s27");
    require(
        activity.state.profile.age == 23 && activity.state.profile.heightCm == 170,
        "Profile inputs lost");
    click("첫 식사 입력");
    click("텍스트 입력");
    input("음식 이름", "돈가스 정식");
    click("음식 인식하기");
    waitUntil(() -> activity.recognitionReady);
    click("음식과 양 확인하기");
    screen("s14");
    onMain(
        () -> {
          View name = findInput(activity.getWindow().getDecorView(), "음식 이름 밥");
          android.graphics.Rect rect = new android.graphics.Rect();
          require(
              name != null && name.getGlobalVisibleRect(rect) && rect.width() > 30,
              "Food name is not visibly editable");
        });
    input("밥 섭취량 g", "150");
    click("＋ 음식 추가");
    input("음식 이름", "돈가스 소스");
    input("섭취량", "20");
    click("음식 목록으로 돌아가기");
    require(activity.state.meal.foods.size() == 4, "Food add failed");
    click("돈가스 소스 삭제");
    require(activity.state.meal.foods.size() == 3, "Food delete failed");
    click("수정 후 확정");
    waitUntil(() -> activity.screen.equals("s13"));
    require(
        Math.abs(activity.state.meal.nutrition.caloriesKcal - 775) < .1,
        "Confirmed portions did not change nutrition");
    click("맞춤 대안 3개 보기");
    waitUntil(() -> activity.screen.equals("s16"));
    click("추천 2 소스 양 조절");
    require(activity.state.selectedAlternative == 1, "Selection lost");
    click("추천 근거 및 상세 ›");
    screen("s17");
    click("이 대안 선택");
    screen("s18");
    waitUntil(() -> activity.futureComparisons.size() == 3);
    click("3개월");
    require(activity.state.months == 3, "3 month tab failed");
    click("6개월");
    require(activity.state.months == 6, "6 month tab failed");
    click("1개월");
    click("이 대안으로 먹을게요");
    screen("s20");
    click("추천 1 · 밥 양 조절");
    click("만족도 5점");
    input("한 줄 메모", "실천하기 편했어요");
    ActivityMonitor monitor = addMonitor(MainActivity.class.getName(), null, false);
    onMain(() -> activity.recreate());
    Activity recreated = monitor.waitForActivityWithTimeout(5000);
    require(recreated instanceof MainActivity, "Activity recreation failed");
    activity = (MainActivity) recreated;
    removeMonitor(monitor);
    waitForIdleSync();
    screen("s20");
    require(activity.state.note.equals("실천하기 편했어요"), "Memo lost on recreation");
    require(
        activity.state.actualChoice == 0 && activity.state.selectedAlternative == 1,
        "Choices lost on recreation");
    click("식사 기록 저장");
    screen("s21");
    AppState stored = new StateStore(getTargetContext()).load();
    require(stored.records.size() == 1, "Record not persisted");
    MealRecord r = stored.records.get(0);
    require(
        r.actualChoice == 0 && r.plannedAlternative == 1 && r.satisfaction == 5,
        "Actual/planned feedback mixed");
    require(r.note.equals("실천하기 편했어요"), "Memo not persisted");
    click("홈으로 가기");
    screen("s08");
    onMain(
        () -> {
          View label = find(activity.getWindow().getDecorView(), "나의 아바타");
          android.graphics.Rect rect = new android.graphics.Rect();
          require(
              label != null && label.getGlobalVisibleRect(rect) && rect.width() > 20,
              "Home avatar label is clipped");
        });
    click("기록");
    screen("s22");
    click("돈가스 정식 · 밥 양 조절");
    screen("s23");
    click("기록 삭제");
    waitForIdleSync();
    onMain(
        () -> {
          require(activity.activeDialog != null, "Deletion dialog missing");
          View remove = clickable(activity.activeDialog.getWindow().getDecorView(), "삭제");
          require(remove != null, "Delete confirmation control missing");
          remove.performClick();
        });
    waitUntil(() -> activity.screen.equals("s22"));
    require(new StateStore(getTargetContext()).load().records.isEmpty(), "Delete not persisted");
    click("홈");
    screen("s27");
    click("첫 식사 입력");
    IntentFilter galleryFilter = new IntentFilter(Intent.ACTION_OPEN_DOCUMENT);
    galleryFilter.addCategory(Intent.CATEGORY_OPENABLE);
    galleryFilter.addDataType("image/*");
    ActivityMonitor gallery =
        addMonitor(galleryFilter, new ActivityResult(Activity.RESULT_CANCELED, null), true);
    click("갤러리");
    require(gallery.getHits() == 1, "Gallery intent not launched");
    removeMonitor(gallery);
    click("사진 촬영");
    screen("s10");
    ActivityMonitor camera =
        addMonitor(
            new IntentFilter(android.provider.MediaStore.ACTION_IMAGE_CAPTURE),
            new ActivityResult(Activity.RESULT_CANCELED, null),
            true);
    click("사진 촬영");
    require(camera.getHits() == 1, "Camera intent not launched");
    removeMonitor(camera);
    onMain(() -> activity.getOnBackPressedDispatcher().onBackPressed());
    waitForIdleSync();
    screen("s09");
    // Exercise the real URI provider and both success callbacks without depending on a vendor
    // camera UI.
    click("사진 촬영");
    java.util.concurrent.atomic.AtomicReference<android.net.Uri> fixture =
        new java.util.concurrent.atomic.AtomicReference<>();
    ActivityMonitor successfulCamera =
        new ActivityMonitor() {
          @Override
          public ActivityResult onStartActivity(Intent request) {
            if (!android.provider.MediaStore.ACTION_IMAGE_CAPTURE.equals(request.getAction()))
              return null;
            android.net.Uri uri =
                request.getParcelableExtra(android.provider.MediaStore.EXTRA_OUTPUT);
            fixture.set(uri);
            try (java.io.OutputStream output =
                getTargetContext().getContentResolver().openOutputStream(uri)) {
              android.graphics.Bitmap bitmap =
                  android.graphics.Bitmap.createBitmap(
                      64, 64, android.graphics.Bitmap.Config.ARGB_8888);
              bitmap.eraseColor(android.graphics.Color.rgb(160, 110, 60));
              bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, output);
              bitmap.recycle();
            } catch (Exception ex) {
              throw new IllegalStateException(ex);
            }
            return new ActivityResult(Activity.RESULT_OK, null);
          }
        };
    addMonitor(successfulCamera);
    click("사진 촬영");
    removeMonitor(successfulCamera);
    waitUntil(() -> activity.recognitionReady);
    require(activity.state.photoUri.equals(fixture.get().toString()), "Camera URI not propagated");
    click("음식과 양 확인하기");
    screen("s14");
    require(
        activity.findViewById(R.id.photo_image).getVisibility() == View.VISIBLE,
        "Captured photo not displayed");
    onMain(() -> activity.root("s09"));
    ActivityMonitor successfulGallery =
        new ActivityMonitor() {
          @Override
          public ActivityResult onStartActivity(Intent request) {
            if (!Intent.ACTION_OPEN_DOCUMENT.equals(request.getAction())) return null;
            return new ActivityResult(Activity.RESULT_OK, new Intent().setData(fixture.get()));
          }
        };
    addMonitor(successfulGallery);
    click("갤러리");
    removeMonitor(successfulGallery);
    waitUntil(() -> activity.recognitionReady);
    require(activity.state.photoUri.equals(fixture.get().toString()), "Gallery URI not propagated");
  }
}
