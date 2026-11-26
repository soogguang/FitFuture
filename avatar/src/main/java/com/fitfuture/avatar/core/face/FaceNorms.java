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
     * 초기 추정치 (ML Kit 윤곽 기준, D = 두 눈 중심 거리).
     * 눈 가로 ≈ 28mm, 눈 중심 거리 ≈ 60mm, 눈꼬리 평균 +4° 정도의 일반적인 얼굴 비율에서 출발했다.
     */
    public static final FaceNorms DEFAULT = new FaceNorms(
            new Stat(1.20, 0.08),  // faceAspect
            new Stat(0.62, 0.07),  // jawRatio
            new Stat(0.80, 0.06),  // foreheadRatio
            new Stat(0.45, 0.04),  // eyeWidth
            new Stat(0.36, 0.06),  // eyeOpen
            new Stat(4.0, 4.0),    // eyeTiltDeg
            new Stat(0.12, 0.03),  // browThickness
            new Stat(0.10, 0.04),  // browArch
            new Stat(0.58, 0.06),  // noseWidth
            new Stat(0.80, 0.07),  // mouthWidth
            new Stat(0.30, 0.07),  // lipThickness
            new Stat(0.03, 0.015)  // cupidBow
    );
}
