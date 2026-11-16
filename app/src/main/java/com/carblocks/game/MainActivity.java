package com.carblocks.game;

import android.app.Activity;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.WindowManager;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        setContentView(new GameView(this));
    }
}
