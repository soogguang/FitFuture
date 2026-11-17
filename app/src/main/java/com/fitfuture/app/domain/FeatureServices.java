package com.fitfuture.app.domain;

import com.fitfuture.app.domain.Models.*;
import java.util.List;

/**
 * Implementations are invoked on a worker thread; replace demo adapters at the composition root.
 */
public final class FeatureServices {
  public interface FoodAnalysisService {
    Meal recognize(String text, String localPhotoUri) throws ModuleException;

    Nutrition analyze(List<FoodPortion> confirmedFoods) throws ModuleException;
  }

  public interface MealRecommendationService {
    List<Alternative> recommend(Meal meal, Profile profile) throws ModuleException;

    default List<Alternative> recommend(Meal meal, Profile profile, List<MealRecord> history)
        throws ModuleException {
      return recommend(meal, profile);
    }
  }

  public interface FutureAvatarService {
    FutureComparison compare(Meal meal, Alternative alternative, Profile profile, int months)
        throws ModuleException;
  }

  public final FoodAnalysisService foodAnalysis;
  public final MealRecommendationService recommendations;
  public final FutureAvatarService futureAvatar;

  public FeatureServices(
      FoodAnalysisService f, MealRecommendationService r, FutureAvatarService a) {
    foodAnalysis = f;
    recommendations = r;
    futureAvatar = a;
  }
}
