package com.carblocks.game;

import java.util.ArrayList;

/** 布局、绘制和触摸交互，不依赖安卓类。 */
public final class Scene {
    public interface Listener {
        void sound(int kind, int n);

        void save();
    }

    public static final int SND_PLACE = 0, SND_PICK = 1, SND_CLEAR = 2, SND_OVER = 3;
    static final int N = Game.N;
    static final int[] COLORS = {0, 0xffff6b6b, 0xffffa94d, 0xffffd43b, 0xff69db7c, 0xff38d9a9, 0xff4dabf7, 0xff9775fa, 0xfff783ac};
    static final int BG = 0xff141a2e, BOARD = 0xff1c2340, SLOT = 0xff252d52, TEXT = 0xffe8ecff, MUTED = 0xff8089b3,
            GOLD = 0xffffd43b, BTN = 0xff27305a, PRIMARY = 0xff4c6ef5;
    static final long CLEAR_MS = 320, FLOAT_MS = 1100, OVER_DELAY = 650, ARM_MS = 2500;

    public final Game g;
    public boolean soundOn = true;
    private final Listener listener;

    // 布局
    float W, H, size, bx, by, cell, tcell;
    boolean land;
    final float[][] slots = new float[3][4];
    final float[] btnNew = new float[4], btnSnd = new float[4], btnAgain = new float[4];

    // 交互状态
    int dragIdx = -1;
    float dragX, dragY;
    long armUntil, overAt = -1;
    boolean overShown;

    private static final class Anim {
        int r, c, color;
        long t0;
    }

    private static final class Floater {
        String text;
        float r, c;
        long t0;
    }

    private final ArrayList<Anim> anims = new ArrayList<Anim>();
    private final ArrayList<Floater> floats = new ArrayList<Floater>();

    public Scene(Game g, Listener l) {
        this.g = g;
        this.listener = l;
        if (g.over) {
            overAt = 0;
        }
    }

    public void layout(float w, float h) {
        W = w;
        H = h;
        if (w <= 0 || h <= 0) return;
        land = W > H * 1.15f;
        if (land) {
            size = Math.min(H * 0.92f, W * 0.56f);
            bx = (W - size) / 2;
            by = (H - size) / 2;
            cell = size / N;
            float tx = bx + size, tw = W - tx, sh = H / 3;
            for (int i = 0; i < 3; i++) set(slots[i], tx, sh * i, tw, sh);
            float bw = Math.min(bx * 0.62f, cell * 2.4f), bh = cell * 0.82f, cx = bx / 2;
            set(btnNew, cx - bw / 2, H * 0.66f, bw, bh);
            set(btnSnd, cx - bw / 2, H * 0.66f + bh * 1.25f, bw, bh);
        } else {
            size = Math.min(W * 0.94f, H * 0.6f);
            float hudH = H * 0.13f;
            bx = (W - size) / 2;
            by = hudH + H * 0.02f;
            cell = size / N;
            float ty = by + size, th = H - ty - H * 0.03f, sw = W / 3;
            for (int i = 0; i < 3; i++) set(slots[i], sw * i, ty, sw, th);
            float bw = W * 0.24f, bh = hudH * 0.5f, y = (hudH - bh) / 2 + H * 0.01f;
            set(btnNew, W * 0.46f, y, bw, bh);
            set(btnSnd, W * 0.73f, y, bw, bh);
        }
        tcell = Math.min(cell * 0.62f, Math.min(slots[0][2], slots[0][3]) / 5.6f);
        float pw = cell * 4.8f, ph = cell * 5.4f;
        float bw = cell * 2.6f, bh = cell * 0.9f;
        set(btnAgain, W / 2 - bw / 2, H / 2 + ph / 2 - bh - cell * 0.6f, bw, bh);
    }

    private static void set(float[] a, float x, float y, float w, float h) {
        a[0] = x;
        a[1] = y;
        a[2] = w;
        a[3] = h;
    }

    private static boolean hit(float[] a, float x, float y) {
        return x >= a[0] && x < a[0] + a[2] && y >= a[1] && y < a[1] + a[3];
    }

    static int alpha(int argb, float a) {
        if (a < 0) a = 0;
        if (a > 1) a = 1;
        int al = Math.round(((argb >>> 24) & 255) * a);
        return (al << 24) | (argb & 0xffffff);
    }

    private void block(Gfx f, float x, float y, float s, int color, float a, float scale) {
        float gap = s * 0.06f, w = (s - gap * 2) * scale, o = (s - w) / 2;
        if (w <= 0) return;
        f.rrect(x + o, y + o, w, w, w * 0.2f, alpha(color, a));
        f.rrect(x + o + w * 0.12f, y + o + w * 0.1f, w * 0.76f, w * 0.3f, w * 0.14f, alpha(0x47ffffff, a));
        f.rrect(x + o + w * 0.12f, y + o + w * 0.72f, w * 0.76f, w * 0.18f, w * 0.09f, alpha(0x29000000, a));
    }

    private void shape(Gfx f, Game.Shape s, float x, float y, float sz, float a) {
        for (int[] c : s.cells) block(f, x + c[1] * sz, y + c[0] * sz, sz, COLORS[s.color], a, 1);
    }

    private void button(Gfx f, float[] b, String label, int color, boolean bold) {
        f.rrect(b[0], b[1], b[2], b[3], b[3] * 0.28f, color);
        f.text(label, b[0] + b[2] / 2, b[1] + b[3] / 2, b[3] * 0.4f, TEXT, bold);
    }

    // 拖动中的方块左上角位置及对应的棋盘格
    private float dpx, dpy;
    private int dr, dc;
    private boolean dok;

    private void dragTarget() {
        Game.Shape s = Game.SHAPES[g.tray[dragIdx]];
        dpx = dragX - s.w * cell / 2;
        dpy = dragY - cell * 1.1f - s.h * cell;
        dc = Math.round((dpx - bx) / cell);
        dr = Math.round((dpy - by) / cell);
        dok = Game.fits(g.grid, s, dr, dc);
    }

    /** 绘制一帧；返回 true 表示还有动画，需要继续刷新。 */
    public boolean draw(Gfx f, long now) {
        if (W <= 0 || H <= 0) return false;
        boolean busy = false;
        f.rrect(0, 0, W, H, 0, BG);

        // 信息区
        if (land) {
            float cx = bx / 2;
            f.text("Car Blocks", cx, H * 0.13f, cell * 0.42f, 0xff8fa2ff, true);
            f.text("SCORE", cx, H * 0.26f, cell * 0.25f, MUTED, false);
            f.text(String.valueOf(g.score), cx, H * 0.345f, cell * 0.85f, TEXT, true);
            f.text("BEST", cx, H * 0.47f, cell * 0.25f, MUTED, false);
            f.text(String.valueOf(g.best), cx, H * 0.535f, cell * 0.52f, GOLD, true);
            f.text("Please play only while parked", cx, H * 0.965f, cell * 0.2f, 0xff4d567f, false);
        } else {
            float hudH = H * 0.13f, mid = hudH / 2 + H * 0.01f;
            f.text("SCORE", W * 0.13f, mid - hudH * 0.28f, cell * 0.26f, MUTED, false);
            f.text(String.valueOf(g.score), W * 0.13f, mid + hudH * 0.12f, cell * 0.7f, TEXT, true);
            f.text("BEST", W * 0.33f, mid - hudH * 0.28f, cell * 0.26f, MUTED, false);
            f.text(String.valueOf(g.best), W * 0.33f, mid + hudH * 0.12f, cell * 0.5f, GOLD, true);
            f.text("Please play only while parked", W / 2, H * 0.985f, cell * 0.2f, 0xff4d567f, false);
        }
        boolean armed = now < armUntil;
        if (armed) busy = true;
        button(f, btnNew, armed ? "Restart?" : "New Game", BTN, false);
        button(f, btnSnd, soundOn ? "Sound: On" : "Sound: Off", BTN, false);

        // 棋盘
        float pad = cell * 0.12f;
        f.rrect(bx - pad, by - pad, size + pad * 2, size + pad * 2, cell * 0.3f, BOARD);
        float gap = cell * 0.06f;
        for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) {
            float x = bx + c * cell, y = by + r * cell;
            if (g.grid[r][c] != 0) block(f, x, y, cell, COLORS[g.grid[r][c]], 1, 1);
            else f.rrect(x + gap, y + gap, cell - gap * 2, cell - gap * 2, cell * 0.18f, SLOT);
        }

        // 拖动预览
        boolean dragging = dragIdx >= 0 && g.tray[dragIdx] >= 0;
        if (dragging) {
            dragTarget();
            if (dok) {
                Game.Shape s = Game.SHAPES[g.tray[dragIdx]];
                int[][] g2 = Game.copy(g.grid);
                Game.stamp(g2, s, dr, dc);
                boolean[][] L = Game.fullLines(g2);
                for (int i = 0; i < N; i++) {
                    if (L[0][i]) f.rrect(bx, by + i * cell, size, cell, cell * 0.2f, 0x38ffffff);
                    if (L[1][i]) f.rrect(bx + i * cell, by, cell, size, cell * 0.2f, 0x38ffffff);
                }
                shape(f, s, bx + dc * cell, by + dr * cell, cell, 0.45f);
            }
        }

        // 消除动画
        for (int i = anims.size() - 1; i >= 0; i--) if (now - anims.get(i).t0 >= CLEAR_MS) anims.remove(i);
        for (Anim a : anims) {
            float p = Math.max(0, (now - a.t0) / (float) CLEAR_MS);
            block(f, bx + a.c * cell, by + a.r * cell, cell, p < 0.25f ? 0xffffffff : COLORS[a.color], 1 - p,
                    1 + 0.15f * (float) Math.sin(p * Math.PI) - p * 0.5f);
        }
        if (!anims.isEmpty()) busy = true;

        // 待选方块
        for (int i = 0; i < 3; i++) {
            if (g.tray[i] < 0 || (dragging && dragIdx == i)) continue;
            Game.Shape s = Game.SHAPES[g.tray[i]];
            float[] sl = slots[i];
            boolean dim = !g.over && !Game.canFitAnywhere(g.grid, s);
            shape(f, s, sl[0] + (sl[2] - s.w * tcell) / 2, sl[1] + (sl[3] - s.h * tcell) / 2, tcell, dim ? 0.3f : 1);
        }
        if (dragging) shape(f, Game.SHAPES[g.tray[dragIdx]], dpx, dpy, cell, 0.95f);

        // 飘字
        for (int i = floats.size() - 1; i >= 0; i--) if (now - floats.get(i).t0 >= FLOAT_MS) floats.remove(i);
        for (Floater fl : floats) {
            float p = Math.max(0, (now - fl.t0) / (float) FLOAT_MS);
            float a = p < 0.7f ? 1 : 1 - (p - 0.7f) / 0.3f;
            float fx = Math.max(bx + cell * 1.8f, Math.min(bx + size - cell * 1.8f, bx + fl.c * cell));
            float fy = by + fl.r * cell - p * cell * 1.2f, sz = cell * 0.55f, o = cell * 0.035f;
            f.text(fl.text, fx + o, fy + o, sz, alpha(0xe60f1326, a), true);
            f.text(fl.text, fx - o, fy + o, sz, alpha(0xe60f1326, a), true);
            f.text(fl.text, fx, fy, sz, alpha(0xfffff3bf, a), true);
        }
        if (!floats.isEmpty()) busy = true;

        // 结束面板
        if (g.over) {
            if (overAt < 0) overAt = now + OVER_DELAY;
            if (now >= overAt) {
                if (!overShown) {
                    overShown = true;
                    listener.sound(SND_OVER, 0);
                }
                float pw = cell * 4.8f, ph = cell * 5.4f, px = W / 2 - pw / 2, py = H / 2 - ph / 2;
                f.rrect(0, 0, W, H, 0, 0xd10a0d1c);
                f.rrect(px, py, pw, ph, cell * 0.4f, 0xff1f2748);
                f.text("No more moves", W / 2, py + cell * 0.95f, cell * 0.52f, TEXT, true);
                f.text(String.valueOf(g.score), W / 2, py + cell * 2.2f, cell * 1.15f, TEXT, true);
                if (g.newBest) f.text("New best!", W / 2, py + cell * 3.25f, cell * 0.32f, GOLD, true);
                else f.text("Best " + g.best, W / 2, py + cell * 3.25f, cell * 0.3f, MUTED, false);
                button(f, btnAgain, "Play Again", PRIMARY, true);
            } else {
                busy = true;
            }
        }
        return busy;
    }

    private void startNew() {
        g.newGame();
        anims.clear();
        floats.clear();
        dragIdx = -1;
        overAt = -1;
        overShown = false;
        armUntil = 0;
        listener.save();
    }

    public void down(float x, float y, long now) {
        if (overShown) {
            if (hit(btnAgain, x, y)) startNew();
            return;
        }
        if (hit(btnSnd, x, y)) {
            soundOn = !soundOn;
            listener.save();
            listener.sound(SND_PICK, 0);
            return;
        }
        if (hit(btnNew, x, y)) {
            if (g.score == 0 || now < armUntil) startNew();
            else armUntil = now + ARM_MS;
            return;
        }
        if (g.over || dragIdx >= 0) return;
        for (int i = 0; i < 3; i++) {
            if (hit(slots[i], x, y) && g.tray[i] >= 0) {
                dragIdx = i;
                dragX = x;
                dragY = y;
                listener.sound(SND_PICK, 0);
                return;
            }
        }
    }

    public void move(float x, float y) {
        if (dragIdx >= 0) {
            dragX = x;
            dragY = y;
        }
    }

    public void cancel() {
        dragIdx = -1;
    }

    public void up(long now) {
        if (dragIdx < 0) return;
        int idx = dragIdx;
        dragIdx = -1;
        if (g.tray[idx] < 0) return;
        dragIdx = idx;
        dragTarget();
        dragIdx = -1;
        if (!dok) return;
        Game.Shape s = Game.SHAPES[g.tray[idx]];
        Game.Result res = g.place(idx, dr, dc);
        if (!res.ok) return;
        if (res.lines > 0) {
            for (int i = 0; i < res.clearedPos.length; i++) {
                Anim a = new Anim();
                a.r = res.clearedPos[i] / N;
                a.c = res.clearedPos[i] % N;
                a.color = res.clearedColor[i];
                a.t0 = now;
                anims.add(a);
            }
            String txt = "+" + res.gain;
            if (res.boardCleared) txt = "Board clear!  " + txt;
            else if (g.streak > 1) txt = "Combo x" + g.streak + "  " + txt;
            else if (res.lines > 1) txt = res.lines + " lines  " + txt;
            Floater fl = new Floater();
            fl.text = txt;
            fl.r = dr + s.h / 2f;
            fl.c = dc + s.w / 2f;
            fl.t0 = now;
            floats.add(fl);
            listener.sound(SND_CLEAR, Math.min(res.lines + g.streak - 1, 6));
        } else {
            listener.sound(SND_PLACE, 0);
        }
        if (g.over) overAt = now + OVER_DELAY;
        listener.save();
    }
}
