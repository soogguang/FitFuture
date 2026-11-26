package com.fitfuture.avatar.face;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.media.ExifInterface;
import android.net.Uri;

import com.fitfuture.avatar.core.AvatarSpec;
import com.fitfuture.avatar.core.face.FaceContours;
import com.fitfuture.avatar.core.face.FaceMeasurements;
import com.fitfuture.avatar.core.face.FaceNorms;
import com.fitfuture.avatar.core.face.PartRecommender;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceContour;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 셀카 → 얼굴 윤곽(ML Kit, 기기 안에서 실행) → 측정 → 닮은 파츠 초안.
 *
 * <p>사진은 메모리에서만 쓰고 저장하지 않는다. {@link #analyze}는 오래 걸리므로 작업 스레드에서 호출한다.
 * 얼굴 감지 모델은 학습된 ML Kit 모델을 그대로 쓰고, 우리는 측정·파츠 선택 규칙만 만든다.
 */
public final class SelfieAnalyzer {

    /** 고개가 이보다 많이 돌아가면 다시 찍도록 안내 (도) */
    public static final float MAX_TURN_DEG = 15f;
    /** 얼굴 폭이 사진 폭의 이 비율보다 작으면 가까이 찍도록 안내 */
    public static final float MIN_FACE_WIDTH = 0.2f;

    public static final class Outcome {
        public final boolean ok;
        /** 화면에 그대로 보여줄 문구 (실패 이유 또는 참고 안내) */
        public final String message;
        public final PartRecommender.Result result;
        public final FaceMeasurements measurements;

        Outcome(boolean ok, String message, PartRecommender.Result result, FaceMeasurements m) {
            this.ok = ok;
            this.message = message;
            this.result = result;
            this.measurements = m;
        }

        static Outcome fail(String message) {
            return new Outcome(false, message, null, null);
        }
    }

    private final FaceDetector detector;

    public SelfieAnalyzer() {
        detector = FaceDetection.getClient(new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build());
    }

    /** 작업 스레드에서 호출. 예외를 던지지 않고 결과(성공/실패 문구)를 돌려준다. */
    public Outcome analyze(Context context, Uri photo, AvatarSpec base, FaceNorms norms) {
        Bitmap bitmap = null;
        try {
            bitmap = load(context, photo, 1280);
            List<Face> faces = Tasks.await(
                    detector.process(InputImage.fromBitmap(bitmap, 0)), 20, TimeUnit.SECONDS);
            if (faces == null || faces.isEmpty()) {
                return Outcome.fail("얼굴을 찾지 못했어요. 얼굴이 잘 보이는 정면 사진으로 다시 시도해 주세요.");
            }
            // 윤곽은 가장 크게 나온 얼굴에만 나온다
            Face face = null;
            for (Face f : faces) if (f.getContour(FaceContour.FACE) != null) face = f;
            if (face == null) {
                return Outcome.fail("얼굴 윤곽을 읽지 못했어요. 밝은 곳에서 정면으로 다시 찍어 주세요.");
            }
            if (Math.abs(face.getHeadEulerAngleY()) > MAX_TURN_DEG
                    || Math.abs(face.getHeadEulerAngleX()) > MAX_TURN_DEG) {
                return Outcome.fail("고개가 돌아가 있어요. 카메라를 정면으로 보고 다시 찍어 주세요.");
            }
            if (face.getBoundingBox().width() < bitmap.getWidth() * MIN_FACE_WIDTH) {
                return Outcome.fail("얼굴이 너무 작게 나왔어요. 얼굴이 크게 나오도록 가까이에서 찍어 주세요.");
            }
            Float le = face.getLeftEyeOpenProbability(), re = face.getRightEyeOpenProbability();
            if (le != null && re != null && (le < 0.4f || re < 0.4f)) {
                return Outcome.fail("눈을 감은 것 같아요. 눈을 뜬 사진으로 다시 시도해 주세요.");
            }

            FaceMeasurements m = FaceMeasurements.measure(contours(face));
            List<String> notes = new ArrayList<>();
            Float smile = face.getSmilingProbability();
            if (smile != null && smile > 0.7f) {
                // 웃으면 입 폭·입술 두께가 크게 달라져서 입은 추천하지 않고 지금 것을 둔다
                m.mouthWidth = Double.NaN;
                m.lipThickness = Double.NaN;
                m.cupidBow = Double.NaN;
                notes.add("웃는 사진이라 입은 바꾸지 않았어요. 입을 다문 사진이면 입도 골라 드려요.");
            }
            PartRecommender.Result r = PartRecommender.recommend(m, norms, base);
            if (faces.size() > 1) notes.add("여러 명이 있어서 가장 크게 나온 얼굴로 골랐어요.");
            return new Outcome(true, String.join(" ", notes), r, m);
        } catch (IOException e) {
            return Outcome.fail("사진을 열지 못했어요. 다른 사진으로 시도해 주세요.");
        } catch (IllegalArgumentException e) {
            return Outcome.fail("얼굴 윤곽이 충분하지 않아요. 정면 사진으로 다시 시도해 주세요.");
        } catch (Exception e) {
            return Outcome.fail("사진을 분석하지 못했어요. 잠시 뒤 다시 시도해 주세요.");
        } finally {
            if (bitmap != null) bitmap.recycle(); // 사진은 남기지 않는다
        }
    }

    /** 화면을 닫을 때 호출 */
    public void close() {
        detector.close();
    }

    static FaceContours contours(Face f) {
        FaceContours c = new FaceContours();
        c.face = pts(f, FaceContour.FACE);
        c.leftEye = pts(f, FaceContour.LEFT_EYE);
        c.rightEye = pts(f, FaceContour.RIGHT_EYE);
        c.leftBrowTop = pts(f, FaceContour.LEFT_EYEBROW_TOP);
        c.leftBrowBottom = pts(f, FaceContour.LEFT_EYEBROW_BOTTOM);
        c.rightBrowTop = pts(f, FaceContour.RIGHT_EYEBROW_TOP);
        c.rightBrowBottom = pts(f, FaceContour.RIGHT_EYEBROW_BOTTOM);
        c.noseBridge = pts(f, FaceContour.NOSE_BRIDGE);
        c.noseBottom = pts(f, FaceContour.NOSE_BOTTOM);
        c.upperLipTop = pts(f, FaceContour.UPPER_LIP_TOP);
        c.upperLipBottom = pts(f, FaceContour.UPPER_LIP_BOTTOM);
        c.lowerLipTop = pts(f, FaceContour.LOWER_LIP_TOP);
        c.lowerLipBottom = pts(f, FaceContour.LOWER_LIP_BOTTOM);
        return c;
    }

    private static float[] pts(Face f, int type) {
        FaceContour contour = f.getContour(type);
        if (contour == null) return null;
        List<PointF> list = contour.getPoints();
        float[] out = new float[list.size() * 2];
        for (int i = 0; i < list.size(); i++) {
            out[2 * i] = list.get(i).x;
            out[2 * i + 1] = list.get(i).y;
        }
        return out;
    }

    /** 긴 변 maxSize 이하로 줄여 읽고, 휴대폰 사진의 회전(EXIF)을 바로잡는다. */
    static Bitmap load(Context context, Uri uri, int maxSize) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("no stream");
            BitmapFactory.decodeStream(in, null, bounds);
        }
        int longest = Math.max(bounds.outWidth, bounds.outHeight);
        if (longest <= 0) throw new IOException("not an image");
        int sample = 1;
        while (longest / (sample * 2) >= maxSize) sample *= 2;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bmp;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("no stream");
            bmp = BitmapFactory.decodeStream(in, null, opts);
        }
        if (bmp == null) throw new IOException("decode failed");

        int degrees = 0;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in != null) {
                int o = new ExifInterface(in).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                degrees = o == ExifInterface.ORIENTATION_ROTATE_90 ? 90
                        : o == ExifInterface.ORIENTATION_ROTATE_180 ? 180
                        : o == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
            }
        } catch (IOException ignored) {
            // EXIF가 없으면 그대로
        }
        if (degrees != 0) {
            Matrix m = new Matrix();
            m.postRotate(degrees);
            Bitmap rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
            if (rotated != bmp) bmp.recycle();
            bmp = rotated;
        }
        return bmp;
    }
}
