package com.fitfuture.avatar.core;

import static org.junit.Assert.*;

import org.junit.Test;

/** 발표 예시(22세 여성, 160cm, 55kg, 가벼운 활동)와 같은 숫자가 나오는지 확인. */
public class WeightSimulatorTest {

    private final WeightSimulator.Profile p = new WeightSimulator.Profile(
            22, 160, 55, WeightSimulator.Sex.FEMALE, WeightSimulator.ActivityLevel.LIGHT);

    @Test
    public void bmrAndTdee() {
        assertEquals(1279.0, WeightSimulator.bmr(p), 0.01);
        assertEquals(1758.6, WeightSimulator.tdee(p), 0.1);
    }

    @Test
    public void sixMonths() {
        WeightSimulator.Comparison c = WeightSimulator.compare(p, 1860, 1690);
        int i = c.indexOfMonths(6);
        assertEquals(2.37, c.a[i].changeKg, 0.01);
        assertEquals(0.0431, c.a[i].changeRate, 0.0005);
        assertEquals(-1.60, c.b[i].changeKg, 0.01);
        assertFalse(c.bBelowBmr);
    }

    @Test
    public void warnsBelowBmr() {
        assertTrue(WeightSimulator.compare(p, 1860, 1200).bBelowBmr);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidProfile() {
        new WeightSimulator.Profile(22, 0, 55, WeightSimulator.Sex.FEMALE, WeightSimulator.ActivityLevel.LIGHT);
    }
}
