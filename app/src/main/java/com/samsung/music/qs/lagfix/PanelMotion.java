package com.samsung.music.qs.lagfix;

final class PanelMotion {
    static final float HEADER_TRAVEL_DP = 24f;
    static final float HEADER_FADE_END = 0.45f;
    static final long SPRING_BACK_MS = 400;

    private static final double DAMPING = 10.2;
    private static final double REST_RESPONSE = springResponse(1);

    private PanelMotion() {}

    static boolean favorsHorizontal(float dx, float dy, float slop) {
        float x = Math.abs(dx);
        float y = Math.abs(dy);
        return (x > slop || y > slop) && x * 1.5f > y;
    }

    static boolean withinAxisHandoff(float dx, float dy, float slop) {
        float limit = 3f * slop;
        return dx * dx + dy * dy <= limit * limit;
    }

    static float axisHandoffOffset(float dx, float fraction, float width) {
        return Math.copySign(fraction * width, dx) - dx;
    }

    static float settleTarget(float distance, float velocity, float width, float minVelocity,
            int direction, boolean canceled) {
        if (canceled || distance * direction <= 0f) return 0f;
        boolean complete = Math.abs(velocity) > minVelocity
                ? velocity * direction > 0f : Math.abs(distance) > width * 0.5f;
        return complete ? direction * width : 0f;
    }

    static long settleDuration(float distance, float width) {
        return 200L + (long) (Math.min(1f, Math.abs(distance) / width) * 100f);
    }

    static float springBack(float progress) {
        if (progress <= 0f) return 0f;
        if (progress >= 1f) return 1f;
        return (float) (springResponse(progress) / REST_RESPONSE);
    }

    private static double springResponse(double progress) {
        double time = DAMPING * progress;
        return 1 - (1 + time) * Math.exp(-time);
    }
}
