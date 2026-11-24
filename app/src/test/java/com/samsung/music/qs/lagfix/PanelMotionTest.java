package com.samsung.music.qs.lagfix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PanelMotionTest {
    @Test
    public void diagonalCanBecomeHorizontalAfterSlop() {
        assertFalse(PanelMotion.favorsHorizontal(7f, 9f, 10f));
        assertTrue(PanelMotion.favorsHorizontal(12f, 17f, 10f));
        assertFalse(PanelMotion.favorsHorizontal(12f, 19f, 10f));
        assertTrue(PanelMotion.withinAxisHandoff(12f, 17f, 10f));
        assertFalse(PanelMotion.withinAxisHandoff(25f, 25f, 10f));
    }

    @Test
    public void handoffPreservesTheSignedDisplacement() {
        assertEquals(26f, -21f + PanelMotion.axisHandoffOffset(-21f, 26f), 0f);
        assertEquals(26f, 21f + PanelMotion.axisHandoffOffset(21f, 26f), 0f);
        assertEquals(-17f, 22f + PanelMotion.axisHandoffOffset(22f, -17f), 0f);
    }

    @Test
    public void slowReleaseUsesDistanceAndFastReleaseUsesDirection() {
        assertEquals(-1000f, PanelMotion.settleTarget(-600f, 50f, 1000f, 400f, -1, false), 0f);
        assertEquals(0f, PanelMotion.settleTarget(500f, 0f, 1000f, 400f, 1, false), 0f);
        assertEquals(1000f, PanelMotion.settleTarget(100f, 500f, 1000f, 400f, 1, false), 0f);
        assertEquals(0f, PanelMotion.settleTarget(800f, -600f, 1000f, 400f, 1, false), 0f);
        assertEquals(0f, PanelMotion.settleTarget(-800f, -600f, 1000f, 400f, -1, true), 0f);
        assertEquals(1000f, PanelMotion.settleTarget(-800f, 600f, 1000f, 400f, 1, false), 0f);
    }

    @Test
    public void zeroVelocityTimeUsesAbsoluteTravel() {
        assertEquals(260L, PanelMotion.settleDuration(-600f, 1000f));
        assertEquals(260L, PanelMotion.settleDuration(600f, 1000f));
        assertEquals(320L, PanelMotion.settleDuration(1200f, 1000f));
    }

    @Test
    public void springSettlesWithoutOvershoot() {
        assertEquals(0f, PanelMotion.springBack(0f), 0f);
        assertEquals(0.723013994f, PanelMotion.springBack(0.25f), 0.000001f);
        assertEquals(0.963181044f, PanelMotion.springBack(0.5f), 0.000001f);
        float previous = 0f;
        for (int sample = 1; sample <= 100; sample++) {
            float value = PanelMotion.springBack(sample / 100f);
            assertTrue(value >= previous && value <= 1f);
            previous = value;
        }
        assertEquals(1f, previous, 0f);
    }
}
