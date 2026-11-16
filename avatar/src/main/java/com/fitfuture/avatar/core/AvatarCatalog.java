package com.fitfuture.avatar.core;

/**
 * 고를 수 있는 파츠 목록 (Figma Paper Cut 시트 기준).
 * 파츠를 추가하면 여기에 ID·이름을 넣고, PaperCut v2 manifest와 같은 이름의 PNG를 만든다.
 *
 * <p>얼굴·머리 파츠는 남녀 공통(assets/avatar/head/), 몸·옷만 남녀별(assets/avatar/{male|female}/).
 * 눈 크기·눈썹 두께 같은 차이는 별도 값이 아니라 파츠 ID로 구분한다.
 */
public final class AvatarCatalog {

    private AvatarCatalog() {}

    public enum Category {
        FACE("얼굴형"), EYES("눈"), BROWS("눈썹"), NOSE("코"), MOUTH("입"),
        HAIR("앞머리"), HAIR_BACK("뒷머리"), OUTFIT("옷"), ACCESSORY("소품");

        public final String label;

        Category(String label) {
            this.label = label;
        }
    }

    public static final class Part {
        public final String id;
        public final String label;

        Part(String id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static final String NONE = "none";

    private static final Part[] FACES = {
            p("round", "둥근형"), p("v", "타원형"), p("square", "각진형"), p("heart", "광대형")};

    private static final Part[] EYES = {
            p("a_s", "기본 눈 · 작게"), p("a_m", "기본 눈 · 보통"), p("a_l", "기본 눈 · 크게"),
            p("b_s", "내려간 눈 · 작게"), p("b_m", "내려간 눈 · 보통"), p("b_l", "내려간 눈 · 크게"),
            p("c_s", "둥근 눈 · 작게"), p("c_m", "둥근 눈 · 보통"), p("c_l", "둥근 눈 · 크게"),
            p("d_s", "올라간 눈 · 작게"), p("d_m", "올라간 눈 · 보통"), p("d_l", "올라간 눈 · 크게")};

    private static final Part[] BROWS = {
            p("01", "가는 일자"), p("02", "보통 일자"), p("03", "굵은 일자"),
            p("04", "가는 아치"), p("05", "보통 아치"), p("06", "굵은 아치"),
            p("07", "가는 각진"), p("08", "보통 각진"), p("09", "굵은 각진")};

    private static final Part[] NOSES = {
            p("01", "타원 코 · 작게"), p("02", "타원 코 · 보통"), p("03", "타원 코 · 크게"), p("04", "둥근 코 · 작게"), p("05", "둥근 코 · 보통"),
            p("06", "둥근 코 · 크게"), p("07", "넓은 콧볼 · 작게"), p("08", "넓은 콧볼 · 보통"), p("09", "넓은 콧볼 · 크게")};

    private static final Part[] MOUTHS = {
            p("l01", "기본 입술 · 얇게"), p("l02", "기본 입술 · 보통"), p("l03", "기본 입술 · 도톰"), p("l04", "굴곡 입술 · 얇게"),
            p("l05", "굴곡 입술 · 보통"), p("l06", "굴곡 입술 · 도톰"), p("l07", "둥근 입술 · 얇게"), p("l08", "둥근 입술 · 보통"),
            p("l09", "둥근 입술 · 도톰"), p("e01", "표정 · 일자"), p("e02", "표정 · 미소"), p("e03", "표정 · 활짝")};

    private static final Part[] HAIRS = {
            p("f01", "짧은 크롭"), p("f02", "둥근 앞머리"), p("f03", "일자 앞머리"),
            p("f04", "웨이브 가르마"), p("f05", "단발"), p("f06", "중단발 가르마"),
            p("f07", "긴 생머리"), p("f08", "낮은 포니테일"), p("f09", "짧은 뽀글 파마"),
            p("f10", "풀뱅 긴 웨이브")};

    private static final Part[] HAIR_BACKS = {
            p(NONE, "없음"), p("b01", "단발 뒷머리"), p("b02", "중단발 뒷머리"), p("b03", "긴 생머리 뒷머리"),
            p("b04", "낮은 포니테일 꼬리"), p("b05", "긴 웨이브 뒷머리")};

    private static final Part[] OUTFITS = {
            p("hoodie_mint", "민트 후드"), p("sweatshirt", "맨투맨"), p("shirt", "셔츠")};

    private static final Part[] ACCESSORIES = {
            p(NONE, "없음"), p("glasses_round", "둥근 안경"), p("glasses_square", "사각 안경")};

    public static Part[] parts(Category c) {
        switch (c) {
            case FACE: return FACES.clone();
            case EYES: return EYES.clone();
            case BROWS: return BROWS.clone();
            case NOSE: return NOSES.clone();
            case MOUTH: return MOUTHS.clone();
            case HAIR: return HAIRS.clone();
            case HAIR_BACK: return HAIR_BACKS.clone();
            case OUTFIT: return OUTFITS.clone();
            case ACCESSORY: return ACCESSORIES.clone();
            default: throw new IllegalArgumentException(String.valueOf(c));
        }
    }

    /** 남녀 기본형마다 처음 보여줄 파츠. 바꿀 수 있다. */
    public static String defaultId(Category c, BodyPreset preset) {
        boolean male = preset == BodyPreset.MALE;
        switch (c) {
            case FACE: return male ? "square" : "round";
            case EYES: return "a_m";
            case BROWS: return male ? "03" : "05";
            case NOSE: return "01";
            case MOUTH: return "e02";
            case HAIR: return male ? "f04" : "f06";
            case HAIR_BACK: return male ? NONE : "b02";
            case OUTFIT: return "hoodie_mint";
            case ACCESSORY: return NONE;
            default: throw new IllegalArgumentException(String.valueOf(c));
        }
    }

    /** 앞머리를 고를 때 함께 선택할 뒷머리. 이후 뒷머리만 직접 바꿀 수도 있다. */
    public static String matchingHairBack(String frontId) {
        switch (frontId) {
            case "f05": return "b01";
            case "f06": return "b02";
            case "f07": return "b03";
            case "f08": return "b04";
            case "f10": return "b05";
            default: return NONE;
        }
    }

    public static boolean contains(Category c, String id) {
        for (Part part : parts(c)) {
            if (part.id.equals(id)) return true;
        }
        return false;
    }

    private static Part p(String id, String label) {
        return new Part(id, label);
    }
}
