package com.fitfuture.app;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.fitfuture.app.data.*;
import com.fitfuture.app.domain.*;
import com.fitfuture.app.domain.Models.*;
import com.fitfuture.app.ui.*;
import com.fitfuture.avatar.AvatarStore;
import com.fitfuture.avatar.core.AvatarSpec;
import com.fitfuture.avatar.core.BodyCellConfig;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends androidx.activity.ComponentActivity {
  public AppState state;
  public FeatureServices services;
  public Ui ui;
  public LinearLayout content, footer, page;
  public String screen = "s01";
  public Dialog activeDialog;
  private final ArrayList<String> backStack = new ArrayList<>();
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private StateStore store;
  private int operation;
  private boolean preview;
  private String retryScreen = "s09";
  private OnboardingScreens onboarding;
  private MealScreens meals;
  private RecommendationScreens recommendations;
  private AccountScreens account;
  public MealCamera mealCamera;
  public boolean recognitionReady;
  public String failureMessage = "";
  public String requestError = "";

  public interface Work<T> {
    T get() throws Exception;
  }

  public interface Done<T> {
    void accept(T value);
  }

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    if (Build.VERSION.SDK_INT >= 27)
      getWindow()
          .getDecorView()
          .setSystemUiVisibility(
              View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    ui = new Ui(this);
    mealCamera = new MealCamera(this);
    store = new StateStore(this);
    DemoServices demo = new DemoServices();
    avatarFuture = new AvatarFutureService(BODY_CELLS);
    RecommendationHttpClient recommendationClient =
        new RecommendationHttpClient(BuildConfig.RECOMMENDATION_BASE_URL);
    FeatureServices.FoodAnalysisService foodAnalysis = BuildConfig.FOOD_API_URL.isEmpty()
        ? new ServerFoodAnalysisService(demo, recommendationClient)
        : new RemoteFoodAnalysis(this, BuildConfig.FOOD_API_URL);
    services = new FeatureServices(foodAnalysis,
        new HttpMealRecommendationService(recommendationClient), avatarFuture);
    if (saved != null) {
      state = (AppState) saved.getSerializable("state");
      screen = saved.getString("screen", "s01");
      ArrayList<String> old = saved.getStringArrayList("back");
      if (old != null) backStack.addAll(old);
      preview = saved.getBoolean("preview");
      retryScreen = saved.getString("retry", "s09");
      failureMessage = saved.getString("failureMessage", "");
      requestError = saved.getString("requestError", "");
      recognitionReady = saved.getBoolean("ready");
    }
    if (state == null) {
      state = store.load();
      screen = state.onboarded ? "s08" : "s01";
    }
    onboarding = new OnboardingScreens(this);
    meals = new MealScreens(this);
    recommendations = new RecommendationScreens(this);
    account = new AccountScreens(this);
    applyPreview(getIntent());
    render();
    if (!preview && screen.equals("s16_loading")) loadRecommendations();
    if (!preview && screen.equals("s12") && !recognitionReady) recognize();
    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new androidx.activity.OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                back();
              }
            });
  }

  private void applyPreview(Intent intent) {
    if (!BuildConfig.DEBUG) return;
    String route = intent.getStringExtra("preview");
    if (route == null) return;
    preview = true;
    state = new AppState();
    seedPreview();
    screen = route;
    state.selectedAlternative = Math.max(0, Math.min(2, intent.getIntExtra("alternative", 0)));
    state.actualChoice = state.selectedAlternative;
    state.months = intent.getIntExtra("months", 1);
    state.introPage = intent.getIntExtra("intro", 0);
    state.assumptionsExpanded = intent.getBooleanExtra("expanded", false);
    recognitionReady = true;
    MealRecord sample = state.record();
    sample.plannedAlternative = state.selectedAlternative;
    sample.actualChoice = state.selectedAlternative;
    sample.actualTitle = state.selected().title;
    sample.actualNutrition = state.selected().nutrition;
    if (route.equals("s08")) {
      state.records.clear();
      MealRecord breakfast = new MealRecord();
      breakfast.meal = new Meal("요거트와 토스트");
      breakfast.meal.nutrition = new Nutrition(430, 58, 19, 14);
      breakfast.actualTitle = "요거트와 토스트";
      breakfast.mealType = "아침";
      breakfast.actualChoice = 3;
      breakfast.actualNutrition = breakfast.meal.nutrition;
      state.records.add(breakfast);
    }
    if (route.equals("s24")) {
      state.weights.addAll(List.of(58.0, 58.0, 56.0));
      state.weightDates.addAll(List.of("2026-11-01", "2026-11-08", "2026-11-15"));
    }
    if (route.equals("s22")) {
      state.selectedDate = "2026-11-12";
      try {
        sample.timestamp =
            new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
                .parse(state.selectedDate)
                .getTime();
        for (int i = 0; i < 7; i++) {
          MealRecord breakfast = new MealRecord();
          breakfast.meal = new Meal("요거트와 토스트");
          breakfast.mealType = "아침";
          Calendar date = Calendar.getInstance();
          date.setTimeInMillis(sample.timestamp);
          date.add(Calendar.DAY_OF_MONTH, i - 3);
          breakfast.timestamp = date.getTimeInMillis();
          breakfast.actualNutrition =
              new Nutrition(new int[] {850, 1100, 950, 430, 600, 800, 1000}[i], 58, 19, 14);
          breakfast.meal.nutrition = breakfast.actualNutrition;
          breakfast.actualTitle = breakfast.meal.name;
          breakfast.actualChoice = 3;
          state.records.add(0, breakfast);
        }
      } catch (Exception ignored) {
      }
    }
  }

  private void seedPreview() {
    DemoServices fixture = new DemoServices();
    try {
      services = new FeatureServices(fixture, fixture, avatarFuture);
      state.meal = services.foodAnalysis.recognize("돈가스 정식", "");
      state.meal.nutrition = services.foodAnalysis.analyze(state.meal.foods);
      state.meal.confirmed = true;
      state.alternatives = fixture.recommend(state.meal, state.profile);
    } catch (Exception ignored) {
    }
    state.note = "밥을 조금 덜어도 충분했어요";
    MealRecord record = new MealRecord();
    record.meal = state.meal.copy();
    record.actualTitle = "밥 양 조절";
    record.actualNutrition = state.alternatives.get(0).nutrition;
    record.satisfaction = 4;
    record.note = state.note;
    state.records.add(record);
    state.selectedRecordId = record.id;
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    operation++;
    applyPreview(intent);
    render();
  }

  public void go(String target) {
    operation++;
    futureLoading = false;
    backStack.add(screen);
    screen = target;
    hideKeyboard();
    render();
  }

  public void root(String target) {
    operation++;
    futureLoading = false;
    backStack.clear();
    screen = target;
    hideKeyboard();
    render();
  }

  public void back() {
    // 아바타를 고치다가 저장하지 않고 나가면 한 번 확인한다
    if (screen.equals("s07")
        && state.onboarded
        && avatarDraft != null
        && !avatarDraft.equals(avatar())) {
      new android.app.AlertDialog.Builder(this)
          .setTitle("저장하지 않고 나갈까요?")
          .setMessage("바꾼 아바타는 저장되지 않아요.")
          .setPositiveButton(
              "나가기",
              (dialog, which) -> {
                avatarDraft = null;
                back();
              })
          .setNegativeButton("계속 고치기", null)
          .show();
      return;
    }
    if (screen.equals("s02") && state.introPage > 0) {
      state.introPage--;
      render();
      return;
    }
    if (screen.equals("s01")) {
      finish();
      return;
    }
    if (backStack.isEmpty()) {
      if (screen.equals("s08") || screen.equals("s27")) finish();
      else root(state.onboarded ? "s08" : "s01");
      return;
    }
    operation++;
    futureLoading = false;
    screen = backStack.remove(backStack.size() - 1);
    hideKeyboard();
    render();
  }

  private void hideKeyboard() {
    View focus = getCurrentFocus();
    if (focus != null)
      ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
          .hideSoftInputFromWindow(focus.getWindowToken(), 0);
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    out.putSerializable("state", state);
    out.putString("screen", screen);
    out.putStringArrayList("back", backStack);
    out.putBoolean("preview", preview);
    out.putString("retry", retryScreen);
    out.putString("failureMessage", failureMessage);
    out.putString("requestError", requestError);
    out.putBoolean("ready", recognitionReady);
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (mealCamera != null) mealCamera.resume();
  }

  @Override
  protected void onPause() {
    super.onPause();
    persist();
  }

  @Override
  protected void onDestroy() {
    operation++;
    mealCamera.close();
    if (activeDialog != null) activeDialog.dismiss();
    worker.shutdownNow();
    if (selfieAnalyzer != null) selfieAnalyzer.close();
    super.onDestroy();
  }

  public void persist() {
    if (!preview) store.save(state);
  }

  public LinearLayout slot(String name) {
    int id =
        switch (name) {
          case "add" -> R.id.slot_add;
          case "alternative" -> R.id.slot_alternative;
          case "alternatives" -> R.id.slot_alternatives;
          case "assumptions" -> R.id.slot_assumptions;
          case "avatar" -> R.id.slot_avatar;
          case "avatars" -> R.id.slot_avatars;
          case "brand" -> R.id.slot_brand;
          case "budget" -> R.id.slot_budget;
          case "calendar" -> R.id.slot_calendar;
          case "calories" -> R.id.slot_calories;
          case "camera" -> R.id.slot_camera;
          case "chart" -> R.id.slot_chart;
          case "choices" -> R.id.slot_choices;
          case "compare" -> R.id.slot_compare;
          case "composition" -> R.id.slot_composition;
          case "conditions" -> R.id.slot_conditions;
          case "date" -> R.id.slot_date;
          case "delete" -> R.id.slot_delete;
          case "detail_link" -> R.id.slot_detail_link;
          case "disclaimer" -> R.id.slot_disclaimer;
          case "dots" -> R.id.slot_dots;
          case "empty" -> R.id.slot_empty;
          case "environment" -> R.id.slot_environment;
          case "error" -> R.id.slot_error;
          case "example" -> R.id.slot_example;
          case "examples" -> R.id.slot_examples;
          case "feedback" -> R.id.slot_feedback;
          case "fields" -> R.id.slot_fields;
          case "filters" -> R.id.slot_filters;
          case "food_name" -> R.id.slot_food_name;
          case "frequent" -> R.id.slot_frequent;
          case "gender" -> R.id.slot_gender;
          case "goals" -> R.id.slot_goals;
          case "graph" -> R.id.slot_graph;
          case "greeting" -> R.id.slot_greeting;
          case "heading" -> R.id.slot_heading;
          case "hero" -> R.id.slot_hero;
          case "intro" -> R.id.slot_intro;
          case "loading" -> R.id.slot_loading;
          case "logout" -> R.id.slot_logout;
          case "meal" -> R.id.slot_meal;
          case "memo" -> R.id.slot_memo;
          case "methods" -> R.id.slot_methods;
          case "note" -> R.id.slot_note;
          case "notes" -> R.id.slot_notes;
          case "notice" -> R.id.slot_notice;
          case "notices" -> R.id.slot_notices;
          case "notifications" -> R.id.slot_notifications;
          case "nutrients" -> R.id.slot_nutrients;
          case "nutrition" -> R.id.slot_nutrition;
          case "periods" -> R.id.slot_periods;
          case "photo" -> R.id.slot_photo;
          case "planned" -> R.id.slot_planned;
          case "portions" -> R.id.slot_portions;
          case "preferences" -> R.id.slot_preferences;
          case "profile" -> R.id.slot_profile;
          case "progress" -> R.id.slot_progress;
          case "rating" -> R.id.slot_rating;
          case "reason" -> R.id.slot_reason;
          case "records" -> R.id.slot_records;
          case "retry" -> R.id.slot_retry;
          case "scores" -> R.id.slot_scores;
          case "settings" -> R.id.slot_settings;
          case "skip" -> R.id.slot_skip;
          case "status" -> R.id.slot_status;
          case "success" -> R.id.slot_success;
          case "summary" -> R.id.slot_summary;
          case "target" -> R.id.slot_target;
          case "text_link" -> R.id.slot_text_link;
          case "toast" -> R.id.slot_toast;
          case "upload" -> R.id.slot_upload;
          case "warning" -> R.id.slot_warning;
          case "weeks" -> R.id.slot_weeks;
          case "weight" -> R.id.slot_weight;
          case "welcome" -> R.id.slot_welcome;
          default -> throw new IllegalArgumentException("Unknown slot: " + name);
        };
    return page.findViewById(id);
  }

  public void render() {
    mealCamera.close();
    if (screen.equals("s08") && state.records.isEmpty()) screen = "s27";
    setContentView(R.layout.activity_main);
    View root = findViewById(R.id.root);
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets bars =
                insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsets.CONSUMED;
          }
          v.setPadding(
              insets.getSystemWindowInsetLeft(),
              insets.getSystemWindowInsetTop(),
              insets.getSystemWindowInsetRight(),
              insets.getSystemWindowInsetBottom());
          return insets.consumeSystemWindowInsets();
        });
    root.requestApplyInsets();
    content = findViewById(R.id.content);
    footer = findViewById(R.id.footer);
    LinearLayout nav = findViewById(R.id.bottom_nav);
    int id =
        switch (screen) {
          case "s01" -> R.layout.screen_s01;
          case "s02" -> R.layout.screen_s02;
          case "s03" -> R.layout.screen_s03;
          case "s04" -> R.layout.screen_s04;
          case "s05" -> R.layout.screen_s05;
          case "s06" -> R.layout.screen_s06;
          case "s07" -> R.layout.screen_s07;
          case "s08" -> R.layout.screen_s08;
          case "s09" -> R.layout.screen_s09;
          case "s10" -> R.layout.screen_s10;
          case "s11" -> R.layout.screen_s11;
          case "s12" -> R.layout.screen_s12;
          case "s13" -> R.layout.screen_s13;
          case "s14" -> R.layout.screen_s14;
          case "s14_add" -> R.layout.screen_s14_add;
          case "s15" -> R.layout.screen_s15;
          case "s16" -> R.layout.screen_s16;
          case "s16_loading" -> R.layout.screen_s16_loading;
          case "s17" -> R.layout.screen_s17;
          case "s18" -> R.layout.screen_s18;
          case "s20" -> R.layout.screen_s20;
          case "s21" -> R.layout.screen_s21;
          case "s22" -> R.layout.screen_s22;
          case "s23" -> R.layout.screen_s23;
          case "s24" -> R.layout.screen_s24;
          case "s25" -> R.layout.screen_s25;
          case "s26" -> R.layout.screen_s26;
          case "s27" -> R.layout.screen_s27;
          case "s28" -> R.layout.screen_s28;
          case "s29" -> R.layout.screen_s29;
          case "s29_allergy" -> R.layout.screen_s29_allergy;
          case "s29_delete" -> R.layout.screen_s29_delete;
          case "s29_nutrition" -> R.layout.screen_s29_nutrition;
          case "s29_required" -> R.layout.screen_s29_required;
          case "s29_shortage" -> R.layout.screen_s29_shortage;
          default -> 0;
        };
    if (id == 0) {
      screen = "s08";
      id = R.layout.screen_s08;
    }
    page = (LinearLayout) getLayoutInflater().inflate(id, content, false);
    content.addView(page, new LinearLayout.LayoutParams(-1, -1));
    findViewById(R.id.back).setOnClickListener(v -> back());
    findViewById(R.id.more).setOnClickListener(this::more);
    String title = titleFor(screen);
    ((TextView) findViewById(R.id.toolbar_title)).setText(title);
    boolean top = List.of("s08", "s22", "s24", "s25", "s27").contains(screen);
    findViewById(R.id.back).setVisibility(top ? View.GONE : View.VISIBLE);
    if (top) {
      ((TextView) findViewById(R.id.toolbar_title)).setTextSize(22);
      LinearLayout.LayoutParams titleParams =
          (LinearLayout.LayoutParams) findViewById(R.id.toolbar_title).getLayoutParams();
      titleParams.setMarginStart(ui.dp(8));
      findViewById(R.id.toolbar_title).setLayoutParams(titleParams);
    }
    if (screen.equals("s01")) {
      findViewById(R.id.toolbar).setVisibility(View.GONE);
      footer.setBackgroundColor(ui.color(R.color.background));
      root.setBackgroundColor(ui.color(R.color.mint_strong));
      getWindow().setStatusBarColor(ui.color(R.color.mint_strong));
    } else getWindow().setStatusBarColor(ui.color(R.color.background));
    boolean hasNav = top || screen.equals("s09");
    nav.setVisibility(hasNav ? View.VISIBLE : View.GONE);
    if (hasNav) bottomNav(nav);
    if (screen.compareTo("s08") < 0) onboarding.render(screen);
    else if (List.of("s08", "s09", "s10", "s11", "s12", "s13", "s14", "s14_add", "s15")
        .contains(screen)) meals.render(screen);
    else if (List.of("s16", "s16_loading", "s17", "s18", "s20", "s21").contains(screen))
      recommendations.render(screen);
    else account.render(screen);
    if (footer.getChildCount() == 0) footer.setVisibility(View.GONE);
  }

  public void primary(String label, Runnable action) {
    ui.button(footer, label, action);
  }

  public void refresh() {
    render();
  }

  private String titleFor(String s) {
    switch (s) {
      case "s01":
        return "";
      case "s02":
        return "FitFuture";
      case "s03":
        return "기본 정보";
      case "s04":
        return "목표 설정";
      case "s05":
        return "취향과 알레르기";
      case "s06":
        return "예산과 식사 환경";
      case "s07":
        return "아바타 설정";
      case "s08":
      case "s27":
        return "FitFuture";
      case "s09":
      case "s12":
      case "s15":
        return "식단 입력";
      case "s10":
        return "사진 촬영";
      case "s11":
        return "텍스트 입력";
      case "s13":
      case "s29_nutrition":
        return "식단 분석";
      case "s14":
      case "s29_allergy":
        return "음식·섭취량 확인";
      case "s14_add":
        return "음식 추가";
      case "s16":
      case "s16_loading":
      case "s29_shortage":
        return "나에게 맞는 대안";
      case "s17":
        return "대안 상세";
      case "s18":
      case "s24":
        return "미래의 나";
      case "s20":
        return "식사 피드백";
      case "s21":
        return "식사 기록 완료";
      case "s22":
        return "식사 기록";
      case "s23":
      case "s29_delete":
        return "식사 상세";
      case "s25":
      case "s29":
        return "마이";
      case "s26":
        return "취향·알레르기 수정";
      case "s28":
        return "연결 오류";
      case "s29_required":
        return "프로필 설정";
      default:
        return "FitFuture";
    }
  }

  private void bottomNav(LinearLayout nav) {
    String[] labels = {"홈", "기록", "식단 입력", "미래의 나", "마이"},
        targets = {"s08", "s22", "s09", "s24", "s25"};
    int[] icons = {
      R.drawable.ic_home,
      R.drawable.ic_history,
      R.drawable.ic_plus,
      R.drawable.ic_spark,
      R.drawable.ic_user
    };
    int active =
        screen.equals("s22")
            ? 1
            : screen.equals("s09") ? 2 : screen.equals("s24") ? 3 : screen.equals("s25") ? 4 : 0;
    for (int i = 0; i < 5; i++) {
      final String target = targets[i];
      LinearLayout item = ui.column();
      item.setGravity(Gravity.CENTER);
      LinearLayout bubble = ui.column();
      bubble.setGravity(Gravity.CENTER);
      if (i == 2) bubble.setBackgroundResource(R.drawable.bg_primary);
      else if (active == i) bubble.setBackgroundResource(R.drawable.bg_mint_strong);
      bubble.addView(
          ui.icon(
              icons[i],
              i == 2 ? 24 : 20,
              i == 2 ? R.color.white : active == i ? R.color.primary : R.color.muted));
      item.addView(bubble, new LinearLayout.LayoutParams(ui.dp(40), ui.dp(28)));
      TextView label = ui.text(labels[i], 10, active == i ? R.color.primary : R.color.muted, false);
      label.setGravity(Gravity.CENTER);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
      lp.topMargin = ui.dp(4);
      item.addView(label, lp);
      nav.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
      item.setContentDescription(labels[i]);
      item.setOnClickListener(v -> root(target));
    }
  }

  private void more(View anchor) {
    PopupMenu m = new PopupMenu(this, anchor);
    if (screen.equals("s23")) {
      m.getMenu()
          .add("기록 삭제")
          .setOnMenuItemClickListener(
              i -> {
                account.deleteSheet();
                return true;
              });
    } else if (List.of("s03", "s04", "s05", "s06", "s07", "s25").contains(screen)) {
      m.getMenu()
          .add("취향·알레르기 수정")
          .setOnMenuItemClickListener(
              i -> {
                go("s26");
                return true;
              });
    } else {
      m.getMenu()
          .add("음식 정보 수정")
          .setOnMenuItemClickListener(
              i -> {
                if (state.meal != null) go("s14");
                else go("s11");
                return true;
              });
    }
    m.show();
  }

  public <T> void async(Work<T> work, Done<T> done) {
    requestError = "";
    int token = ++operation;
    worker.submit(
        () -> {
          try {
            T value = work.get();
            runOnUiThread(
                () -> {
                  if (!isFinishing() && token == operation) done.accept(value);
                });
          } catch (Exception ex) {
            runOnUiThread(
                () -> {
                  if (!isFinishing() && token == operation) showFailure(ex);
                });
          }
        });
  }

  public void recognize() {
    recognitionReady = false;
    go("s12");
    String input = state.inputText, photo = state.photoUri;
    async(
        () -> {
          Thread.sleep(650);
          return services.foodAnalysis.recognize(input, photo);
        },
        m -> {
          state.meal = m;
          state.alternatives.clear();
          recognitionReady = true;
          persist();
          if (screen.equals("s12")) render();
        });
  }

  public void confirmMeal() {
    if (state.meal == null) {
      go("s11");
      return;
    }
    Meal draft = state.meal.copy();
    async(
        () -> services.foodAnalysis.analyze(draft.foods),
        n -> {
          state.meal.nutrition = n;
          state.meal.nutritionSource = n.source;
          state.meal.confirmed = true;
          state.alternatives.clear();
          persist();
          go("s13");
        });
  }

  public void loadRecommendations() {
    if (state.meal == null || !state.meal.confirmed) {
      go("s14");
      return;
    }
    go("s16_loading");
    Meal meal = state.meal.copy();
    Profile profile = state.profile;
    List<MealRecord> history = new ArrayList<>(state.records);
    async(
        () -> services.recommendations.recommend(meal, profile, history),
        list -> {
          if (list.isEmpty()) {
            state.alternatives.clear();
            failureMessage = "조건에 맞는 대안을 찾지 못했어요. 음식과 식사 조건을 확인해 주세요.";
            persist();
            go("s29_shortage");
            return;
          }
          state.alternatives = list;
          state.meal.nutrition = meal.nutrition;
          state.meal.nutritionSource = meal.nutritionSource;
          futureKey = "";
          futureComparisons.clear();
          state.selectedAlternative = Math.max(0, Math.min(state.selectedAlternative, list.size() - 1));
          state.actualChoice = state.selectedAlternative;
          persist();
          go("s16");
        });
  }

  private void showFailure(Exception ex) {
    failureMessage = ex instanceof ModuleException ? ex.getMessage() : "서버 요청을 처리하지 못했어요. 다시 시도해 주세요.";
    if (ex instanceof ModuleException) {
      switch (((ModuleException) ex).code) {
        case "RECOGNITION":
          go("s15");
          return;
        case "NUTRITION":
          requestError = ex.getMessage();
          go("s29_nutrition");
          return;
        case "SHORTAGE":
          state.alternatives.clear();
          persist();
          go("s29_shortage");
          return;
      }
    }
    retryScreen = screen;
    if (screen.equals("s14")) retryScreen = "confirmMeal";
    requestError = ex instanceof ModuleException ? ex.getMessage() : "서버 연결을 확인한 뒤 다시 시도해 주세요.";
    go("s28");
  }

  public void retry() {
    if (retryScreen.equals("s12")) recognize();
    else if (retryScreen.equals("confirmMeal")) confirmMeal();
    else if (retryScreen.equals("s16_loading")) loadRecommendations();
    else go("s09");
  }

  public void pickPhoto() {
    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    i.setType("image/*");
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.addFlags(
        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
    try {
      startActivityForResult(i, 42);
    } catch (ActivityNotFoundException ex) {
      go("s28");
    }
  }

  public void capturePhoto() {
    mealCamera.takePhoto();
  }

  public void acceptMealPhoto(Uri uri) {
    state.photoUri = uri.toString();
    state.inputText = "";
    state.meal = null;
    state.alternatives.clear();
    recognize();
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == REQ_SELFIE_PICK || request == REQ_SELFIE_CAMERA) {
      onSelfieResult(request, result, data);
      return;
    }
    if (result != RESULT_OK) return;
    Uri uri = null;
    if (request == 42 && data != null) {
      uri = data.getData();
      if (uri != null)
        try {
          getContentResolver()
              .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
        }
    }
    if (uri != null) {
      acceptMealPhoto(uri);
    }
  }

  // ── 아바타 ──
  /** 체형 7칸 BMI 경계·키 표시 범위. 팀에서 확정하면 이 값만 바꾼다. */
  public static final BodyCellConfig BODY_CELLS = BodyCellConfig.DEFAULT;

  public AvatarFutureService avatarFuture;
  private AvatarSpec avatar;
  /** 아바타 만들기 화면에서 고치는 중인 사본과 열린 탭. */
  public AvatarSpec avatarDraft;
  public int avatarTab;

  /** 저장된 아바타. 몸 기본형은 프로필 성별을 따른다. */
  public AvatarSpec avatar() {
    if (avatar == null) avatar = AvatarStore.load(this, AvatarFutureService.preset(state.profile));
    return avatar.withPreset(AvatarFutureService.preset(state.profile));
  }

  public void saveAvatar(AvatarSpec spec) {
    avatar = spec;
    if (!preview) AvatarStore.save(this, spec);
  }

  /** 사용자가 확정한 현재 체형 칸, 확정 전이면 BMI 초안. */
  public int currentCell(AvatarSpec spec) {
    return spec.currentCell(BODY_CELLS, state.profile.heightCm, state.profile.weightKg);
  }

  public int draftCell() {
    return BODY_CELLS.draftCell(state.profile.heightCm, state.profile.weightKg);
  }

  public double heightScale() {
    return BODY_CELLS.heightScale(state.profile.heightCm);
  }

  // ── 셀카로 닮은 파츠 추천 ──
  private static final int REQ_SELFIE_PICK = 44, REQ_SELFIE_CAMERA = 45;
  private com.fitfuture.avatar.face.SelfieAnalyzer selfieAnalyzer;
  private Uri pendingSelfieUri;
  private File pendingSelfieFile;
  /** 아바타 만들기 화면에 보여줄 상태 */
  public boolean selfieBusy;
  public String selfieMessage = "";
  public boolean selfieOk;
  public Map<com.fitfuture.avatar.core.AvatarCatalog.Category, String> selfieReasons =
      new LinkedHashMap<>();
  public Map<String, Double> selfieZ = new LinkedHashMap<>();

  public void clearSelfie() {
    selfieBusy = false;
    selfieMessage = "";
    selfieOk = false;
    selfieReasons.clear();
    selfieZ.clear();
  }

  /** 처음 한 번 사진 분석 안내·동의를 받고 셀카 촬영 또는 사진 고르기를 연다. */
  public void startSelfie(boolean camera) {
    android.content.SharedPreferences prefs = getSharedPreferences("fitfuture.avatar", MODE_PRIVATE);
    if (!prefs.getBoolean("selfieConsent", false)) {
      new android.app.AlertDialog.Builder(this)
          .setTitle("사진으로 닮은 얼굴 추천")
          .setMessage(
              "사진은 이 기기 안에서만 분석하고 서버로 보내지 않아요. 분석이 끝나면 사진은 바로 지우고,"
                  + " 저장되는 건 고른 파츠 이름뿐이에요.")
          .setPositiveButton(
              "동의하고 계속",
              (d, w) -> {
                prefs.edit().putBoolean("selfieConsent", true).apply();
                startSelfie(camera);
              })
          .setNegativeButton("취소", null)
          .show();
      return;
    }
    try {
      if (camera) {
        File directory = new File(getFilesDir(), "photos");
        if (!directory.exists() && !directory.mkdirs()) throw new java.io.IOException();
        // PhotoProvider가 허용하는 이름 형식(meal-*.jpg)을 쓴다
        pendingSelfieFile = File.createTempFile("meal-selfie-", ".jpg", directory);
        pendingSelfieUri =
            Uri.parse("content://" + getPackageName() + ".photos/" + pendingSelfieFile.getName());
        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        i.putExtra(MediaStore.EXTRA_OUTPUT, pendingSelfieUri);
        i.putExtra("android.intent.extras.CAMERA_FACING", 1); // 가능하면 전면 카메라
        i.setClipData(ClipData.newRawUri("selfie", pendingSelfieUri));
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i, REQ_SELFIE_CAMERA);
      } else {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i, REQ_SELFIE_PICK);
      }
    } catch (Exception ex) {
      selfieOk = false;
      selfieMessage = camera ? "카메라를 열지 못했어요. 사진 고르기로 시도해 주세요." : "사진 선택 화면을 열지 못했어요.";
      deleteSelfieFile();
      render();
    }
  }

  private void onSelfieResult(int request, int result, Intent data) {
    Uri uri = request == REQ_SELFIE_CAMERA ? pendingSelfieUri : (data != null ? data.getData() : null);
    if (result != RESULT_OK || uri == null) {
      deleteSelfieFile();
      return;
    }
    if (avatarDraft == null) avatarDraft = avatar();
    com.fitfuture.avatar.core.AvatarSpec base = avatarDraft;
    if (selfieAnalyzer == null) selfieAnalyzer = new com.fitfuture.avatar.face.SelfieAnalyzer();
    com.fitfuture.avatar.face.SelfieAnalyzer analyzer = selfieAnalyzer;
    clearSelfie();
    selfieBusy = true;
    selfieMessage = "얼굴을 살펴보는 중이에요…";
    render();
    async(
        () ->
            analyzer.analyze(
                this, uri, base, com.fitfuture.avatar.core.face.FaceNorms.DEFAULT),
        (com.fitfuture.avatar.face.SelfieAnalyzer.Outcome outcome) -> {
          deleteSelfieFile();
          selfieBusy = false;
          selfieOk = outcome.ok;
          if (outcome.ok) {
            avatarDraft = outcome.result.spec;
            selfieReasons.putAll(outcome.result.reasons);
            selfieZ.putAll(outcome.result.z);
            selfieMessage =
                "사진과 닮은 얼굴형·눈·눈썹·코·입을 골랐어요. 마음에 안 드는 부위는 아래에서 바꿀 수 있어요."
                    + (outcome.message.isEmpty() ? "" : " " + outcome.message);
          } else {
            selfieMessage = outcome.message;
          }
          if (screen.equals("s07")) render();
        });
  }

  /** 촬영한 셀카 파일은 분석 뒤 바로 지운다 (갤러리 사진은 원래 자리에 그대로). */
  private void deleteSelfieFile() {
    if (pendingSelfieFile != null) {
      //noinspection ResultOfMethodCallIgnored
      pendingSelfieFile.delete();
    }
    pendingSelfieFile = null;
    pendingSelfieUri = null;
  }

  private String futureKey = "";
  private boolean futureLoading;
  public List<FutureComparison> futureComparisons = new ArrayList<>();

  public boolean ensureComparisons() {
    if (state.meal == null || state.selected() == null) return false;
    String key =
        state.meal.id
            + ":"
            + state.meal.nutrition.caloriesKcal
            + ":"
            + state.selectedAlternative
            + ":" + state.selected().id + ":" + state.selected().nutrition.caloriesKcal
            + ":"
            + state.profile.weightKg
            + ":"
            + state.profile.heightCm
            + ":"
            + state.profile.age
            + ":"
            + state.profile.activity
            + ":"
            + state.profile.gender
            + ":"
            + avatar().hashCode()
            + ":"
            + state.records.size();
    if (key.equals(futureKey) && futureComparisons.size() == 3) return true;
    if (key.equals(futureKey) && futureLoading) return false;
    futureKey = key;
    futureLoading = true;
    futureComparisons.clear();
    Meal meal = state.meal.copy();
    Alternative alternative = state.selected();
    Profile profile = state.profile;
    avatarFuture.setContext(new ArrayList<>(state.records), avatar());
    async(
        () -> {
          List<FutureComparison> result = new ArrayList<>();
          for (int months : new int[] {1, 3, 6})
            result.add(services.futureAvatar.compare(meal, alternative, profile, months));
          return result;
        },
        result -> {
          futureLoading = false;
          futureComparisons = result;
          if (screen.equals("s18")) render();
        });
    return false;
  }

  public void saveFeedback() {
    if (state.meal == null || state.alternatives.isEmpty()) return;
    MealRecord r = new MealRecord();
    r.meal = state.meal.copy();
    r.mealType = state.draftMealType;
    r.plannedAlternative = state.selectedAlternative;
    r.plannedSnapshot = state.selected();
    r.actualChoice = state.actualChoice;
    r.satisfaction = state.satisfaction;
    r.note = state.note;
    if (state.actualChoice < state.alternatives.size()) {
      Alternative alt = state.alternatives.get(state.actualChoice);
      r.actualTitle = alt.title;
      r.actualNutrition = alt.nutrition;
      r.actualSnapshot = alt;
      for (FoodPortion f : alt.foods) r.actualFoods.add(f.copy());
    } else {
      r.actualTitle = state.actualChoice == 3 ? "원래대로" : "기타";
      r.actualNutrition = state.actualChoice == 3 ? state.meal.nutrition : null;
      if (state.actualChoice == 3)
        for (FoodPortion f : state.meal.foods) r.actualFoods.add(f.copy());
    }
    // A repeated save on the same completed flow replaces the record instead of duplicating it.
    state.records.removeIf(old -> old.meal.id.equals(r.meal.id));
    state.records.add(r);
    state.selectedRecordId = r.id;
    persist();
    go("s21");
  }

  public void startNewMeal() {
    state.draftMealType = "점심";
    state.meal = null;
    state.alternatives.clear();
    state.photoUri = "";
    state.inputText = "돈가스 정식";
    state.note = "";
    state.selectedAlternative = 0;
    state.actualChoice = 0;
    recognitionReady = false;
    go("s09");
  }
}
