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
        assertEquals(BodyCellMapper.CHEEK_BASE, a.cheekLevel); // 4칸: 기본 볼
        assertEquals(BodyCellMapper.CHEEK_SLIM, b.cheekLevel); // 2칸: 갸름 구간
    }

    @Test
    public void cheekFollowsBodyBand() {
        BodyCellMapper.Future up = BodyCellMapper.future(4, 0.06);
        assertEquals(6, up.cell);
        assertEquals(BodyCellMapper.CHEEK_FULL, up.cheekLevel);

        BodyCellMapper.Future down = BodyCellMapper.future(4, -0.06);
        assertEquals(2, down.cell);
        assertEquals(BodyCellMapper.CHEEK_SLIM, down.cheekLevel);
    }

    @Test
    public void oneStepAcrossBandChangesCheek() {
        // 5칸 → 6칸은 1칸 이동이어도 도톰 구간에 들어가므로 볼이 바뀐다
        assertEquals(BodyCellMapper.CHEEK_FULL, BodyCellMapper.future(5, 0.03).cheekLevel);
        // 4칸 → 5칸은 같은 기본 구간
        assertEquals(BodyCellMapper.CHEEK_BASE, BodyCellMapper.future(4, 0.03).cheekLevel);
        // 이동이 없어도 현재 칸의 볼 (7칸이면 도톰)
        assertEquals(BodyCellMapper.CHEEK_FULL, BodyCellMapper.future(7, 0.0).cheekLevel);
    }

    @Test
    public void clampedAtEdge() {
        // 6칸에서 +2 → 7칸에서 멈춤. 볼은 7칸 구간(도톰)
        BodyCellMapper.Future f = BodyCellMapper.future(6, 0.07);
        assertEquals(7, f.cell);
        assertEquals(2, f.intendedMove);
        assertEquals(1, f.actualMove);
        assertTrue(f.clamped());
        assertEquals(BodyCellMapper.CHEEK_FULL, f.cheekLevel);

        BodyCellMapper.Future g = BodyCellMapper.future(1, -0.03);
        assertEquals(1, g.cell);
        assertEquals(0, g.actualMove);
        assertTrue(g.clamped());
    }

    @Test
    public void cheekBandsAreConfigurable() {
        BodyCellConfig c = BodyCellConfig.DEFAULT;
        int[] expected = {-1, -1, 0, 0, 0, 1, 1};
        for (int cell = 1; cell <= 7; cell++) assertEquals(expected[cell - 1], c.cheekForCell(cell));
        BodyCellConfig narrow = new BodyCellConfig(new double[]{18.5, 20.5, 23, 25, 27.5, 30}, 140, 200, 0.8, 1.0, 1, 7);
        assertEquals(BodyCellMapper.CHEEK_BASE, narrow.cheekForCell(2));
        assertEquals(BodyCellMapper.CHEEK_FULL, narrow.cheekForCell(7));
    }
}
