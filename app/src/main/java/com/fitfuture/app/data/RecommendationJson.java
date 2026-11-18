package com.fitfuture.app.data;

import com.fitfuture.app.domain.Models.*;
import java.util.*;
import org.json.*;

/** Explicit wire contract. Server IDs are identities; index is only a local display position. */
public final class RecommendationJson {
  private RecommendationJson() {}

  public static JSONObject nutrition(Nutrition n) throws JSONException {
    return new JSONObject().put("calories_kcal", n.caloriesKcal)
        .put("carbohydrate_grams", n.carbohydrateGrams).put("protein_grams", n.proteinGrams)
        .put("fat_grams", n.fatGrams);
  }

  private static double number(JSONObject o, String key) throws JSONException {
    double value = o.getDouble(key);
    if (!Double.isFinite(value) || value < 0) throw new JSONException("Invalid " + key);
    return value;
  }

  public static Nutrition readNutrition(JSONObject o) throws JSONException {
    return new Nutrition(number(o, "calories_kcal"), number(o, "carbohydrate_grams"),
        number(o, "protein_grams"), number(o, "fat_grams"));
  }

  public static JSONArray foods(List<FoodPortion> foods) throws JSONException {
    JSONArray out = new JSONArray();
    if (foods != null) for (FoodPortion f : foods)
      out.put(new JSONObject().put("name", f.name).put("grams", f.grams));
    return out;
  }

  public static List<FoodPortion> readFoods(JSONArray array) throws JSONException {
    List<FoodPortion> result = new ArrayList<>();
    for (int i = 0; i < array.length(); i++) {
      JSONObject f = array.getJSONObject(i);
      double grams = number(f, "grams");
      if (grams <= 0) throw new JSONException("Nonpositive portion");
      result.add(new FoodPortion(f.getString("name"), grams));
    }
    if (result.isEmpty()) throw new JSONException("Empty foods");
    return result;
  }

  public static JSONObject request(Meal meal, Profile p, List<MealRecord> history) throws JSONException {
    JSONObject profile = new JSONObject().put("goal", p.goal).put("gender", p.gender)
        .put("age", p.age).put("height_cm", p.heightCm).put("weight_kg", p.weightKg)
        .put("target_weight_kg", p.targetWeightKg).put("activity", p.activity)
        .put("preferences", new JSONArray(p.preferences)).put("avoided", new JSONArray(p.avoided))
        .put("allergies", new JSONArray(p.allergies)).put("budget_won", p.budgetWon)
        .put("environment", p.environment);
    JSONArray feedback = new JSONArray();
    for (int i = Math.max(0, history.size() - 100); i < history.size(); i++) {
      MealRecord r = history.get(i);
      // Legacy records without actual food composition never become invented history.
      if (r.actualFoods == null || r.actualFoods.isEmpty() || r.satisfaction < 1 || r.satisfaction > 5) continue;
      feedback.put(new JSONObject()
          .put("planned_id", r.plannedSnapshot == null ? "" : r.plannedSnapshot.id)
          .put("actual_id", r.actualSnapshot == null ? "" : r.actualSnapshot.id)
          .put("actual_foods", foods(r.actualFoods)).put("satisfaction", r.satisfaction)
          // Notes stay local. They remain linked in MealRecord but are not sent to Gemini.
          .put("note", ""));
    }
    return new JSONObject().put("meal", new JSONObject().put("name", meal.name)
        .put("confirmed", meal.confirmed).put("foods", foods(meal.foods))
        .put("nutrition", nutrition(meal.nutrition))).put("profile", profile).put("feedback", feedback);
  }

  public static Alternative readAlternative(JSONObject o, int position) throws JSONException {
    double value = number(o, "score");
    if (value > 100) throw new JSONException("Invalid score");
    Alternative alt = new Alternative(position, o.getString("title"), o.getString("instructions"),
        readFoods(o.getJSONArray("foods")), readNutrition(o.getJSONObject("nutrition")),
        o.getString("composition"), (int) Math.round(value));
    alt.id = o.getString("id");
    if (alt.id.isEmpty()) throw new JSONException("Missing identity");
    alt.scoreValue = value;
    alt.reason = o.getString("reason");
    alt.source = o.getString("source");
    alt.verification = o.getString("verification");
    alt.priceWon = o.getInt("price_won");
    alt.calorieDifference = o.getDouble("calorie_difference");
    if (!Double.isFinite(alt.calorieDifference) || alt.priceWon <= 0) throw new JSONException("Invalid metadata");
    alt.componentScores = new double[4];
    alt.scoreWeights = new double[4];
    String[] keys = {"N", "P", "C", "F"};
    double weight = 0, total = 0;
    for (int i = 0; i < 4; i++) {
      alt.componentScores[i] = number(o.getJSONObject("scores"), keys[i]);
      alt.scoreWeights[i] = number(o.getJSONObject("weights"), keys[i]);
      if (alt.componentScores[i] > 100 || alt.scoreWeights[i] > 1) throw new JSONException("Invalid subscore");
      weight += alt.scoreWeights[i];
      total += alt.componentScores[i] * alt.scoreWeights[i];
    }
    if (Math.abs(weight - 1) > .001 || Math.abs(total - value) > .02) throw new JSONException("Inconsistent score");
    return alt;
  }

  public static JSONObject alternative(Alternative alt) throws JSONException {
    JSONObject scores = new JSONObject(), weights = new JSONObject();
    String[] keys = {"N", "P", "C", "F"};
    if (alt.componentScores == null || alt.scoreWeights == null) return null; // Legacy demo state.
    for (int i = 0; i < 4; i++) {
      scores.put(keys[i], alt.componentScores[i]);
      weights.put(keys[i], alt.scoreWeights[i]);
    }
    return new JSONObject().put("id", alt.id).put("title", alt.title).put("instructions", alt.instructions)
        .put("foods", foods(alt.foods)).put("nutrition", nutrition(alt.nutrition))
        .put("composition", alt.composition).put("score", alt.scoreValue).put("scores", scores)
        .put("weights", weights).put("reason", alt.reason).put("source", alt.source)
        .put("verification", alt.verification).put("price_won", alt.priceWon)
        .put("calorie_difference", alt.calorieDifference);
  }

  public static List<Alternative> response(JSONObject root, Meal meal) throws JSONException, ModuleException {
    JSONArray array = root.getJSONArray("alternatives");
    if (array.length() == 0) throw new ModuleException("SHORTAGE", "조건에 맞는 대안을 찾지 못했어요.");
    if (array.length() > 3) throw new JSONException("Expected at most three alternatives");
    List<Alternative> result = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    Nutrition original = readNutrition(root.getJSONObject("original_nutrition"));
    if (original.caloriesKcal <= 0) throw new JSONException("Invalid original nutrition");
    for (int i = 0; i < array.length(); i++) {
      Alternative alt = readAlternative(array.getJSONObject(i), i);
      if (!ids.add(alt.id)) throw new JSONException("Duplicate identity");
      if (Math.abs(alt.nutrition.caloriesKcal - original.caloriesKcal - alt.calorieDifference) > .1)
        throw new JSONException("Inconsistent calorie difference");
      result.add(alt);
    }
    meal.nutrition = original;
    meal.nutritionSource = root.getString("nutrition_source");
    return result;
  }
}
