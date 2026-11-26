package com.fitfuture.avatar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import com.fitfuture.avatar.core.AvatarCatalog.Category;
import com.fitfuture.avatar.core.AvatarLayers;
import com.fitfuture.avatar.core.AvatarSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * 파츠 고르기 타일용 썸네일. 지금 만드는 아바타에 후보 파츠를 입힌 모습을
 * 해당 부위만 잘라서 보여준다 (눈이면 눈 주변, 헤어면 머리, 옷이면 몸통).
 * 레이어와 캐시는 {@link AvatarLayerView}와 같은 것을 쓴다.
 */
public class PartThumbView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final List<Bitmap> layers = new ArrayList<>();
    private final RectF dst = new RectF();
    private final Rect src = new Rect();
    private float[] crop = REGION_FACE;

    // 전신 캔버스(320×790) 안에서 잘라 볼 영역 {left, top, right, bottom}
    static final float[] REGION_FACE = {70, 55, 250, 225};
    static final float[] REGION_EYES = {96, 92, 224, 168};
    static final float[] REGION_NOSE = {120, 128, 200, 178};
    static final float[] REGION_MOUTH = {120, 150, 200, 200};
    static final float[] REGION_HAIR = {55, 15, 265, 305};
    static final float[] REGION_OUTFIT = {0, 185, 320, 465};

    public PartThumbView(Context context) {
        super(context);
    }

    public static float[] region(Category c) {
        switch (c) {
            case EYES:
            case BROWS:
            case ACCESSORY:
                return REGION_EYES;
            case NOSE:
                return REGION_NOSE;
            case MOUTH:
                return REGION_MOUTH;
            case HAIR:
            case HAIR_BACK:
                return REGION_HAIR;
            case OUTFIT:
                return REGION_OUTFIT;
            case FACE:
            default:
                return REGION_FACE;
        }
    }

    /** spec: 후보 파츠를 입힌 아바타, category: 어느 부위를 잘라 볼지 */
    public void setPart(AvatarSpec spec, int cell, int cheekLevel, Category category) {
        crop = region(category);
        layers.clear();
        AvatarAssets assets = AvatarAssets.get(getContext());
        for (String path : AvatarLayers.paths(spec, cell, cheekLevel)) {
            Bitmap b = assets.load(path);
            if (b != null) layers.add(b);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (layers.isEmpty()) return;
        float cw = crop[2] - crop[0], ch = crop[3] - crop[1];
        float s = Math.min(getWidth() / cw, getHeight() / ch);
        float w = cw * s, h = ch * s;
        dst.set((getWidth() - w) / 2f, (getHeight() - h) / 2f, (getWidth() + w) / 2f, (getHeight() + h) / 2f);
        for (Bitmap b : layers) {
            // PNG가 1배(320 폭)든 2배든 같은 영역을 잘라낸다
            float k = b.getWidth() / (float) AvatarLayers.CANVAS_WIDTH;
            src.set(Math.round(crop[0] * k), Math.round(crop[1] * k),
                    Math.round(crop[2] * k), Math.round(crop[3] * k));
            canvas.drawBitmap(b, src, dst, paint);
        }
    }
}
