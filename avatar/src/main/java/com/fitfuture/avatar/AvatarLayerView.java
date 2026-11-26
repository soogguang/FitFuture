package com.fitfuture.avatar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.fitfuture.avatar.core.AvatarLayers;
import com.fitfuture.avatar.core.AvatarSpec;
import com.fitfuture.avatar.core.BodyCellMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Paper Cut 파츠 PNG를 겹쳐 전신 아바타를 그리는 View.
 * 편집 미리보기와 A/B 비교 화면이 모두 이 View를 쓴다.
 *
 * <ul>
 *   <li>모든 레이어를 같은 위치(전신 캔버스 320×790의 (0,0))에 겹친다.</li>
 *   <li>발바닥 기준선은 View 아래쪽에 고정. 키에 따라 heightScale(0.8~1.0)만큼 작아진다.</li>
 *   <li>A/B에 같은 heightScale을 주면 두 아바타의 키와 발바닥 높이가 같다.</li>
 * </ul>
 *
 * <pre>
 * view.setAvatar(spec, cell, cheekLevel, heightScale);
 * </pre>
 */
public class AvatarLayerView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF dst = new RectF();
    private final List<Bitmap> layers = new ArrayList<>();

    private AvatarSpec spec;
    private int cell = 4;
    private int cheekLevel = BodyCellMapper.CHEEK_BASE;
    private float heightScale = 1f;

    public AvatarLayerView(Context context) {
        super(context);
    }

    public AvatarLayerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AvatarLayerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * @param cell        그릴 체형 칸 (1~7)
     * @param cheekLevel  볼 윤곽 (−1, 0, +1)
     * @param heightScale 프레임 높이 대비 전신 높이 (BodyCellConfig.heightScale)
     */
    public void setAvatar(AvatarSpec spec, int cell, int cheekLevel, double heightScale) {
        this.spec = spec;
        this.cell = cell;
        this.cheekLevel = cheekLevel;
        this.heightScale = (float) Math.max(0.1, Math.min(1.0, heightScale));
        reload();
        setContentDescription("아바타 체형 " + cell + "칸");
        invalidate();
    }

    private void reload() {
        layers.clear();
        if (spec == null) return;
        AvatarAssets assets = AvatarAssets.get(getContext());
        for (String path : AvatarLayers.paths(spec, cell, cheekLevel)) {
            Bitmap b = assets.load(path);
            if (b != null) layers.add(b);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float d = getResources().getDisplayMetrics().density;
        int w = resolveSize(Math.round(128 * d) + getPaddingLeft() + getPaddingRight(), widthMeasureSpec);
        int h = resolveSize(Math.round(316 * d) + getPaddingTop() + getPaddingBottom(), heightMeasureSpec);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (layers.isEmpty()) return;
        float availW = getWidth() - getPaddingLeft() - getPaddingRight();
        float availH = getHeight() - getPaddingTop() - getPaddingBottom();
        if (availW <= 0 || availH <= 0) return;

        // 프레임에 꼭 맞는 크기 × 키 비율
        float fit = Math.min(availW / AvatarLayers.CANVAS_WIDTH, availH / AvatarLayers.CANVAS_HEIGHT);
        float s = fit * heightScale;
        float w = AvatarLayers.CANVAS_WIDTH * s;
        float h = AvatarLayers.CANVAS_HEIGHT * s;
        float left = getPaddingLeft() + (availW - w) / 2f;
        float bottom = getHeight() - getPaddingBottom(); // 발바닥 기준선
        dst.set(left, bottom - h, left + w, bottom);

        // PNG가 1배(320×790)든 2배(640×1580)든 같은 칸에 맞춰 그린다
        for (Bitmap b : layers) {
            canvas.drawBitmap(b, null, dst, paint);
        }
    }
}
