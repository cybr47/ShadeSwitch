package com.samsung.music.qs.lagfix;

import static com.samsung.music.qs.lagfix.NativePanels.field;
import static com.samsung.music.qs.lagfix.NativePanels.method;

import android.os.Looper;
import android.os.SystemClock;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Observes Samsung's progress once for the rendering hooks that use it. */
final class PanelMovementHooks {
    private static final int SWITCHING = 2;

    private final SamsungMusicWavesHook module;
    private final PanelMotionWindow motion;
    private final Method expansionChanged, splitChanged;
    private final Field expansionFraction, splitFraction, splitEnabled, splitState;
    private boolean failed;

    private PanelMovementHooks(SamsungMusicWavesHook module, ClassLoader loader, PanelMotionWindow motion)
            throws ReflectiveOperationException {
        this.module = module;
        this.motion = motion;
        Class<?> manager = loader.loadClass("com.android.systemui.qs.animator.SecQSImplAnimatorManager");
        Class<?> expansionEvent = loader.loadClass("com.android.systemui.shade.ShadeExpansionChangeEvent");
        Class<?> splitEvent = loader.loadClass("com.android.systemui.shade.PanelTransitionStateChangeEvent");
        expansionChanged = method(manager, "onPanelExpansionChanged", expansionEvent);
        splitChanged = method(manager, "onPanelTransitionStateChanged", splitEvent);
        expansionFraction = field(expansionEvent, "fraction");
        splitFraction = field(splitEvent, "fraction");
        splitEnabled = field(splitEvent, "enabled");
        splitState = field(splitEvent, "state");
    }

    static PanelMotionWindow install(SamsungMusicWavesHook module, ClassLoader loader) {
        PanelMotionWindow motion = new PanelMotionWindow();
        XposedInterface.HookHandle expansionHook = null;
        try {
            PanelMovementHooks observer = new PanelMovementHooks(module, loader, motion);
            expansionHook = module.hook(observer.expansionChanged).setId("shade-render-movement")
                    .intercept(observer::shadeChanged);
            module.hook(observer.splitChanged).setId("split-render-movement")
                    .intercept(observer::splitChanged);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            if (expansionHook != null) expansionHook.unhook();
            motion.reset();
            module.warn("Unable to track panel movement", error);
        }
        return motion;
    }

    private Object shadeChanged(XposedInterface.Chain chain) throws Throwable {
        if (active()) {
            try {
                motion.onShadePosition(expansionFraction.getFloat(chain.getArg(0)), SystemClock.uptimeMillis());
            } catch (ReflectiveOperationException | RuntimeException error) {
                fail(error);
            }
        }
        return chain.proceed();
    }

    private Object splitChanged(XposedInterface.Chain chain) throws Throwable {
        if (active()) {
            try {
                Object event = chain.getArg(0);
                boolean switching = splitEnabled.getBoolean(event) && splitState.getInt(event) == SWITCHING;
                motion.onSwitchPosition(switching, splitFraction.getFloat(event), SystemClock.uptimeMillis());
            } catch (ReflectiveOperationException | RuntimeException error) {
                fail(error);
            }
        }
        return chain.proceed();
    }

    private boolean active() {
        return !failed && Looper.myLooper() == Looper.getMainLooper();
    }

    private void fail(Throwable error) {
        failed = true;
        motion.reset();
        module.warn("Panel movement observation stopped", error);
    }
}
