package com.fitfuture.app.data;

import com.fitfuture.app.domain.FeatureServices;
import com.fitfuture.app.domain.Models.*;
import com.fitfuture.avatar.core.AvatarSpec;
import com.fitfuture.avatar.core.BodyCellConfig;
import com.fitfuture.avatar.core.BodyCellMapper;
import com.fitfuture.avatar.core.BodyPreset;
import com.fitfuture.avatar.core.WeightSimulator;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 미래 아바타 모듈 구현: BMR·TDEE → 하루 섭취 차이 → 1·3·6개월 체중 변화 → 체형 칸 이동.
 *
 * <p>하루 섭취 열량(A, 현재 식단 유지)
 *
 * <ul>
 *   <li>아침·점심·저녁이 모두 기록된 날이 {@link #MIN_FULL_DAYS}일 이상이면 그 날들의 평균
 *   <li>아니면 "하루 소비 열량(TDEE)만큼 먹되, 한 끼(TDEE의 1/3)를 이 메뉴로 매일 먹는다"고 가정
 * </ul>
 *
 * B(선택 대안 실천) = A − (원래 메뉴 kcal − 대안 kcal). 매일 한 끼를 같은 대안으로 바꾼다고 가정한다.
 *
 * <p>compare()는 작업 스레드에서 호출되므로, 기록·아바타는 UI 스레드에서 {@link #setContext}로 미리 복사해 넘긴다.
 */
public final class AvatarFutureService implements FeatureServices.FutureAvatarService {

  public static final int MIN_FULL_DAYS = 3;

  private final BodyCellConfig config;
  private volatile List<MealRecord> records = new ArrayList<>();
  private volatile AvatarSpec spec = AvatarSpec.defaults(BodyPreset.FEMALE);

  public AvatarFutureService(BodyCellConfig config) {
    this.config = config;
  }

  /** UI 스레드에서 호출. 기록은 사본을 넘긴다. */
  public void setContext(List<MealRecord> recordsSnapshot, AvatarSpec avatar) {
    records = recordsSnapshot != null ? recordsSnapshot : new ArrayList<>();
    spec = avatar != null ? avatar : spec;
  }

  @Override
  public FutureComparison compare(Meal meal, Alternative alt, Profile profile, int months)
      throws ModuleException {
    if (months != 1 && months != 3 && months != 6)
      throw new ModuleException("PERIOD", "1·3·6개월 중 선택해 주세요.");
    if (meal == null || meal.nutrition == null || alt == null || alt.nutrition == null)
      throw new ModuleException("NUTRITION", "영양 정보가 있어야 미래 비교를 할 수 있어요.");

    WeightSimulator.Profile p;
    try {
      p = toProfile(profile);
    } catch (IllegalArgumentException e) {
      throw new ModuleException("PROFILE", "나이·키·체중을 확인해 주세요.");
    }
    double tdee = WeightSimulator.tdee(p);
    double mealKcal = meal.nutrition.caloriesKcal;
    double altKcal = alt.nutrition.caloriesKcal;

    Double recorded = recordedDailyKcal(records);
    double dailyA = recorded != null ? recorded : tdee - tdee / 3.0 + mealKcal;
    double dailyB = dailyA - (mealKcal - altKcal);

    WeightSimulator.Comparison c = WeightSimulator.compare(p, dailyA, dailyB);
    int i = c.indexOfMonths(months);
    WeightSimulator.Point a = c.a[i], b = c.b[i];

    int base = spec.currentCell(config, profile.heightCm, profile.weightKg);
    BodyCellMapper.Future fa = BodyCellMapper.future(base, a.changeRate, config);
    BodyCellMapper.Future fb = BodyCellMapper.future(base, b.changeRate, config);

    FutureComparison f = new FutureComparison();
    f.months = months;
    f.currentWeightKg = profile.weightKg + a.changeKg;
    f.alternativeWeightKg = profile.weightKg + b.changeKg;
    f.currentChangeKg = a.changeKg;
    f.alternativeChangeKg = b.changeKg;
    f.baseCell = base;
    f.currentCell = fa.cell;
    f.alternativeCell = fb.cell;
    f.currentCheek = fa.cheekLevel;
    f.alternativeCheek = fb.cheekLevel;
    f.currentClamped = fa.clamped();
    f.alternativeClamped = fb.clamped();
    f.currentStage = AvatarStage.from(fa.actualMove);
    f.alternativeStage = AvatarStage.from(fb.actualMove);
    f.currentDailyKcal = dailyA;
    f.alternativeDailyKcal = dailyB;
    f.alternativeBelowBmr = c.bBelowBmr;
    f.assumptions = assumptions(c, recorded != null, dailyA, dailyB, mealKcal, altKcal);
    return f;
  }

  static WeightSimulator.Profile toProfile(Profile profile) {
    WeightSimulator.Sex sex =
        "남성".equals(profile.gender) ? WeightSimulator.Sex.MALE : WeightSimulator.Sex.FEMALE;
    return new WeightSimulator.Profile(
        profile.age, profile.heightCm, profile.weightKg, sex, activity(profile.activity));
  }

  /** 온보딩 활동량 보기(가벼운 활동 / 보통 활동 / 활동량 많음) → 활동 계수. */
  static WeightSimulator.ActivityLevel activity(String label) {
    if (label == null) return WeightSimulator.ActivityLevel.LIGHT;
    if (label.contains("많")) return WeightSimulator.ActivityLevel.ACTIVE;
    if (label.contains("보통")) return WeightSimulator.ActivityLevel.MODERATE;
    if (label.contains("앉")) return WeightSimulator.ActivityLevel.SEDENTARY;
    return WeightSimulator.ActivityLevel.LIGHT;
  }

  public static BodyPreset preset(Profile profile) {
    return "남성".equals(profile.gender) ? BodyPreset.MALE : BodyPreset.FEMALE;
  }

  /** 아침·점심·저녁이 모두 있고 열량을 아는 날의 평균. 그런 날이 부족하면 null. */
  static Double recordedDailyKcal(List<MealRecord> list) {
    Map<String, Map<String, Double>> days = new HashMap<>();
    Calendar cal = Calendar.getInstance();
    for (MealRecord r : list) {
      Nutrition n = r.actualNutrition; // 원래대로 먹은 경우도 저장 시 원래 영양이 들어간다
      if (n == null) continue; // 기타: 열량을 모름
      cal.setTimeInMillis(r.timestamp);
      String day = cal.get(Calendar.YEAR) + "-" + cal.get(Calendar.DAY_OF_YEAR);
      days.computeIfAbsent(day, k -> new HashMap<>()).merge(r.mealType, n.caloriesKcal, Double::sum);
    }
    double sum = 0;
    int full = 0;
    for (Map<String, Double> meals : days.values()) {
      if (meals.containsKey("아침") && meals.containsKey("점심") && meals.containsKey("저녁")) {
        double total = 0;
        for (double v : meals.values()) total += v;
        sum += total;
        full++;
      }
    }
    return full >= MIN_FULL_DAYS ? sum / full : null;
  }

  private static String assumptions(
      WeightSimulator.Comparison c,
      boolean fromRecords,
      double dailyA,
      double dailyB,
      double mealKcal,
      double altKcal) {
    String base =
        fromRecords
            ? String.format(
                Locale.KOREA,
                "A는 아침·점심·저녁을 모두 기록한 날의 평균 %,.0f kcal를 하루 섭취로 봤어요.",
                dailyA)
            : String.format(
                Locale.KOREA,
                "기록이 %d일보다 적어서, 하루 소비 열량 %,.0f kcal만큼 먹되 한 끼를 이 메뉴(%,.0f kcal)로 매일 먹는다고 가정했어요 (A 하루 %,.0f kcal).",
                MIN_FULL_DAYS,
                c.tdee,
                mealKcal,
                dailyA);
    return base
        + String.format(
            Locale.KOREA,
            " B는 매일 그 한 끼를 대안(%,.0f kcal)으로 바꾼 경우예요 (하루 %,.0f kcal)."
                + " 기초대사량 %,.0f kcal, 하루 소비 열량 %,.0f kcal 기준이며, 체지방 1kg ≈ 7,700kcal로 계산하고 ±20%% 범위로 봐요."
                + " 아바타 체형은 지금 체형 칸에서 체중 변화율 2%%·5%%마다 한 칸씩 움직이고, 볼 윤곽은 체형 구간(1~2칸 갸름·3~5칸 기본·6~7칸 도톰)을 따라가요.",
            altKcal,
            dailyB,
            c.bmr,
            c.tdee);
  }
}
