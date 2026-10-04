package com.superbros.retrorunner;

final class TouchController {
    enum Action { NONE, LEFT, RIGHT, JUMP, PAUSE }

    Action map(float x, float y, int w, int h, float tile) {
        // Match the real on-screen button positions instead of using arbitrary
        // percentages. This keeps controls correct on different aspect ratios.
        float pauseR = tile * 0.9f;
        float pauseX = w - tile * 0.65f;
        float pauseY = tile * 0.75f;
        if (x >= pauseX - pauseR && x <= pauseX + pauseR
                && y >= pauseY - pauseR && y <= pauseY + pauseR) {
            return Action.PAUSE;
        }

        float cy = h - tile * 2.0f;
        float leftX = tile * 2.1f;
        float rightX = tile * 6.2f;
        float jumpX = w - tile * 2.6f;
        float jumpY = h - tile * 2.2f;

        float buttonR = tile * 1.55f;
        if (y >= cy - buttonR && y <= cy + buttonR) {
            if (Math.abs(x - leftX) <= buttonR) return Action.LEFT;
            if (Math.abs(x - rightX) <= buttonR) return Action.RIGHT;
        }

        float jumpR = tile * 1.85f;
        if (Math.abs(x - jumpX) <= jumpR && Math.abs(y - jumpY) <= jumpR) {
            return Action.JUMP;
        }

        return Action.NONE;
    }
}
