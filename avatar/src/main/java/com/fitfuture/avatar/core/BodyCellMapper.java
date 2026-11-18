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
 * 볼 윤곽은 미래 칸의 체형 구간을 따른다 ({@link BodyCellConfig#cheekForCell}).
 * 기본값: 1~2칸 갸름, 3~5칸 기본, 6~7칸 도톰. 얼굴형은 바꾸지 않는다.
 */
public final class BodyCellMapper {

    public static final double SMALL_CHANGE = 0.02;
    public static final double LARGE_CHANGE = 0.05;

    /** 볼 윤곽 단계: −1 갸름, 0 기본, +1 도톰 */
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
        return future(currentCell, changeRate, BodyCellConfig.DEFAULT);
    }

    public static Future future(int currentCell, double changeRate, BodyCellConfig config) {
        int current = BodyCellConfig.clampCell(currentCell);
        int intended = intendedMove(changeRate);
        int cell = BodyCellConfig.clampCell(current + intended);
        int actual = cell - current;
        return new Future(current, cell, intended, actual, config.cheekForCell(cell));
    }

    public static final class Future {
        public final int currentCell;
        /** 미래 체형 칸 (1~7) */
        public final int cell;
        /** 변화율로 계산한 이동 칸 수 */
        public final int intendedMove;
        /** 1~7 제한 뒤 실제 이동한 칸 수 */
        public final int actualMove;
        /** 미래 칸의 볼 윤곽 (체형 구간을 따름) */
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
