package com.superbros.retrorunner;

final class TouchController {
    enum Action { NONE, LEFT, RIGHT, JUMP, PAUSE }

    Action map(float x, float y, int w, int h, float tile) {
        // Large forgiving touch zones. Controls are deliberately independent so
        // LEFT/RIGHT + JUMP can be pressed simultaneously.
        float pauseR = Math.max(tile * 0.95f, 48f);
        float pauseX = w - Math.max(tile * 0.7f, 34f);
        float pauseY = Math.max(tile * 0.75f, 42f);
        if (x >= pauseX - pauseR && y <= pauseY + pauseR * 0.8f) {
            return Action.PAUSE;
        }

        float buttonTop = h * 0.58f;
        if (y < buttonTop) return Action.NONE;

        float leftZoneEnd = w * 0.27f;
        float rightZoneEnd = w * 0.50f;
        float jumpZoneStart = w * 0.72f;

        if (x < leftZoneEnd) return Action.LEFT;
        if (x < rightZoneEnd) return Action.RIGHT;
        if (x >= jumpZoneStart) return Action.JUMP;

        // Small dead zone in the middle prevents accidental direction changes.
        return Action.NONE;
    }
}
