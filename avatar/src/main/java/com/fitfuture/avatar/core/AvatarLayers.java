package com.fitfuture.avatar.core;

import com.fitfuture.avatar.core.AvatarCatalog.Category;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 아바타를 그릴 PNG 레이어 목록을 순서대로 만든다.
 *
 * <p>순서: 뒷머리 → 몸·피부 → 옷 → 얼굴 → 눈 → 눈썹 → 코 → 입 → 앞머리 → 소품
 *
 * <p>모든 PNG는 같은 투명 전신 캔버스(320×790)에 기준 위치 그대로 놓여 있다
 * (PaperCut v2의 app/ 파츠 세트). 렌더러는 위치 계산 없이 (0, 0)에 겹치기만 한다.
 *
 * <pre>
 * assets/avatar/{male|female}/
 *   body_c{1~7}.png              목·손·바지·신발
 *   outfit_{id}_c{1~7}.png       체형 칸마다 따로 그린 상의
 * assets/avatar/head/            남녀 공통
 *   face_{shape}_{slim|base|full}.png
 *   eyes_{id}.png  brows_{id}.png  nose_{id}.png  mouth_{id}.png
 *   hair_{id}.png                앞머리(f..)·뒷머리(b..)
 *   acc_{id}.png
 * </pre>
 */
public final class AvatarLayers {

    public static final String ROOT = "avatar";
    public static final String HEAD = ROOT + "/head/";
    public static final int CANVAS_WIDTH = 320;
    public static final int CANVAS_HEIGHT = 790;

    private AvatarLayers() {}

    /**
     * @param cell       그릴 체형 칸 (1~7)
     * @param cheekLevel 볼 윤곽 (−1 갸름, 0 기본, +1 통통)
     * @return assets 기준 경로 목록. 아래 레이어부터.
     */
    public static List<String> paths(AvatarSpec spec, int cell, int cheekLevel) {
        String body = ROOT + "/" + spec.preset.folder + "/";
        int c = BodyCellConfig.clampCell(cell);
        List<String> out = new ArrayList<>();
        String back = spec.get(Category.HAIR_BACK);
        if (!AvatarCatalog.NONE.equals(back)) out.add(HEAD + "hair_" + back + ".png");
        out.add(body + "body_c" + c + ".png");
        out.add(body + "outfit_" + spec.get(Category.OUTFIT) + "_c" + c + ".png");
        out.add(HEAD + "face_" + spec.get(Category.FACE) + "_" + cheekSuffix(cheekLevel) + ".png");
        out.add(HEAD + "eyes_" + spec.get(Category.EYES) + ".png");
        out.add(HEAD + "brows_" + spec.get(Category.BROWS) + ".png");
        out.add(HEAD + "nose_" + spec.get(Category.NOSE) + ".png");
        out.add(HEAD + "mouth_" + spec.get(Category.MOUTH) + ".png");
        out.add(HEAD + "hair_" + spec.get(Category.HAIR) + ".png");
        String acc = spec.get(Category.ACCESSORY);
        if (!AvatarCatalog.NONE.equals(acc)) out.add(HEAD + "acc_" + acc + ".png");
        return Collections.unmodifiableList(out);
    }

    public static String cheekSuffix(int cheekLevel) {
        if (cheekLevel < 0) return "slim";
        if (cheekLevel > 0) return "full";
        return "base";
    }
}
