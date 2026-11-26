package com.fitfuture.avatar.core;

import static org.junit.Assert.*;

import com.fitfuture.avatar.core.AvatarCatalog.Category;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class PartGroupsTest {

    @Test
    public void everyCatalogIdAppearsExactlyOnce() {
        for (Category c : Category.values()) {
            Set<String> seen = new HashSet<>();
            for (PartGroups.Group g : PartGroups.groups(c)) {
                for (String id : g.ids) {
                    assertTrue(c + " 중복 " + id, seen.add(id));
                    assertTrue(c + " 카탈로그에 없음 " + id, AvatarCatalog.contains(c, id));
                }
            }
            assertEquals(c + " 개수", AvatarCatalog.parts(c).length, seen.size());
        }
    }

    @Test
    public void locateAndIdForRoundTrip() {
        for (Category c : Category.values()) {
            for (AvatarCatalog.Part p : AvatarCatalog.parts(c)) {
                int[] at = PartGroups.locate(c, p.id);
                assertEquals(p.id, PartGroups.idFor(c, at[0], at[1]));
            }
        }
    }

    @Test
    public void switchingTypeKeepsSize() {
        // 내려간 눈·크게 → 둥근 눈으로 바꾸면 둥근 눈·크게
        assertEquals("c_l", PartGroups.switchGroup(Category.EYES, "b_l", 2));
        // 굵은 일자 → 각진으로 바꾸면 굵은 각진
        assertEquals("09", PartGroups.switchGroup(Category.BROWS, "03", 2));
        // 미소 표정(단계 없음) → 굴곡 입술은 '보통'부터
        assertEquals("l05", PartGroups.switchGroup(Category.MOUTH, "e02", 1));
        // 도톰한 기본 입술 → 활짝 웃음 표정
        assertEquals("e03", PartGroups.switchGroup(Category.MOUTH, "l03", 5));
    }

    @Test
    public void levelLabels() {
        assertArrayEquals(new String[] {"작게", "보통", "크게"}, PartGroups.levelLabels(Category.EYES));
        assertArrayEquals(new String[] {"가늘게", "보통", "굵게"}, PartGroups.levelLabels(Category.BROWS));
        assertArrayEquals(new String[] {"얇게", "보통", "도톰"}, PartGroups.levelLabels(Category.MOUTH));
        assertEquals(0, PartGroups.levelLabels(Category.HAIR).length);
    }
}
