package com.carblocks.game;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

public class GameView extends View implements Scene.Listener, Gfx {
    private final Game game = new Game();
    private final Scene scene;
    private final SharedPreferences prefs;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Canvas canvas;
    private int pointerId = -1;

    public GameView(Context ctx) {
        super(ctx);
        prefs = ctx.getSharedPreferences("carblocks", Context.MODE_PRIVATE);
        game.best = prefs.getInt("best", 0);
        if (!game.load(prefs.getString("state", null))) game.newGame();
        scene = new Scene(game, this);
        scene.soundOn = prefs.getBoolean("sound", true);
        paint.setTextAlign(Paint.Align.CENTER);
        setHapticFeedbackEnabled(false);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        scene.layout(w, h);
    }

    @Override
    protected void onDraw(Canvas c) {
        canvas = c;
        boolean busy = scene.draw(this, SystemClock.uptimeMillis());
        canvas = null;
        if (busy) postInvalidateOnAnimation();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        long now = SystemClock.uptimeMillis();
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            pointerId = e.getPointerId(0);
            scene.down(e.getX(), e.getY(), now);
        } else if (action == MotionEvent.ACTION_MOVE) {
            int i = e.findPointerIndex(pointerId);
            if (i >= 0) scene.move(e.getX(i), e.getY(i));
        } else if (action == MotionEvent.ACTION_UP) {
            int i = e.findPointerIndex(pointerId);
            if (i >= 0) scene.move(e.getX(i), e.getY(i));
            scene.up(now);
            pointerId = -1;
        } else if (action == MotionEvent.ACTION_CANCEL) {
            scene.cancel();
            pointerId = -1;
        }
        invalidate();
        return true;
    }

    // ---- Gfx ----
    @Override
    public void rrect(float x, float y, float w, float h, float r, int argb) {
        paint.setColor(argb);
        if (r <= 0) {
            canvas.drawRect(x, y, x + w, y + h, paint);
        } else {
            rect.set(x, y, x + w, y + h);
            canvas.drawRoundRect(rect, r, r, paint);
        }
    }

    @Override
    public void text(String s, float cx, float cy, float size, int argb, boolean bold) {
        paint.setColor(argb);
        paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        canvas.drawText(s, cx, cy - (paint.ascent() + paint.descent()) / 2, paint);
    }

    // ---- Scene.Listener ----
    @Override
    public void save() {
        prefs.edit()
                .putString("state", game.save())
                .putInt("best", game.best)
                .putBoolean("sound", scene.soundOn)
                .apply();
    }

    @Override
    public void sound(int kind, int n) {
        if (!scene.soundOn) return;
        try {
            short[] pcm = Tones.make(kind, n);
            final AudioTrack t = new AudioTrack(AudioManager.STREAM_MUSIC, Tones.RATE,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, pcm.length * 2, AudioTrack.MODE_STATIC);
            t.write(pcm, 0, pcm.length);
            t.play();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        t.release();
                    } catch (Throwable ignored) {
                    }
                }
            }, pcm.length * 1000L / Tones.RATE + 300);
        } catch (Throwable ignored) {
            // 没有音频输出时静默
        }
    }
}
