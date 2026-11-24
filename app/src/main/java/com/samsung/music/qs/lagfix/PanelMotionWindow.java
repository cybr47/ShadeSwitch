package com.samsung.music.qs.lagfix;

/** Tracks recent changes in the shade and split-panel positions. */
final class PanelMotionWindow {
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

    void reset() {
        shadePosition = switchPosition = 0f;
        shadeUntil = switchUntil = 0L;
    }
}
