package com.fitfuture.app.ui;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import com.fitfuture.app.R;
import java.util.*;

/** Native vector chart, never a screenshot. */
public final class GraphView extends View {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final boolean bars;
  private double[] current = {0, .01, .025, .045}, alternative = {0, 0, -.025, -.05}, values = {};
  private String[] labels = {"현재", "1개월", "3개월", "6개월"};

  public GraphView(Context c, boolean bars) {
    super(c);
    this.bars = bars;
    setContentDescription(bars ? "날짜별 섭취 열량 그래프" : "현재 식단과 선택 대안의 미래 체중 변화 비교 그래프");
  }

  public void series(double[] current, double[] alternative) {
    this.current = current;
    this.alternative = alternative;
    invalidate();
  }

  public void bars(double[] values, String[] labels) {
    this.values = values;
    this.labels = labels;
    invalidate();
  }

  private float d(float n) {
    return n * getResources().getDisplayMetrics().density;
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    float w = getWidth(),
        h = getHeight(),
        left = d(22),
        right = w - d(14),
        top = d(8),
        bottom = h - d(30);
    paint.setStrokeWidth(d(1));
    paint.setColor(getContext().getColor(R.color.line));
    if (!bars) {
      for (int i = 0; i < 3; i++) {
        float y = top + (bottom - top) * i / 2;
        c.drawLine(left, y, right, y, paint);
      }
      for (int i = 0; i < 4; i++) {
        float x = left + (right - left) * i / 3;
        paint.setPathEffect(new DashPathEffect(new float[] {d(2), d(3)}, 0));
        c.drawLine(x, top, x, bottom, paint);
      }
      paint.setPathEffect(null);
      double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
      for (double[] data : new double[][] {current, alternative})
        for (double v : data) {
          min = Math.min(min, v);
          max = Math.max(max, v);
        }
      double margin = Math.max(.02, (max - min) * .2);
      min -= margin;
      max += margin;
      line(c, current, left, right, top, bottom, min, max, R.color.text);
      line(c, alternative, left, right, top, bottom, min, max, R.color.primary);
    } else {
      double max = 1;
      for (double v : values) max = Math.max(max, v);
      float segment = (right - left) / Math.max(1, values.length);
      for (int i = 0; i < values.length; i++) {
        paint.setColor(
            getContext().getColor(i == values.length - 1 ? R.color.primary : R.color.carbs));
        float height = (float) (values[i] / max) * (bottom - top);
        c.drawRoundRect(
            left + i * segment + segment * .25f,
            bottom - height,
            left + i * segment + segment * .75f,
            bottom,
            d(4),
            d(4),
            paint);
      }
    }
    paint.setTextSize(d(9));
    paint.setTypeface(Typeface.DEFAULT);
    paint.setTextAlign(Paint.Align.CENTER);
    paint.setColor(getContext().getColor(R.color.muted));
    for (int i = 0; i < labels.length; i++) {
      float x =
          bars
              ? left + (right - left) * (i + .5f) / Math.max(1, labels.length)
              : left + (right - left) * i / Math.max(1, labels.length - 1);
      c.drawText(labels[i], x, h - d(8), paint);
    }
  }

  private void line(
      Canvas c,
      double[] values,
      float left,
      float right,
      float top,
      float bottom,
      double min,
      double max,
      int color) {
    Path p = new Path();
    for (int i = 0; i < values.length; i++) {
      float x = left + (right - left) * i / Math.max(1, values.length - 1),
          y = bottom - (float) ((values[i] - min) / (max - min)) * (bottom - top);
      if (i == 0) p.moveTo(x, y);
      else p.lineTo(x, y);
    }
    paint.setColor(getContext().getColor(color));
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(d(2));
    c.drawPath(p, paint);
    paint.setStyle(Paint.Style.FILL);
  }
}
