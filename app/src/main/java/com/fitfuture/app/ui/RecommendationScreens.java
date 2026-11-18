package com.fitfuture.app.ui;

import android.view.*;
import android.widget.*;
import com.fitfuture.app.MainActivity;
import com.fitfuture.app.R;
import com.fitfuture.app.domain.Models.*;
import java.util.*;

public final class RecommendationScreens {
  private final MainActivity a;
  private final Ui u;

  public RecommendationScreens(MainActivity a) {
    this.a = a;
    u = a.ui;
  }

  private LinearLayout s(String key) {
    return a.slot(key);
  }

  public void render(String screen) {
    if (!screen.equals("s21")
        && !screen.equals("s16_loading")
        && (a.state.meal == null || a.state.alternatives.isEmpty())) {
      a.go(a.state.meal == null ? "s09" : "s13");
      return;
    }
    switch (screen) {
      case "s16":
        alternatives();
        break;
      case "s16_loading":
        loading();
        break;
      case "s17":
        detail();
        break;
      case "s18":
        future();
        break;
      case "s20":
        feedback();
        break;
      case "s21":
        done();
        break;
    }
  }

  private void filters(LinearLayout p) {
    u.chips(
        p,
        new String[] {
          a.state.profile.goal.equals("체중 감량") ? "체중 관리" : a.state.profile.goal, a.state.profile.environment, "조건 검증 후 추천"
        },
        Set.of(a.state.profile.goal.equals("체중 감량") ? "체중 관리" : a.state.profile.goal),
        false,
        v -> {});
  }

  private void alternatives() {
    Meal meal = a.state.meal;
    u.heading(s("heading"), meal.name + ", 이렇게 먹어볼까요?", "");
    filters(s("filters"));
    u.gap(s("alternatives"), 12);
    for (int position = 0; position < a.state.alternatives.size(); position++) {
      final int selectedPosition = position;
      Alternative alt = a.state.alternatives.get(position);
      boolean selected = position == a.state.selectedAlternative;
      LinearLayout c = u.card(s("alternatives"));
      c.setPadding(u.dp(8), u.dp(15), u.dp(8), u.dp(15));
      c.setBackgroundResource(selected ? R.drawable.bg_selected_card : R.drawable.bg_input);
      LinearLayout row = u.row(), photo = u.column();
      u.photo(photo, "사진", 58, "");
      row.addView(photo, new LinearLayout.LayoutParams(u.dp(54), -2));
      LinearLayout words = u.column();
      words.setPadding(u.dp(8), 0, u.dp(4), 0);
      LinearLayout heading = u.row();
      TextView index =
          u.text(String.format(Locale.KOREA, "%02d  ", selectedPosition + 1), 12, R.color.primary, true);
      heading.addView(index, new LinearLayout.LayoutParams(-2, -2));
      heading.addView(u.text(alt.title, 14, R.color.text, true));
      words.addView(heading);
      u.gap(words, 6);
      u.small(words, alt.instructions);
      u.gap(words, 6);
      words.addView(
          u.text(
              Ui.number(alt.nutrition.caloriesKcal)
                  + " kcal · "
                  + signed(alt.nutrition.caloriesKcal - meal.nutrition.caloriesKcal)
                  + " kcal · " + String.format(Locale.KOREA, "%.1f점", alt.scoreValue),
              12,
              R.color.primary_dark,
              true));
      row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
      TextView radio = u.text(selected ? "◉" : "○", 18, R.color.primary, true);
      radio.setGravity(Gravity.CENTER);
      row.addView(radio, new LinearLayout.LayoutParams(u.dp(22), u.dp(48)));
      c.addView(row);
      c.setContentDescription("추천 " + (selectedPosition + 1) + " " + alt.title);
      c.setOnClickListener(
          v -> {
            a.state.selectedAlternative = selectedPosition;
            a.state.months = 1;
            a.persist();
            a.refresh();
          });
      u.gap(s("alternatives"), 12);
    }
    LinearLayout row = u.row();
    TextView selection =
        u.text(
            "추천 " + (a.state.selectedAlternative + 1) + " · " + a.state.selected().title,
            11,
            R.color.muted,
            true);
    u.weight(row, selection);
    TextView detail = u.text("추천 근거 및 상세 ›", 11, R.color.primary, true);
    row.addView(detail, new LinearLayout.LayoutParams(-2, u.dp(36)));
    detail.setGravity(Gravity.CENTER);
    detail.setOnClickListener(v -> a.go("s17"));
    s("detail_link").addView(row);
    u.gap(s("reason"), 8);
    u.notice(s("reason"), "추천 이유", a.state.selected().reason, 0);
    u.gap(s("note"), 14);
    u.small(s("note"), "목표·취향·비용·실천 가능성을 반영한 순위예요.\n" + a.state.selected().source);
    a.primary("선택한 대안으로 미래 비교", () -> a.go("s18"));
  }

  private void loading() {
    LinearLayout p = s("loading");
    p.setGravity(Gravity.CENTER);
    u.gap(p, 224);
    p.addView(u.icon(R.drawable.ic_spark, 48, R.color.primary));
    u.gap(p, 18);
    TextView t = u.title(p, "내게 맞는 대안을 찾고 있어요");
    t.setGravity(Gravity.CENTER);
    u.gap(s("progress"), 36);
    u.bar(s("progress"), .72, R.color.primary, 6);
    u.gap(s("notice"), 12);
    u.notice(s("notice"), "추천 후보를 확인하는 중", "알레르기, 예산, 영양정보와 실천 가능성을 확인하고 있어요.", 0);
    u.link(s("example"), "추천 후보 부족 예시", () -> a.go("s29_shortage"));
    a.primary("음식 목록으로 돌아가기", () -> a.go("s14"));
  }

  public static String signed(double value) {
    return (value > 0 ? "+" : value < 0 ? "−" : "") + Ui.number(Math.abs(value));
  }

  private void detail() {
    Alternative alt = a.state.selected();
    Meal m = a.state.meal;
    u.heading(s("heading"), alt.title, alt.instructions);
    LinearLayout c = u.card(s("calories"));
    u.section(c, "원래 식단과 열량 비교");
    c.addView(
        u.text(
            m.name
                + " "
                + Ui.number(m.nutrition.caloriesKcal)
                + " kcal → "
                + Ui.number(alt.nutrition.caloriesKcal)
                + " kcal",
            12,
            R.color.primary_dark,
            false));
    u.gap(c, 8);
    u.bar(c, 1, R.color.text, 6);
    u.gap(c, 5);
    u.bar(c, alt.nutrition.caloriesKcal / m.nutrition.caloriesKcal, R.color.primary, 6);
    u.gap(c, 8);
    u.small(
        c, "열량 차이 " + signed(alt.nutrition.caloriesKcal - m.nutrition.caloriesKcal) + " kcal");
    u.gap(s("nutrients"), 12);
    nutrients(s("nutrients"), m.nutrition, alt.nutrition);
    u.gap(s("composition"), 12);
    u.notice(s("composition"), "구성과 양", alt.composition, 0);
    u.gap(s("conditions"), 12);
    u.notice(s("conditions"), "추천 조건 확인 결과",
        alt.verification + "\n한 끼 비용 " + alt.priceWon + "원 · 예산 "
            + a.state.profile.budgetWon + "원", 0);
    u.gap(s("scores"), 14);
    LinearLayout scores = u.card(s("scores"));
    u.section(scores, "추천 기준 · 종합 점수 " + String.format(Locale.KOREA, "%.2f", alt.scoreValue) + "점");
    String[] labels = {"영양 균형 N", "음식 선호도 P", "가격·접근성 C", "실천 가능성 F"};
    double[] values = alt.componentScores, weights = alt.scoreWeights;
    if (values == null || weights == null) {
      u.small(scores, "개발용 예시 · 세부 점수 검증 없음");
      a.primary("이 대안 선택", () -> a.go("s18"));
      return;
    }
    for (int i = 0; i < 4; i++) {
      LinearLayout r = u.row();
      u.weight(r, u.text(labels[i], 12, R.color.text, true));
      TextView score =
          u.text(String.format(Locale.KOREA, "%.2f · %.0f%%", values[i], weights[i] * 100), 12, R.color.primary_dark, true);
      r.addView(score, new LinearLayout.LayoutParams(-2, -2));
      scores.addView(r);
      u.gap(scores, 6);
      u.bar(scores, values[i] / 100.0, R.color.primary, 5);
      u.gap(scores, 14);
    }
    a.primary("이 대안 선택", () -> a.go("s18"));
  }

  private void nutrients(LinearLayout p, Nutrition original, Nutrition alt) {
    LinearLayout c = u.card(p), header = u.row();
    u.weight(header, u.text("영양소", 12, R.color.text, true));
    u.weight(header, u.text("원래 식단", 12, R.color.muted, true));
    u.weight(header, u.text("선택한 대안", 12, R.color.primary, true));
    c.addView(header);
    u.gap(c, 18);
    String[] names = {"탄수화물", "단백질", "지방"};
    double[] old = {original.carbohydrateGrams, original.proteinGrams, original.fatGrams},
        now = {alt.carbohydrateGrams, alt.proteinGrams, alt.fatGrams};
    for (int i = 0; i < 3; i++) {
      LinearLayout r = u.row();
      r.addView(
          u.text(names[i], 12, R.color.text, false), new LinearLayout.LayoutParams(u.dp(62), -2));
      LinearLayout oldGroup = u.row();
      LinearLayout oldBar = u.column();
      u.bar(
          oldBar,
          old[i] / Math.max(120, old[i]),
          new int[] {R.color.carbs, R.color.primary, R.color.fat}[i],
          5);
      oldGroup.addView(oldBar, new LinearLayout.LayoutParams(0, -2, 1));
      TextView oldValue = u.text(Ui.number(old[i]) + " g", 11, R.color.muted, false);
      oldValue.setGravity(Gravity.END);
      oldGroup.addView(oldValue, new LinearLayout.LayoutParams(u.dp(50), -2));
      u.weight(r, oldGroup);
      LinearLayout newGroup = u.row();
      newGroup.setPadding(u.dp(6), 0, 0, 0);
      LinearLayout newBar = u.column();
      u.bar(newBar, now[i] / Math.max(120, old[i]), R.color.line, 5);
      newGroup.addView(newBar, new LinearLayout.LayoutParams(0, -2, 1));
      TextView n = u.text(Ui.number(now[i]) + " g", 11, R.color.primary, false);
      n.setGravity(Gravity.END);
      newGroup.addView(n, new LinearLayout.LayoutParams(u.dp(50), -2));
      u.weight(r, newGroup);
      c.addView(r);
      u.gap(c, 16);
    }
    u.small(c, "섭취 중량을 기준으로 서버 DB에서 재계산했어요.\n" + a.state.meal.nutritionSource);
  }

  private void future() {
    u.heading(s("heading"), "같은 기간, 다른 식사 선택", "");
    LinearLayout tabs = u.row();
    tabs.setBackgroundResource(R.drawable.bg_chip);
    tabs.setPadding(u.dp(4), u.dp(4), u.dp(4), u.dp(4));
    for (int month : new int[] {1, 3, 6}) {
      TextView t =
          u.text(
              month + "개월",
              12,
              a.state.months == month ? R.color.white : R.color.primary_dark,
              true);
      t.setGravity(Gravity.CENTER);
      if (a.state.months == month) t.setBackgroundResource(R.drawable.bg_chip_selected);
      tabs.addView(t, new LinearLayout.LayoutParams(0, u.dp(36), 1));
      t.setOnClickListener(
          v -> {
            a.state.months = month;
            a.persist();
            a.refresh();
          });
    }
    s("periods").addView(tabs);
    u.gap(s("avatars"), 12);
    boolean ready = a.ensureComparisons();
    FutureComparison f = new FutureComparison();
    f.currentStage = AvatarStage.BASELINE;
    f.alternativeStage = AvatarStage.BASELINE;
    f.assumptions = "";
    if (ready) f = a.futureComparisons.get(a.state.months == 1 ? 0 : a.state.months == 3 ? 1 : 2);
    LinearLayout row = u.row();
    row.setGravity(Gravity.TOP);
    int base = a.currentCell(a.avatar());
    avatarCard(row, "A · 현재 식단 유지", a.state.meal.name, ready ? f.currentCell : base,
        f.currentCheek, ready ? f.currentChangeKg : null, ready ? f.currentCell - f.baseCell : 0,
        ready && f.currentClamped, false);
    View gap = new View(a);
    row.addView(gap, new LinearLayout.LayoutParams(u.dp(8), 1));
    avatarCard(row, "B · 선택 대안 실천", a.state.selected().title, ready ? f.alternativeCell : base,
        f.alternativeCheek, ready ? f.alternativeChangeKg : null,
        ready ? f.alternativeCell - f.baseCell : 0, ready && f.alternativeClamped, true);
    s("avatars").addView(row);
    u.gap(s("note"), 14);
    u.small(s("note"), "지금 체형 " + base + "칸에서 시작해요. 체중이 2%·5% 바뀔 때마다 한 칸씩 움직여요.");
    if (ready && f.alternativeBelowBmr) {
      u.gap(s("note"), 10);
      u.notice(s("note"), "극단적인 감량은 권장하지 않아요",
          "B의 하루 섭취 열량이 기초대사량보다 낮아요. 다른 대안도 함께 살펴보세요.", 1);
    }
    u.gap(s("graph"), 12);
    LinearLayout c = u.card(s("graph"));
    c.setPadding(u.dp(12), u.dp(12), u.dp(12), u.dp(12));
    c.addView(u.text("기간별 체중 변화", 14, R.color.text, true));
    u.gap(c, 8);
    u.small(c, "현재 체중 기준 · 가정에 따른 예상");
    u.gap(c, 8);
    GraphView graph = new GraphView(a, false);
    double[] current = new double[4], alternative = new double[4];
    int[] periods = {0, 1, 3, 6};
    for (int i = 0; i < 4; i++) {
      if (i == 0) {
        current[i] = alternative[i] = a.state.profile.weightKg;
      } else if (ready) {
        FutureComparison each = a.futureComparisons.get(i - 1);
        current[i] = each.currentWeightKg;
        alternative[i] = each.alternativeWeightKg;
      } else {
        current[i] = alternative[i] = a.state.profile.weightKg;
      }
    }
    graph.series(current, alternative);
    c.addView(graph, new LinearLayout.LayoutParams(-1, u.dp(80)));
    LinearLayout legend = u.row();
    legend.addView(
        u.text("● A 현재 식단   ", 10, R.color.text, false), new LinearLayout.LayoutParams(-2, -2));
    legend.addView(u.text("● B 선택 대안", 10, R.color.primary, false));
    c.addView(legend);
    u.gap(s("assumptions"), 14);
    TextView accordion =
        u.text(
            "가정에 따른 변화 시나리오                        " + (a.state.assumptionsExpanded ? "⌃" : "⌄"),
            12,
            R.color.primary_dark,
            true);
    accordion.setBackgroundResource(R.drawable.bg_mint);
    accordion.setPadding(u.dp(12), u.dp(14), u.dp(12), u.dp(14));
    s("assumptions").addView(accordion);
    accordion.setOnClickListener(
        v -> {
          a.state.assumptionsExpanded = !a.state.assumptionsExpanded;
          a.refresh();
        });
    if (a.state.assumptionsExpanded) {
      u.gap(s("assumptions"), 12);
      u.notice(s("assumptions"), "하루 한 끼를 대체하는 경우", f.assumptions, 0);
    }
    u.gap(s("disclaimer"), 16);
    u.small(s("disclaimer"), "※ 의료적 예측이 아닌 참고용 시나리오예요");
    a.primary(
        "이 대안으로 먹을게요",
        () -> {
          a.state.actualChoice = a.state.selectedAlternative;
          a.go("s20");
        });
    if (!ready) a.footer.getChildAt(0).setEnabled(false);
  }

  private void avatarCard(
      LinearLayout row,
      String title,
      String name,
      int cell,
      int cheek,
      Double changeKg,
      int moved,
      boolean clamped,
      boolean selected) {
    LinearLayout c = u.column();
    c.setPadding(u.dp(6), u.dp(8), u.dp(6), u.dp(8));
    c.setGravity(Gravity.CENTER);
    c.setBackgroundResource(selected ? R.drawable.bg_mint : R.drawable.bg_card);
    TextView badge = u.text(title, 11, R.color.white, true);
    badge.setGravity(Gravity.CENTER);
    badge.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_dark);
    if (!selected)
      badge.setBackgroundTintList(
          android.content.res.ColorStateList.valueOf(u.color(R.color.text)));
    c.addView(badge, new LinearLayout.LayoutParams(-1, u.dp(28)));
    u.gap(c, 10);
    // 아바타 옆에 예상 변화량 (끝 칸에서 그림이 멈춰도 kg은 그대로 보여준다)
    LinearLayout figureRow = u.row();
    figureRow.setGravity(Gravity.CENTER);
    u.figure(figureRow, a.avatar(), cell, cheek, a.heightScale(), 74, 180);
    TextView kg =
        u.text(
            changeKg == null ? "—" : String.format(java.util.Locale.KOREA, "%+.1f kg", changeKg),
            13,
            selected ? R.color.primary_dark : R.color.text,
            true);
    figureRow.addView(kg, new LinearLayout.LayoutParams(-2, -2));
    c.addView(figureRow);
    u.gap(c, 8);
    TextView food = u.text(name, 12, R.color.text, true);
    food.setGravity(Gravity.CENTER);
    c.addView(food);
    u.gap(c, 6);
    String move = moved == 0 ? "그대로" : (moved > 0 ? "+" : "") + moved + "칸";
    TextView change =
        u.text(
            changeKg == null ? "계산 중…" : "체형 " + cell + "칸 (" + move + ")"
                + (clamped ? "\n끝 칸이라 그림은 여기서 멈춰요" : ""),
            11,
            R.color.muted,
            false);
    change.setGravity(Gravity.CENTER);
    c.addView(change);
    row.addView(c, new LinearLayout.LayoutParams(0, -2, 1));
  }

  private void feedback() {
    u.heading(s("heading"), "실제로 어떻게 드셨나요?", "");
    LinearLayout planned = u.card(s("planned"));
    planned.setBackgroundResource(R.drawable.bg_mint);
    u.small(planned, "먹기 전 고른 대안");
    u.gap(planned, 6);
    planned.addView(
        u.text(
            "추천 " + (a.state.selectedAlternative + 1) + " · " + a.state.selected().title,
            14,
            R.color.text,
            true));
    u.gap(s("choices"), 18);
    u.section(s("choices"), "실제 식사");
    List<String> choices = new ArrayList<>();
    for (int i = 0; i < a.state.alternatives.size(); i++)
      choices.add("추천 " + (i + 1) + " · " + a.state.alternatives.get(i).title);
    choices.add("원래대로");
    choices.add("기타");
    for (int i = 0; i < choices.size(); i++) {
      // Preserve the saved IDs: alternative positions 0..2, original 3, other 4.
      final int index = i < a.state.alternatives.size() ? i : 3 + i - a.state.alternatives.size();
      TextView choice =
          u.text(
              (index == a.state.actualChoice ? "◉  " : "○  ") + choices.get(i), 12, R.color.text, true);
      choice.setGravity(Gravity.CENTER_VERTICAL);
      choice.setPadding(u.dp(12), 0, u.dp(12), 0);
      choice.setBackgroundResource(
          index == a.state.actualChoice ? R.drawable.bg_mint_strong : R.drawable.bg_card);
      s("choices").addView(choice, new LinearLayout.LayoutParams(-1, u.dp(36)));
      choice.setContentDescription(choices.get(i));
      choice.setOnClickListener(
          v -> {
            a.state.actualChoice = index;
            a.refresh();
          });
      u.gap(s("choices"), 10);
    }
    u.gap(s("rating"), 6);
    u.section(s("rating"), "얼마나 만족하셨나요?");
    LinearLayout ratings = u.row();
    String[] emoji = {"😞", "🙁", "😐", "🙂", "😊"};
    for (int i = 1; i <= 5; i++) {
      final int rating = i;
      TextView t = u.text(emoji[i - 1], 20, R.color.text, false);
      t.setGravity(Gravity.CENTER);
      t.setBackgroundResource(
          i == a.state.satisfaction ? R.drawable.bg_mint_strong : R.drawable.bg_card);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, u.dp(40), 1);
      if (i < 5) lp.setMarginEnd(u.dp(6));
      ratings.addView(t, lp);
      t.setContentDescription("만족도 " + i + "점");
      t.setOnClickListener(
          v -> {
            a.state.satisfaction = rating;
            a.refresh();
          });
    }
    s("rating").addView(ratings);
    u.gap(s("memo"), 16);
    u.input(s("memo"), "한 줄 메모", a.state.note, "밥을 조금 덜어도 충분했어요", v -> a.state.note = v);
    u.gap(s("note"), 16);
    u.small(s("note"), "식사 선택과 피드백은 다음 추천에 반영돼요.");
    a.primary("식사 기록 저장", a::saveFeedback);
  }

  private void done() {
    MealRecord r = a.state.record();
    if (r == null) {
      a.root("s08");
      return;
    }
    LinearLayout p = s("success");
    p.setGravity(Gravity.CENTER);
    u.gap(p, 84);
    p.addView(u.icon(R.drawable.ic_check, 32, R.color.primary));
    u.gap(p, 18);
    u.figure(p, a.avatar(), a.currentCell(a.avatar()), 0, a.heightScale(), 90, 220);
    u.gap(p, 24);
    TextView title = u.title(p, "오늘의 식사를 기록했어요");
    title.setGravity(Gravity.CENTER);
    TextView sub = u.muted(p, "축하 모션 자리");
    sub.setGravity(Gravity.CENTER);
    u.gap(s("summary"), 28);
    LinearLayout c = u.card(s("summary"));
    u.small(c, "오늘 " + r.mealType);
    u.gap(c, 10);
    u.section(c, r.meal.name);
    u.body(c, "실제 선택 · " + r.actualTitle);
    u.gap(c, 10);
    u.small(
        c,
        r.actualNutrition == null
            ? "기타 식사의 열량은 기록하지 않았어요."
            : "선택 대안 기준 예상 차이 "
                + signed(r.actualNutrition.caloriesKcal - r.meal.nutrition.caloriesKcal)
                + " kcal");
    u.gap(s("note"), 16);
    u.small(s("note"), "남겨준 피드백은 다음 식사 추천에 활용돼요.");
    a.primary("홈으로 가기", () -> a.root("s08"));
  }
}
