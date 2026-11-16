package com.carblocks.game;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;

/** 纯游戏逻辑，不依赖任何安卓类。 */
public final class Game {
    public static final int N = 8;

    public static final class Shape {
        public final int[][] cells;
        public final int h, w, color;
        public final double weight;

        Shape(int[][] cells, int color, double weight) {
            this.cells = cells;
            this.color = color;
            this.weight = weight;
            int mh = 0, mw = 0;
            for (int[] c : cells) {
                mh = Math.max(mh, c[0] + 1);
                mw = Math.max(mw, c[1] + 1);
            }
            h = mh;
            w = mw;
        }
    }

    public static final class Result {
        public boolean ok, boardCleared;
        public int lines, gain, cells;
        public int[] clearedPos = new int[0];   // r * N + c
        public int[] clearedColor = new int[0];
    }

    public static final Shape[] SHAPES;
    private static final double TOTAL_W;

    private static int[][] rect(int h, int w) {
        int[][] a = new int[h * w][];
        for (int r = 0; r < h; r++) for (int c = 0; c < w; c++) a[r * w + c] = new int[]{r, c};
        return a;
    }

    private static int[][] norm(int[][] cells) {
        int mr = Integer.MAX_VALUE, mc = Integer.MAX_VALUE;
        for (int[] c : cells) {
            mr = Math.min(mr, c[0]);
            mc = Math.min(mc, c[1]);
        }
        int[][] out = new int[cells.length][];
        for (int i = 0; i < cells.length; i++) out[i] = new int[]{cells[i][0] - mr, cells[i][1] - mc};
        // 插入排序，按行、列
        for (int i = 1; i < out.length; i++) {
            int[] k = out[i];
            int j = i - 1;
            while (j >= 0 && (out[j][0] > k[0] || (out[j][0] == k[0] && out[j][1] > k[1]))) {
                out[j + 1] = out[j];
                j--;
            }
            out[j + 1] = k;
        }
        return out;
    }

    private static int[][] rot(int[][] cells) {
        int maxR = 0;
        for (int[] c : cells) maxR = Math.max(maxR, c[0]);
        int[][] out = new int[cells.length][];
        for (int i = 0; i < cells.length; i++) out[i] = new int[]{cells[i][1], maxR - cells[i][0]};
        return norm(out);
    }

    private static String key(int[][] cells) {
        StringBuilder sb = new StringBuilder();
        for (int[] c : cells) sb.append(c[0]).append(',').append(c[1]).append(';');
        return sb.toString();
    }

    static {
        Object[][] base = {
                {rect(1, 1), 2.0, 3},
                {rect(1, 2), 3.0, 5},
                {rect(1, 3), 4.0, 6},
                {rect(1, 4), 3.0, 7},
                {rect(1, 5), 2.0, 1},
                {rect(2, 2), 4.0, 2},
                {rect(3, 3), 2.0, 8},
                {rect(2, 3), 2.0, 4},
                {new int[][]{{0, 0}, {1, 0}, {1, 1}}, 4.0, 5},                  // 小拐角
                {new int[][]{{0, 0}, {1, 0}, {2, 0}, {2, 1}}, 3.0, 2},          // L
                {new int[][]{{0, 1}, {1, 1}, {2, 1}, {2, 0}}, 3.0, 6},          // J
                {new int[][]{{0, 0}, {0, 1}, {0, 2}, {1, 1}}, 3.0, 7},          // T
                {new int[][]{{0, 1}, {0, 2}, {1, 0}, {1, 1}}, 2.0, 4},          // S
                {new int[][]{{0, 0}, {0, 1}, {1, 1}, {1, 2}}, 2.0, 1},          // Z
                {new int[][]{{0, 0}, {1, 0}, {2, 0}, {2, 1}, {2, 2}}, 2.0, 8},  // 大拐角
        };
        ArrayList<Shape> all = new ArrayList<Shape>();
        double total = 0;
        for (Object[] b : base) {
            HashSet<String> seen = new HashSet<String>();
            ArrayList<int[][]> vars = new ArrayList<int[][]>();
            int[][] cur = norm((int[][]) b[0]);
            for (int i = 0; i < 4; i++) {
                if (seen.add(key(cur))) vars.add(cur);
                cur = rot(cur);
            }
            double w = ((Double) b[1]) / vars.size();
            for (int[][] v : vars) {
                all.add(new Shape(v, (Integer) b[2], w));
                total += w;
            }
        }
        SHAPES = all.toArray(new Shape[0]);
        TOTAL_W = total;
    }

    public int[][] grid = new int[N][N];
    public int[] tray = {-1, -1, -1};
    public int score, streak, best;
    public boolean over, newBest;
    private final Random rnd;

    public Game() {
        this(new Random());
    }

    public Game(Random rnd) {
        this.rnd = rnd;
    }

    public static boolean fits(int[][] g, Shape s, int r, int c) {
        for (int[] cell : s.cells) {
            int rr = r + cell[0], cc = c + cell[1];
            if (rr < 0 || cc < 0 || rr >= N || cc >= N || g[rr][cc] != 0) return false;
        }
        return true;
    }

    public static boolean canFitAnywhere(int[][] g, Shape s) {
        for (int r = 0; r <= N - s.h; r++) for (int c = 0; c <= N - s.w; c++) if (fits(g, s, r, c)) return true;
        return false;
    }

    static void stamp(int[][] g, Shape s, int r, int c) {
        for (int[] cell : s.cells) g[r + cell[0]][c + cell[1]] = s.color;
    }

    static int[][] copy(int[][] g) {
        int[][] o = new int[N][];
        for (int i = 0; i < N; i++) o[i] = g[i].clone();
        return o;
    }

    /** 返回两个布尔数组：[0] 满行，[1] 满列。 */
    public static boolean[][] fullLines(int[][] g) {
        boolean[] rows = new boolean[N], cols = new boolean[N];
        for (int r = 0; r < N; r++) {
            boolean ok = true;
            for (int c = 0; c < N; c++) if (g[r][c] == 0) { ok = false; break; }
            rows[r] = ok;
        }
        for (int c = 0; c < N; c++) {
            boolean ok = true;
            for (int r = 0; r < N; r++) if (g[r][c] == 0) { ok = false; break; }
            cols[c] = ok;
        }
        return new boolean[][]{rows, cols};
    }

    static int clearLines(int[][] g, boolean[][] L) {
        int n = 0;
        for (int i = 0; i < N; i++) {
            if (L[0][i]) n++;
            if (L[1][i]) n++;
        }
        for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) if (L[0][r] || L[1][c]) g[r][c] = 0;
        return n;
    }

    static boolean isEmpty(int[][] g) {
        for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) if (g[r][c] != 0) return false;
        return true;
    }

    /** 这几块能否按某种顺序全部放下（放置后模拟消除）。 */
    public static boolean canPlaceAll(int[][] g, int[] ids, int usedMask) {
        boolean any = false;
        for (int i = 0; i < ids.length; i++) {
            if ((usedMask & (1 << i)) != 0) continue;
            any = true;
            Shape s = SHAPES[ids[i]];
            for (int r = 0; r <= N - s.h; r++) for (int c = 0; c <= N - s.w; c++) {
                if (!fits(g, s, r, c)) continue;
                int[][] g2 = copy(g);
                stamp(g2, s, r, c);
                clearLines(g2, fullLines(g2));
                if (canPlaceAll(g2, ids, usedMask | (1 << i))) return true;
            }
        }
        return !any;
    }

    private int randShape() {
        double x = rnd.nextDouble() * TOTAL_W;
        for (int i = 0; i < SHAPES.length; i++) {
            x -= SHAPES[i].weight;
            if (x <= 0) return i;
        }
        return SHAPES.length - 1;
    }

    private boolean anyFits(int[][] g, int[] ids) {
        for (int id : ids) if (canFitAnywhere(g, SHAPES[id])) return true;
        return false;
    }

    public int[] dealTray(int[][] g) {
        int[] best = null;
        for (int t = 0; t < 8; t++) {
            int[] ids = {randShape(), randShape(), randShape()};
            if (canPlaceAll(g, ids, 0)) return ids;
            if (best == null && anyFits(g, ids)) best = ids;
        }
        if (best != null) return best;
        for (int t = 0; t < 200; t++) {
            int[] ids = {randShape(), randShape(), randShape()};
            if (anyFits(g, ids)) return ids;
        }
        return new int[]{0, 0, 0};
    }

    public static int scoreFor(int cells, int lines, int streak, boolean boardCleared) {
        int s = cells;
        if (lines > 0) s += 10 * lines * lines * streak;
        if (boardCleared) s += 200;
        return s;
    }

    public void newGame() {
        grid = new int[N][N];
        score = 0;
        streak = 0;
        over = false;
        newBest = false;
        tray = dealTray(grid);
    }

    public boolean checkOver() {
        for (int id : tray) if (id >= 0 && canFitAnywhere(grid, SHAPES[id])) return false;
        return true;
    }

    public Result place(int idx, int r, int c) {
        Result res = new Result();
        if (over || idx < 0 || idx > 2 || tray[idx] < 0) return res;
        Shape s = SHAPES[tray[idx]];
        if (!fits(grid, s, r, c)) return res;
        res.ok = true;
        res.cells = s.cells.length;
        stamp(grid, s, r, c);
        tray[idx] = -1;
        boolean[][] L = fullLines(grid);
        int cnt = 0;
        for (int rr = 0; rr < N; rr++) for (int cc = 0; cc < N; cc++) if (L[0][rr] || L[1][cc]) cnt++;
        res.clearedPos = new int[cnt];
        res.clearedColor = new int[cnt];
        int k = 0;
        for (int rr = 0; rr < N; rr++) for (int cc = 0; cc < N; cc++) if (L[0][rr] || L[1][cc]) {
            res.clearedPos[k] = rr * N + cc;
            res.clearedColor[k++] = grid[rr][cc];
        }
        res.lines = clearLines(grid, L);
        if (res.lines > 0) streak++; else streak = 0;
        res.boardCleared = res.lines > 0 && isEmpty(grid);
        res.gain = scoreFor(res.cells, res.lines, streak, res.boardCleared);
        score += res.gain;
        if (score > best) {
            best = score;
            newBest = true;
        }
        if (tray[0] < 0 && tray[1] < 0 && tray[2] < 0) tray = dealTray(grid);
        if (checkOver()) over = true;
        return res;
    }

    public String save() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) sb.append(grid[r][c]);
        sb.append('|').append(tray[0]).append(',').append(tray[1]).append(',').append(tray[2]);
        sb.append('|').append(score).append('|').append(streak);
        return sb.toString();
    }

    public boolean load(String s) {
        try {
            if (s == null) return false;
            String[] p = s.split("\\|");
            if (p.length != 4 || p[0].length() != N * N) return false;
            int[][] g = new int[N][N];
            for (int i = 0; i < N * N; i++) {
                int v = p[0].charAt(i) - '0';
                if (v < 0 || v > 8) return false;
                g[i / N][i % N] = v;
            }
            String[] t = p[1].split(",");
            if (t.length != 3) return false;
            int[] tr = new int[3];
            boolean allUsed = true;
            for (int i = 0; i < 3; i++) {
                tr[i] = Integer.parseInt(t[i]);
                if (tr[i] < -1 || tr[i] >= SHAPES.length) return false;
                if (tr[i] >= 0) allUsed = false;
            }
            int sc = Integer.parseInt(p[2]), st = Integer.parseInt(p[3]);
            if (sc < 0 || st < 0) return false;
            grid = g;
            tray = allUsed ? dealTray(g) : tr;
            score = sc;
            streak = st;
            newBest = false;
            over = checkOver();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
