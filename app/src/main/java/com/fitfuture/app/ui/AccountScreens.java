package com.fitfuture.app.ui;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import com.fitfuture.app.MainActivity;
import com.fitfuture.app.R;
import com.fitfuture.app.domain.Models.*;
import java.text.*;
import java.util.*;

public final class AccountScreens {
  private final MainActivity a;
  private final Ui u;

  public AccountScreens(MainActivity a) {
    this.a = a;
    u = a.ui;
  }

  private LinearLayout s(String key) {
    return a.slot(key);
  }

  public void render(String screen) {
    switch (screen) {
      case "s22":
        history();
        break;
      case "s23":
        detail();
        break;
      case "s24":
        progress();
        break;
      case "s25":
        my();
        break;
      case "s26":
        new OnboardingScreens(a).preferences(true);
        break;
      case "s27":
        empty();
        break;
      case "s28":
        network();
        break;
      case "s29":
        saved();
        break;
      case "s29_required":
        required();
        break;
      case "s29_nutrition":
        nutritionError();
        break;
      case "s29_shortage":
        shortage();
        break;
      case "s29_allergy":
        allergy();
        break;
      case "s29_delete":
        a.page.post(this::deleteSheet);
        break;
    }
  }

  private String day(long stamp) {
    return new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(new Date(stamp));
  }

  private void history() {
    Calendar selected = Calendar.getInstance();
    if (!a.state.selectedDate.isEmpty())
      try {
        selected.setTime(
            new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).parse(a.state.selectedDate));
      } catch (ParseException ignored) {
      }
    u.title(s("date"), new SimpleDateFormat("M월 d일 EEEE", Locale.KOREA).format(selected.getTime()));
    u.gap(s("calendar"), 8);
    LinearLayout week = u.row();
    Calendar start = (Calendar) selected.clone();
    int delta = (start.get(Calendar.DAY_OF_WEEK) + 5) % 7;
    start.add(Calendar.DAY_OF_MONTH, -delta);
    String[] names = {"월", "화", "수", "목", "금", "토", "일"};
    String chosen = day(selected.getTimeInMillis());
    for (int i = 0; i < 7; i++) {
      Calendar date = (Calendar) start.clone();
      date.add(Calendar.DAY_OF_MONTH, i);
      String key = day(date.getTimeInMillis());
      LinearLayout c = u.column();
      c.setGravity(Gravity.CENTER);
      c.setBackgroundResource(chosen.equals(key) ? R.drawable.bg_primary : R.drawable.bg_card);
      TextView label =
          u.text(names[i], 11, chosen.equals(key) ? R.color.white : R.color.muted, false);
      label.setGravity(Gravity.CENTER);
      c.addView(label);
      u.gap(c, 8);
      TextView n =
          u.text(
              String.valueOf(date.get(Calendar.DAY_OF_MONTH)),
              14,
              chosen.equals(key) ? R.color.white : R.color.text,
              true);
      n.setGravity(Gravity.CENTER);
      c.addView(n);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, u.dp(60), 1);
      if (i < 6) lp.setMarginEnd(u.dp(4));
      week.addView(c, lp);
      c.setContentDescription(key + " 기록");
      c.setOnClickListener(
          v -> {
            a.state.selectedDate = key;
            a.refresh();
          });
    }
    s("calendar").addView(week);
    u.gap(s("records"), 20);
    u.section(s("records"), "오늘 기록");
    boolean has = false;
    for (MealRecord r : a.state.records)
      if (day(r.timestamp).equals(chosen)) {
        new MealScreens(a).recordRow(s("records"), r);
        has = true;
      }
    if (!has) {
      LinearLayout c = u.card(s("records"));
      u.muted(c, "아직 식사 기록이 없어요");
      c.setOnClickListener(v -> a.startNewMeal());
      u.gap(s("records"), 12);
    }
    LinearLayout c = u.card(s("chart"));
    u.section(c, "주간 kcal · 확장");
    double[] values = new double[7];
    String[] labels = new String[7];
    for (int i = 0; i < 7; i++) {
      Calendar date = (Calendar) start.clone();
      date.add(Calendar.DAY_OF_MONTH, i);
      String key = day(date.getTimeInMillis());
      labels[i] = String.valueOf(date.get(Calendar.DAY_OF_MONTH));
      for (MealRecord r : a.state.records)
        if (day(r.timestamp).equals(key) && r.actualNutrition != null)
          values[i] += r.actualNutrition.caloriesKcal;
    }
    GraphView chart = new GraphView(a, true);
    chart.bars(values, labels);
    c.addView(chart, new LinearLayout.LayoutParams(-1, u.dp(100)));
    u.small(c, "주간 추세를 보여주는 예시예요.");
    u.gap(s("notice"), 12);
    u.notice(s("notice"), "대안 실천률 · 확장", "실제로 먹은 대안 수를 기준으로 집계해요.", 0);
  }

  private void detail() {
    MealRecord r = a.state.record();
    if (r == null) {
      a.root("s22");
      return;
    }
    u.heading(
        s("heading"),
        r.meal.name,
        new SimpleDateFormat("M월 d일", Locale.KOREA).format(new Date(r.timestamp))
            + " · "
            + r.mealType);
    u.photo(s("photo"), r.meal.name + " 사진", 148, r.meal.photoUri);
    u.gap(s("summary"), 12);
    LinearLayout c = u.card(s("summary"));
    u.section(c, "원래 식단 → 실제 식사");
    u.small(
        c,
        r.meal.name
            + " "
            + Ui.number(r.meal.nutrition.caloriesKcal)
            + " kcal → "
            + r.actualTitle
            + " "
            + (r.actualNutrition == null
                ? "—"
                : Ui.number(r.actualNutrition.caloriesKcal) + " kcal"));
    u.gap(c, 12);
    u.small(c, "계획한 선택 · 추천 " + (r.plannedAlternative + 1));
    u.gap(c, 10);
    u.small(c, "실제 식사 · " + r.actualTitle);
    u.gap(c, 10);
    u.small(c, "표시 열량은 가상 예시예요.");
    u.gap(s("feedback"), 12);
    u.notice(
        s("feedback"), "식사 피드백 · 만족도 " + r.satisfaction + "/5", r.note.isEmpty() ? "" : r.note, 0);
    u.gap(s("delete"), 12);
    u.link(s("delete"), "기록 삭제", this::deleteSheet);
  }

  private void progress() {
    u.heading(s("heading"), "내 아바타 변화 기록", "기록한 체중과 식사 선택의 시나리오를 각각 확인해요.");
    u.button(
        s("compare"),
        "식사 선택의 미래 비교",
        () -> {
          if (a.state.meal == null) a.startNewMeal();
          else if (a.state.alternatives.isEmpty()) a.loadRecommendations();
          else a.go("s18");
        });
    u.gap(s("weeks"), 18);
    u.section(s("weeks"), "주차별 아바타 · 확장");
    LinearLayout row = u.row();
    for (int i = 0; i < 3; i++) {
      LinearLayout c = u.column();
      c.setGravity(Gravity.CENTER);
      c.setPadding(u.dp(4), u.dp(12), u.dp(4), u.dp(12));
      c.setBackgroundResource(R.drawable.bg_card);
      TextView week = u.text((i + 1) + "주 차", 12, R.color.text, true);
      week.setGravity(Gravity.CENTER);
      c.addView(week);
      u.gap(c, 12);
      // 기록한 체중의 변화율로 지금 체형 칸에서 이동 (미래 비교와 같은 규칙)
      double rate = 0;
      if (a.state.weights.size() > i)
        rate = (a.state.weights.get(i) - a.state.profile.weightKg) / a.state.profile.weightKg;
      com.fitfuture.avatar.core.BodyCellMapper.Future fw =
          com.fitfuture.avatar.core.BodyCellMapper.future(a.currentCell(a.avatar()), rate);
      u.figure(c, a.avatar(), fw.cell, fw.cheekLevel, a.heightScale(), 60, 150);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
      if (i < 2) lp.setMarginEnd(u.dp(6));
      row.addView(c, lp);
    }
    s("weeks").addView(row);
    u.gap(s("weeks"), 12);
    u.small(s("weeks"), "기록한 체중으로 지금 체형에서 몇 칸 움직였는지 보여줘요.");
    u.gap(s("weight"), 18);
    u.section(s("weight"), "직접 잰 체중 · 확장");
    double recent =
        a.state.weights.isEmpty()
            ? a.state.profile.weightKg
            : a.state.weights.get(a.state.weights.size() - 1);
    EditText weight = u.input(s("weight"), "오늘 실제 체중", Ui.number(recent) + " kg", "체중 kg", null);
    weight.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    u.gap(s("weight"), 12);
    u.outline(
        s("weight"),
        "체중 기록 저장",
        () -> {
          double v;
          try {
            v = Double.parseDouble(weight.getText().toString().replaceAll("[^0-9.]", ""));
          } catch (NumberFormatException ex) {
            v = 0;
          }
          if (v < 20 || v > 400) {
            weight.setError("체중을 확인해 주세요");
            return;
          }
          String date = day(System.currentTimeMillis());
          int existing = a.state.weightDates.indexOf(date);
          if (existing >= 0) a.state.weights.set(existing, v);
          else {
            a.state.weights.add(v);
            a.state.weightDates.add(date);
          }
          a.persist();
          Toast.makeText(a, "체중 기록을 저장했어요", Toast.LENGTH_SHORT).show();
          a.refresh();
        });
  }

  private void my() {
    Profile p = a.state.profile;
    LinearLayout c = u.card(s("profile"));
    c.setBackgroundResource(R.drawable.bg_mint);
    u.title(c, p.name + "님의 프로필");
    u.small(
        c,
        "대학생 · "
            + p.age
            + "세 · "
            + Ui.number(p.heightCm)
            + " cm · "
            + Ui.number(p.weightKg)
            + " kg");
    u.gap(c, 10);
    u.small(
        c,
        (p.goal.equals("체중 감량") ? "체중 관리" : p.goal)
            + " · 한 끼 "
            + String.format(Locale.KOREA, "%,d", p.budgetWon)
            + "원 · "
            + p.environment);
    u.gap(s("settings"), 12);
    u.menuRow(
        s("settings"),
        "목표",
        p.goal,
        R.drawable.ic_chevron,
        () -> {
          a.state.editingProfile = true;
          a.go("s04");
        });
    u.menuRow(
        s("settings"),
        "취향·알레르기",
        String.join("·", p.preferences) + " 선호 · " + String.join("·", p.allergies) + " 제외",
        R.drawable.ic_chevron,
        () -> a.go("s26"));
    u.menuRow(
        s("settings"),
        "예산·식사 환경",
        "한 끼 " + String.format(Locale.KOREA, "%,d", p.budgetWon) + "원 · " + p.environment,
        R.drawable.ic_chevron,
        () -> {
          a.state.editingProfile = true;
          a.go("s06");
        });
    u.menuRow(s("settings"), "아바타 다시 만들기", "얼굴·머리·옷·체형 바꾸기", R.drawable.ic_spark, () -> {
          a.avatarDraft = null;
          a.go("s07");
        });
    LinearLayout notifications = u.card(s("notifications")), r = u.row();
    u.weight(r, u.text("알림 설정 · 확장", 14, R.color.text, true));
    Switch toggle = new Switch(a);
    toggle.setEnabled(false);
    toggle.setContentDescription("알림 설정 확장 기능");
    r.addView(toggle);
    notifications.addView(r);
    u.gap(s("logout"), 16);
    TextView logout = u.text("로그아웃 · 확장", 14, R.color.muted, true);
    logout.setGravity(Gravity.CENTER);
    s("logout").addView(logout);
    c.setOnClickListener(
        v -> {
          a.state.editingProfile = true;
          a.go("s03");
        });
  }

  private void empty() {
    u.heading(s("greeting"), "안녕하세요, " + a.state.profile.name + "님", "오늘 먹을 한 끼부터 시작해 볼까요?");
    LinearLayout p = s("empty");
    p.setGravity(Gravity.CENTER);
    u.gap(p, 40);
    u.figure(p, a.avatar(), a.currentCell(a.avatar()), 0, a.heightScale(), 90, 220);
    u.gap(p, 22);
    TextView title = u.text("아직 식사 기록이 없어요", 18, R.color.text, true);
    title.setGravity(Gravity.CENTER);
    p.addView(title);
    u.gap(p, 12);
    TextView sub = u.muted(p, "먹을 음식을 입력하면 내게 맞는 대안을 볼 수 있어요.");
    sub.setGravity(Gravity.CENTER);
    a.primary("첫 식사 입력", a::startNewMeal);
  }

  private void network() {
    LinearLayout p = s("error");
    p.setGravity(Gravity.CENTER);
    u.gap(p, 202);
    p.addView(u.icon(R.drawable.ic_wifi_off, 42, R.color.error_text));
    u.gap(p, 20);
    TextView title = u.title(p, "잠시 연결이 끊겼어요");
    title.setGravity(Gravity.CENTER);
    TextView body = u.muted(p, "네트워크 연결을 확인한 뒤 다시 시도해 주세요.");
    body.setGravity(Gravity.CENTER);
    a.primary("다시 시도", a::retry);
  }

  private void saved() {
    u.heading(s("heading"), "설정을 저장했어요", "");
    u.notice(s("notice"), "다음 추천에 반영돼요", "변경한 정보를 다음 식사 추천에 활용해요.", 0);
    LinearLayout toast = s("toast");
    toast.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1));
    toast.setGravity(Gravity.BOTTOM);
    TextView bar = u.text("✓  변경 사항을 저장했어요", 14, R.color.white, true);
    bar.setBackgroundResource(R.drawable.bg_dark);
    bar.setPadding(u.dp(14), u.dp(16), u.dp(14), u.dp(16));
    toast.addView(bar);
    u.gap(toast, 64);
    a.primary("마이로 돌아가기", () -> a.root("s25"));
  }

  private void required() {
    u.gap(s("notice"), 140);
    u.notice(s("notice"), "기본 정보를 먼저 입력해 주세요", "목표 열량 설정에 필요한 나이, 키, 체중, 활동량을 입력해 주세요.", 1);
    a.primary("기본 정보 입력", () -> a.go("s03"));
  }

  private void nutritionError() {
    u.gap(s("notice"), 180);
    u.notice(s("notice"), "영양정보를 찾지 못했어요", "음식 이름을 더 구체적으로 입력하거나 목록에서 비슷한 음식을 골라 주세요.", 2);
    u.gap(s("retry"), 14);
    u.outline(s("retry"), "다시 입력", () -> a.go("s11"));
    a.primary("음식 정보 수정", () -> a.go(a.state.meal == null ? "s11" : "s14"));
  }

  private void shortage() {
    u.gap(s("notice"), 140);
    u.notice(
        s("notice"),
        "조건에 맞는 대안이 충분하지 않아요",
        "알레르기와 예산 조건을 충족한 후보만 보여드려요. 다른 음식을 입력하거나 식사 조건을 확인해 주세요.",
        1);
    u.gap(s("retry"), 14);
    u.outline(
        s("retry"),
        "식사 조건 확인",
        () -> {
          a.state.editingProfile = true;
          a.go("s06");
        });
    a.primary("다른 음식 입력", () -> a.go("s11"));
  }

  private void allergy() {
    u.heading(s("heading"), "음식과 양을 확인해 주세요", "");
    u.photo(
        s("photo"),
        (a.state.meal == null ? "돈가스 정식" : a.state.meal.name) + " 사진",
        124,
        a.state.photoUri);
    a.page.post(
        () -> {
          if (a.screen.equals("s29_allergy")) allergySheet();
        });
  }

  private Dialog dialog(LinearLayout body, boolean bottom) {
    Dialog dialog = new Dialog(a);
    dialog.setContentView(body);
    Window w = dialog.getWindow();
    if (w != null) {
      w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
      w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
      WindowManager.LayoutParams params = w.getAttributes();
      params.dimAmount = .16f;
      w.setAttributes(params);
      w.setGravity(bottom ? Gravity.BOTTOM : Gravity.CENTER);
      w.setLayout(-1, -2);
    }
    a.activeDialog = dialog;
    dialog.setOnDismissListener(
        ignored -> {
          if (a.activeDialog == dialog) a.activeDialog = null;
        });
    dialog.show();
    if (w != null) {
      w.setLayout(a.getResources().getDisplayMetrics().widthPixels - (bottom ? 0 : u.dp(48)), -2);
      w.setGravity(bottom ? Gravity.BOTTOM : Gravity.CENTER);
    }
    return dialog;
  }

  public void deleteSheet() {
    MealRecord record = a.state.record();
    if (record == null) {
      a.root("s22");
      return;
    }
    LinearLayout body = u.column();
    body.setPadding(u.dp(16), u.dp(20), u.dp(16), u.dp(16));
    body.setBackgroundResource(R.drawable.bg_sheet);
    u.section(body, "식사 기록을 삭제할까요?");
    u.muted(body, record.meal.name + "의 식사 기록과 피드백이 삭제돼요.");
    u.gap(body, 20);
    LinearLayout row = u.row();
    TextView cancel = u.text("취소", 14, R.color.primary, true);
    cancel.setGravity(Gravity.CENTER);
    row.addView(cancel, new LinearLayout.LayoutParams(0, u.dp(44), 1));
    TextView remove = u.text("삭제", 14, R.color.error_text, true);
    remove.setGravity(Gravity.CENTER);
    row.addView(remove, new LinearLayout.LayoutParams(0, u.dp(44), 1));
    body.addView(row);
    Dialog d = dialog(body, false);
    cancel.setOnClickListener(
        v -> {
          d.dismiss();
          if (a.screen.equals("s29_delete")) a.back();
        });
    remove.setOnClickListener(
        v -> {
          a.state.records.removeIf(r -> r.id.equals(record.id));
          a.state.selectedRecordId = "";
          a.persist();
          d.dismiss();
          a.root("s22");
        });
  }

  private void allergySheet() {
    LinearLayout body = u.column();
    body.setPadding(u.dp(24), u.dp(18), u.dp(24), u.dp(16));
    body.setBackgroundResource(R.drawable.bg_sheet);
    View handle = new View(a);
    handle.setBackgroundResource(R.drawable.bg_placeholder);
    LinearLayout.LayoutParams h = new LinearLayout.LayoutParams(u.dp(30), u.dp(4));
    h.gravity = Gravity.CENTER;
    h.bottomMargin = u.dp(18);
    body.addView(handle, h);
    u.title(body, "갑각류 알레르기 주의");
    u.muted(body, "이 음식에는 갑각류가 포함될 수 있어요. 원재료를 확인하거나 음식 목록에서 제외해 주세요.");
    u.gap(body, 12);
    final Dialog[] ref = new Dialog[1];
    u.button(
        body,
        "음식 목록 확인",
        () -> {
          ref[0].dismiss();
          a.go("s14");
        });
    ref[0] = dialog(body, true);
  }
}
