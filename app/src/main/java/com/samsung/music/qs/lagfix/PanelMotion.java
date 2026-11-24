package com.samsung.music.qs.lagfix;

final class PanelMotion {
    static final float HEADER_TRAVEL_DP = 24f;
    static final float HEADER_FADE_END = 0.45f;
    static final float SWITCH_CONTROL_TRAVEL_X = 15f;
    static final float SWITCH_CONTROL_TRAVEL_Y = 30f;
    static final long SPRING_BACK_MS = 400;

    private static final double SPRING_RATE = Math.sqrt(650.0);
    private static final double REST_RESPONSE = springResponse(1);

    private PanelMotion() {}

    static boolean favorsHorizontal(float dx, float dy, float slop) {
        float x = Math.abs(dx);
        float y = Math.abs(dy);
        return (x > slop || y > slop) && x * 1.5f > y;
    }

    static boolean withinAxisHandoff(float dx, float dy, float slop) {
        return Math.hypot(dx, dy) <= 3f * slop;
    }

    static float axisHandoffOffset(float dx, float dy) {
        return dy - dx;
    }

    static float settleTarget(float distance, float velocity, float width, float minVelocity,
            int direction, boolean canceled) {
        if (canceled) return 0f;
        boolean complete = Math.abs(velocity) > minVelocity
                ? velocity * direction > 0f : Math.abs(distance) > width * 0.5f;
        return complete ? direction * width : 0f;
    }

    static long settleDuration(float distance, float width) {
        return (long) (200f + Math.abs(distance) / width * 100f);
    }

    static float springBack(float progress) {
        if (progress <= 0f) return 0f;
        if (progress >= 1f) return 1f;
        return (float) (springResponse(progress) / REST_RESPONSE);
    }

    private static double springResponse(double progress) {
        double time = (progress * (SPRING_BACK_MS / 1000.0)) * SPRING_RATE;
        return 1 - (1 + time) * Math.exp(-time);
    }
}
