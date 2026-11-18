package com.fitfuture.avatar.core.face;

/**
 * 얼굴 윤곽에서 잰 비율. 모두 크기와 무관한 값이다.
 *
 * <ul>
 *   <li>먼저 두 눈 중심을 잇는 선이 수평이 되도록 회전한다 (고개 기울기를 눈꼬리로 오해하지 않게).
 *   <li>길이는 두 눈 중심 사이 거리(D)로 나눈다 (사진을 얼마나 가까이 찍었는지와 무관).
 *   <li>잴 수 없는 값은 {@link Double#NaN}. 추천에서 그 부위는 건너뛴다.
 * </ul>
 */
public final class FaceMeasurements {

    /** 얼굴 세로 ÷ 가장 넓은 가로 (클수록 긴 얼굴) */
    public double faceAspect = Double.NaN;
    /** 아래턱 폭(세로 85% 지점) ÷ 가장 넓은 가로 (클수록 각진 턱) */
    public double jawRatio = Double.NaN;
    /** 이마 폭(세로 15% 지점) ÷ 가장 넓은 가로 (작을수록 광대가 도드라짐) */
    public double foreheadRatio = Double.NaN;

    /** 눈 가로 ÷ D */
    public double eyeWidth = Double.NaN;
    /** 눈 세로 ÷ 눈 가로 (클수록 동그란 눈) */
    public double eyeOpen = Double.NaN;
    /** 눈꼬리 각도(도). +는 바깥이 올라간 눈, −는 내려간 눈 */
    public double eyeTiltDeg = Double.NaN;

    /** 눈썹 두께 ÷ D */
    public double browThickness = Double.NaN;
    /** 눈썹 아치 높이 ÷ 눈썹 길이 (클수록 둥글게·각지게 올라감) */
    public double browArch = Double.NaN;

    /** 콧볼 폭 ÷ D */
    public double noseWidth = Double.NaN;

    /** 입 폭 ÷ D */
    public double mouthWidth = Double.NaN;
    /** (윗입술 + 아랫입술 두께) ÷ 입 폭 */
    public double lipThickness = Double.NaN;
    /** 윗입술 가운데 패인 깊이(큐피드 활) ÷ 입 폭 */
    public double cupidBow = Double.NaN;

    public static FaceMeasurements measure(FaceContours c) {
        if (c == null || !c.hasCore()) throw new IllegalArgumentException("얼굴 윤곽이 부족해요");
        FaceMeasurements m = new FaceMeasurements();

        double[] le = centroid(c.leftEye), re = centroid(c.rightEye);
        double cx = (le[0] + re[0]) / 2, cy = (le[1] + re[1]) / 2;
        double angle = Math.atan2(re[1] - le[1], re[0] - le[0]);
        double d = Math.hypot(re[0] - le[0], re[1] - le[1]);
        if (d < 1e-3) throw new IllegalArgumentException("두 눈 위치를 찾지 못했어요");
        // 두 눈이 x축 위에 오도록: 회전·이동·크기 정규화 (D = 1)
        Norm n = new Norm(cx, cy, angle, d);

        // 얼굴형
        double[] face = n.apply(c.face);
        double top = minY(face), bottom = maxY(face), height = bottom - top;
        double maxW = 0;
        for (int i = 0; i <= 20; i++) maxW = Math.max(maxW, widthAt(face, top + height * i / 20.0));
        if (maxW > 0 && height > 0) {
            m.faceAspect = height / maxW;
            m.jawRatio = widthAt(face, top + height * 0.85) / maxW;
            m.foreheadRatio = widthAt(face, top + height * 0.15) / maxW;
        }

        // 눈 (두 눈 평균). 얼굴 가운데(x=0)에 가까운 끝이 안쪽 눈꼬리.
        double[] l = eye(n.apply(c.leftEye)), r = eye(n.apply(c.rightEye));
        m.eyeWidth = (l[0] + r[0]) / 2;
        m.eyeOpen = (l[1] / l[0] + r[1] / r[0]) / 2;
        m.eyeTiltDeg = (l[2] + r[2]) / 2;

        // 눈썹
        if (FaceContours.ok(c.leftBrowTop, 3) && FaceContours.ok(c.leftBrowBottom, 3)
                && FaceContours.ok(c.rightBrowTop, 3) && FaceContours.ok(c.rightBrowBottom, 3)) {
            double[] lt = n.apply(c.leftBrowTop), lb = n.apply(c.leftBrowBottom);
            double[] rt = n.apply(c.rightBrowTop), rb = n.apply(c.rightBrowBottom);
            m.browThickness = ((meanY(lb) - meanY(lt)) + (meanY(rb) - meanY(rt))) / 2;
            m.browArch = (arch(lt) + arch(rt)) / 2;
        }

        // 코
        if (FaceContours.ok(c.noseBottom, 3)) {
            double[] nb = n.apply(c.noseBottom);
            m.noseWidth = maxX(nb) - minX(nb);
        }

        // 입
        if (FaceContours.ok(c.upperLipTop, 5) && FaceContours.ok(c.upperLipBottom, 3)
                && FaceContours.ok(c.lowerLipTop, 3) && FaceContours.ok(c.lowerLipBottom, 3)) {
            double[] ut = n.apply(c.upperLipTop), ub = n.apply(c.upperLipBottom);
            double[] lt = n.apply(c.lowerLipTop), lb = n.apply(c.lowerLipBottom);
            double width = maxX(ut) - minX(ut);
            double mid = (maxX(ut) + minX(ut)) / 2;
            if (width > 0) {
                m.mouthWidth = width;
                double upper = yNear(ub, mid) - yNear(ut, mid);
                double lower = yNear(lb, mid) - yNear(lt, mid);
                m.lipThickness = Math.max(0, upper + lower) / width;
                // 윗입술 윗선: 가운데가 양쪽 봉우리보다 얼마나 내려가 있나
                double peak = Double.MAX_VALUE;
                for (int i = 0; i < ut.length; i += 2) {
                    if (Math.abs(ut[i] - mid) < width * 0.3 && Math.abs(ut[i] - mid) > width * 0.04) {
                        peak = Math.min(peak, ut[i + 1]);
                    }
                }
                if (peak != Double.MAX_VALUE) m.cupidBow = Math.max(0, yNear(ut, mid) - peak) / width;
            }
        }
        return m;
    }

    // ── 기하 도우미 ──

    private static final class Norm {
        final double cx, cy, cos, sin, scale;

        Norm(double cx, double cy, double angle, double d) {
            this.cx = cx;
            this.cy = cy;
            this.cos = Math.cos(-angle);
            this.sin = Math.sin(-angle);
            this.scale = 1.0 / d;
        }

        double[] apply(float[] pts) {
            double[] out = new double[pts.length];
            for (int i = 0; i + 1 < pts.length; i += 2) {
                double x = pts[i] - cx, y = pts[i + 1] - cy;
                out[i] = (x * cos - y * sin) * scale;
                out[i + 1] = (x * sin + y * cos) * scale;
            }
            return out;
        }
    }

    static double[] centroid(float[] p) {
        double x = 0, y = 0;
        int n = p.length / 2;
        for (int i = 0; i < n; i++) {
            x += p[2 * i];
            y += p[2 * i + 1];
        }
        return new double[] {x / n, y / n};
    }

    /** {가로, 세로, 눈꼬리 각도(도)} */
    private static double[] eye(double[] p) {
        int inner = 0, outer = 0;
        for (int i = 0; i < p.length; i += 2) {
            if (Math.abs(p[i]) < Math.abs(p[inner])) inner = i;
            if (Math.abs(p[i]) > Math.abs(p[outer])) outer = i;
        }
        double w = maxX(p) - minX(p);
        double h = maxY(p) - minY(p);
        double dx = Math.abs(p[outer] - p[inner]);
        double dy = p[inner + 1] - p[outer + 1]; // y가 아래로 커지므로, 바깥이 위면 +
        return new double[] {w, h, Math.toDegrees(Math.atan2(dy, dx))};
    }

    /** 양 끝을 잇는 선에서 가장 위로 솟은 거리 ÷ 길이 */
    private static double arch(double[] top) {
        int first = 0, last = top.length - 2;
        if (top[first] > top[last]) {
            int t = first;
            first = last;
            last = t;
        }
        double x0 = top[first], y0 = top[first + 1], x1 = top[last], y1 = top[last + 1];
        double len = Math.hypot(x1 - x0, y1 - y0);
        if (len < 1e-6) return Double.NaN;
        double best = 0;
        for (int i = 0; i < top.length; i += 2) {
            // 선보다 위(y 작음)에 있는 거리
            double cross = ((x1 - x0) * (top[i + 1] - y0) - (y1 - y0) * (top[i] - x0)) / len;
            best = Math.max(best, -cross);
        }
        return best / len;
    }

    /** 다각형(닫힌 윤곽)을 높이 y에서 자른 폭 */
    static double widthAt(double[] poly, double y) {
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        int n = poly.length / 2;
        for (int i = 0; i < n; i++) {
            double x0 = poly[2 * i], y0 = poly[2 * i + 1];
            double x1 = poly[2 * ((i + 1) % n)], y1 = poly[2 * ((i + 1) % n) + 1];
            if ((y0 <= y && y <= y1) || (y1 <= y && y <= y0)) {
                double x = y1 == y0 ? x0 : x0 + (x1 - x0) * (y - y0) / (y1 - y0);
                lo = Math.min(lo, x);
                hi = Math.max(hi, x);
            }
        }
        return hi > lo ? hi - lo : 0;
    }

    private static double yNear(double[] p, double x) {
        int best = 0;
        for (int i = 0; i < p.length; i += 2) if (Math.abs(p[i] - x) < Math.abs(p[best] - x)) best = i;
        return p[best + 1];
    }

    private static double meanY(double[] p) {
        double s = 0;
        for (int i = 1; i < p.length; i += 2) s += p[i];
        return s / (p.length / 2);
    }

    private static double minX(double[] p) {
        double v = Double.MAX_VALUE;
        for (int i = 0; i < p.length; i += 2) v = Math.min(v, p[i]);
        return v;
    }

    private static double maxX(double[] p) {
        double v = -Double.MAX_VALUE;
        for (int i = 0; i < p.length; i += 2) v = Math.max(v, p[i]);
        return v;
    }

    private static double minY(double[] p) {
        double v = Double.MAX_VALUE;
        for (int i = 1; i < p.length; i += 2) v = Math.min(v, p[i]);
        return v;
    }

    private static double maxY(double[] p) {
        double v = -Double.MAX_VALUE;
        for (int i = 1; i < p.length; i += 2) v = Math.max(v, p[i]);
        return v;
    }
}
