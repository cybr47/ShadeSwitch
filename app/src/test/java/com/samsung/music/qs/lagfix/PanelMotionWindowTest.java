package com.samsung.music.qs.lagfix;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PanelMotionWindowTest {
    @Test
    public void openingAndClosingStopAtTheirEndpoints() {
        PanelMotionWindow motion = new PanelMotionWindow();
        motion.onShadePosition(0.2f, 10);
        assertTrue(motion.isMoving(11));
        motion.onShadePosition(1f, 20);
        assertFalse(motion.isMoving(20));

        motion.onShadePosition(0.9f, 30);
        assertTrue(motion.isMoving(31));
        motion.onShadePosition(0f, 40);
        assertFalse(motion.isMoving(40));
    }

    @Test
    public void repeatedStationarySamplesDoNotKeepTheWaveDeferred() {
        PanelMotionWindow motion = new PanelMotionWindow();
        motion.onShadePosition(0.5f, 10);
        motion.onShadePosition(0.5f, 70);
        assertTrue(motion.isMoving(89));
        assertFalse(motion.isMoving(90));

        motion.onSwitchPosition(true, 0.3f, 100);
        motion.onSwitchPosition(true, 0.3f, 170);
        assertTrue(motion.isMoving(179));
        assertFalse(motion.isMoving(180));
    }

    @Test
    public void settlingOnePanelMotionLeavesTheOtherActive() {
        PanelMotionWindow motion = new PanelMotionWindow();
        motion.onShadePosition(0.2f, 10);
        motion.onSwitchPosition(true, 0.4f, 11);
        motion.onShadePosition(1f, 20);
        assertTrue(motion.isMoving(21));
        motion.onSwitchPosition(false, 0.4f, 22);
        assertFalse(motion.isMoving(22));
    }

    @Test
    public void resetClearsBothMotionsAndAcceptsTheNextSample() {
        PanelMotionWindow motion = new PanelMotionWindow();
        motion.onShadePosition(0.5f, 10);
        motion.onSwitchPosition(true, 0.4f, 11);
        motion.reset();
        assertFalse(motion.isMoving(12));

        motion.onShadePosition(0.5f, 13);
        assertTrue(motion.isMoving(13));
    }
}
