package com.samsung.music.qs.lagfix;

import static com.samsung.music.qs.lagfix.NativePanels.field;
import static com.samsung.music.qs.lagfix.NativePanels.method;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewParent;
import android.view.animation.PathInterpolator;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Retimes Samsung's existing expansion tracks and Together-mode overdrag settle. */
final class QsOpenMotionHooks {
    private static final int SAMPLES = 100;
    private static final String QS = "com.android.systemui.qs.";
    private static final String SHADE = "com.android.systemui.shade.";

    private final Field[] views;
    private final Field[] tracks;
    private final Field mode, enabled, detailShowing;
    private final Method updateAnimators, isPopOver, isTablet, getView, addFloat, build;
    private final Constructor<?> builder;
    private final float[] alpha = new float[SAMPLES + 1];
    private final float[] translation = new float[SAMPLES + 1];

    private QsOpenMotionHooks(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> owner = loader.loadClass(QS + "animator.PanelSplitOpenAnimator");
        Class<?> split = loader.loadClass(SHADE + "SecPanelSplitHelper");
        Class<?> state = loader.loadClass(QS + "animator.QsAnimatorState");
        Class<?> trackBuilder = loader.loadClass(QS + "TouchAnimator$Builder");
        Class<?> view = loader.loadClass(QS + "animator.QSAnimView");
        updateAnimators = method(owner, "updateAnimators");
        tracks = new Field[] {field(owner, "headerAnimator"), field(owner, "clockDateAnimator"),
                field(owner, "buttonAnimator"), field(owner, "blurViewAnimator")};
        views = new Field[] {field(owner, "header"), field(owner, "clockDateContainer"),
                field(owner, "buttonContainer"), field(owner, "blurParent"),
                field(owner, "largeShadowView"), field(owner, "smallShadowView")};
        enabled = field(split, "isEnabled");
        detailShowing = field(state, "isDetailShowing");
        mode = field(owner, "secQsUiDisplayModeInteractor");
        isPopOver = method(mode.getType(), "isPopOverEnabled");
        isTablet = method(mode.getType(), "isTablet");
        getView = method(view, "getView");
        builder = trackBuilder.getDeclaredConstructor();
        builder.setAccessible(true);
        addFloat = method(trackBuilder, "addFloat", Object.class, String.class, float[].class);
        build = method(trackBuilder, "build");

        PathInterpolator easing = new PathInterpolator(0.42f, 0f, 0.58f, 1f);
        for (int i = 0; i <= SAMPLES; i++) {
            float progress = (float) i / SAMPLES;
            alpha[i] = easing.getInterpolation(Math.min(1f, progress / PanelMotion.HEADER_FADE_END));
            translation[i] = easing.getInterpolation(progress) - 1f;
        }
    }

    static void install(SamsungMusicWavesHook module, ClassLoader loader) {
        try {
            QsOpenMotionHooks hooks = new QsOpenMotionHooks(loader);
            module.hook(hooks.updateAnimators).setId("qs-open-timing").intercept(chain -> {
                Object result = chain.proceed();
                try {
                    hooks.retime(chain.getThisObject());
                } catch (ReflectiveOperationException | RuntimeException error) {
                    module.warn("Unable to rebuild Quick Settings opening tracks", error);
                }
                return result;
            });
            module.note("Quick Settings opening motion hook installed");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            module.warn("Unable to install Quick Settings opening motion", error);
        }
        installTogetherSpring(module, loader);
    }

    private void retime(Object animator) throws ReflectiveOperationException {
        if (!enabled.getBoolean(null) || detailShowing.getBoolean(null)) return;
        Object displayMode = mode.get(animator);
        if ((boolean) isPopOver.invoke(displayMode) || (boolean) isTablet.invoke(displayMode)) return;

        Object[] wrappers = new Object[views.length];
        View[] targets = new View[views.length];
        for (int i = 0; i < views.length; i++) {
            wrappers[i] = views[i].get(animator);
            if (wrappers[i] != null) targets[i] = (View) getView.invoke(wrappers[i]);
        }

        // Build before swapping any of Samsung's tracks. The same position updates drive
        // all the new keyframes, including when the finger pauses or reverses direction.
        Object[] replacements = new Object[tracks.length];
        for (int i = 0; i < 3; i++) {
            if (targets[i] == null || tracks[i].get(animator) == null) continue;
            if (alreadyTargeted(targets, i, 0)) {
                replacements[i] = build.invoke(builder.newInstance());
                continue;
            }
            Object next = builder.newInstance();
            boolean inheritsMotion = inheritsTransform(targets, i, 0);
            addFloat.invoke(next, wrappers[i], "alpha", inheritsMotion ? new float[] {1f, 1f} : alpha);
            addFloat.invoke(next, wrappers[i], "translationY", offsets(targets[i], inheritsMotion));
            if (i != 0) {
                addFloat.invoke(next, wrappers[i], "translationX", new float[] {0f, 0f});
                addFloat.invoke(next, wrappers[i], "scaleX", new float[] {1f, 1f});
                addFloat.invoke(next, wrappers[i], "scaleY", new float[] {1f, 1f});
            }
            replacements[i] = build.invoke(next);
        }
        if (targets[3] != null && targets[4] != null && targets[5] != null
                && tracks[3].get(animator) != null) {
            Object next = builder.newInstance();
            for (int i = 3; i < views.length; i++) {
                if (alreadyTargeted(targets, i, 3)) continue;
                addFloat.invoke(next, wrappers[i], "translationY", offsets(targets[i],
                        inheritsTransform(targets, i, 3)));
            }
            replacements[3] = build.invoke(next);
        }
        for (int i = 0; i < tracks.length; i++) {
            if (replacements[i] != null) tracks[i].set(animator, replacements[i]);
        }
    }

    private static boolean alreadyTargeted(View[] targets, int index, int first) {
        for (int i = first; i < index; i++) {
            if (targets[i] == targets[index]) return true;
        }
        return false;
    }

    private static boolean inheritsTransform(View[] targets, int index, int first) {
        for (ViewParent parent = targets[index].getParent(); parent != null; parent = parent.getParent()) {
            for (int i = first; i < index; i++) {
                if (parent == targets[i]) return true;
            }
        }
        return false;
    }

    private float[] offsets(View view, boolean inheritsMotion) {
        if (inheritsMotion) return new float[] {0f, 0f};
        float travel = PanelMotion.HEADER_TRAVEL_DP * view.getResources().getDisplayMetrics().density;
        float[] values = new float[translation.length];
        for (int i = 0; i < values.length; i++) values[i] = travel * translation[i];
        return values;
    }

    private static void installTogetherSpring(SamsungMusicWavesHook module, ClassLoader loader) {
        try {
            Class<?> panel = loader.loadClass(SHADE + "NotificationPanelViewController");
            Class<?> split = loader.loadClass(SHADE + "SecPanelSplitHelper");
            Field springing = field(panel, "mIsSpringBackAnimation");
            Field separate = field(split, "isEnabled");
            Method setAnimator = method(panel, "setAnimator", ValueAnimator.class);
            module.hook(setAnimator).setId("together-overdrag-settle").intercept(chain -> {
                ValueAnimator animator = (ValueAnimator) chain.getArg(0);
                if (animator != null && animator.getDuration() == 250L && !separate.getBoolean(null)
                        && springing.getBoolean(chain.getThisObject())) {
                    animator.setDuration(PanelMotion.SPRING_BACK_MS);
                    animator.setInterpolator(PanelMotion::springBack);
                }
                return chain.proceed();
            });
            module.note("Together overdrag spring hook installed");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            module.warn("Unable to install the Together overdrag spring", error);
        }
    }
}
