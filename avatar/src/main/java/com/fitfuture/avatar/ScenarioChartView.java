package com.fitfuture.avatar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import com.fitfuture.avatar.core.WeightSimulator;

import java.util.Locale;

/**
 * A(현재 유지)와 B(대안 실천)의 기간별 체중 변화 선 그래프. 세로축은 "현재 체중 = 100" 지수.
 * 외부 라이브러리 없이 그렸다. 앱에서 MPAndroidChart를 쓰기로 하면 같은 데이터로 바꿔 그리면 된다.
 */
public class ScenarioChartView extends View {

    private static final int COLOR_A = 0xFF232323;
    private static final int COLOR_B = 0xFF2B8471;
    private static final int COLOR_SELECTED = 0xFF82E2CE;
    private static final int COLOR_GRID = 0xFFC9C4BB;
    private static final int COLOR_TEXT = 0xFF5F5F5F;

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final DashPathEffect dash;
    private final Path path = new Path();
    private final float dp;

    private WeightSimulator.Comparison data;
    private int selectedMonths = 6;

    public ScenarioChartView(Context context) {
        this(context, null);
    }

    public ScenarioChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(3 * dp);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        dot.setStyle(Paint.Style.FILL);
        grid.setStyle(Paint.Style.STROKE);
        text.setTextSize(11 * dp);
        text.setColor(COLOR_TEXT);
        dash = new DashPathEffect(new float[]{6 * dp, 5 * dp}, 0);
    }

    public void setComparison(WeightSimulator.Comparison data, int selectedMonths) {
        this.data = data;
        this.selectedMonths = selectedMonths;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(resolveSize(Math.round(300 * dp), widthMeasureSpec),
                resolveSize(Math.round(180 * dp), heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (data == null) return;

        int n = WeightSimulator.PERIOD_MONTHS.length;
        float[] months = new float[n + 1];
        float[] ya = new float[n + 1];
        float[] yb = new float[n + 1];
        ya[0] = 100f;
        yb[0] = 100f;
        for (int i = 0; i < n; i++) {
            months[i + 1] = WeightSimulator.PERIOD_MONTHS[i];
            ya[i + 1] = (float) (100.0 * (1 + data.a[i].changeRate));
            yb[i + 1] = (float) (100.0 * (1 + data.b[i].changeRate));
        }
        float min = 100f, max = 100f;
        for (int i = 0; i <= n; i++) {
            min = Math.min(min, Math.min(ya[i], yb[i]));
            max = Math.max(max, Math.max(ya[i], yb[i]));
        }
        float span = Math.max(2f, max - min);
        min -= span * 0.15f;
        max += span * 0.15f;

        float left = 40 * dp, right = getWidth() - 16 * dp;
        float top = 30 * dp, bottom = getHeight() - 26 * dp;
        float maxMonth = months[n];

        grid.setColor(COLOR_GRID);
        grid.setStrokeWidth(dp);
        grid.setPathEffect(dash);
        float y100 = mapY(100f, min, max, top, bottom);
        canvas.drawLine(left, y100, right, y100, grid);
        grid.setPathEffect(null);

        text.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("100", left - 6 * dp, y100 + 4 * dp, text);
        canvas.drawText(fmt(max), left - 6 * dp, top + 4 * dp, text);
        canvas.drawText(fmt(min), left - 6 * dp, bottom + 4 * dp, text);

        text.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i <= n; i++) {
            float x = left + (right - left) * months[i] / maxMonth;
            boolean selected = i > 0 && (int) months[i] == selectedMonths;
            text.setFakeBoldText(selected);
            canvas.drawText(i == 0 ? "현재" : (int) months[i] + "개월", x, getHeight() - 8 * dp, text);
            if (selected) {
                grid.setColor(COLOR_SELECTED);
                grid.setStrokeWidth(2 * dp);
                canvas.drawLine(x, top, x, bottom, grid);
            }
        }
        text.setFakeBoldText(false);

        drawSeries(canvas, months, ya, COLOR_A, min, max, left, right, top, bottom, maxMonth);
        drawSeries(canvas, months, yb, COLOR_B, min, max, left, right, top, bottom, maxMonth);

        text.setTextAlign(Paint.Align.LEFT);
        float ly = 14 * dp;
        dot.setColor(COLOR_A);
        canvas.drawCircle(left + 5 * dp, ly - 4 * dp, 5 * dp, dot);
        canvas.drawText("A 현재 유지", left + 14 * dp, ly, text);
        dot.setColor(COLOR_B);
        canvas.drawCircle(left + 105 * dp, ly - 4 * dp, 5 * dp, dot);
        canvas.drawText("B 대안 실천", left + 114 * dp, ly, text);
    }

    private void drawSeries(Canvas canvas, float[] months, float[] ys, int color, float min, float max,
                            float left, float right, float top, float bottom, float maxMonth) {
        path.reset();
        for (int i = 0; i < months.length; i++) {
            float x = left + (right - left) * months[i] / maxMonth;
            float y = mapY(ys[i], min, max, top, bottom);
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        line.setColor(color);
        canvas.drawPath(path, line);
        dot.setColor(color);
        for (int i = 0; i < months.length; i++) {
            canvas.drawCircle(left + (right - left) * months[i] / maxMonth,
                    mapY(ys[i], min, max, top, bottom), 4 * dp, dot);
        }
    }

    private static float mapY(float v, float min, float max, float top, float bottom) {
        return bottom - (v - min) / (max - min) * (bottom - top);
    }

    private static String fmt(float v) {
        return String.format(Locale.KOREA, "%.1f", v);
    }
}
