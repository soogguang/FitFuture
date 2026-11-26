package com.fitfuture.avatar.core;

/**
 * 사용자가 확정한 현재 체형 칸에서 체중 변화율에 따라 미래 칸을 구한다.
 *
 * <pre>
 * |변화율| &lt; 2%        → 0칸 이동
 * 2% ≤ |변화율| &lt; 5%  → 1칸 이동
 * 5% ≤ |변화율|        → 2칸 이동   (부호는 변화 방향)
 * 결과는 1~7칸으로 제한
 * </pre>
 * 실제로 2칸 이동했을 때만 같은 얼굴형의 볼 윤곽을 한 단계 바꾼다.
 */
public final class BodyCellMapper {

    public static final double SMALL_CHANGE = 0.02;
    public static final double LARGE_CHANGE = 0.05;

    /** 볼 윤곽 단계: −1 갸름, 0 기본, +1 통통 */
    public static final int CHEEK_SLIM = -1;
    public static final int CHEEK_BASE = 0;
    public static final int CHEEK_FULL = 1;

    private BodyCellMapper() {}

    /** 변화율 → 이동하려는 칸 수 (−2 ~ +2). 아직 1~7 제한 전. */
    public static int intendedMove(double changeRate) {
        double abs = Math.abs(changeRate);
        int steps = abs < SMALL_CHANGE ? 0 : abs < LARGE_CHANGE ? 1 : 2;
        return changeRate < 0 ? -steps : steps;
    }

    public static Future future(int currentCell, double changeRate) {
        int current = BodyCellConfig.clampCell(currentCell);
        int intended = intendedMove(changeRate);
        int cell = BodyCellConfig.clampCell(current + intended);
        int actual = cell - current;
        int cheek = Math.abs(actual) == 2 ? Integer.signum(actual) : CHEEK_BASE;
        return new Future(current, cell, intended, actual, cheek);
    }

    public static final class Future {
        public final int currentCell;
        /** 미래 체형 칸 (1~7) */
        public final int cell;
        /** 변화율로 계산한 이동 칸 수 */
        public final int intendedMove;
        /** 1~7 제한 뒤 실제 이동한 칸 수 */
        public final int actualMove;
        /** 볼 윤곽 단계 (실제로 2칸 이동했을 때만 ±1) */
        public final int cheekLevel;

        Future(int currentCell, int cell, int intendedMove, int actualMove, int cheekLevel) {
            this.currentCell = currentCell;
            this.cell = cell;
            this.intendedMove = intendedMove;
            this.actualMove = actualMove;
            this.cheekLevel = cheekLevel;
        }

        /** 끝 칸이라 그림이 덜 움직였는지. 이때도 예상 kg은 그대로 표시한다. */
        public boolean clamped() {
            return actualMove != intendedMove;
        }
    }
}
