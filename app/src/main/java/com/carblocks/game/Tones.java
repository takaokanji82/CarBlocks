package com.carblocks.game;

/** 合成音效的 PCM 数据（16 位单声道）。 */
public final class Tones {
    public static final int RATE = 22050;

    private static void tone(float[] buf, double freq, double start, double dur, double vol, boolean tri) {
        int s0 = (int) (start * RATE), n = (int) (dur * RATE);
        for (int i = 0; i < n && s0 + i < buf.length; i++) {
            double t = i / (double) RATE, ph = t * freq;
            double v = tri ? 4 * Math.abs(ph - Math.floor(ph + 0.5)) - 1 : Math.sin(2 * Math.PI * ph);
            double env = Math.min(1, t / 0.008) * Math.exp(-5 * t / dur);
            buf[s0 + i] += (float) (v * env * vol);
        }
    }

    public static short[] make(int kind, int n) {
        float[] b;
        if (kind == Scene.SND_PLACE) {
            b = new float[(int) (0.12 * RATE)];
            tone(b, 220, 0, 0.1, 0.5, true);
        } else if (kind == Scene.SND_PICK) {
            b = new float[(int) (0.07 * RATE)];
            tone(b, 520, 0, 0.06, 0.25, false);
        } else if (kind == Scene.SND_CLEAR) {
            double[] sc = {523, 659, 784, 1047, 1319, 1568, 2093};
            n = Math.max(0, Math.min(n, 6));
            b = new float[(int) ((n * 0.07 + 0.26) * RATE)];
            for (int i = 0; i <= n; i++) tone(b, sc[i], i * 0.07, 0.24, 0.4, false);
        } else {
            double[] sc = {392, 330, 262, 196};
            b = new float[(int) ((3 * 0.16 + 0.34) * RATE)];
            for (int i = 0; i < 4; i++) tone(b, sc[i], i * 0.16, 0.32, 0.45, true);
        }
        short[] out = new short[b.length];
        for (int i = 0; i < b.length; i++) {
            float v = Math.max(-1, Math.min(1, b[i]));
            out[i] = (short) (v * 32767 * 0.8f);
        }
        return out;
    }
}
