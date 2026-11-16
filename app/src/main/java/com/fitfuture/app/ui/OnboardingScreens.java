package com.fitfuture.app.ui;

import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import com.fitfuture.app.MainActivity;
import com.fitfuture.app.R;
import com.fitfuture.app.domain.Models.*;
import java.util.*;

public final class OnboardingScreens {
  private final MainActivity a;
  private final Ui u;

  public OnboardingScreens(MainActivity a) {
    this.a = a;
    u = a.ui;
  }

  public void render(String screen) {
    switch (screen) {
      case "s01":
        splash();
        break;
      case "s02":
        intro();
        break;
      case "s03":
        basic();
        break;
      case "s04":
        goal();
        break;
      case "s05":
        preferences(false);
        break;
      case "s06":
        budget();
        break;
      case "s07":
        avatar();
        break;
    }
  }

  private LinearLayout s(String n) {
    return a.slot(n);
  }

  private void splash() {
    LinearLayout p = s("brand");
    p.setGravity(Gravity.CENTER);
    p.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout logo = u.column();
    logo.setGravity(Gravity.CENTER);
    logo.setBackgroundResource(R.drawable.bg_primary);
    logo.addView(u.icon(R.drawable.ic_spark, 42, R.color.white));
    p.addView(logo, new LinearLayout.LayoutParams(u.dp(84), u.dp(84)));
    u.gap(p, 14);
    TextView title = u.text("FitFuture", 28, R.color.primary_dark, true);
    title.setGravity(Gravity.CENTER);
    p.addView(title);
    u.gap(p, 12);
    TextView sub = u.text("미래의 나를 위한 오늘의 한 끼", 14, R.color.primary_dark, true);
    sub.setGravity(Gravity.CENTER);
    p.addView(sub);
    a.primary("시작하기", () -> a.go("s02"));
  }

  private void intro() {
    int i = a.state.introPage;
    String[] titles = {"사진으로 기록", "맞춤 대안 3개", "미래의 나 비교"},
        sub =
            {
              "먹을 음식을 사진으로 찍거나 이름을 입력해요.",
              "목표, 취향, 예산을 반영한 식사 방법을 골라요.",
              "같은 기간에 식사 선택에 따른 변화 시나리오를 봐요."
            },
        photos = {"음식 사진 예시", "대안 카드 예시", "아바타 비교 예시"};
    LinearLayout hero = s("hero");
    u.gap(hero, 52);
    u.photo(hero, photos[i], 216, "");
    LinearLayout p = s("intro");
    p.setGravity(Gravity.CENTER);
    u.gap(p, 28);
    p.addView(
        u.icon(
            new int[] {R.drawable.ic_camera, R.drawable.ic_history, R.drawable.ic_spark}[i],
            26,
            R.color.primary));
    u.gap(p, 14);
    TextView title = u.title(p, titles[i]);
    title.setGravity(Gravity.CENTER);
    TextView body = u.muted(p, sub[i]);
    body.setGravity(Gravity.CENTER);
    u.gap(p, 28);
    LinearLayout dots = u.row();
    dots.setGravity(Gravity.CENTER);
    for (int j = 0; j < 3; j++) {
      View v = new View(a);
      v.setBackgroundResource(j == i ? R.drawable.bg_chip_selected : R.drawable.bg_placeholder);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(u.dp(j == i ? 20 : 8), u.dp(8));
      lp.setMarginEnd(u.dp(5));
      dots.addView(v, lp);
    }
    s("dots").addView(dots);
    u.gap(s("skip"), 18);
    u.link(s("skip"), "건너뛰기", () -> a.go("s03"));
    a.primary(
        i == 2 ? "프로필 설정하기" : "다음",
        () -> {
          if (a.state.introPage < 2) {
            a.state.introPage++;
            a.refresh();
          } else a.go("s03");
        });
  }

  private void basic() {
    Profile p = a.state.profile;
    u.progress(s("progress"), 1);
    u.heading(s("heading"), "기본 정보를 알려주세요", "일일 목표 열량을 설정할 때 활용해요.");
    u.section(s("gender"), "성별");
    u.chips(
        s("gender"),
        new String[] {"여성", "남성"},
        Set.of(p.gender),
        false,
        v -> {
          p.gender = v;
          a.refresh();
        });
    u.gap(s("gender"), 14);
    LinearLayout fields = s("fields");
    number(fields, "나이", p.age + "세", v -> p.age = (int) v);
    u.gap(fields, 12);
    number(fields, "키", Ui.number(p.heightCm) + " cm", v -> p.heightCm = v);
    u.gap(fields, 12);
    number(fields, "현재 체중", Ui.number(p.weightKg) + " kg", v -> p.weightKg = v);
    u.gap(fields, 12);
    EditText activity = u.input(fields, "활동량", p.activity + " ▾", "활동량", null);
    activity.setFocusable(false);
    activity.setOnClickListener(
        v -> {
          PopupMenu m = new PopupMenu(a, activity);
          for (String option : new String[] {"가벼운 활동", "보통 활동", "활동량 많음"})
            m.getMenu()
                .add(option)
                .setOnMenuItemClickListener(
                    item -> {
                      p.activity = option;
                      activity.setText(option + " ▾");
                      return true;
                    });
          m.show();
        });
    u.gap(s("note"), 18);
    u.small(s("note"), "프로토타입에 표시된 사용자 정보는 예시예요.");
    a.primary(
        "다음",
        () -> {
          if (p.age < 10
              || p.age > 120
              || p.heightCm < 80
              || p.heightCm > 250
              || p.weightKg < 20
              || p.weightKg > 400) a.go("s29_required");
          else {
            a.persist();
            a.go(a.state.editingProfile ? "s25" : "s04");
            a.state.editingProfile = false;
          }
        });
  }

  private interface NumberChanged {
    void accept(double value);
  }

  private EditText number(LinearLayout parent, String label, String value, NumberChanged changed) {
    EditText e =
        u.input(
            parent,
            label,
            value,
            "숫자 입력",
            v -> {
              try {
                String n = v.replaceAll("[^0-9.]", "");
                changed.accept(n.isEmpty() ? 0 : Double.parseDouble(n));
              } catch (NumberFormatException ex) {
                changed.accept(0);
              }
            });
    e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    return e;
  }

  private void goal() {
    Profile p = a.state.profile;
    u.progress(s("progress"), 2);
    u.heading(s("heading"), "어떤 목표가 있나요?", "");
    u.chips(
        s("goals"),
        new String[] {"체중 감량", "유지", "근육 증가"},
        Set.of(p.goal),
        false,
        v -> {
          p.goal = v;
          a.refresh();
        });
    u.gap(s("target"), 16);
    number(s("target"), "목표 체중", Ui.number(p.targetWeightKg) + " kg", v -> p.targetWeightKg = v);
    u.gap(s("notice"), 16);
    u.notice(
        s("notice"),
        "선택한 목표 · " + (p.goal.equals("체중 감량") ? "체중 관리" : p.goal),
        "추천할 때 영양 균형과 함께 고려해요.",
        0);
    a.primary(
        "다음",
        () -> {
          if (p.targetWeightKg < 20 || p.targetWeightKg > 400) a.go("s29_required");
          else if (a.state.editingProfile) {
            a.state.editingProfile = false;
            a.persist();
            a.go("s29");
          } else a.go("s05");
        });
  }

  public void preferences(boolean edit) {
    Profile p = a.state.profile;
    if (!edit) u.progress(s("progress"), 3);
    u.heading(s("heading"), edit ? "식사 취향을 수정해요" : "평소 식사 취향을 알려주세요", "");
    LinearLayout parent = s("preferences");
    group(parent, "좋아하는 음식", new String[] {"돈가스", "비빔밥", "샐러드"}, p.preferences);
    u.gap(parent, 18);
    group(parent, "피하고 싶은 음식", new String[] {"매운 음식", "기름진 음식"}, p.avoided);
    u.gap(parent, 18);
    group(parent, "알레르기", new String[] {"갑각류", "유제품", "견과류"}, p.allergies);
    u.gap(s("warning"), 16);
    if (!p.allergies.isEmpty())
      u.notice(
          s("warning"),
          String.join("·", p.allergies) + "를 피하도록 설정했어요",
          "추천 대안에서 " + String.join("·", p.allergies) + "가 포함된 후보를 제외해요.",
          1);
    a.primary(
        edit ? "저장하기" : "다음",
        () -> {
          a.state.alternatives.clear();
          a.persist();
          a.go(edit ? "s29" : "s06");
        });
  }

  private void group(LinearLayout parent, String title, String[] choices, List<String> values) {
    u.section(parent, title);
    u.chips(
        parent,
        choices,
        new HashSet<>(values),
        true,
        v -> {
          if (values.contains(v)) values.remove(v);
          else values.add(v);
          a.refresh();
        });
  }

  private void budget() {
    Profile p = a.state.profile;
    u.progress(s("progress"), 4);
    u.heading(s("heading"), "평소 한 끼는 어떤가요?", "");
    u.section(s("budget"), "한 끼 예산");
    u.gap(s("budget"), 12);
    TextView amount =
        u.text(String.format(Locale.KOREA, "%,d원", p.budgetWon), 20, R.color.primary, false);
    s("budget").addView(amount);
    SeekBar seek = new SeekBar(a);
    seek.setProgressDrawable(a.getDrawable(R.drawable.progress_track));
    seek.setThumb(a.getDrawable(R.drawable.seek_thumb));
    if (android.os.Build.VERSION.SDK_INT >= 29) {
      seek.setMinHeight(u.dp(8));
      seek.setMaxHeight(u.dp(8));
    }
    seek.setMax(100);
    seek.setProgress((p.budgetWon - 5000) / 100);
    seek.setProgressTintList(android.content.res.ColorStateList.valueOf(u.color(R.color.primary)));
    seek.setThumbTintList(android.content.res.ColorStateList.valueOf(u.color(R.color.primary)));
    s("budget").addView(seek, new LinearLayout.LayoutParams(-1, u.dp(44)));
    seek.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar b, int progress, boolean user) {
            p.budgetWon = 5000 + progress * 100;
            amount.setText(String.format(Locale.KOREA, "%,d원", p.budgetWon));
          }

          public void onStartTrackingTouch(SeekBar b) {}

          public void onStopTrackingTouch(SeekBar b) {}
        });
    LinearLayout range = u.row();
    u.weight(range, u.text("5,000원", 11, R.color.muted, false));
    TextView max = u.text("15,000원", 11, R.color.muted, false);
    max.setGravity(Gravity.END);
    u.weight(range, max);
    s("budget").addView(range);
    u.gap(s("environment"), 28);
    u.section(s("environment"), "주로 어디에서 식사하나요?");
    u.chips(
        s("environment"),
        new String[] {"학식", "외식", "자취 조리", "편의점"},
        Set.of(p.environment),
        false,
        v -> {
          p.environment = v;
          a.refresh();
        });
    a.primary(
        "설정 완료",
        () -> {
          a.persist();
          if (a.state.editingProfile) {
            a.state.editingProfile = false;
            a.go("s29");
          } else a.go("s07");
        });
  }

  private void avatar() {
    u.heading(s("heading"), "나의 첫 아바타를 설정해요", "사전 제작된 2D 토끼 아바타로 시작할 수 있어요.");
    LinearLayout center = s("avatar");
    center.setGravity(Gravity.CENTER);
    u.gap(center, 80);
    u.avatar(center, AvatarStage.BASELINE);
    u.gap(center, 14);
    TextView label = u.text("기본 토끼 아바타", 12, R.color.primary, true);
    label.setGravity(Gravity.CENTER);
    center.addView(label);
    u.gap(center, 80);
    LinearLayout p = s("upload"), c = u.card(p);
    u.section(c, "사진으로 아바타 설정 · 확장");
    u.small(c, "사용자 사진을 활용한 캐릭터 설정은 확장 기능이에요.");
    u.gap(c, 10);
    FrameLayout placeholder = u.photo(c, "사용자 사진 업로드", 80, "");
    placeholder
        .findViewById(R.id.photo_placeholder)
        .findViewById(R.id.photo_label)
        .setPadding(0, 0, 0, 0);
    u.gap(p, 12);
    Button disabled = u.button(p, "사진 업로드", () -> {});
    disabled.setEnabled(false);
    disabled.setBackgroundResource(R.drawable.bg_placeholder);
    disabled.setTextColor(u.color(R.color.muted));
    a.primary(
        "기본 아바타로 시작",
        () -> {
          a.state.onboarded = true;
          a.persist();
          a.root("s08");
        });
  }
}
