package com.samsung.music.qs.lagfix;

import static com.samsung.music.qs.lagfix.NativePanels.field;
import static com.samsung.music.qs.lagfix.NativePanels.method;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Retimes the controls in the legacy split header using the beta's fullscreen tracks. */
final class SplitHeaderMotionHooks {
    private final NativeTouchTracks tracks;
    private final Field buttons, enabled, layoutMode;
    private final Method rebuild, isPopOver;
    private final Field[] animations;

    private SplitHeaderMotionHooks(ClassLoader loader, NativeTouchTracks tracks)
            throws ReflectiveOperationException {
        this.tracks = tracks;
        Class<?> animator = loader.loadClass("com.android.systemui.qs.animator.PanelTransitionAnimator");
        Class<?> split = loader.loadClass("com.android.systemui.shade.SecPanelSplitHelper");
        buttons = field(animator, "buttonContainer");
        enabled = field(split, "isEnabled");
        layoutMode = field(animator, "secQsUiDisplayModeInteractor");
        rebuild = method(animator, "updateAnimators");
        isPopOver = method(layoutMode.getType(), "isPopOverEnabled");
        animations = new Field[] {
                field(animator, "buttonFadeInLeftAnimator"),
                field(animator, "buttonFadeOutLeftAnimator"),
                field(animator, "buttonFadeInRightAnimator"),
                field(animator, "buttonFadeOutRightAnimator"),
                field(animator, "buttonFadeInDownAnimator"),
                field(animator, "buttonFadeOutDownAnimator")
        };
    }

    static void install(SamsungMusicWavesHook module, ClassLoader loader, NativeTouchTracks tracks) {
        try {
            SplitHeaderMotionHooks hooks = new SplitHeaderMotionHooks(loader, tracks);
            module.hook(hooks.rebuild).setId("split-header-beta-tracks").intercept(chain -> {
                Object result = chain.proceed();
                try {
                    hooks.retime(chain.getThisObject());
                } catch (ReflectiveOperationException | RuntimeException error) {
                    module.warn("Unable to retime the split header controls", error);
                }
                return result;
            });
            module.note("Split header control timing hook installed");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            module.warn("Unable to install split header control timing", error);
        }
    }

    private void retime(Object owner) throws ReflectiveOperationException {
        if (!enabled.getBoolean(null) || (boolean) isPopOver.invoke(layoutMode.get(owner))) return;
        Object view = buttons.get(owner);
        if (view == null || animations[0].get(owner) == null) return;

        // Left and right use the same full-width alpha span. DOWN uses its own vertical travel.
        float x = PanelMotion.SWITCH_CONTROL_TRAVEL_X;
        float y = PanelMotion.SWITCH_CONTROL_TRAVEL_Y;
        Object[] replacement = {
                track(view, true, x, 0f),
                track(view, false, -x, 0f),
                track(view, true, -x, 0f),
                track(view, false, x, 0f),
                track(view, true, 0f, -y),
                track(view, false, 0f, y)
        };
        for (int i = 0; i < animations.length; i++) {
            animations[i].set(owner, replacement[i]);
        }
    }

    private Object track(Object view, boolean entering, float x, float y)
            throws ReflectiveOperationException {
        Object builder = tracks.builder(1f, null);
        tracks.add(builder, view, "alpha", entering ? 0f : 1f, entering ? 1f : 0f);
        tracks.add(builder, view, "translationX", entering ? x : 0f, entering ? 0f : x);
        tracks.add(builder, view, "translationY", entering ? y : 0f, entering ? 0f : y);
        return tracks.build(builder);
    }
}
