package com.fitfuture.app.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Wraps chips rather than truncating text at 320dp or with large accessibility fonts. */
public final class FlowLayout extends ViewGroup {
  private final int gap;

  public FlowLayout(Context c) {
    super(c);
    gap = Math.round(6 * c.getResources().getDisplayMetrics().density);
  }

  protected void onMeasure(int widthSpec, int heightSpec) {
    int width = MeasureSpec.getSize(widthSpec), x = 0, y = 0, line = 0;
    for (int i = 0; i < getChildCount(); i++) {
      View v = getChildAt(i);
      measureChild(v, widthSpec, heightSpec);
      int w = v.getMeasuredWidth(), h = v.getMeasuredHeight();
      if (x + w > width && x > 0) {
        x = 0;
        y += line + gap;
        line = 0;
      }
      x += w + gap;
      line = Math.max(line, h);
    }
    setMeasuredDimension(width, resolveSize(y + line, heightSpec));
  }

  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    int x = 0, y = 0, line = 0;
    for (int i = 0; i < getChildCount(); i++) {
      View v = getChildAt(i);
      int w = v.getMeasuredWidth(), h = v.getMeasuredHeight();
      if (x + w > r - l && x > 0) {
        x = 0;
        y += line + gap;
        line = 0;
      }
      v.layout(x, y, x + w, y + h);
      x += w + gap;
      line = Math.max(line, h);
    }
  }

  protected LayoutParams generateDefaultLayoutParams() {
    return new MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
  }
}
