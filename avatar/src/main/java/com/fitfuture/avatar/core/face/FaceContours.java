package com.fitfuture.avatar.core.face;

/**
 * 얼굴 윤곽 좌표 묶음 (ML Kit FaceContour와 같은 구성, Android 타입 없이).
 * 좌표는 사진 픽셀 기준, 왼쪽 위가 (0,0), 아래로 갈수록 y가 커진다.
 * 각 배열은 {x0, y0, x1, y1, ...} 형식.
 *
 * <p>"left/right"는 ML Kit 기준(사진 속 사람의 왼쪽/오른쪽)이지만, 계산은 좌우를 구분하지 않는다.
 */
public final class FaceContours {
    public float[] face;           // 얼굴 테두리 36점
    public float[] leftEye;        // 16점
    public float[] rightEye;       // 16점
    public float[] leftBrowTop;    // 5점
    public float[] leftBrowBottom; // 5점
    public float[] rightBrowTop;
    public float[] rightBrowBottom;
    public float[] noseBridge;     // 2점
    public float[] noseBottom;     // 3점 (콧볼 · 코끝 · 콧볼)
    public float[] upperLipTop;    // 11점
    public float[] upperLipBottom; // 9점
    public float[] lowerLipTop;    // 9점
    public float[] lowerLipBottom; // 9점

    /** 필수 윤곽이 모두 있는지 (눈썹·입술은 일부 없어도 해당 부위만 건너뜀). */
    public boolean hasCore() {
        return ok(face, 8) && ok(leftEye, 4) && ok(rightEye, 4);
    }

    static boolean ok(float[] pts, int minPoints) {
        return pts != null && pts.length >= minPoints * 2;
    }
}
