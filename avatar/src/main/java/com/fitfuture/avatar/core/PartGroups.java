package com.fitfuture.avatar.core;

import com.fitfuture.avatar.core.AvatarCatalog.Category;

/**
 * 편집 화면용 묶음: "종류"를 고르고, 크기·굵기·두께는 같은 종류 안에서 3단계로 바꾼다.
 * 저장 ID(`b_m`, `05`, `l06` …)와 PNG는 그대로이고, 화면에서 묶어 보여주기만 한다.
 *
 * <pre>
 * 눈    기본·내려간·둥근·올라간 × 작게/보통/크게      a_s a_m a_l …
 * 눈썹  일자·아치·각진 × 가늘게/보통/굵게            01~03, 04~06, 07~09
 * 코    타원·둥근·넓은 콧볼 × 작게/보통/크게          01~03, 04~06, 07~09
 * 입    기본·굴곡·둥근 입술 × 얇게/보통/도톰          l01~l03, l04~l06, l07~l09
 *       일자·미소·활짝 표정 (단계 없음)               e01, e02, e03
 * 그 외 얼굴형·헤어·뒷머리·옷·안경은 단계 없이 종류만
 * </pre>
 */
public final class PartGroups {

    private PartGroups() {}

    public static final class Group {
        public final String label;
        /** 단계별 ID (단계가 없으면 1개) */
        public final String[] ids;

        Group(String label, String... ids) {
            this.label = label;
            this.ids = ids;
        }

        public boolean hasLevels() {
            return ids.length > 1;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final String[] SIZE = {"작게", "보통", "크게"};
    private static final String[] THICK = {"가늘게", "보통", "굵게"};
    private static final String[] LIP = {"얇게", "보통", "도톰"};

    public static Group[] groups(Category c) {
        switch (c) {
            case EYES:
                return new Group[] {
                        new Group("기본 눈", "a_s", "a_m", "a_l"),
                        new Group("내려간 눈", "b_s", "b_m", "b_l"),
                        new Group("둥근 눈", "c_s", "c_m", "c_l"),
                        new Group("올라간 눈", "d_s", "d_m", "d_l")};
            case BROWS:
                return new Group[] {
                        new Group("일자", "01", "02", "03"),
                        new Group("아치", "04", "05", "06"),
                        new Group("각진", "07", "08", "09")};
            case NOSE:
                return new Group[] {
                        new Group("타원 코", "01", "02", "03"),
                        new Group("둥근 코", "04", "05", "06"),
                        new Group("넓은 콧볼", "07", "08", "09")};
            case MOUTH:
                return new Group[] {
                        new Group("기본 입술", "l01", "l02", "l03"),
                        new Group("굴곡 입술", "l04", "l05", "l06"),
                        new Group("둥근 입술", "l07", "l08", "l09"),
                        new Group("일자 표정", "e01"),
                        new Group("미소", "e02"),
                        new Group("활짝 웃음", "e03")};
            default: {
                AvatarCatalog.Part[] parts = AvatarCatalog.parts(c);
                Group[] out = new Group[parts.length];
                for (int i = 0; i < parts.length; i++) out[i] = new Group(parts[i].label, parts[i].id);
                return out;
            }
        }
    }

    /** 단계 이름 (작게/보통/크게 등). 단계가 없는 카테고리는 빈 배열. */
    public static String[] levelLabels(Category c) {
        switch (c) {
            case EYES:
            case NOSE:
                return SIZE.clone();
            case BROWS:
                return THICK.clone();
            case MOUTH:
                return LIP.clone();
            default:
                return new String[0];
        }
    }

    /** ID → {묶음 번호, 단계}. 없으면 {0, 가운데 단계}. */
    public static int[] locate(Category c, String id) {
        Group[] gs = groups(c);
        for (int g = 0; g < gs.length; g++) {
            for (int l = 0; l < gs[g].ids.length; l++) {
                if (gs[g].ids[l].equals(id)) return new int[] {g, l};
            }
        }
        return new int[] {0, Math.min(1, gs[0].ids.length - 1)};
    }

    /** 묶음과 단계로 ID. 단계가 없는 묶음이면 그 하나, 단계는 범위 안으로 맞춘다. */
    public static String idFor(Category c, int group, int level) {
        Group[] gs = groups(c);
        Group g = gs[Math.max(0, Math.min(gs.length - 1, group))];
        return g.ids[Math.max(0, Math.min(g.ids.length - 1, level))];
    }

    /**
     * 종류를 바꿀 때 지금 단계(크기·굵기)를 유지한 ID.
     * 단계 없는 표정에서 입술로 오면 '보통'으로 시작한다.
     */
    public static String switchGroup(Category c, String currentId, int newGroup) {
        Group[] gs = groups(c);
        int[] at = locate(c, currentId);
        int level = gs[at[0]].hasLevels() ? at[1] : 1;
        return idFor(c, newGroup, level);
    }
}
