package com.fitfuture.app.ui;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import com.fitfuture.app.R;
import com.fitfuture.app.domain.Models.AvatarStage;
import com.fitfuture.avatar.AvatarLayerView;
import com.fitfuture.avatar.core.AvatarSpec;
import java.util.*;
import java.util.function.Consumer;

/**
 * Shared XML-backed components; dimensions match the 360dp design without fixed canvas positioning.
 */
public final class Ui {
  public final Activity a;

  public Ui(Activity activity) {
    a = activity;
  }

  public int dp(float n) {
    return Math.round(n * a.getResources().getDisplayMetrics().density);
  }

  public int color(int resource) {
    return a.getColor(resource);
  }

  public LinearLayout column() {
    LinearLayout v = new LinearLayout(a);
    v.setOrientation(LinearLayout.VERTICAL);
    v.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
    return v;
  }

  public LinearLayout row() {
    LinearLayout v = new LinearLayout(a);
    v.setOrientation(LinearLayout.HORIZONTAL);
    v.setGravity(Gravity.CENTER_VERTICAL);
    v.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
    return v;
  }

  public void add(LinearLayout parent, View child) {
    parent.addView(child);
  }

  public void gap(LinearLayout parent, int height) {
    View v = new View(a);
    parent.addView(v, new LinearLayout.LayoutParams(1, dp(height)));
  }

  public void flex(LinearLayout parent) {
    View v = new View(a);
    parent.addView(v, new LinearLayout.LayoutParams(1, 0, 1));
  }

  public void height(View v, int h) {
    ViewGroup.LayoutParams p = v.getLayoutParams();
    if (p == null) p = new LinearLayout.LayoutParams(-1, dp(h));
    p.height = dp(h);
    v.setLayoutParams(p);
  }

  public void weight(LinearLayout p, View v) {
    p.addView(v, new LinearLayout.LayoutParams(0, -2, 1));
  }

  public TextView text(String value, int size, int c, boolean bold) {
    TextView t = new TextView(a);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(color(c));
    t.setIncludeFontPadding(false);
    t.setLineSpacing(dp(3), 1);
    if (bold) t.setTypeface(null, Typeface.BOLD);
    t.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
    return t;
  }

  public TextView body(LinearLayout p, String text) {
    TextView t = text(text, 14, R.color.text, false);
    p.addView(t);
    return t;
  }

  public TextView title(LinearLayout p, String text) {
    TextView t = text(text, 22, R.color.text, true);
    p.addView(t);
    gap(p, 8);
    return t;
  }

  public TextView section(LinearLayout p, String text) {
    TextView t = text(text, 16, R.color.text, true);
    p.addView(t);
    gap(p, 12);
    return t;
  }

  public TextView muted(LinearLayout p, String text) {
    TextView t = text(text, 12, R.color.muted, false);
    p.addView(t);
    return t;
  }

  public TextView small(LinearLayout p, String text) {
    TextView t = text(text, 11, R.color.muted, false);
    p.addView(t);
    return t;
  }

  public void heading(LinearLayout p, String title, String subtitle) {
    title(p, title);
    if (!subtitle.isEmpty()) {
      muted(p, subtitle);
      gap(p, 16);
    } else gap(p, 4);
  }

  public LinearLayout card(LinearLayout p) {
    LinearLayout c =
        (LinearLayout) a.getLayoutInflater().inflate(R.layout.component_card, p, false);
    p.addView(c);
    return c;
  }

  public void background(View view, int res) {
    view.setBackgroundResource(res);
  }

  public Button button(LinearLayout p, String label, Runnable click) {
    Button b = (Button) a.getLayoutInflater().inflate(R.layout.component_button, p, false);
    b.setText(label);
    b.setOnClickListener(v -> click.run());
    p.addView(b);
    return b;
  }

  public Button outline(LinearLayout p, String label, Runnable click) {
    Button b = button(p, label, click);
    b.setBackgroundResource(R.drawable.bg_outline);
    b.setTextColor(color(R.color.primary));
    return b;
  }

  public TextView link(LinearLayout p, String text, Runnable click) {
    TextView t = text(text, 14, R.color.primary, true);
    t.setGravity(Gravity.CENTER);
    t.setMinHeight(dp(48));
    t.setGravity(Gravity.CENTER);
    t.setOnClickListener(v -> click.run());
    p.addView(t);
    return t;
  }

  public EditText input(
      LinearLayout p, String label, String value, String hint, Consumer<String> changed) {
    if (!label.isEmpty()) {
      TextView l = text(label, 12, R.color.text, true);
      p.addView(l);
      gap(p, 6);
    }
    EditText e = (EditText) a.getLayoutInflater().inflate(R.layout.component_input, p, false);
    e.setImeHintLocales(android.os.LocaleList.forLanguageTags("ko-KR,en-US"));
    e.setText(value);
    e.setHint(hint);
    e.setContentDescription(label.isEmpty() ? hint : label);
    p.addView(e);
    if (changed != null)
      e.addTextChangedListener(
          new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int aft) {}

            public void onTextChanged(CharSequence s, int st, int before, int count) {
              changed.accept(s.toString());
            }

            public void afterTextChanged(Editable e) {}
          });
    return e;
  }

  public ImageView icon(int drawable, int size, int tint) {
    ImageView im = new ImageView(a);
    im.setImageResource(drawable);
    im.setImageTintList(ColorStateList.valueOf(color(tint)));
    im.setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size)));
    im.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    return im;
  }

  public LinearLayout notice(LinearLayout p, String title, String body, int kind) {
    LinearLayout c = card(p);
    c.setBackgroundResource(
        kind == 1 ? R.drawable.bg_warning : kind == 2 ? R.drawable.bg_error : R.drawable.bg_mint);
    int col =
        kind == 1 ? R.color.warning_text : kind == 2 ? R.color.error_text : R.color.primary_dark;
    LinearLayout r = row();
    r.addView(icon(kind == 0 ? R.drawable.ic_spark : R.drawable.ic_warning, 18, col));
    TextView t = text("  " + title, 14, col, true);
    r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
    c.addView(r);
    if (!body.isEmpty()) {
      gap(c, 8);
      c.addView(text(body, 12, col, false));
    }
    return c;
  }

  public void chips(
      LinearLayout p,
      String[] labels,
      Set<String> selected,
      boolean multiple,
      Consumer<String> onSelect) {
    // A FlowLayout wraps on smaller devices and with large system fonts.
    FlowLayout flow = new FlowLayout(a);
    p.addView(flow, new LinearLayout.LayoutParams(-1, -2));
    for (String label : labels) {
      TextView t = text(label, 12, R.color.primary_dark, true);
      t.setGravity(Gravity.CENTER);
      t.setPadding(dp(18), 0, dp(18), 0);
      t.setMinHeight(dp(30));
      t.setMinWidth(dp(78));
      boolean allergy = selected.contains(label) && Set.of("갑각류", "유제품", "견과류").contains(label);
      t.setBackgroundResource(
          allergy
              ? R.drawable.bg_warning
              : selected.contains(label) ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
      t.setTextColor(
          color(
              allergy
                  ? R.color.warning_text
                  : selected.contains(label) ? R.color.white : R.color.primary_dark));
      flow.addView(t, new ViewGroup.MarginLayoutParams(-2, dp(30)));
      t.setOnClickListener(
          v -> {
            onSelect.accept(label);
          });
    }
  }

  public FrameLayout photo(LinearLayout p, String label, int h, String uri) {
    FrameLayout f = (FrameLayout) a.getLayoutInflater().inflate(R.layout.component_photo, p, false);
    f.setClipToOutline(true);
    height(f, h);
    ((TextView) f.findViewById(R.id.photo_label)).setText(label);
    p.addView(f);
    if (uri != null && !uri.isEmpty())
      try {
        ImageView im = f.findViewById(R.id.photo_image);
        im.setImageBitmap(decodePhoto(Uri.parse(uri)));
        if (im.getDrawable() != null) {
          im.setVisibility(View.VISIBLE);
          f.findViewById(R.id.photo_placeholder).setVisibility(View.GONE);
        }
      } catch (Exception ignored) {
      }
    return f;
  }

  private android.graphics.Bitmap decodePhoto(Uri uri) throws java.io.IOException {
    android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
    options.inJustDecodeBounds = true;
    try (java.io.InputStream stream = a.getContentResolver().openInputStream(uri)) {
      android.graphics.BitmapFactory.decodeStream(stream, null, options);
    }
    options.inSampleSize = 1;
    while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 1600)
      options.inSampleSize *= 2;
    options.inJustDecodeBounds = false;
    android.graphics.Bitmap bitmap;
    try (java.io.InputStream stream = a.getContentResolver().openInputStream(uri)) {
      bitmap = android.graphics.BitmapFactory.decodeStream(stream, null, options);
    }
    if (bitmap == null) throw new java.io.IOException("Invalid photo");
    try (java.io.InputStream stream = a.getContentResolver().openInputStream(uri)) {
      android.media.ExifInterface exif = new android.media.ExifInterface(stream);
      int orientation = exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
      android.graphics.Matrix matrix = new android.graphics.Matrix();
      switch (orientation) {
        case 2:
          matrix.setScale(-1, 1);
          break;
        case 3:
          matrix.setRotate(180);
          break;
        case 4:
          matrix.setScale(1, -1);
          break;
        case 5:
          matrix.setRotate(90);
          matrix.postScale(-1, 1);
          break;
        case 6:
          matrix.setRotate(90);
          break;
        case 7:
          matrix.setRotate(270);
          matrix.postScale(-1, 1);
          break;
        case 8:
          matrix.setRotate(270);
          break;
        default:
          break;
      }
      if (!matrix.isIdentity())
        bitmap =
            android.graphics.Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    } catch (java.io.IOException ignored) {
    }
    return bitmap;
  }

  /**
   * Paper Cut 전신 아바타. 편집 미리보기·미래 비교·홈·기록이 모두 이 렌더러를 쓴다.
   * 발바닥은 아래에 고정되고, heightScale(키)만큼 작아진다.
   */
  public AvatarLayerView figure(
      LinearLayout p, AvatarSpec spec, int cell, double heightScale, int wDp, int hDp) {
    AvatarLayerView v = new AvatarLayerView(a);
    // 볼 윤곽은 체형 칸 구간을 따른다 (얼굴형은 그대로). 어느 화면이든 같은 규칙.
    int cheek = com.fitfuture.app.MainActivity.BODY_CELLS.cheekForCell(cell);
    v.setAvatar(spec, cell, cheek, heightScale);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(wDp), dp(hDp));
    lp.gravity = Gravity.CENTER_HORIZONTAL;
    p.addView(v, lp);
    return v;
  }

  /** 예전 숫자 자리표시 아바타. 새 화면에서는 figure()를 쓴다. */
  public View avatar(LinearLayout p, AvatarStage stage) {
    View v = a.getLayoutInflater().inflate(R.layout.component_avatar, p, false);
    TextView n = v.findViewById(R.id.avatar_stage), cap = v.findViewById(R.id.avatar_caption);
    n.setText(stage.value > 0 ? "+" + stage.value : String.valueOf(stage.value));
    n.setBackgroundResource(
        new int[] {
              R.drawable.avatar_minus_two,
              R.drawable.avatar_minus_one,
              R.drawable.avatar_baseline,
              R.drawable.avatar_plus_one,
              R.drawable.avatar_plus_two
            }
            [stage.value + 2]);
    cap.setText(a.getString(R.string.avatar_caption, (stage.value > 0 ? "+" : "") + stage.value));
    p.addView(v);
    return v;
  }

  public void progress(LinearLayout p, int step) {
    gap(p, 5);
    LinearLayout r = row();
    for (int i = 1; i <= 4; i++) {
      View bar = new View(a);
      bar.setBackgroundTintList(
          ColorStateList.valueOf(color(i <= step ? R.color.primary : R.color.line)));
      bar.setBackgroundResource(R.drawable.bg_chip);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(5), 1);
      lp.setMarginEnd(dp(6));
      r.addView(bar, lp);
    }
    TextView t = text(step + "/4", 12, R.color.primary, true);
    t.setGravity(Gravity.END);
    r.addView(t, new LinearLayout.LayoutParams(dp(60), -2));
    p.addView(r);
    gap(p, 13);
  }

  public void bar(LinearLayout p, double fraction, int tint, int height) {
    ProgressBar v = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
    v.setProgressDrawable(a.getDrawable(R.drawable.progress_track));
    if (android.os.Build.VERSION.SDK_INT >= 29) {
      v.setMinHeight(dp(height));
      v.setMaxHeight(dp(height));
    }
    v.setMax(1000);
    v.setProgress((int) Math.max(0, Math.min(1000, fraction * 1000)));
    v.setProgressTintList(colorStateList(tint));
    v.setProgressBackgroundTintList(colorStateList(R.color.mint_strong));
    p.addView(v, new LinearLayout.LayoutParams(-1, dp(height)));
  }

  private ColorStateList colorStateList(int tint) {
    return ColorStateList.valueOf(color(tint));
  }

  public void menuRow(LinearLayout p, String title, String subtitle, int icon, Runnable action) {
    LinearLayout c = card(p), r = row();
    c.setPadding(dp(14), dp(12), dp(14), dp(12));
    LinearLayout box = column();
    box.setGravity(Gravity.CENTER);
    box.setBackgroundResource(R.drawable.bg_mint);
    box.addView(icon(icon, 22, R.color.primary));
    r.addView(box, new LinearLayout.LayoutParams(dp(36), dp(36)));
    LinearLayout words = column();
    words.setPadding(dp(12), 0, dp(8), 0);
    words.addView(text(title, 16, R.color.text, true));
    if (!subtitle.isEmpty()) {
      gap(words, 4);
      muted(words, subtitle);
    }
    r.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
    r.addView(icon(R.drawable.ic_chevron, 16, R.color.muted));
    c.addView(r);
    c.setOnClickListener(v -> action.run());
    gap(p, 12);
  }

  public static String number(double v) {
    return Math.abs(v - Math.round(v)) < 0.05
        ? String.valueOf(Math.round(v))
        : String.format(Locale.KOREA, "%.1f", v);
  }
}
