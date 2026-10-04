package com.superbros.retrorunner;

import android.view.Choreographer;

/** Vsync-driven loop. GameView only renders in onDraw; simulation is never driven by onDraw. */
final class GameLoop implements Choreographer.FrameCallback {
    private final GameView view;
    private boolean running;

    GameLoop(GameView view) { this.view = view; }

    void start() {
        if (running) return;
        running = true;
        view.resetFrameClock();
        Choreographer.getInstance().postFrameCallback(this);
    }

    void stop() {
        if (!running) return;
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
    }

    @Override public void doFrame(long frameTimeNanos) {
        if (!running) return;
        view.onFrame(frameTimeNanos);
        Choreographer.getInstance().postFrameCallback(this);
    }
}
