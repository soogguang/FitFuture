package com.fitfuture.app.ui;

import android.graphics.Color;
import android.text.*;
import android.view.*;
import android.widget.*;
import com.fitfuture.app.MainActivity;
import com.fitfuture.app.R;
import com.fitfuture.app.domain.Models.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class MealScreens {
  private final MainActivity a;
  private final Ui u;

  public MealScreens(MainActivity a) {
    this.a = a;
    u = a.ui;
  }

  private LinearLayout s(String key) {
    return a.slot(key);
  }

  public void render(String screen) {
    switch (screen) {
      case "s08":
        home();
        break;
      case "s09":
        method();
        break;
      case "s10":
        camera();
        break;
      case "s11":
        text();
        break;
      case "s12":
        analyzing();
        break;
      case "s13":
        analysis();
        break;
      case "s14":
        confirm();
        break;
      case "s14_add":
        addFood();
        break;
      case "s15":
        error();
        break;
    }
  }

  public static boolean sameDay(long first, long second) {
    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
    return f.format(new Date(first)).equals(f.format(new Date(second)));
  }

  private List<MealRecord> today() {
    List<MealRecord> r = new ArrayList<>();
    for (MealRecord m : a.state.records)
      if (sameDay(m.timestamp, System.currentTimeMillis())) r.add(m);
    return r;
  }

  private void home() {
    LinearLayout welcome = u.card(s("welcome"));
    welcome.setBackgroundResource(R.drawable.bg_mint_strong);
    welcome.addView(u.text("안녕하세요, " + a.state.profile.name + "님", 12, R.color.primary_dark, true));
    u.gap(welcome, 12);
    u.title(welcome, "지금 어떤 음식을\n먹을 예정인가요?");
    u.button(welcome, "지금 먹을 음식 입력", a::startNewMeal);
    u.gap(s("nutrition"), 12);
    LinearLayout c = u.card(s("nutrition"));
    u.section(c, "오늘의 영양 요약");
    Nutrition total = new Nutrition(0, 0, 0, 0);
    for (MealRecord r : today())
      if (r.actualNutrition != null) total = total.plus(r.actualNutrition);
    LinearLayout line = u.row();
    TextView kcal = u.text(Ui.number(total.caloriesKcal), 22, R.color.primary, true);
    line.addView(kcal, new LinearLayout.LayoutParams(-2, -2));
    line.addView(u.text(" / 1,850 kcal · 예시 목표", 11, R.color.muted, false));
    c.addView(line);
    u.gap(c, 8);
    u.bar(c, total.caloriesKcal / 1850, R.color.primary, 6);
    u.gap(c, 8);
    LinearLayout macros = u.row();
    for (String t :
        new String[] {
          "탄수화물 " + Ui.number(total.carbohydrateGrams) + " g",
          "단백질 " + Ui.number(total.proteinGrams) + " g",
          "지방 " + Ui.number(total.fatGrams) + " g"
        }) u.weight(macros, u.text(t, 10, R.color.muted, false));
    c.addView(macros);
    u.gap(s("avatar"), 12);
    LinearLayout avatar = u.card(s("avatar")), r = u.row(), icon = u.column();
    u.figure(icon, a.avatar(), a.currentCell(a.avatar()), a.heightScale(), 56, 136);
    r.addView(icon, new LinearLayout.LayoutParams(-2, -2));
    LinearLayout label = u.column();
    label.setPadding(u.dp(20), 0, 0, 0);
    u.section(label, "나의 아바타");
    label.addView(u.text("미래의 나 보러가기 ›", 12, R.color.primary, true));
    r.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
    avatar.addView(r);
    avatar.setOnClickListener(v -> a.root("s24"));
    u.gap(s("records"), 18);
    u.section(s("records"), "오늘의 식사 기록");
    for (String when : new String[] {"아침", "점심", "저녁"}) {
      boolean found = false;
      for (MealRecord record : today())
        if (when.equals(record.mealType)) {
          recordRow(s("records"), record);
          found = true;
        }
      if (!found) emptyRecord(s("records"), when);
    }
  }

  public void recordRow(LinearLayout p, MealRecord record) {
    LinearLayout c = u.card(p);
    c.setPadding(u.dp(12), u.dp(14), u.dp(12), u.dp(14));
    LinearLayout r = u.row();
    TextView mealType = u.text(record.mealType, 11, R.color.muted, true);
    r.addView(mealType, new LinearLayout.LayoutParams(u.dp(40), -2));
    TextView name =
        u.text(
            record.meal.name + (record.actualChoice < 3 ? " · " + record.actualTitle : ""),
            14,
            R.color.text,
            true);
    u.weight(r, name);
    TextView kcal =
        u.text(
            record.actualNutrition == null
                ? "—"
                : Ui.number(record.actualNutrition.caloriesKcal) + " kcal",
            11,
            R.color.muted,
            false);
    kcal.setGravity(Gravity.END);
    r.addView(kcal, new LinearLayout.LayoutParams(-2, -2));
    c.addView(r);
    c.setOnClickListener(
        v -> {
          a.state.selectedRecordId = record.id;
          a.go("s23");
        });
    u.gap(p, 12);
  }

  private void emptyRecord(LinearLayout p, String when) {
    LinearLayout c = u.card(p);
    c.setPadding(u.dp(12), u.dp(14), u.dp(12), u.dp(14));
    LinearLayout row = u.row();
    row.addView(u.text(when, 11, R.color.muted, true), new LinearLayout.LayoutParams(u.dp(40), -2));
    u.weight(row, u.text("아직 기록이 없어요", 14, R.color.text, true));
    TextView action = u.text(when.equals("점심") ? "입력하기" : "—", 11, R.color.muted, false);
    action.setGravity(Gravity.END);
    row.addView(action, new LinearLayout.LayoutParams(-2, -2));
    c.addView(row);
    u.gap(p, 12);
    c.setContentDescription(when + " 식사 입력");
    c.setOnClickListener(
        v -> {
          a.startNewMeal();
          a.state.draftMealType = when;
        });
  }

  private void method() {
    u.heading(s("heading"), "먹을 음식을 알려주세요", "지금 먹으려는 음식을 입력하면 내 조건에 맞는 대안을 찾을 수 있어요.");
    u.photo(s("photo"), "음식 사진 예시", 152, "");
    u.gap(s("methods"), 12);
    u.menuRow(s("methods"), "사진 촬영", "카메라로 음식 전체를 찍어요", R.drawable.ic_camera, () -> a.go("s10"));
    u.menuRow(s("methods"), "갤러리", "저장된 사진을 가져오는 예시", R.drawable.ic_image, a::pickPhoto);
    u.menuRow(s("methods"), "텍스트 입력", "음식 이름을 직접 입력해요", R.drawable.ic_text, () -> a.go("s11"));
    u.small(s("note"), "음식을 인식한 뒤 음식 이름과 양을 확인할 수 있어요.");
  }

  private void camera() {
    u.heading(s("heading"), "음식이 잘 보이게 찍어주세요", "한 끼의 음식이 가이드 안에 들어오게 맞춰주세요.");
    LinearLayout p = s("camera");
    FrameLayout camera = new FrameLayout(a);
    android.graphics.drawable.GradientDrawable bg =
        new android.graphics.drawable.GradientDrawable();
    bg.setColor(Color.parseColor("#343D38"));
    bg.setCornerRadius(u.dp(16));
    camera.setBackground(bg);
    p.addView(camera, new LinearLayout.LayoutParams(-1, u.dp(372)));
    LinearLayout frame = u.column();
    frame.setGravity(Gravity.CENTER);
    android.graphics.drawable.GradientDrawable outline =
        new android.graphics.drawable.GradientDrawable();
    outline.setColor(Color.TRANSPARENT);
    outline.setStroke(u.dp(1), Color.WHITE);
    outline.setCornerRadius(u.dp(16));
    frame.setBackground(outline);
    frame.addView(u.icon(R.drawable.ic_camera, 36, R.color.white));
    u.gap(frame, 10);
    TextView label = u.text("카메라 뷰파인더", 12, R.color.white, false);
    label.setGravity(Gravity.CENTER);
    frame.addView(label);
    FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(-1, -1);
    fp.setMargins(u.dp(30), u.dp(46), u.dp(30), u.dp(46));
    camera.addView(frame, fp);
    u.gap(s("text_link"), 14);
    u.link(s("text_link"), "텍스트로 입력", () -> a.go("s11"));
    a.primary("사진 촬영", a::capturePhoto);
  }

  private void text() {
    u.heading(s("heading"), "어떤 음식을 먹을 예정인가요?", "");
    EditText input =
        u.input(
            s("food_name"), "음식 이름", a.state.inputText, "예) 돈가스 정식", v -> a.state.inputText = v);
    u.gap(s("frequent"), 18);
    u.section(s("frequent"), "자주 먹는 음식");
    u.chips(
        s("frequent"),
        new String[] {"돈가스 정식", "비빔밥", "샐러드"},
        Set.of(a.state.inputText),
        false,
        v -> {
          a.state.inputText = v;
          input.setText(v);
          a.refresh();
        });
    u.gap(s("notice"), 14);
    u.notice(s("notice"), "음식 이름으로도 시작할 수 있어요", "돈가스 정식처럼 메뉴 이름을 입력해 주세요.", 0);
    a.primary(
        "음식 인식하기",
        () -> {
          if (a.state.inputText.trim().isEmpty()) {
            input.setError("음식 이름을 입력해 주세요");
            input.requestFocus();
            return;
          }
          a.state.photoUri = "";
          a.recognize();
        });
  }

  private void analyzing() {
    LinearLayout p = s("loading");
    p.setGravity(Gravity.CENTER);
    u.gap(p, 136);
    p.addView(u.icon(R.drawable.ic_spark, 48, R.color.primary));
    u.gap(p, 24);
    TextView title = u.title(p, "음식을 인식하고 있어요");
    title.setGravity(Gravity.CENTER);
    TextView desc = u.muted(p, "입력한 음식 이름과 구성을 확인하고 있어요.");
    desc.setGravity(Gravity.CENTER);
    u.gap(s("progress"), 40);
    u.bar(s("progress"), a.recognitionReady ? 1 : 0.45, R.color.primary, 6);
    u.gap(s("notices"), 12);
    u.notice(
        s("notices"),
        "01 · 음식을 인식하고 있어요",
        "Gemini가 "
            + (a.state.inputText.isEmpty() ? "음식 사진" : a.state.inputText)
            + "의 구성 항목을 찾는 중이에요.",
        0);
    u.gap(s("notices"), 12);
    u.notice(s("notices"), "02 · 영양정보 확인 대기", "음식과 양을 확정하면 식약처 영양 DB에서 확인해요.", 0);
    u.gap(s("examples"), 20);
    u.link(s("examples"), "인식 실패 예시 보기", () -> a.go("s15"));
    u.link(s("examples"), "연결 오류 예시 보기", () -> a.go("s28"));
    a.primary(
        "음식과 양 확인하기",
        () -> {
          if (a.recognitionReady && a.state.meal != null) a.go("s14");
        });
    if (!a.recognitionReady) {
      View b = a.footer.getChildAt(0);
      b.setEnabled(false);
      b.setAlpha(.6f);
    }
  }

  private void analysis() {
    Meal m = a.state.meal;
    if (m == null || m.nutrition == null) {
      a.go("s11");
      return;
    }
    u.photo(s("photo"), m.name + " 사진", 216, m.photoUri);
    u.gap(s("status"), 10);
    LinearLayout status = u.row();
    TextView chip = u.text("식단 분석 완료", 11, R.color.white, true);
    chip.setBackgroundResource(R.drawable.bg_chip_selected);
    chip.setPadding(u.dp(16), u.dp(7), u.dp(16), u.dp(7));
    status.addView(chip, new LinearLayout.LayoutParams(-2, -2));
    status.addView(u.text("  오늘 " + a.state.draftMealType, 11, R.color.muted, false));
    s("status").addView(status);
    u.gap(s("meal"), 16);
    LinearLayout row = u.row();
    u.weight(row, u.text(m.name, 22, R.color.text, true));
    TextView edit = u.text("음식·섭취량 수정 ›", 11, R.color.primary, true);
    row.addView(edit, new LinearLayout.LayoutParams(-2, -2));
    edit.setOnClickListener(v -> a.go("s14"));
    s("meal").addView(row);
    u.gap(s("nutrition"), 26);
    nutritionCard(s("nutrition"), m.nutrition);
    u.gap(s("notes"), 14);
    u.small(s("notes"), "입력한 음식과 섭취량을 확인했어요");
    u.gap(s("notes"), 14);
    u.small(s("notes"), "식약처 영양 DB 계산 화면 예시 · 가상 영양값");
    a.primary("맞춤 대안 3개 보기", a::loadRecommendations);
  }

  public void nutritionCard(LinearLayout p, Nutrition n) {
    LinearLayout c = u.card(p);
    LinearLayout kcal = u.row();
    TextView amount = u.text(Ui.number(n.caloriesKcal), 36, R.color.primary, true);
    kcal.addView(amount, new LinearLayout.LayoutParams(-2, -2));
    TextView unit = u.text(" kcal", 16, R.color.primary, true);
    kcal.addView(unit);
    c.addView(kcal);
    u.gap(c, 10);
    LinearLayout bars = u.row();
    double total = n.carbohydrateGrams + n.proteinGrams + n.fatGrams;
    for (int i = 0; i < 3; i++) {
      View bar = new View(a);
      bar.setBackgroundResource(R.drawable.bg_chip_selected);
      bar.setBackgroundTintList(
          android.content.res.ColorStateList.valueOf(
              u.color(new int[] {R.color.carbs, R.color.primary, R.color.fat}[i])));
      LinearLayout.LayoutParams lp =
          new LinearLayout.LayoutParams(
              0,
              u.dp(5),
              (float)
                  (new double[] {n.carbohydrateGrams, n.proteinGrams, n.fatGrams}[i]
                      / Math.max(1, total)));
      lp.setMarginEnd(u.dp(3));
      bars.addView(bar, lp);
    }
    c.addView(bars);
    u.gap(c, 12);
    LinearLayout macros = u.row();
    String[] labels = {"탄수화물", "단백질", "지방"};
    double[] values = {n.carbohydrateGrams, n.proteinGrams, n.fatGrams};
    for (int i = 0; i < 3; i++) {
      LinearLayout col = u.column();
      u.small(col, labels[i]);
      u.gap(col, 6);
      col.addView(u.text(Ui.number(values[i]) + " g", 14, R.color.text, true));
      u.weight(macros, col);
    }
    c.addView(macros);
  }

  private void confirm() {
    Meal m = a.state.meal;
    if (m == null) {
      a.go("s11");
      return;
    }
    u.heading(s("heading"), "이렇게 드실 예정인가요?", "음식 이름과 양을 확인해 주세요. 확정한 양으로 영양정보를 계산해요.");
    u.photo(s("photo"), m.name + " 사진", 112, m.photoUri);
    u.gap(s("portions"), 12);
    for (FoodPortion f : new ArrayList<>(m.foods)) portion(s("portions"), f);
    u.notice(s("warning"), "이 음식이 맞나요?", "음식 이름과 양을 확인해 주세요.", 1);
    u.gap(s("add"), 12);
    u.outline(s("add"), "＋ 음식 추가", () -> a.go("s14_add"));
    u.gap(s("examples"), 12);
    LinearLayout examples = u.row();
    TextView allergy = u.text("알레르기 주의", 11, R.color.primary_dark, true);
    allergy.setGravity(Gravity.CENTER);
    allergy.setBackgroundResource(R.drawable.bg_mint);
    examples.addView(allergy, new LinearLayout.LayoutParams(0, u.dp(36), 1));
    allergy.setOnClickListener(v -> a.go("s29_allergy"));
    View gap = new View(a);
    examples.addView(gap, new LinearLayout.LayoutParams(u.dp(6), 1));
    TextView fail = u.text("영양정보 없음", 11, R.color.primary_dark, true);
    fail.setGravity(Gravity.CENTER);
    fail.setBackgroundResource(R.drawable.bg_mint);
    examples.addView(fail, new LinearLayout.LayoutParams(0, u.dp(36), 1));
    fail.setOnClickListener(v -> a.go("s29_nutrition"));
    s("examples").addView(examples);
    a.primary("수정 후 확정", a::confirmMeal);
  }

  private void portion(LinearLayout p, FoodPortion f) {
    LinearLayout c = u.card(p);
    c.setPadding(u.dp(12), u.dp(10), u.dp(12), u.dp(10));
    LinearLayout r = u.row();
    EditText name = new EditText(a);
    name.setText(f.name);
    name.setTextSize(14);
    name.setTextColor(u.color(R.color.text));
    name.setSingleLine(true);
    name.setBackgroundColor(Color.TRANSPARENT);
    name.setPadding(0, 0, 0, 0);
    name.setContentDescription("음식 이름 " + f.name);
    name.setSelectAllOnFocus(true);
    r.addView(name, new LinearLayout.LayoutParams(0, u.dp(36), 1));
    watch(
        name,
        v -> {
          f.name = v;
          a.state.invalidateAnalysis();
        });
    LinearLayout step = u.row();
    step.setBackgroundResource(R.drawable.bg_chip);
    TextView minus =
        stepButton(
            "−",
            () -> {
              f.grams = Math.max(10, f.grams - 10);
              a.state.invalidateAnalysis();
              a.refresh();
            });
    step.addView(minus, new LinearLayout.LayoutParams(u.dp(28), u.dp(36)));
    EditText grams = new EditText(a);
    grams.setText(Ui.number(f.grams));
    grams.setGravity(Gravity.CENTER);
    grams.setTextSize(12);
    grams.setTextColor(u.color(R.color.primary_dark));
    grams.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    grams.setSingleLine(true);
    grams.setSelectAllOnFocus(true);
    grams.setBackgroundColor(Color.TRANSPARENT);
    grams.setPadding(0, 0, 0, 0);
    grams.setContentDescription(f.name + " 섭취량 g");
    step.addView(grams, new LinearLayout.LayoutParams(u.dp(40), u.dp(36)));
    watch(
        grams,
        v -> {
          try {
            f.grams = Double.parseDouble(v);
          } catch (NumberFormatException ex) {
            f.grams = 0;
          }
          a.state.invalidateAnalysis();
        });
    TextView unit = u.text("g", 11, R.color.primary_dark, true);
    step.addView(unit, new LinearLayout.LayoutParams(u.dp(8), -2));
    step.addView(
        stepButton(
            "+",
            () -> {
              f.grams = Math.min(5000, f.grams + 10);
              a.state.invalidateAnalysis();
              a.refresh();
            }),
        new LinearLayout.LayoutParams(u.dp(28), u.dp(36)));
    r.addView(step, new LinearLayout.LayoutParams(-2, -2));
    ImageView remove = u.icon(R.drawable.ic_close, 22, R.color.muted);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(u.dp(28), u.dp(36));
    lp.setMarginStart(u.dp(8));
    r.addView(remove, lp);
    remove.setContentDescription(f.name + " 삭제");
    remove.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    remove.setOnClickListener(
        v -> {
          a.state.meal.foods.remove(f);
          a.state.invalidateAnalysis();
          a.refresh();
        });
    c.addView(r);
    u.gap(p, 12);
  }

  private TextView stepButton(String label, Runnable action) {
    TextView t = u.text(label, 14, R.color.primary_dark, true);
    t.setGravity(Gravity.CENTER);
    t.setOnClickListener(v -> action.run());
    t.setContentDescription(label.equals("+") ? "섭취량 늘리기" : "섭취량 줄이기");
    return t;
  }

  private void watch(EditText input, java.util.function.Consumer<String> changed) {
    input.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int st, int c, int aft) {}

          public void onTextChanged(CharSequence s, int st, int before, int count) {
            changed.accept(s.toString());
          }

          public void afterTextChanged(Editable e) {}
        });
  }

  private void addFood() {
    u.heading(s("heading"), "누락된 음식이 있나요?", "음식 이름과 양을 입력해 주세요.");
    EditText name = u.input(s("fields"), "음식 이름", "", "예) 돈가스 소스", null);
    u.gap(s("fields"), 16);
    EditText grams = u.input(s("fields"), "섭취량", "", "예) 20 g", null);
    grams.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    u.gap(s("note"), 16);
    u.small(s("note"), "입력한 항목은 음식 목록에 추가해 확인할 수 있어요.");
    a.primary(
        "음식 목록으로 돌아가기",
        () -> {
          String value = name.getText().toString().trim();
          double amount;
          try {
            amount = Double.parseDouble(grams.getText().toString().replaceAll("[^0-9.]", ""));
          } catch (NumberFormatException ex) {
            amount = 0;
          }
          if (value.isEmpty()) {
            name.setError("음식 이름을 입력해 주세요");
            return;
          }
          if (amount <= 0 || amount > 5000) {
            grams.setError("섭취량을 확인해 주세요");
            return;
          }
          if (a.state.meal == null) a.state.meal = new Meal(a.state.inputText);
          a.state.meal.foods.add(new FoodPortion(value, amount));
          a.state.invalidateAnalysis();
          a.persist();
          a.back();
        });
  }

  private void error() {
    LinearLayout p = s("error");
    p.setGravity(Gravity.CENTER);
    u.gap(p, 176);
    p.addView(u.icon(R.drawable.ic_warning, 42, R.color.error_text));
    u.gap(p, 20);
    TextView title = u.title(p, "음식을 인식하지 못했어요");
    title.setGravity(Gravity.CENTER);
    TextView body = u.muted(p, "조명이 밝은 곳에서 음식이 잘 보이도록 다시 찍어보세요.");
    body.setGravity(Gravity.CENTER);
    u.gap(s("alternative"), 32);
    u.outline(s("alternative"), "텍스트로 입력", () -> a.go("s11"));
    a.primary("다시 촬영", () -> a.go("s10"));
  }
}
