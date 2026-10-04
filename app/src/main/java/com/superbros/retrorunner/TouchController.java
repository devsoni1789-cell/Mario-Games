package com.superbros.retrorunner;

final class TouchController {
    enum Action { NONE, LEFT, RIGHT, JUMP, PAUSE }

    Action map(float x, float y, int w, int h, float tile) {
        // Match the actual on-screen control layout in GameView. This works
        // correctly in both portrait and landscape because the button centers
        // are derived from tile size rather than screen-width percentages.
        float pauseR = Math.max(tile * 0.95f, 48f);
        float pauseX = w - Math.max(tile * 0.7f, 34f);
        float pauseY = Math.max(tile * 0.75f, 42f);
        if (x >= pauseX - pauseR && y <= pauseY + pauseR * 0.8f) return Action.PAUSE;

        float cy = h - tile * 2.0f;
        float leftX = tile * 2.1f;
        float rightX = tile * 6.2f;
        float jumpX = w - tile * 2.6f;
        float jumpY = h - tile * 2.2f;

        float controlY = Math.max(tile * 1.5f, h * 0.20f);
        if (y < cy - controlY) return Action.NONE;

        // Use nearest-button hit regions. The old percentage zones caused
        // the right button to be interpreted as LEFT on wide/landscape screens.
        float dirRadius = Math.max(tile * 1.65f, 70f);
        if (Math.abs(y - cy) <= dirRadius) {
            float split = (leftX + rightX) * 0.5f;
            if (x >= leftX - dirRadius && x < split) return Action.LEFT;
            if (x >= split && x <= rightX + dirRadius) return Action.RIGHT;
        }

        float jumpR = Math.max(tile * 2.0f, 90f);
        if (Math.abs(x - jumpX) <= jumpR && Math.abs(y - jumpY) <= jumpR) return Action.JUMP;

        return Action.NONE;
    }
}
