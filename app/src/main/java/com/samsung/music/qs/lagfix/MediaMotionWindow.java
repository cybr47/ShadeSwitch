package com.samsung.music.qs.lagfix;

/** Tracks only recent changes in the shade and split-panel positions. */
final class MediaMotionWindow {
    private static final long FRAME_GAP_MS = 80;

    private float shadePosition, switchPosition;
    private long shadeUntil, switchUntil;

    void onShadePosition(float position, long now) {
        if (position > 0f && position < 1f) {
            if (position != shadePosition) shadeUntil = now + FRAME_GAP_MS;
        } else {
            shadeUntil = 0;
        }
        shadePosition = position;
    }

    void onSwitchPosition(boolean switching, float position, long now) {
        if (switching && position > 0f && position < 1f) {
            if (position != switchPosition) switchUntil = now + FRAME_GAP_MS;
            switchPosition = position;
        } else {
            switchUntil = 0;
            switchPosition = 0f;
        }
    }

    boolean isMoving(long now) {
        return now < shadeUntil || now < switchUntil;
    }
}
