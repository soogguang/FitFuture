package com.fitfuture.avatar.core;

import com.fitfuture.avatar.core.AvatarCatalog.Category;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * 사용자가 고른 아바타 구성. 원본 사진은 저장하지 않고 이 값만 저장한다.
 * 키·체중은 프로필에서 읽으므로 여기에 넣지 않는다.
 */
public final class AvatarSpec {

    /** 현재 체형 칸을 사용자가 아직 확정하지 않음 → BMI 초안 사용 */
    public static final int CELL_AUTO = 0;
    /** 색상 선택 전: Paper Cut 원본 색 그대로 */
    public static final String COLOR_ORIGINAL = "original";

    public final BodyPreset preset;
    /** 사용자가 확정한 현재 체형 칸 (1~7) 또는 CELL_AUTO */
    public final int bodyCell;
    private final Map<Category, String> parts;
    /** 색상 선택용 예약 필드. 지금은 항상 "original". */
    public final String skin, hairColor;

    /**
     * @param parts 카테고리별 파츠 ID. 없거나 카탈로그에 없는 ID는 기본값으로 바뀐다
     *              (예전 저장 데이터, 파츠 이름 변경 대비).
     */
    public AvatarSpec(BodyPreset preset, int bodyCell, Map<Category, String> parts, String skin, String hairColor) {
        this.preset = preset != null ? preset : BodyPreset.FEMALE;
        this.bodyCell = bodyCell == CELL_AUTO ? CELL_AUTO : BodyCellConfig.clampCell(bodyCell);
        EnumMap<Category, String> m = new EnumMap<>(Category.class);
        for (Category c : Category.values()) {
            String id = parts != null ? parts.get(c) : null;
            m.put(c, id != null && AvatarCatalog.contains(c, id) ? id : AvatarCatalog.defaultId(c, this.preset));
        }
        this.parts = m;
        this.skin = skin != null ? skin : COLOR_ORIGINAL;
        this.hairColor = hairColor != null ? hairColor : COLOR_ORIGINAL;
    }

    public static AvatarSpec defaults(BodyPreset preset) {
        return new AvatarSpec(preset, CELL_AUTO, null, null, null);
    }

    public String get(Category c) {
        return parts.get(c);
    }

    /** 확정 칸이 있으면 그 칸, 없으면 BMI 초안. */
    public int currentCell(BodyCellConfig config, double heightCm, double weightKg) {
        return bodyCell != CELL_AUTO ? bodyCell : config.draftCell(heightCm, weightKg);
    }

    public AvatarSpec with(Category c, String id) {
        EnumMap<Category, String> m = new EnumMap<>(parts);
        m.put(c, id);
        return new AvatarSpec(preset, bodyCell, m, skin, hairColor);
    }

    /** 헤어 선택 시 앞·뒤 한 쌍으로 적용한다. with(HAIR_BACK, ...)으로 따로 조정 가능. */
    public AvatarSpec withHairStyle(String frontId) {
        AvatarSpec selected = with(Category.HAIR, frontId);
        return selected.with(Category.HAIR_BACK,
                AvatarCatalog.matchingHairBack(selected.get(Category.HAIR)));
    }

    /** 남녀 기본형만 바꾼다. 얼굴·머리 파츠는 남녀 공통이라 그대로 둔다. */
    public AvatarSpec withPreset(BodyPreset p) {
        return new AvatarSpec(p, bodyCell, parts, skin, hairColor);
    }

    public AvatarSpec withBodyCell(int cell) {
        return new AvatarSpec(preset, cell, parts, skin, hairColor);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AvatarSpec)) return false;
        AvatarSpec s = (AvatarSpec) o;
        return preset == s.preset && bodyCell == s.bodyCell && parts.equals(s.parts)
                && skin.equals(s.skin) && hairColor.equals(s.hairColor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(preset, bodyCell, parts, skin, hairColor);
    }
}
