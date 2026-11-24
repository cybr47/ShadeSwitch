package com.samsung.music.qs.lagfix;

import static com.samsung.music.qs.lagfix.NativePanels.field;
import static com.samsung.music.qs.lagfix.NativePanels.method;

import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Keeps the hidden notification stack's pending layout for the next eligible traversal. */
final class HiddenShadeLayoutHooks {
    private static final int QS_PANEL = 0;
    private static final int UNLOCKED_SHADE = 0;

    private final SamsungMusicWavesHook module;
    private final PanelMotionWindow motion;
    private final Method preDraw, getSplitHelper, isPopOver;
    private final Field owner, updater, updateRequested, barState, splitEnabled, splitState, displayMode;
    private final Field[] animationFlags;
    private boolean failed;

    private HiddenShadeLayoutHooks(SamsungMusicWavesHook module, ClassLoader loader, PanelMotionWindow motion)
            throws ReflectiveOperationException {
        this.module = module;
        this.motion = motion;
        Class<?> stack = loader.loadClass("com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout");
        updater = field(stack, "mChildrenUpdater");
        owner = field(updater.getType(), "this$0");
        preDraw = method(updater.getType(), "onPreDraw");
        updateRequested = field(stack, "mChildrenUpdateRequested");
        animationFlags = new Field[] {
                field(stack, "mNeedsAnimation"),
                field(stack, "mAnimationRunning"),
                field(stack, "mInHeadsUpPinnedMode"),
                field(stack, "mHeadsUpAnimatingAway"),
                field(stack, "mClearAllInProgress")
        };
        barState = field(loader.loadClass("com.android.systemui.qs.animator.QsAnimatorState"), "state");
        getSplitHelper = method(stack, "getPanelSplitHelper$1");
        Class<?> split = getSplitHelper.getReturnType();
        splitEnabled = field(split, "enabled");
        splitState = field(split, "currentState");
        displayMode = field(split, "secQsUiDisplayModeInteractor");
        isPopOver = method(displayMode.getType(), "isPopOverEnabled");
    }

    static void install(SamsungMusicWavesHook module, ClassLoader loader, PanelMotionWindow motion) {
        try {
            HiddenShadeLayoutHooks hooks = new HiddenShadeLayoutHooks(module, loader, motion);
            module.hook(hooks.preDraw).setId("defer-hidden-shade-layout").intercept(hooks::onPreDraw);
            module.note("Hidden notification stack layout control installed");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            module.warn("Unable to bind hidden notification stack layout", error);
        }
    }

    private Object onPreDraw(XposedInterface.Chain chain) throws Throwable {
        if (!failed && Looper.myLooper() == Looper.getMainLooper()
                && motion.isMoving(SystemClock.uptimeMillis())) {
            try {
                if (canDefer(chain.getThisObject())) {
                    // Samsung clears both the pending flag and the listener on completion.
                    // Leave them together so the next eligible traversal sees every queued change.
                    return Boolean.TRUE;
                }
            } catch (ReflectiveOperationException | RuntimeException error) {
                failed = true;
                module.warn("Hidden notification stack fell back to Samsung layout", error);
            }
        }
        return chain.proceed();
    }

    private boolean canDefer(Object listener) throws ReflectiveOperationException {
        View stack = (View) owner.get(listener);
        if (stack == null || stack.getVisibility() != View.INVISIBLE || !stack.isAttachedToWindow()
                || updater.get(stack) != listener || !updateRequested.getBoolean(stack)
                || barState.getInt(null) != UNLOCKED_SHADE) return false;
        for (Field flag : animationFlags) {
            if (flag.getBoolean(stack)) return false;
        }
        Object split = getSplitHelper.invoke(stack);
        return split != null && splitEnabled.getBoolean(split) && splitState.getInt(split) == QS_PANEL
                && !(boolean) isPopOver.invoke(displayMode.get(split));
    }
}
