package com.fitfuture.app.data;

import com.fitfuture.app.domain.FeatureServices;
import com.fitfuture.app.domain.Models.*;
import java.util.*;
import org.json.JSONException;

public final class HttpMealRecommendationService implements FeatureServices.MealRecommendationService {
  private final RecommendationHttpClient client;

  public HttpMealRecommendationService(RecommendationHttpClient client) {
    this.client = client;
  }

  @Override
  public List<Alternative> recommend(Meal meal, Profile profile) throws ModuleException {
    return recommend(meal, profile, Collections.emptyList());
  }

  @Override
  public List<Alternative> recommend(Meal meal, Profile profile, List<MealRecord> history) throws ModuleException {
    if (!meal.confirmed || meal.nutrition == null)
      throw new ModuleException("NUTRITION", "음식과 양을 먼저 확정해 주세요.");
    try {
      return RecommendationJson.response(client.post("/recommend", RecommendationJson.request(meal, profile, history)), meal);
    } catch (JSONException ex) {
      throw new ModuleException("SERVER", "추천 응답을 해석하지 못했어요.");
    }
  }
}
