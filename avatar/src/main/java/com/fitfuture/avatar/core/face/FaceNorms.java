package com.fitfuture.avatar.core.face;

/**
 * 측정값의 "보통 사람" 평균과 퍼짐(표준편차).
 * 추천은 사용자의 값이 평균에서 얼마나 떨어져 있는지(z = (값 − 평균) ÷ 표준편차)로 정한다.
 *
 * <p><b>초기값은 추정치다.</b> 팀원·지인 정면 사진으로 측정값을 모은 뒤
 * 평균·표준편차를 다시 넣어야 한다 (디버그 빌드의 측정값 보기로 수집).
 */
public final class FaceNorms {

    public static final class Stat {
        public final double mean, sd;

        public Stat(double mean, double sd) {
            if (sd <= 0) throw new IllegalArgumentException("표준편차는 0보다 커야 해요");
            this.mean = mean;
            this.sd = sd;
        }

        /** 잴 수 없으면 0(보통)으로 본다. */
        public double z(double v) {
            return Double.isNaN(v) ? 0 : (v - mean) / sd;
        }
    }

    public final Stat faceAspect, jawRatio, foreheadRatio;
    public final Stat eyeWidth, eyeOpen, eyeTiltDeg;
    public final Stat browThickness, browArch;
    public final Stat noseWidth;
    public final Stat mouthWidth, lipThickness, cupidBow;

    public FaceNorms(Stat faceAspect, Stat jawRatio, Stat foreheadRatio,
                     Stat eyeWidth, Stat eyeOpen, Stat eyeTiltDeg,
                     Stat browThickness, Stat browArch, Stat noseWidth,
                     Stat mouthWidth, Stat lipThickness, Stat cupidBow) {
        this.faceAspect = faceAspect;
        this.jawRatio = jawRatio;
        this.foreheadRatio = foreheadRatio;
        this.eyeWidth = eyeWidth;
        this.eyeOpen = eyeOpen;
        this.eyeTiltDeg = eyeTiltDeg;
        this.browThickness = browThickness;
        this.browArch = browArch;
        this.noseWidth = noseWidth;
        this.mouthWidth = mouthWidth;
        this.lipThickness = lipThickness;
        this.cupidBow = cupidBow;
    }

    /**
     * 2026-11-26 보정: 정면 사진 3장(무표정 2, 웃는 얼굴 1)을 ML Kit로 잰 값의 평균으로 맞춤.
     * ML Kit 윤곽은 사람마다 차이가 작게 나오므로(눈 가로·이마·눈썹 두께는 거의 같음) 표준편차를 좁게 잡았다.
     * 사진이 더 모이면 평균·표준편차를 다시 계산한다 (docs/avatar/AVATAR_GUIDE.md 참고).
     */
    public static final FaceNorms DEFAULT = new FaceNorms(
            new Stat(1.23, 0.06),  // faceAspect
            new Stat(0.65, 0.04),  // jawRatio
            new Stat(0.90, 0.04),  // foreheadRatio
            new Stat(0.43, 0.02),  // eyeWidth
            new Stat(0.30, 0.04),  // eyeOpen   ← 사람마다 가장 많이 달라서 눈 크기를 정한다
            new Stat(3.0, 2.0),    // eyeTiltDeg
            new Stat(0.093, 0.015),// browThickness
            new Stat(0.145, 0.03), // browArch
            new Stat(0.53, 0.04),  // noseWidth
            new Stat(0.74, 0.05),  // mouthWidth
            new Stat(0.33, 0.05),  // lipThickness
            new Stat(0.026, 0.01)  // cupidBow
    );
}
