package com.fitfuture.avatar.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class BodyCellMapperTest {

    @Test
    public void intendedMoveThresholds() {
        assertEquals(0, BodyCellMapper.intendedMove(0.0));
        assertEquals(0, BodyCellMapper.intendedMove(0.0199));
        assertEquals(0, BodyCellMapper.intendedMove(-0.0199));
        assertEquals(1, BodyCellMapper.intendedMove(0.02));
        assertEquals(-1, BodyCellMapper.intendedMove(-0.02));
        assertEquals(1, BodyCellMapper.intendedMove(0.0499));
        assertEquals(2, BodyCellMapper.intendedMove(0.05));
        assertEquals(-2, BodyCellMapper.intendedMove(-0.08));
    }

    @Test
    public void presentationExample() {
        // 현재 3칸, A 6개월 +4.3% → 4칸, B 6개월 −2.9% → 2칸
        BodyCellMapper.Future a = BodyCellMapper.future(3, 0.0431);
        BodyCellMapper.Future b = BodyCellMapper.future(3, -0.0291);
        assertEquals(4, a.cell);
        assertEquals(2, b.cell);
        assertEquals(BodyCellMapper.CHEEK_BASE, a.cheekLevel);
        assertEquals(BodyCellMapper.CHEEK_BASE, b.cheekLevel);
    }

    @Test
    public void cheekChangesOnlyOnActualTwoStepMove() {
        BodyCellMapper.Future up = BodyCellMapper.future(4, 0.06);
        assertEquals(6, up.cell);
        assertEquals(BodyCellMapper.CHEEK_FULL, up.cheekLevel);

        BodyCellMapper.Future down = BodyCellMapper.future(4, -0.06);
        assertEquals(2, down.cell);
        assertEquals(BodyCellMapper.CHEEK_SLIM, down.cheekLevel);
    }

    @Test
    public void clampedAtEdgeKeepsFace() {
        // 6칸에서 +2 → 7칸에서 멈춤, 실제 1칸 이동이라 볼 윤곽 그대로
        BodyCellMapper.Future f = BodyCellMapper.future(6, 0.07);
        assertEquals(7, f.cell);
        assertEquals(2, f.intendedMove);
        assertEquals(1, f.actualMove);
        assertTrue(f.clamped());
        assertEquals(BodyCellMapper.CHEEK_BASE, f.cheekLevel);

        BodyCellMapper.Future g = BodyCellMapper.future(1, -0.03);
        assertEquals(1, g.cell);
        assertEquals(0, g.actualMove);
        assertTrue(g.clamped());
    }
}
