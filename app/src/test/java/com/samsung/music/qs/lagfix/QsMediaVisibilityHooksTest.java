package com.samsung.music.qs.lagfix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public final class QsMediaVisibilityHooksTest {
    @Test
    public void intermediateSlideSamplesKeepOneListeningValue() {
        Object visible = QsMediaVisibilityHooks.listeningFraction(0f);
        assertEquals(0f, visible);
        assertSame(visible, QsMediaVisibilityHooks.listeningFraction(0.4f));
        assertSame(visible, QsMediaVisibilityHooks.listeningFraction(Math.nextDown(1f)));
    }

    @Test
    public void settledAndInvalidSamplesMatchTheNativeListeningTest() {
        Object hidden = QsMediaVisibilityHooks.listeningFraction(1f);
        assertEquals(1f, hidden);
        assertSame(hidden, QsMediaVisibilityHooks.listeningFraction(1.1f));
        assertSame(hidden, QsMediaVisibilityHooks.listeningFraction(Float.NaN));
    }
}
