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
    public String source = "";

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
    public AvatarStage currentStage, alternativeStage;
    public String assumptions;
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
