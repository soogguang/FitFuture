package com.fitfuture.avatar.core.face;

import static org.junit.Assert.*;

import com.fitfuture.avatar.core.AvatarCatalog.Category;
import com.fitfuture.avatar.core.AvatarSpec;
import com.fitfuture.avatar.core.BodyPreset;

import org.junit.Test;

/**
 * 합성 얼굴 윤곽으로 측정·추천을 확인한다.
 * 평균값으로 만든 얼굴은 "보통" 파츠가, 특징을 키운 얼굴은 그 특징의 파츠가 나와야 한다.
 */
public class PartRecommenderTest {

    /** 합성 얼굴 파라미터 (FaceNorms.DEFAULT 평균값에서 시작) */
    static final class P {
        double aspect = 1.23, jaw = 0.65, forehead = 0.90;
        double eyeW = 0.43, eyeOpen = 0.30, tilt = 3;
        double browT = 0.093, arch = 0.145, noseW = 0.53;
        double mouthW = 0.74, lip = 0.33, cupid = 0.026;
    }

    /** 두 눈 중심 거리 1 기준으로 윤곽을 만든 뒤, 회전·크기·이동을 적용한다. */
    static FaceContours build(P p, double rotDeg, double scale, double tx, double ty) {
        FaceContours c = new FaceContours();
        double maxW = 2.3, height = p.aspect * maxW, top = -1.0;
        double[] ts = {0, 0.15, 0.45, 0.85, 1.0};
        double[] ws = {0.6, p.forehead, 1.0, p.jaw, 0.25};
        int levels = 18;
        double[] face = new double[levels * 4];
        for (int i = 0; i < levels; i++) {
            double t = i / (double) (levels - 1);
            double w = interp(ts, ws, t) * maxW / 2;
            double y = top + t * height;
            face[2 * i] = w;                                   // 오른쪽: 위→아래
            face[2 * i + 1] = y;
            face[2 * (2 * levels - 1 - i)] = -w;               // 왼쪽: 아래→위
            face[2 * (2 * levels - 1 - i) + 1] = y;
        }
        c.face = tf(face, rotDeg, scale, tx, ty);
        c.leftEye = tf(eye(-0.5, p), rotDeg, scale, tx, ty);
        c.rightEye = tf(eye(0.5, p), rotDeg, scale, tx, ty);
        c.leftBrowTop = tf(brow(-1, p, 0), rotDeg, scale, tx, ty);
        c.leftBrowBottom = tf(brow(-1, p, p.browT), rotDeg, scale, tx, ty);
        c.rightBrowTop = tf(brow(1, p, 0), rotDeg, scale, tx, ty);
        c.rightBrowBottom = tf(brow(1, p, p.browT), rotDeg, scale, tx, ty);
        c.noseBottom = tf(new double[] {-p.noseW / 2, 0.75, 0, 0.8, p.noseW / 2, 0.75}, rotDeg, scale, tx, ty);
        double mw = p.mouthW, y0 = 1.05 + p.cupid * mw;
        double[] ut = new double[22];
        for (int i = 0; i < 11; i++) {
            double x = -mw / 2 + mw * i / 10.0, ax = Math.abs(x);
            double y = ax < 0.01 ? y0 : ax <= 0.15 * mw + 1e-9 ? 1.05 : 1.05 + (ax - 0.15 * mw) * 0.2;
            ut[2 * i] = x;
            ut[2 * i + 1] = y;
        }
        double ub = y0 + 0.4 * p.lip * mw, lb = ub + 0.6 * p.lip * mw;
        c.upperLipTop = tf(ut, rotDeg, scale, tx, ty);
        c.upperLipBottom = tf(line(mw * 0.9, ub), rotDeg, scale, tx, ty);
        c.lowerLipTop = tf(line(mw * 0.9, ub), rotDeg, scale, tx, ty);
        c.lowerLipBottom = tf(line(mw * 0.9, lb), rotDeg, scale, tx, ty);
        return c;
    }

    static double[] eye(double cx, P p) {
        double w = p.eyeW, h = p.eyeOpen * w, sgn = Math.signum(cx), tan = Math.tan(Math.toRadians(p.tilt));
        double[] out = new double[32];
        for (int i = 0; i < 16; i++) {
            double a = 2 * Math.PI * i / 16;
            double x = w / 2 * Math.cos(a), y = h / 2 * Math.sin(a);
            out[2 * i] = cx + x;
            out[2 * i + 1] = y - x * sgn * tan; // 바깥 눈꼬리를 tilt만큼 올림
        }
        return out;
    }

    static double[] brow(int sgn, P p, double down) {
        double x0 = 0.25, x1 = 0.8, len = x1 - x0;
        double[] out = new double[10];
        for (int i = 0; i < 5; i++) {
            double t = i / 4.0;
            double bump = 1 - Math.pow(2 * t - 1, 2);
            out[2 * i] = sgn * (x0 + len * t);
            out[2 * i + 1] = -0.45 - p.arch * len * bump + down;
        }
        return out;
    }

    static double[] line(double w, double y) {
        double[] out = new double[18];
        for (int i = 0; i < 9; i++) {
            out[2 * i] = -w / 2 + w * i / 8.0;
            out[2 * i + 1] = y;
        }
        return out;
    }

    static double interp(double[] xs, double[] ys, double x) {
        for (int i = 1; i < xs.length; i++) {
            if (x <= xs[i]) return ys[i - 1] + (ys[i] - ys[i - 1]) * (x - xs[i - 1]) / (xs[i] - xs[i - 1]);
        }
        return ys[ys.length - 1];
    }

    static float[] tf(double[] p, double rotDeg, double scale, double tx, double ty) {
        double r = Math.toRadians(rotDeg), cos = Math.cos(r), sin = Math.sin(r);
        float[] out = new float[p.length];
        for (int i = 0; i < p.length; i += 2) {
            double x = p[i] * scale, y = p[i + 1] * scale;
            out[i] = (float) (x * cos - y * sin + tx);
            out[i + 1] = (float) (x * sin + y * cos + ty);
        }
        return out;
    }

    static PartRecommender.Result rec(P p) {
        return rec(p, 0, 120, 500, 600);
    }

    static PartRecommender.Result rec(P p, double rot, double scale, double tx, double ty) {
        FaceMeasurements m = FaceMeasurements.measure(build(p, rot, scale, tx, ty));
        return PartRecommender.recommend(m, FaceNorms.DEFAULT, AvatarSpec.defaults(BodyPreset.FEMALE));
    }

    @Test
    public void measuresWhatWasBuilt() {
        P p = new P();
        FaceMeasurements m = FaceMeasurements.measure(build(p, 0, 120, 500, 600));
        assertEquals(p.aspect, m.faceAspect, 0.03);
        assertEquals(p.jaw, m.jawRatio, 0.02);
        assertEquals(p.forehead, m.foreheadRatio, 0.02);
        assertEquals(p.eyeW, m.eyeWidth, 0.01);
        assertEquals(p.eyeOpen, m.eyeOpen, 0.02);
        assertEquals(p.tilt, m.eyeTiltDeg, 0.5);
        assertEquals(p.browT, m.browThickness, 0.01);
        assertEquals(p.arch, m.browArch, 0.01);
        assertEquals(p.noseW, m.noseWidth, 0.01);
        assertEquals(p.mouthW, m.mouthWidth, 0.01);
        assertEquals(p.lip, m.lipThickness, 0.02);
        assertEquals(p.cupid, m.cupidBow, 0.005);
    }

    @Test
    public void averageFaceGetsMiddleParts() {
        AvatarSpec s = rec(new P()).spec;
        assertEquals("round", s.get(Category.FACE));
        assertEquals("a_m", s.get(Category.EYES));
        assertEquals("05", s.get(Category.BROWS));
        assertEquals("02", s.get(Category.NOSE));
        assertEquals("l02", s.get(Category.MOUTH));
    }

    @Test
    public void tiltedOrScaledPhotoGivesSameResult() {
        P p = new P();
        p.tilt = -6; // 내려간 눈
        p.jaw = 0.75;
        AvatarSpec straight = rec(p).spec;
        AvatarSpec tilted = rec(p, 17, 45, 120, 900).spec; // 고개 기울임 + 멀리서 찍음
        assertEquals(straight, tilted);
    }

    @Test
    public void distinctiveFeatures() {
        P p = new P();
        p.tilt = -2;
        p.eyeOpen = 0.36;
        assertEquals("b_l", rec(p).spec.get(Category.EYES));

        p = new P();
        p.tilt = 8;
        p.eyeOpen = 0.24;
        assertEquals("d_s", rec(p).spec.get(Category.EYES));

        p = new P();
        p.eyeOpen = 0.36;
        assertEquals("c_l", rec(p).spec.get(Category.EYES));

        p = new P();
        p.jaw = 0.75;
        assertEquals("square", rec(p).spec.get(Category.FACE));

        p = new P();
        p.forehead = 0.70;
        p.jaw = 0.55;
        assertEquals("heart", rec(p).spec.get(Category.FACE));

        p = new P();
        p.aspect = 1.30;
        assertEquals("v", rec(p).spec.get(Category.FACE));

        p = new P();
        p.arch = 0.02;
        p.browT = 0.17;
        assertEquals("03", rec(p).spec.get(Category.BROWS)); // 굵은 일자

        p = new P();
        p.arch = 0.19;
        p.browT = 0.075;
        assertEquals("07", rec(p).spec.get(Category.BROWS)); // 가는 각진

        p = new P();
        p.noseW = 0.58;
        assertEquals("08", rec(p).spec.get(Category.NOSE)); // 넓은 콧볼 보통

        p = new P();
        p.cupid = 0.05;
        p.lip = 0.40;
        assertEquals("l06", rec(p).spec.get(Category.MOUTH)); // 굴곡 도톰
    }

    @Test
    public void keepsHairOutfitAndBodyCell() {
        AvatarSpec base = AvatarSpec.defaults(BodyPreset.MALE).withHairStyle("f10").withBodyCell(5);
        FaceMeasurements m = FaceMeasurements.measure(build(new P(), 0, 100, 300, 300));
        AvatarSpec s = PartRecommender.recommend(m, FaceNorms.DEFAULT, base).spec;
        assertEquals("f10", s.get(Category.HAIR));
        assertEquals("b05", s.get(Category.HAIR_BACK));
        assertEquals(5, s.bodyCell);
        assertEquals(base.get(Category.OUTFIT), s.get(Category.OUTFIT));
    }

    @Test
    public void missingBrowsAndLipsKeepCurrentParts() {
        FaceContours c = build(new P(), 0, 100, 300, 300);
        c.leftBrowTop = null;
        c.upperLipTop = null;
        AvatarSpec base = AvatarSpec.defaults(BodyPreset.FEMALE).with(Category.BROWS, "09").with(Category.MOUTH, "e03");
        AvatarSpec s = PartRecommender.recommend(FaceMeasurements.measure(c), FaceNorms.DEFAULT, base).spec;
        assertEquals("09", s.get(Category.BROWS));
        assertEquals("e03", s.get(Category.MOUTH));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMissingEyes() {
        FaceContours c = build(new P(), 0, 100, 300, 300);
        c.rightEye = null;
        FaceMeasurements.measure(c);
    }
}
