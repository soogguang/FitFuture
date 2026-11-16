package com.fitfuture.app.data;

import com.fitfuture.app.domain.Models.*;
import java.io.Serializable;
import java.util.*;

/** One shared flow state; immutable record snapshots prevent later edits from changing history. */
public final class AppState implements Serializable {
  public Profile profile = new Profile();
  public boolean onboarded;
  public String draftMealType = "점심";
  public Meal meal;
  public List<Alternative> alternatives = new ArrayList<>();
  public int selectedAlternative, actualChoice, months = 1, satisfaction = 4, introPage;
  public String note = "",
      inputText = "돈가스 정식",
      photoUri = "",
      selectedRecordId = "",
      selectedDate = "";
  public boolean assumptionsExpanded, editingProfile;
  public List<MealRecord> records = new ArrayList<>();
  public List<String> weightDates = new ArrayList<>();
  public List<Double> weights = new ArrayList<>();

  public Alternative selected() {
    return alternatives.isEmpty()
        ? null
        : alternatives.get(Math.min(selectedAlternative, alternatives.size() - 1));
  }

  public MealRecord record() {
    for (MealRecord r : records) if (r.id.equals(selectedRecordId)) return r;
    return records.isEmpty() ? null : records.get(records.size() - 1);
  }

  public void invalidateAnalysis() {
    alternatives.clear();
    selectedAlternative = 0;
    months = 1;
    assumptionsExpanded = false;
    if (meal != null) {
      meal.confirmed = false;
      meal.nutrition = null;
    }
  }
}
