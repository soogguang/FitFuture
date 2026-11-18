package com.fitfuture.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import com.fitfuture.app.domain.Models.*;
import java.util.*;
import org.json.*;

/** Versioned local JSON storage; no API key or remote persistence. */
public final class StateStore {
  private final SharedPreferences prefs;

  public StateStore(Context context) {
    prefs = context.getSharedPreferences("fitfuture.v1", Context.MODE_PRIVATE);
  }

  private JSONArray strings(List<String> list) {
    return new JSONArray(list);
  }

  private List<String> stringList(JSONArray a) {
    List<String> list = new ArrayList<>();
    if (a != null) for (int i = 0; i < a.length(); i++) list.add(a.optString(i));
    return list;
  }

  private JSONObject nutrition(Nutrition n) throws JSONException {
    if (n == null) return new JSONObject();
    return new JSONObject()
        .put("kcal", n.caloriesKcal)
        .put("carbs", n.carbohydrateGrams)
        .put("protein", n.proteinGrams)
        .put("fat", n.fatGrams);
  }

  private Nutrition readNutrition(JSONObject o) {
    return o == null || !o.has("kcal")
        ? null
        : new Nutrition(
            o.optDouble("kcal"), o.optDouble("carbs"), o.optDouble("protein"), o.optDouble("fat"));
  }

  private JSONObject meal(Meal m) throws JSONException {
    if (m == null) return new JSONObject();
    JSONArray foods = new JSONArray();
    for (FoodPortion f : m.foods)
      foods.put(new JSONObject().put("name", f.name).put("grams", f.grams));
    return new JSONObject()
        .put("id", m.id)
        .put("name", m.name)
        .put("photo", m.photoUri)
        .put("confirmed", m.confirmed)
        .put("nutritionSource", m.nutritionSource)
        .put("foods", foods)
        .put("nutrition", nutrition(m.nutrition));
  }

  private Meal readMeal(JSONObject o) {
    if (o == null || !o.has("name")) return null;
    Meal m = new Meal(o.optString("name"));
    m.id = o.optString("id", m.id);
    m.photoUri = o.optString("photo");
    m.confirmed = o.optBoolean("confirmed");
    m.nutritionSource = o.optString("nutritionSource");
    m.nutrition = readNutrition(o.optJSONObject("nutrition"));
    JSONArray a = o.optJSONArray("foods");
    if (a != null)
      for (int i = 0; i < a.length(); i++) {
        JSONObject f = a.optJSONObject(i);
        if (f != null) m.foods.add(new FoodPortion(f.optString("name"), f.optDouble("grams", 100)));
      }
    return m;
  }

  public void save(AppState s) {
    try {
      Profile p = s.profile;
      JSONObject profile =
          new JSONObject()
              .put("name", p.name)
              .put("gender", p.gender)
              .put("goal", p.goal)
              .put("activity", p.activity)
              .put("environment", p.environment)
              .put("age", p.age)
              .put("budget", p.budgetWon)
              .put("height", p.heightCm)
              .put("weight", p.weightKg)
              .put("target", p.targetWeightKg)
              .put("preferences", strings(p.preferences))
              .put("avoided", strings(p.avoided))
              .put("allergies", strings(p.allergies));
      JSONArray records = new JSONArray();
      for (MealRecord r : s.records)
        records.put(
            new JSONObject()
                .put("id", r.id)
                .put("timestamp", r.timestamp)
                .put("meal", meal(r.meal))
                .put("planned", r.plannedAlternative)
                .put("actual", r.actualChoice)
                .put("rating", r.satisfaction)
                .put("title", r.actualTitle)
                .put("mealType", r.mealType)
                .put("note", r.note)
                .put("plannedSnapshot", r.plannedSnapshot == null ? null : RecommendationJson.alternative(r.plannedSnapshot))
                .put("actualSnapshot", r.actualSnapshot == null ? null : RecommendationJson.alternative(r.actualSnapshot))
                .put("actualFoods", RecommendationJson.foods(r.actualFoods))
                .put("nutrition", nutrition(r.actualNutrition)));
      JSONArray alternatives = new JSONArray();
      for (Alternative alt : s.alternatives) {
        JSONObject encoded = RecommendationJson.alternative(alt);
        if (encoded != null) alternatives.put(encoded);
      }
      JSONObject root =
          new JSONObject()
              .put("version", 2)
              .put("profile", profile)
              .put("onboarded", s.onboarded)
              .put("records", records)
              .put("alternatives", alternatives)
              .put("meal", meal(s.meal))
              .put("inputText", s.inputText)
              .put("draftMealType", s.draftMealType)
              .put("photo", s.photoUri)
              .put("selected", s.selectedAlternative)
              .put("actual", s.actualChoice)
              .put("months", s.months)
              .put("rating", s.satisfaction)
              .put("note", s.note)
              .put("weights", new JSONArray(s.weights))
              .put("weightDates", strings(s.weightDates));
      prefs.edit().putString("state", root.toString()).apply();
    } catch (JSONException ex) {
      throw new IllegalStateException("Local data could not be encoded", ex);
    }
  }

  public AppState load() {
    AppState s = new AppState();
    String raw = prefs.getString("state", "");
    if (raw.isEmpty()) return s;
    try {
      JSONObject o = new JSONObject(raw), p = o.getJSONObject("profile");
      Profile v = s.profile;
      v.name = p.optString("name", "희우");
      v.gender = p.optString("gender", "여성");
      v.goal = p.optString("goal", "체중 감량");
      v.activity = p.optString("activity", "가벼운 활동");
      v.environment = p.optString("environment", "학식");
      v.age = p.optInt("age", 21);
      v.budgetWon = p.optInt("budget", 8000);
      v.heightCm = p.optDouble("height", 163);
      v.weightKg = p.optDouble("weight", 58);
      v.targetWeightKg = p.optDouble("target", 55);
      v.preferences = stringList(p.optJSONArray("preferences"));
      v.avoided = stringList(p.optJSONArray("avoided"));
      v.allergies = stringList(p.optJSONArray("allergies"));
      s.onboarded = o.optBoolean("onboarded");
      s.meal = readMeal(o.optJSONObject("meal"));
      JSONArray alternatives = o.optJSONArray("alternatives");
      if (alternatives != null && alternatives.length() >= 1 && alternatives.length() <= 3)
        for (int i = 0; i < alternatives.length(); i++)
          s.alternatives.add(RecommendationJson.readAlternative(alternatives.getJSONObject(i), i));
      s.inputText = o.optString("inputText", "돈가스 정식");
      s.draftMealType = o.optString("draftMealType", "점심");
      s.photoUri = o.optString("photo");
      s.selectedAlternative = Math.max(0, Math.min(s.alternatives.size() - 1, o.optInt("selected")));
      s.actualChoice = Math.max(0, Math.min(4, o.optInt("actual")));
      if (s.actualChoice < 3 && s.actualChoice >= s.alternatives.size())
        s.actualChoice = s.selectedAlternative;
      s.months = o.optInt("months", 1);
      s.satisfaction = o.optInt("rating", 4);
      s.note = o.optString("note");
      JSONArray rs = o.optJSONArray("records");
      if (rs != null)
        for (int i = 0; i < rs.length(); i++) {
          JSONObject r = rs.getJSONObject(i);
          MealRecord record = new MealRecord();
          record.id = r.optString("id", record.id);
          record.timestamp = r.optLong("timestamp", record.timestamp);
          record.meal = readMeal(r.optJSONObject("meal"));
          record.plannedAlternative = r.optInt("planned");
          record.actualChoice = r.optInt("actual");
          record.satisfaction = r.optInt("rating", 4);
          record.actualTitle = r.optString("title");
          record.mealType = r.optString("mealType", "점심");
          record.note = r.optString("note");
          if (r.optJSONObject("plannedSnapshot") != null)
            record.plannedSnapshot = RecommendationJson.readAlternative(r.getJSONObject("plannedSnapshot"), record.plannedAlternative);
          if (r.optJSONObject("actualSnapshot") != null)
            record.actualSnapshot = RecommendationJson.readAlternative(r.getJSONObject("actualSnapshot"), record.actualChoice);
          JSONArray actualFoods = r.optJSONArray("actualFoods");
          if (actualFoods != null && actualFoods.length() > 0) record.actualFoods = RecommendationJson.readFoods(actualFoods);
          record.actualNutrition = readNutrition(r.optJSONObject("nutrition"));
          if (record.meal != null) s.records.add(record);
        }
      JSONArray w = o.optJSONArray("weights");
      if (w != null) for (int i = 0; i < w.length(); i++) s.weights.add(w.optDouble(i));
      s.weightDates = stringList(o.optJSONArray("weightDates"));
    } catch (JSONException ignored) {
      /* Invalid legacy state falls back to a clean draft without deleting the stored data. */
    }
    return s;
  }
}
