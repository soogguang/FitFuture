package com.fitfuture.avatar.core;

import static org.junit.Assert.*;

import com.fitfuture.avatar.core.AvatarCatalog.Category;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class AvatarSpecLayersTest {

    @Test
    public void layerOrderFemaleDefaultsWithGlasses() {
        AvatarSpec s = AvatarSpec.defaults(BodyPreset.FEMALE).with(Category.ACCESSORY, "glasses_round");
        List<String> l = AvatarLayers.paths(s, 4, 0);
        assertEquals("avatar/head/hair_b02.png", l.get(0));      // 뒷머리
        assertEquals("avatar/female/body_c4.png", l.get(1));     // 몸·피부
        assertEquals("avatar/female/outfit_hoodie_mint_c4.png", l.get(2)); // 옷
        assertEquals("avatar/head/face_round_base.png", l.get(3));
        assertEquals("avatar/head/eyes_a_m.png", l.get(4));
        assertEquals("avatar/head/brows_05.png", l.get(5));
        assertEquals("avatar/head/nose_01.png", l.get(6));
        assertEquals("avatar/head/mouth_e02.png", l.get(7));
        assertEquals("avatar/head/hair_f06.png", l.get(8));      // 앞머리
        assertEquals("avatar/head/acc_glasses_round.png", l.get(9));
        assertEquals(10, l.size());
    }

    @Test
    public void noBackHairNoAccessoryForMaleDefault() {
        List<String> l = AvatarLayers.paths(AvatarSpec.defaults(BodyPreset.MALE), 1, 1);
        assertEquals(8, l.size());
        assertEquals("avatar/male/body_c1.png", l.get(0));
        assertEquals("avatar/head/face_square_full.png", l.get(2));
    }

    @Test
    public void sameHeadForAandB() {
        AvatarSpec s = AvatarSpec.defaults(BodyPreset.MALE).with(Category.EYES, "c_l");
        List<String> a = AvatarLayers.paths(s, 5, 0);
        List<String> b = AvatarLayers.paths(s, 2, 0);
        for (int i = 0; i < a.size(); i++) {
            boolean bodyLayer = a.get(i).startsWith("avatar/male/");
            if (bodyLayer) assertNotEquals(a.get(i), b.get(i));
            else assertEquals(a.get(i), b.get(i));
        }
    }

    @Test
    public void unknownIdsFallBackToDefaults() {
        Map<Category, String> m = new EnumMap<>(Category.class);
        m.put(Category.FACE, "x");
        m.put(Category.HAIR, "long"); // 예전 임시 파츠 ID
        m.put(Category.EYES, "b_l");
        AvatarSpec s = new AvatarSpec(BodyPreset.MALE, 9, m, null, null);
        assertEquals(7, s.bodyCell);
        assertEquals("square", s.get(Category.FACE));
        assertEquals("f04", s.get(Category.HAIR));
        assertEquals("b_l", s.get(Category.EYES));
        assertEquals(AvatarCatalog.NONE, s.get(Category.ACCESSORY));
        assertEquals(AvatarSpec.COLOR_ORIGINAL, s.skin);
    }

    @Test
    public void currentCellUsesDraftUntilConfirmed() {
        AvatarSpec s = AvatarSpec.defaults(BodyPreset.FEMALE);
        assertEquals(3, s.currentCell(BodyCellConfig.DEFAULT, 160, 55));
        assertEquals(5, s.withBodyCell(5).currentCell(BodyCellConfig.DEFAULT, 160, 55));
    }

    @Test
    public void presetChangeKeepsHeadParts() {
        AvatarSpec s = AvatarSpec.defaults(BodyPreset.FEMALE).with(Category.HAIR, "f08").withPreset(BodyPreset.MALE);
        assertEquals(BodyPreset.MALE, s.preset);
        assertEquals("f08", s.get(Category.HAIR));
    }

    @Test
    public void everyCatalogIdIsValid() {
        for (Category c : Category.values()) {
            for (AvatarCatalog.Part p : AvatarCatalog.parts(c)) assertTrue(AvatarCatalog.contains(c, p.id));
            for (BodyPreset bp : BodyPreset.values()) assertTrue(AvatarCatalog.contains(c, AvatarCatalog.defaultId(c, bp)));
        }
    }

    @Test
    public void ponytailSelectsRearTailBeforeFace() {
        AvatarSpec s = AvatarSpec.defaults(BodyPreset.FEMALE).withHairStyle("f08");
        assertEquals("b04", s.get(Category.HAIR_BACK));
        List<String> layers = AvatarLayers.paths(s, 4, 0);
        assertEquals("avatar/head/hair_b04.png", layers.get(0));
        assertTrue(layers.indexOf("avatar/head/hair_b04.png")
                < layers.indexOf("avatar/head/face_round_base.png"));
    }

    @Test
    public void shortPermClearsPreviousPonytail() {
        AvatarSpec s = AvatarSpec.defaults(BodyPreset.FEMALE)
                .withHairStyle("f08").withHairStyle("f09");
        assertEquals("f09", s.get(Category.HAIR));
        assertEquals(AvatarCatalog.NONE, s.get(Category.HAIR_BACK));
    }

    @Test
    public void longWaveSelectsMatchingRearButAllowsManualChange() {
        AvatarSpec s = AvatarSpec.defaults(BodyPreset.FEMALE).withHairStyle("f10");
        assertEquals("b05", s.get(Category.HAIR_BACK));
        assertEquals(AvatarCatalog.NONE,
                s.with(Category.HAIR_BACK, AvatarCatalog.NONE).get(Category.HAIR_BACK));
        assertEquals("b05", s.with(Category.EYES, "b_m").get(Category.HAIR_BACK));
    }
}
