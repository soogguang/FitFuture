package com.fitfuture.app.data;

import com.fitfuture.app.domain.FeatureServices;
import com.fitfuture.app.domain.Models.*;
import java.util.*;

/** Deterministic fixtures, never a real recognition model or MFDS nutrition database. */
public final class DemoServices
    implements FeatureServices.FoodAnalysisService,
        FeatureServices.MealRecommendationService,
        FeatureServices.FutureAvatarService {
  public FeatureServices services() {
    return new FeatureServices(this, this, this);
  }

  public Meal recognize(String text, String photoUri) throws ModuleException {
    String name = text == null ? "" : text.trim();
    if (name.isEmpty() && (photoUri == null || photoUri.isEmpty()))
      throw new ModuleException("RECOGNITION", "음식 이름이나 사진을 입력해 주세요.");
    if (name.isEmpty())
      name = "돈가스 정식"; // Photo recognition fixture, irrespective of photo contents.
    Meal m = new Meal(name);
    m.photoUri = photoUri == null ? "" : photoUri;
    if (name.contains("돈가스") || name.contains("돈까스"))
      m.foods.addAll(
          List.of(
              new FoodPortion("돈가스", 150),
              new FoodPortion("밥", 200),
              new FoodPortion("양배추 샐러드", 50)));
    else if (name.contains("비빔밥")) m.foods.add(new FoodPortion("비빔밥", 400));
    else if (name.contains("샐러드")) m.foods.add(new FoodPortion("샐러드", 200));
    else m.foods.add(new FoodPortion(name, 100));
    return m;
  }

  private Nutrition reference(String name) throws ModuleException {
    switch (name.trim()) {
      case "돈가스":
      case "돈까스":
        return new Nutrition(450, 35, 27, 27).times(1.0 / 150);
      case "밥":
        return new Nutrition(300, 67, 4, 1).times(1.0 / 200);
      case "양배추 샐러드":
        return new Nutrition(100, 8, 1, 3).times(1.0 / 50);
      case "돈가스 소스":
      case "소스":
        return new Nutrition(30, 7, 0, 0).times(1.0 / 20);
      case "비빔밥":
        return new Nutrition(620, 95, 21, 17).times(1.0 / 400);
      case "샐러드":
        return new Nutrition(250, 18, 15, 14).times(1.0 / 200);
      case "요거트와 토스트":
        return new Nutrition(430, 58, 19, 14).times(1.0 / 200);
      default:
        throw new ModuleException("NUTRITION", "영양정보를 찾지 못했어요");
    }
  }

  public Nutrition analyze(List<FoodPortion> foods) throws ModuleException {
    if (foods.isEmpty()) throw new ModuleException("NUTRITION", "음식을 한 개 이상 입력해 주세요.");
    Nutrition total = new Nutrition(0, 0, 0, 0);
    for (FoodPortion f : foods) {
      if (f.grams <= 0 || !Double.isFinite(f.grams))
        throw new ModuleException("NUTRITION", "섭취량을 확인해 주세요.");
      total = total.plus(reference(f.name).times(f.grams));
    }
    return total;
  }

  public List<Alternative> recommend(Meal meal, Profile profile) throws ModuleException {
    if (!meal.confirmed || meal.nutrition == null)
      throw new ModuleException("NUTRITION", "음식과 양을 먼저 확정해 주세요.");
    if (profile.budgetWon < 5000) throw new ModuleException("SHORTAGE", "조건에 맞는 대안이 충분하지 않아요");
    List<Alternative> list = new ArrayList<>();
    String[] titles = {"밥 양 조절", "소스 양 조절", "전체 분량 조절"};
    String[] instructions = {"밥 일부를 미리 덜어두기", "소스를 조금씩 찍어 먹기", "밥과 돈가스 일부를 덜어두기"};
    boolean standard =
        meal.foods.size() == 3
            && meal.foods.get(0).name.equals("돈가스")
            && meal.foods.get(0).grams == 150
            && meal.foods.get(1).name.equals("밥")
            && meal.foods.get(1).grams == 200
            && meal.foods.get(2).name.equals("양배추 샐러드")
            && meal.foods.get(2).grams == 50;
    for (int i = 0; i < 3; i++) {
      List<FoodPortion> foods = new ArrayList<>();
      for (FoodPortion f : meal.foods) foods.add(f.copy());
      if (i == 0) {
        boolean rice = false;
        for (FoodPortion f : foods)
          if (f.name.equals("밥")) {
            f.grams *= 2.0 / 3;
            rice = true;
          }
        if (!rice) {
          titles[i] = "주식 분량 조절";
          instructions[i] = "주식 일부를 미리 덜어두기";
          foods.get(0).grams *= 0.85;
        }
      } else if (i == 2) for (FoodPortion f : foods) if (!f.name.contains("샐러드")) f.grams *= 0.75;
      Nutrition n = i == 1 ? meal.nutrition.times(0.9294117647) : analyze(foods);
      if (standard)
        n =
            new Nutrition(
                new int[] {750, 790, 680}[i],
                new double[] {87.7, 102, 84.5}[i],
                new double[] {30.7, 32, 24.3}[i],
                new double[] {30.7, 27.8, 24}[i]);
      StringBuilder composition = new StringBuilder();
      for (FoodPortion f : foods) {
        if (composition.length() > 0) composition.append(" · ");
        composition.append(f.name).append(" ").append(Math.round(f.grams)).append(" g");
      }
      Alternative alt =
          new Alternative(
              i,
              titles[i],
              instructions[i],
              foods,
              n,
              composition.toString(),
              new int[] {91, 86, 83}[i]);
      alt.reason =
          meal.name
              + "을 포기할 필요는 없어요! 밥 양이나 소스만 조절해도 실천 가능한 대안이 돼요. 지금 목표에는 추천 "
              + (i + 1)
              + "을 확인해 보세요.";
      list.add(alt);
    }
    return list;
  }

  public FutureComparison compare(Meal meal, Alternative alt, Profile profile, int months)
      throws ModuleException {
    if (months != 1 && months != 3 && months != 6)
      throw new ModuleException("PERIOD", "1·3·6개월 중 선택해 주세요.");
    FutureComparison f = new FutureComparison();
    f.months = months;
    // Demo scenario only; deliberately separate from the future team's BMR/TDEE model.
    double difference = Math.max(0, meal.nutrition.caloriesKcal - alt.nutrition.caloriesKcal);
    f.currentWeightKg = profile.weightKg + months * 0.2;
    f.alternativeWeightKg = profile.weightKg - difference * 30 * months / 7700.0;
    f.currentStage = AvatarStage.from(months == 1 ? 0 : months == 3 ? 1 : 2);
    f.alternativeStage = AvatarStage.from(months == 1 ? 0 : months == 3 ? -1 : -2);
    f.assumptions =
        "매일 한 끼를 같은 대안으로 바꾼다고 가정해요. 하루 섭취 열량과 BMR 기반 TDEE의 차이를 같은 기간에 비교해요. 현재 값은 화면 연결용 예시이며 실제 예측"
            + " 모델이 아니에요.";
    return f;
  }
}
