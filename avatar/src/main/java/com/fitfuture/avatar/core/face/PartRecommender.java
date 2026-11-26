package com.fitfuture.avatar.core.face;

import com.fitfuture.avatar.core.AvatarCatalog.Category;
import com.fitfuture.avatar.core.AvatarSpec;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 얼굴 측정값 → 닮은 파츠 초안 (얼굴형·눈·눈썹·코·입).
 *
 * <p>고정된 "정답 기준값"이 아니라, 평균에서 얼마나 떨어졌는지(z)로 종류와 크기·굵기를 고른다.
 * 결과는 초안이고, 사용자가 편집 화면에서 고친다. 헤어·옷·안경·체형 칸은 바꾸지 않는다.
 *
 * <pre>
 * 얼굴형  턱 넓음 → 각진형 / 이마 좁고 턱 좁음 → 광대형 / 세로로 김 → 타원형 / 그 외 둥근형
 * 눈      눈꼬리 내려감 → 내려간 눈 / 올라감 → 올라간 눈 / 세로로 큼 → 둥근 눈 / 그 외 기본 눈
 *         + 눈 가로 크기 → 작게·보통·크게
 * 눈썹    아치 낮음 → 일자 / 아치 높음 → 각진 / 그 외 아치  + 두께 → 가늘게·보통·굵게
 * 코      콧볼 매우 넓음 → 넓은 콧볼 / 그 외 타원 코  + 콧볼 폭 → 작게·보통·크게
 * 입술    윗입술 가운데가 깊게 패임 → 굴곡 / 입이 작음 → 둥근 / 그 외 기본  + 두께 → 얇게·보통·도톰
 * </pre>
 */
public final class PartRecommender {

    public static final double TYPE_Z = 0.8;
    public static final double SIZE_Z = 0.5;

    private PartRecommender() {}

    public static final class Result {
        /** 추천을 반영한 아바타 (얼굴형·눈·눈썹·코·입만 바뀜) */
        public final AvatarSpec spec;
        /** 부위별로 왜 그렇게 골랐는지 (화면 안내용) */
        public final Map<Category, String> reasons;
        /** 측정값별 z (디버그·기준값 조정용) */
        public final Map<String, Double> z;

        Result(AvatarSpec spec, Map<Category, String> reasons, Map<String, Double> z) {
            this.spec = spec;
            this.reasons = reasons;
            this.z = z;
        }
    }

    public static Result recommend(FaceMeasurements m, FaceNorms n, AvatarSpec base) {
        Map<String, Double> z = new LinkedHashMap<>();
        double zAspect = put(z, "faceAspect", n.faceAspect.z(m.faceAspect));
        double zJaw = put(z, "jawRatio", n.jawRatio.z(m.jawRatio));
        double zForehead = put(z, "foreheadRatio", n.foreheadRatio.z(m.foreheadRatio));
        double zEyeW = put(z, "eyeWidth", n.eyeWidth.z(m.eyeWidth));
        double zEyeOpen = put(z, "eyeOpen", n.eyeOpen.z(m.eyeOpen));
        double zTilt = put(z, "eyeTiltDeg", n.eyeTiltDeg.z(m.eyeTiltDeg));
        double zBrowT = put(z, "browThickness", n.browThickness.z(m.browThickness));
        double zArch = put(z, "browArch", n.browArch.z(m.browArch));
        double zNose = put(z, "noseWidth", n.noseWidth.z(m.noseWidth));
        double zMouthW = put(z, "mouthWidth", n.mouthWidth.z(m.mouthWidth));
        double zLip = put(z, "lipThickness", n.lipThickness.z(m.lipThickness));
        double zCupid = put(z, "cupidBow", n.cupidBow.z(m.cupidBow));

        Map<Category, String> why = new LinkedHashMap<>();
        AvatarSpec s = base;

        // 얼굴형 (볼 윤곽은 체형 칸이 정하므로 여기서 고르지 않음)
        String face;
        if (zJaw >= TYPE_Z) {
            face = "square";
            why.put(Category.FACE, "아래턱이 넓은 편이라 각진형");
        } else if (zForehead <= -0.6 && zJaw <= 0) {
            face = "heart";
            why.put(Category.FACE, "광대가 이마·턱보다 넓은 편이라 광대형");
        } else if (zAspect >= 0.6) {
            face = "v";
            why.put(Category.FACE, "얼굴이 세로로 긴 편이라 타원형");
        } else {
            face = "round";
            why.put(Category.FACE, "얼굴 가로세로가 비슷한 편이라 둥근형");
        }
        s = s.with(Category.FACE, face);

        // 눈
        String eyeType;
        String eyeWhy;
        if (zTilt <= -TYPE_Z) {
            eyeType = "b";
            eyeWhy = "눈꼬리가 내려간 편";
        } else if (zTilt >= TYPE_Z) {
            eyeType = "d";
            eyeWhy = "눈꼬리가 올라간 편";
        } else if (zEyeOpen >= TYPE_Z) {
            eyeType = "c";
            eyeWhy = "눈이 세로로 큰 편";
        } else {
            eyeType = "a";
            eyeWhy = "눈매가 평균에 가까움";
        }
        String eyeSize = level(zEyeW, "s", "m", "l");
        s = s.with(Category.EYES, eyeType + "_" + eyeSize);
        why.put(Category.EYES, eyeWhy + ", 크기 " + sizeWord(zEyeW, "작게", "보통", "크게"));

        // 눈썹: 01~03 일자, 04~06 아치, 07~09 각진 (+0 가늘게, +1 보통, +2 굵게)
        if (!Double.isNaN(m.browArch)) {
            int baseNo;
            String shape;
            if (zArch <= -0.6) {
                baseNo = 1;
                shape = "일자";
            } else if (zArch >= 1.0) {
                baseNo = 7;
                shape = "각진";
            } else {
                baseNo = 4;
                shape = "아치";
            }
            int offset = offset(zBrowT);
            s = s.with(Category.BROWS, String.format(Locale.US, "%02d", baseNo + offset));
            why.put(Category.BROWS, shape + " 눈썹, " + sizeWord(zBrowT, "가늘게", "보통", "굵게"));
        }

        // 코: 01~03 타원, 07~09 넓은 콧볼 (04~06 둥근 코는 사진으로 구분하기 어려워 직접 선택)
        if (!Double.isNaN(m.noseWidth)) {
            boolean wide = zNose >= 1.0;
            int baseNo = wide ? 7 : 1;
            int offset = wide ? (zNose >= 1.8 ? 2 : 1) : offset(zNose);
            s = s.with(Category.NOSE, String.format(Locale.US, "%02d", baseNo + offset));
            why.put(Category.NOSE, (wide ? "콧볼이 넓은 편" : "타원 코") + ", 크기 "
                    + (wide ? (offset == 2 ? "크게" : "보통") : sizeWord(zNose, "작게", "보통", "크게")));
        }

        // 입술: l01~l03 기본, l04~l06 굴곡, l07~l09 둥근 (+두께)
        if (!Double.isNaN(m.lipThickness)) {
            int baseNo;
            String shape;
            if (zCupid >= TYPE_Z) {
                baseNo = 4;
                shape = "윗입술 가운데가 또렷한 굴곡 입술";
            } else if (zMouthW <= -TYPE_Z) {
                baseNo = 7;
                shape = "입이 작은 편이라 둥근 입술";
            } else {
                baseNo = 1;
                shape = "기본 입술";
            }
            int offset = offset(zLip);
            s = s.with(Category.MOUTH, String.format(Locale.US, "l%02d", baseNo + offset));
            why.put(Category.MOUTH, shape + ", " + sizeWord(zLip, "얇게", "보통", "도톰"));
        }
        return new Result(s, why, z);
    }

    private static double put(Map<String, Double> z, String k, double v) {
        z.put(k, v);
        return v;
    }

    private static int offset(double z) {
        return z < -SIZE_Z ? 0 : z > SIZE_Z ? 2 : 1;
    }

    private static String level(double z, String lo, String mid, String hi) {
        return z < -SIZE_Z ? lo : z > SIZE_Z ? hi : mid;
    }

    private static String sizeWord(double z, String lo, String mid, String hi) {
        return level(z, lo, mid, hi);
    }
}
