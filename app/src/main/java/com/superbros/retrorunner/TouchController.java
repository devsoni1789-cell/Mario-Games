package com.superbros.retrorunner;

final class TouchController {
    enum Action { NONE, LEFT, RIGHT, JUMP, PAUSE }

    Action map(float x, float y, int w, int h, float tile) {
        // Large, separated landscape controls with a safe central dead zone.
        float bottom = h - tile * 0.55f;
        if (y < tile * 1.25f && x > w - tile * 1.25f) return Action.PAUSE;
        if (y < h - tile * 3.1f) return Action.NONE;
        if (x < w * 0.27f) return Action.LEFT;
        if (x < w * 0.47f) return Action.RIGHT;
        if (x > w * 0.60f) return Action.JUMP;
        return Action.NONE;
    }
}
