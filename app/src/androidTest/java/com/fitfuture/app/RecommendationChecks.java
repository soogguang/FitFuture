package com.fitfuture.app;

import android.app.Instrumentation;
import com.fitfuture.app.data.*;
import com.fitfuture.app.domain.FeatureServices;
import com.fitfuture.app.domain.Models.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Uses the Python-generated fixture wire response, real HTTP, the real UI, and real avatar module. */
final class RecommendationChecks {
  static void require(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }

  static void waitFor(Instrumentation test, java.util.function.BooleanSupplier condition) throws Exception {
    long end = System.currentTimeMillis() + 12000;
    while (!condition.getAsBoolean() && System.currentTimeMillis() < end) {
      Thread.sleep(40);
      test.waitForIdleSync();
    }
    require(condition.getAsBoolean(), "Recommendation UI timeout");
  }

  static void run(Instrumentation test, MainActivity a, String serverUrl) throws Exception {
    String json;
    try (InputStream input = test.getContext().getAssets().open("response.fixture.json")) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      int n;
      while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n);
      json = out.toString("UTF-8");
    }
    DemoServices demo = new DemoServices();
    Meal meal = demo.recognize("돈가스 정식", "");
    meal.confirmed = true;
    meal.nutrition = demo.analyze(meal.foods);
    JSONObject response = new JSONObject(json);
    // Remote indices do not determine local selection, and titles are not identities.
    response.getJSONArray("alternatives").getJSONObject(0).put("index", 42);
    List<Alternative> parsed = RecommendationJson.response(response, meal);
    require(parsed.size() == 3 && parsed.get(0).index == 0, "JSON parsing/index normalization");
    require(parsed.get(0).componentScores.length == 4, "Real score details missing");
    for (int months : new int[] {1, 3, 6}) {
      double previous = Double.NaN;
      for (Alternative alt : parsed) {
        FutureComparison comparison = a.avatarFuture.compare(meal, alt, new Profile(), months);
        require(Math.abs(comparison.currentDailyKcal - comparison.alternativeDailyKcal
            - (meal.nutrition.caloriesKcal - alt.nutrition.caloriesKcal)) < .01, "Actual kcal not forwarded");
        if (Double.isFinite(previous)) require(previous != comparison.alternativeWeightKg, "Identical avatar scenario");
        previous = comparison.alternativeWeightKg;
      }
    }
    try (FakeServer server = new FakeServer(json, 200)) {
      HttpMealRecommendationService service = new HttpMealRecommendationService(new RecommendationHttpClient(server.url()));
      List<Alternative> alternatives = service.recommend(meal, new Profile());
      require(alternatives.get(0).id.equals(parsed.get(0).id), "HTTP result mismatch");
    }
    // Partial results must survive the real HTTP adapter, UI selection, storage and feedback.
    for (int count : new int[] {1, 2, 3}) {
      JSONObject partial = new JSONObject(json);
      JSONArray rows = new JSONArray();
      for (int i = 0; i < count; i++) rows.put(partial.getJSONArray("alternatives").getJSONObject(i));
      partial.put("alternatives", rows);
      try (FakeServer server = new FakeServer(partial.toString(), 200)) {
        test.runOnMainSync(() -> {
          a.state = new AppState();
          a.state.meal = meal;
          a.state.selectedAlternative = 2;
          a.services = new FeatureServices(demo,
              new HttpMealRecommendationService(new RecommendationHttpClient(server.url())), a.avatarFuture);
          a.loadRecommendations();
        });
        waitFor(test, () -> a.screen.equals("s16"));
      }
      require(a.state.alternatives.size() == count && a.state.selectedAlternative == count - 1,
          "Partial results rejected or stale selection retained");
      AppState restored = new StateStore(test.getTargetContext()).load();
      require(restored.alternatives.size() == count && restored.selectedAlternative == count - 1,
          "Partial recommendations lost from storage");
      test.runOnMainSync(() -> a.root("s18"));
      waitFor(test, () -> a.futureComparisons.size() == 3);
      test.runOnMainSync(() -> {
        a.root("s20");
        require(control(a.getWindow().getDecorView(), "원래대로").performClick(), "Original choice missing");
        require(a.state.actualChoice == 3, "Partial results changed original choice ID");
        a.saveFeedback();
      });
      restored = new StateStore(test.getTargetContext()).load();
      require(restored.records.get(0).actualChoice == 3
          && restored.records.get(0).actualNutrition.caloriesKcal == meal.nutrition.caloriesKcal
          && restored.records.get(0).actualFoods.size() == meal.foods.size(), "Original feedback corrupted");
      test.runOnMainSync(() -> {
        a.root("s20");
        require(control(a.getWindow().getDecorView(), "기타").performClick(), "Other choice missing");
        require(a.state.actualChoice == 4, "Partial results changed other choice ID");
        a.saveFeedback();
      });
      restored = new StateStore(test.getTargetContext()).load();
      require(restored.records.get(0).actualChoice == 4
          && restored.records.get(0).actualNutrition == null, "Other feedback corrupted");
    }
    JSONObject empty = new JSONObject(json).put("alternatives", new JSONArray());
    try (FakeServer server = new FakeServer(empty.toString(), 200)) {
      test.runOnMainSync(() -> {
        a.state = new AppState();
        a.state.meal = meal;
        a.state.alternatives = new ArrayList<>(parsed);
        a.services = new FeatureServices(demo,
            new HttpMealRecommendationService(new RecommendationHttpClient(server.url())), a.avatarFuture);
        a.loadRecommendations();
      });
      waitFor(test, () -> a.screen.equals("s29_shortage"));
      require(a.state.alternatives.isEmpty(), "Empty response kept stale recommendations");
    }
    for (String code : new String[] {"SHORTAGE", "UPSTREAM", "VALIDATION"}) {
      try (FakeServer server = new FakeServer("{\"error\":{\"code\":\"" + code + "\",\"message\":\"test\"}}", 422)) {
        test.runOnMainSync(() -> {
          a.state = new AppState();
          a.state.meal = meal;
          a.services = new FeatureServices(demo,
              new HttpMealRecommendationService(new RecommendationHttpClient(server.url())), a.avatarFuture);
          a.loadRecommendations();
        });
        waitFor(test, () -> a.screen.equals(code.equals("SHORTAGE") ? "s29_shortage" : "s28"));
      }
    }
    test.runOnMainSync(() -> {
      a.state = new AppState();
      a.state.meal = meal;
      a.state.alternatives = parsed;
      parsed.get(0).index = 99; // UI must still select position 0 safely.
      a.state.selectedAlternative = 0;
      a.root("s17");
      a.root("s18");
    });
    waitFor(test, () -> a.futureComparisons.size() == 3);
    double first = a.futureComparisons.get(0).alternativeDailyKcal;
    test.runOnMainSync(() -> {
      a.state.selectedAlternative = 1;
      a.refresh();
    });
    waitFor(test, () -> a.futureComparisons.size() == 3);
    require(first != a.futureComparisons.get(0).alternativeDailyKcal, "Cached alternative calories stale");
    test.runOnMainSync(() -> {
      a.state.actualChoice = 0;
      a.state.satisfaction = 5;
      a.saveFeedback();
    });
    AppState stored = new StateStore(test.getTargetContext()).load();
    require(stored.records.size() == 1 && stored.records.get(0).actualFoods.size() == 3, "Actual foods not persisted");
    require(!stored.records.get(0).plannedSnapshot.id.equals(stored.records.get(0).actualSnapshot.id), "Planned/actual mixed");
    require(stored.alternatives.size() == 3, "Recommendation cache not persisted");
    require(RecommendationJson.request(meal, stored.profile, stored.records).getJSONArray("feedback").length() == 1,
        "Feedback not connected");
    if (!serverUrl.isEmpty()) {
      Meal fresh = demo.recognize("돈가스 정식", "");
      RecommendationHttpClient http = new RecommendationHttpClient(serverUrl);
      test.runOnMainSync(() -> {
        a.state = new AppState();
        a.state.meal = fresh;
        a.services = new FeatureServices(new ServerFoodAnalysisService(demo, http),
            new HttpMealRecommendationService(http), a.avatarFuture);
        a.root("s14");
        a.confirmMeal();
      });
      waitFor(test, () -> a.screen.equals("s13"));
      require(a.state.meal.nutrition.caloriesKcal == 850, "Live server nutrition mismatch");
      test.runOnMainSync(a::loadRecommendations);
      waitFor(test, () -> a.screen.equals("s16"));
      require(a.state.alternatives.size() == 3, "Live FastAPI top three missing");
      test.runOnMainSync(() -> { a.root("s17"); a.root("s18"); });
      waitFor(test, () -> a.futureComparisons.size() == 3);
      test.runOnMainSync(() -> { a.state.actualChoice = 0; a.saveFeedback(); });
      require(a.state.records.size() == 1 && a.state.records.get(0).actualFoods.size() == 3,
          "Live server recommendation-to-feedback flow failed");
    }
    test.getTargetContext().getSharedPreferences("fitfuture.v1", 0).edit().clear().commit();
  }

  private static android.view.View control(android.view.View root, String label) {
    if (root.isClickable() && label.equals(root.getContentDescription())) return root;
    if (root instanceof android.view.ViewGroup) {
      android.view.ViewGroup group = (android.view.ViewGroup) root;
      for (int i = 0; i < group.getChildCount(); i++) {
        android.view.View found = control(group.getChildAt(i), label);
        if (found != null) return found;
      }
    }
    return null;
  }

  private static final class FakeServer implements AutoCloseable {
    final ServerSocket socket;
    final Thread thread;

    FakeServer(String body, int status) throws IOException {
      socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
      thread = new Thread(() -> {
        try (Socket client = socket.accept()) {
          client.setSoTimeout(5000);
          InputStream input = new BufferedInputStream(client.getInputStream());
          String line;
          int length = 0;
          while (!(line = headerLine(input)).isEmpty()) {
            if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) length = Integer.parseInt(line.substring(15).trim());
          }
          for (int i = 0; i < length; i++) if (input.read() == -1) break;
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          OutputStream output = client.getOutputStream();
          output.write(("HTTP/1.1 " + status + " Result\r\nContent-Type: application/json\r\nContent-Length: "
              + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
          output.write(bytes);
          output.flush();
        } catch (IOException ignored) { }
      });
      thread.start();
    }

    String url() { return "http://127.0.0.1:" + socket.getLocalPort(); }

    private static String headerLine(InputStream input) throws IOException {
      StringBuilder line = new StringBuilder();
      int c;
      while ((c = input.read()) != -1 && c != '\n') if (c != '\r') line.append((char) c);
      return line.toString();
    }

    @Override public void close() throws Exception {
      socket.close();
      thread.join(6000);
    }
  }
}
