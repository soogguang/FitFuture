package com.fitfuture.app.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** UI-independent contracts. Quantities use g, kcal, kg, cm and KRW. */
public final class Models {
  private Models() {}

  public static class Profile implements Serializable {
    public String name = "희우",
        gender = "여성",
        goal = "체중 감량",
        activity = "가벼운 활동",
        environment = "학식";
    public int age = 21, budgetWon = 8000;
    public double heightCm = 163, weightKg = 58, targetWeightKg = 55;
    public List<String> preferences = new ArrayList<>(List.of("돈가스"));
    public List<String> avoided = new ArrayList<>(List.of("매운 음식"));
    public List<String> allergies = new ArrayList<>(List.of("갑각류"));
  }

  public static class FoodPortion implements Serializable {
    public String name;
    public double grams;

    public FoodPortion(String name, double grams) {
      this.name = name;
      this.grams = grams;
    }

    public FoodPortion copy() {
      return new FoodPortion(name, grams);
    }
  }

  public static class Nutrition implements Serializable {
    public double caloriesKcal, carbohydrateGrams, proteinGrams, fatGrams;

    public Nutrition(double c, double carbs, double p, double f) {
      caloriesKcal = c;
      carbohydrateGrams = carbs;
      proteinGrams = p;
      fatGrams = f;
    }

    public Nutrition plus(Nutrition other) {
      return new Nutrition(
          caloriesKcal + other.caloriesKcal,
          carbohydrateGrams + other.carbohydrateGrams,
          proteinGrams + other.proteinGrams,
          fatGrams + other.fatGrams);
    }

    public Nutrition times(double ratio) {
      return new Nutrition(
          caloriesKcal * ratio, carbohydrateGrams * ratio, proteinGrams * ratio, fatGrams * ratio);
    }
  }

  public static class Meal implements Serializable {
    public String id = java.util.UUID.randomUUID().toString(), name, photoUri = "";
    public List<FoodPortion> foods = new ArrayList<>();
    public Nutrition nutrition;
    public boolean confirmed;

    public Meal(String name) {
      this.name = name;
    }

    public Meal copy() {
      Meal m = new Meal(name);
      m.id = id;
      m.photoUri = photoUri;
      m.nutrition = nutrition;
      m.confirmed = confirmed;
      for (FoodPortion f : foods) m.foods.add(f.copy());
      return m;
    }
  }

  public static class Alternative implements Serializable {
    public int index;
    public String title, instructions, reason, composition;
    public List<FoodPortion> foods;
    public Nutrition nutrition;
    public int score;

    public Alternative(
        int index,
        String title,
        String instructions,
        List<FoodPortion> foods,
        Nutrition nutrition,
        String composition,
        int score) {
      this.index = index;
      this.title = title;
      this.instructions = instructions;
      this.foods = foods;
      this.nutrition = nutrition;
      this.composition = composition;
      this.score = score;
    }
  }

  public enum AvatarStage {
    MINUS_TWO(-2),
    MINUS_ONE(-1),
    BASELINE(0),
    PLUS_ONE(1),
    PLUS_TWO(2);
    public final int value;

    AvatarStage(int value) {
      this.value = value;
    }

    public static AvatarStage from(int value) {
      return values()[Math.max(-2, Math.min(2, value)) + 2];
    }
  }

  public static class FutureComparison {
    public int months;
    public double currentWeightKg, alternativeWeightKg;
    /** 실제로 이동한 체형 칸 수(−2~+2). 기존 화면 호환용. */
    public AvatarStage currentStage, alternativeStage;
    public String assumptions;

    // ── 아바타 렌더링용 (A = 현재 식단 유지, B = 선택 대안 실천) ──
    /** 출발점: 사용자가 확정한 현재 체형 칸(1~7). 확정 전이면 BMI 초안. */
    public int baseCell;
    /** 기간 뒤 체형 칸(1~7). */
    public int currentCell, alternativeCell;
    /** 볼 윤곽 −1·0·+1. 실제로 2칸 이동했을 때만 ±1. */
    public int currentCheek, alternativeCheek;
    /** 예상 체중 변화량(kg). 끝 칸에서 그림이 멈춰도 이 값은 그대로 표시한다. */
    public double currentChangeKg, alternativeChangeKg;
    /** 끝 칸이라 그림이 덜 움직였는지. */
    public boolean currentClamped, alternativeClamped;
    /** 비교에 쓴 하루 섭취 열량(kcal). */
    public double currentDailyKcal, alternativeDailyKcal;
    /** B 하루 섭취가 기초대사량보다 낮음 → 극단적 감량 경고 (체형 칸과 별개). */
    public boolean alternativeBelowBmr;
  }

  public static class MealRecord implements Serializable {
    public String id = java.util.UUID.randomUUID().toString();
    public long timestamp = System.currentTimeMillis();
    public Meal meal;
    public int plannedAlternative, actualChoice, satisfaction;
    public String actualTitle, note;
    public String mealType = "점심";
    public Nutrition actualNutrition;
  }

  public static class ModuleException extends Exception {
    public final String code;

    public ModuleException(String code, String message) {
      super(message);
      this.code = code;
    }
  }
}
