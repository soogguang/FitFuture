package com.fitfuture.avatar.core;

/**
 * 하루 섭취 열량으로 1·3·6개월 뒤 체중 변화를 계산한다.
 *
 * <ul>
 *   <li>BMR: Mifflin–St Jeor (남 +5, 여 −161)</li>
 *   <li>TDEE = BMR × 활동 계수</li>
 *   <li>변화(kg) = (섭취 − TDEE) × 일수 ÷ 7,700, 범위는 ±20%</li>
 * </ul>
 * 의료적 예측이 아닌 참고용 시나리오다.
 */
public final class WeightSimulator {

    public static final double KCAL_PER_KG = 7700.0;
    public static final double UNCERTAINTY = 0.20;
    public static final int[] PERIOD_DAYS = {30, 90, 180};
    public static final int[] PERIOD_MONTHS = {1, 3, 6};

    private WeightSimulator() {}

    public enum Sex { MALE, FEMALE }

    public enum ActivityLevel {
        SEDENTARY(1.2, "거의 앉아서 생활"),
        LIGHT(1.375, "가벼운 활동 (주 1~3회 운동)"),
        MODERATE(1.55, "보통 활동 (주 3~5회 운동)"),
        ACTIVE(1.725, "활동 많음 (주 6~7회 운동)");

        public final double factor;
        public final String label;

        ActivityLevel(double factor, String label) {
            this.factor = factor;
            this.label = label;
        }
    }

    public static final class Profile {
        public final int age;
        public final double heightCm;
        public final double weightKg;
        public final Sex sex;
        public final ActivityLevel activity;

        public Profile(int age, double heightCm, double weightKg, Sex sex, ActivityLevel activity) {
            if (age <= 0 || heightCm <= 0 || weightKg <= 0) {
                throw new IllegalArgumentException("나이·키·몸무게는 0보다 커야 해요");
            }
            if (sex == null || activity == null) throw new IllegalArgumentException("성별·활동량이 필요해요");
            this.age = age;
            this.heightCm = heightCm;
            this.weightKg = weightKg;
            this.sex = sex;
            this.activity = activity;
        }
    }

    /** 한 기간의 예상 결과. */
    public static final class Point {
        public final int days;
        public final int months;
        public final double dailyDeltaKcal;
        /** 예상 체중 변화량 (kg, +는 증가) */
        public final double changeKg;
        public final double minKg;
        public final double maxKg;
        /** 현재 체중 대비 변화율 (0.043 = +4.3%) */
        public final double changeRate;

        Point(int days, int months, double dailyDeltaKcal, double weightKg) {
            this.days = days;
            this.months = months;
            this.dailyDeltaKcal = dailyDeltaKcal;
            this.changeKg = dailyDeltaKcal * days / KCAL_PER_KG;
            double spread = Math.abs(changeKg) * UNCERTAINTY;
            this.minKg = changeKg - spread;
            this.maxKg = changeKg + spread;
            this.changeRate = changeKg / weightKg;
        }
    }

    /** A(현재 유지)와 B(대안 실천) 비교. a[i], b[i]는 PERIOD_MONTHS[i]에 해당. */
    public static final class Comparison {
        public final double bmr;
        public final double tdee;
        public final Point[] a;
        public final Point[] b;
        /** B 섭취량이 기초대사량보다 낮음 → 극단적 감량 경고 */
        public final boolean bBelowBmr;

        Comparison(double bmr, double tdee, Point[] a, Point[] b, boolean bBelowBmr) {
            this.bmr = bmr;
            this.tdee = tdee;
            this.a = a;
            this.b = b;
            this.bBelowBmr = bBelowBmr;
        }

        public int indexOfMonths(int months) {
            for (int i = 0; i < PERIOD_MONTHS.length; i++) {
                if (PERIOD_MONTHS[i] == months) return i;
            }
            throw new IllegalArgumentException("1, 3, 6개월만 지원해요: " + months);
        }
    }

    public static double bmr(Profile p) {
        double base = 10 * p.weightKg + 6.25 * p.heightCm - 5 * p.age;
        return p.sex == Sex.MALE ? base + 5 : base - 161;
    }

    public static double tdee(Profile p) {
        return bmr(p) * p.activity.factor;
    }

    public static Point[] simulate(Profile p, double intakeKcal) {
        double delta = intakeKcal - tdee(p);
        Point[] out = new Point[PERIOD_DAYS.length];
        for (int i = 0; i < PERIOD_DAYS.length; i++) {
            out[i] = new Point(PERIOD_DAYS[i], PERIOD_MONTHS[i], delta, p.weightKg);
        }
        return out;
    }

    public static Comparison compare(Profile p, double intakeA, double intakeB) {
        double bmr = bmr(p);
        return new Comparison(bmr, tdee(p), simulate(p, intakeA), simulate(p, intakeB), intakeB < bmr);
    }
}
