package com.fitfuture.avatar.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class BodyCellConfigTest {

    private final BodyCellConfig cfg = BodyCellConfig.DEFAULT;

    @Test
    public void boundaryGoesToUpperCell() {
        assertEquals(1, cfg.cellOf(16.0));
        assertEquals(1, cfg.cellOf(18.49));
        assertEquals(2, cfg.cellOf(18.5));
        assertEquals(3, cfg.cellOf(20.5));
        assertEquals(3, cfg.cellOf(22.99));
        assertEquals(4, cfg.cellOf(23.0));
        assertEquals(5, cfg.cellOf(25.0));
        assertEquals(6, cfg.cellOf(27.5));
        assertEquals(7, cfg.cellOf(30.0));
        assertEquals(7, cfg.cellOf(40.0));
    }

    @Test
    public void presentationExampleDraftCell() {
        // 160cm 55kg → BMI 21.48 → 3칸
        assertEquals(21.48, BodyCellConfig.bmi(160, 55), 0.01);
        assertEquals(3, cfg.draftCell(160, 55));
    }

    @Test
    public void heightScale() {
        assertEquals(0.80, cfg.heightScale(130), 1e-9);
        assertEquals(0.80, cfg.heightScale(140), 1e-9);
        assertEquals(0.90, cfg.heightScale(170), 1e-9);
        assertEquals(1.00, cfg.heightScale(200), 1e-9);
        assertEquals(1.00, cfg.heightScale(210), 1e-9);
    }

    @Test
    public void customBoundaries() {
        BodyCellConfig c = new BodyCellConfig(new double[]{18, 20, 22, 24, 26, 28}, 150, 190, 0.85, 1.0);
        assertEquals(4, c.cellOf(22.0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsWrongBoundaryCount() {
        new BodyCellConfig(new double[]{18.5, 23, 25}, 140, 200, 0.8, 1.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnsortedBoundaries() {
        new BodyCellConfig(new double[]{18.5, 23, 20.5, 25, 27.5, 30}, 140, 200, 0.8, 1.0);
    }
}
