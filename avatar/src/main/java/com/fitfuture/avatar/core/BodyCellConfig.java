package com.fitfuture.avatar.core;

import java.util.Arrays;

/**
 * 체형 7칸의 BMI 경계와 키 → 표시 높이 규칙. 팀에서 값을 확정할 수 있게 설정값으로 둔다.
 *
 * <pre>
 * 칸   1      2          3          4        5          6          7
 * BMI  ~18.5  18.5~20.5  20.5~23    23~25    25~27.5    27.5~30    30~
 * </pre>
 * 경계값과 정확히 같으면 위 칸으로 보낸다 (BMI 23.0 → 4칸).
 */
public final class BodyCellConfig {

    public static final int CELL_COUNT = 7;
    public static final int MIN_CELL = 1;
    public static final int MAX_CELL = CELL_COUNT;

    /** 초기 설정값. 한국 기준(18.5 저체중 / 23 과체중 / 25 비만)이 칸 경계와 겹친다. */
    public static final BodyCellConfig DEFAULT = new BodyCellConfig(
            new double[]{18.5, 20.5, 23.0, 25.0, 27.5, 30.0},
            140, 200, 0.80, 1.00);

    private final double[] bmiBoundaries;
    public final double heightMinCm;
    public final double heightMaxCm;
    public final double scaleAtMin;
    public final double scaleAtMax;

    /**
     * @param bmiBoundaries 오름차순 6개 (7칸)
     * @param heightMinCm   이 키 이하는 scaleAtMin
     * @param heightMaxCm   이 키 이상은 scaleAtMax
     * @param scaleAtMin    프레임 높이 대비 비율 (0~1)
     * @param scaleAtMax    프레임 높이 대비 비율 (0~1)
     */
    public BodyCellConfig(double[] bmiBoundaries, double heightMinCm, double heightMaxCm,
                          double scaleAtMin, double scaleAtMax) {
        if (bmiBoundaries == null || bmiBoundaries.length != CELL_COUNT - 1) {
            throw new IllegalArgumentException("BMI 경계는 " + (CELL_COUNT - 1) + "개여야 해요");
        }
        for (int i = 1; i < bmiBoundaries.length; i++) {
            if (bmiBoundaries[i] <= bmiBoundaries[i - 1]) {
                throw new IllegalArgumentException("BMI 경계는 오름차순이어야 해요");
            }
        }
        if (heightMaxCm <= heightMinCm) throw new IllegalArgumentException("키 범위가 잘못됐어요");
        if (scaleAtMin <= 0 || scaleAtMax > 1 || scaleAtMin > scaleAtMax) {
            throw new IllegalArgumentException("표시 비율은 0 < 최소 ≤ 최대 ≤ 1 이어야 해요");
        }
        this.bmiBoundaries = bmiBoundaries.clone();
        this.heightMinCm = heightMinCm;
        this.heightMaxCm = heightMaxCm;
        this.scaleAtMin = scaleAtMin;
        this.scaleAtMax = scaleAtMax;
    }

    public double[] bmiBoundaries() {
        return bmiBoundaries.clone();
    }

    public static double bmi(double heightCm, double weightKg) {
        double m = heightCm / 100.0;
        return weightKg / (m * m);
    }

    /** BMI → 1~7칸. 경계값은 위 칸. */
    public int cellOf(double bmi) {
        int cell = MIN_CELL;
        for (double b : bmiBoundaries) {
            if (bmi >= b) cell++;
        }
        return cell;
    }

    public int draftCell(double heightCm, double weightKg) {
        return cellOf(bmi(heightCm, weightKg));
    }

    /** 키 → 전신 표시 높이 (프레임 높이 대비 비율). 범위 밖은 끝값. */
    public double heightScale(double heightCm) {
        double t = (heightCm - heightMinCm) / (heightMaxCm - heightMinCm);
        t = Math.max(0, Math.min(1, t));
        return scaleAtMin + (scaleAtMax - scaleAtMin) * t;
    }

    public static int clampCell(int cell) {
        return Math.max(MIN_CELL, Math.min(MAX_CELL, cell));
    }

    @Override
    public String toString() {
        return "BodyCellConfig" + Arrays.toString(bmiBoundaries);
    }
}
