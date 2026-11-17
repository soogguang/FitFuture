package com.fitfuture.app.data;

import com.fitfuture.app.domain.FeatureServices;
import com.fitfuture.app.domain.Models.*;
import java.util.List;
import org.json.*;

/** Recognition is explicitly still a demo; confirmed quantities use the server DB. */
public final class ServerFoodAnalysisService implements FeatureServices.FoodAnalysisService {
  private final FeatureServices.FoodAnalysisService recognition;
  private final RecommendationHttpClient client;

  public ServerFoodAnalysisService(FeatureServices.FoodAnalysisService recognition, RecommendationHttpClient client) {
    this.recognition = recognition;
    this.client = client;
  }

  public Meal recognize(String text, String photo) throws ModuleException {
    return recognition.recognize(text, photo);
  }

  public Nutrition analyze(List<FoodPortion> foods) throws ModuleException {
    try {
      JSONObject response = client.post("/nutrition",
          new JSONObject().put("foods", RecommendationJson.foods(foods)));
      Nutrition n = RecommendationJson.readNutrition(response.getJSONObject("nutrition"));
      n.source = response.getString("source");
      return n;
    } catch (JSONException ex) {
      throw new ModuleException("SERVER", "영양 응답을 해석하지 못했어요.");
    }
  }
}
