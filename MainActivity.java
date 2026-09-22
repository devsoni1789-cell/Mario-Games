package com.superbros.retrorunner;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

public class MainActivity extends Activity {
    private GameView game;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        game = new GameView(this);
        setContentView(game);
    }

    @Override public void onBackPressed() {
        if (game.isPlaying()) game.togglePause();
        else super.onBackPressed();
    }
}
