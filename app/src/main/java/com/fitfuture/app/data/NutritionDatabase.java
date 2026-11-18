package com.fitfuture.app.data;

import com.fitfuture.app.domain.Models.*;
import java.io.*;
import java.util.*;

/** MFDS 2026-08-28 foods, complete energy/macros per 100 g only. */
public final class NutritionDatabase {
  private record Entry(String code, String name, String origin, Nutrition per100) {}
  private final Map<String, Entry> entries = new HashMap<>();
  private static final Map<String, String> ALIASES = Map.ofEntries(
      Map.entry("돼지국밥", "국밥_돼지고기"),
      Map.entry("돼지고기국밥", "국밥_돼지고기"),
      Map.entry("순대국밥", "국밥_순대국밥"),
      Map.entry("콩나물국밥", "국밥_콩나물"),
      Map.entry("소고기국밥", "국밥_소고기"),
      Map.entry("돈까스", "돈가스"),
      Map.entry("밥", "쌀밥"),
      Map.entry("공기밥", "쌀밥"),
      Map.entry("흰밥", "쌀밥"),
      Map.entry("양배추샐러드", "샐러드_양배추"),
      Map.entry("김치볶음밥", "볶음밥_김치"),
      Map.entry("돌솥비빔밥", "비빔밥_돌솥"));

  private static String key(String s) {
    return s.replaceAll("[\\s_]", "").toLowerCase(Locale.ROOT);
  }

  public NutritionDatabase(Reader input) throws IOException {
    try (BufferedReader reader = new BufferedReader(input)) {
      String line;
      while ((line = reader.readLine()) != null) {
        String[] v = line.split("\t", -1);
        if (v.length != 7) throw new IOException("Invalid nutrition database");
        Entry entry = new Entry(v[0], v[1], v[2], new Nutrition(
            Double.parseDouble(v[3]), Double.parseDouble(v[4]),
            Double.parseDouble(v[5]), Double.parseDouble(v[6])));
        // Export is sorted by measured eating-out, measured home, then calculated;
        // retain the first complete record rather than averaging different recipes.
        entries.putIfAbsent(key(entry.name), entry);
      }
    }
  }

  public Nutrition analyze(List<FoodPortion> foods) throws ModuleException {
    if (foods == null || foods.isEmpty() || foods.size() > 20)
      throw new ModuleException("NUTRITION", "음식을 1~20개 입력해 주세요.");
    Nutrition total = new Nutrition(0, 0, 0, 0);
    List<String> sources = new ArrayList<>();
    for (FoodPortion food : foods) {
      if (food.name == null || !Double.isFinite(food.grams) || food.grams <= 0 || food.grams > 5000)
        throw new ModuleException("NUTRITION", "섭취량을 0g 초과, 5,000g 이하로 입력해 주세요.");
      String name = key(food.name);
      Entry entry = entries.get(key(ALIASES.getOrDefault(name, name)));
      if (entry == null)
        throw new ModuleException("NUTRITION", food.name + "의 영양정보가 없어요. DB에 등록된 음식 이름으로 수정해 주세요.");
      total = total.plus(entry.per100.times(food.grams / 100.0));
      sources.add(food.name + " → " + entry.name.replace('_', ' ') + " [" + entry.code + "]");
    }
    total.source = "출처: 식품의약품안전처 음식 DB (2026-08-28) · 100g 기준 환산\n"
        + String.join("\n", sources) + "\n조리법에 따라 실제 영양값은 달라질 수 있어요.";
    return total;
  }
}
