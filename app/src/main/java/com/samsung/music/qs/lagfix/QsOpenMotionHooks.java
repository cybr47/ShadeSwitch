package com.samsung.music.qs.lagfix;

import static com.samsung.music.qs.lagfix.NativePanels.field;
import static com.samsung.music.qs.lagfix.NativePanels.method;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewParent;
import android.view.animation.PathInterpolator;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Uses the beta opening curves in the older animator's existing expansion callbacks. */
final class QsOpenMotionHooks {
    private static final String QS = "com.android.systemui.qs.";
    private static final String SHADE = "com.android.systemui.shade.";

    private final Field[] views;
    private final Field headerAlpha, headerTranslation, unusedHeaderTrack, blurTrack;
    private final Field mode, enabled, detailShowing, qs;
    private final Method updateAnimators, isPopOver;
    private final NativeTouchTracks tracks;
    private final PathInterpolator easing = new PathInterpolator(0.42f, 0f, 0.58f, 1f);

    private QsOpenMotionHooks(ClassLoader loader, NativeTouchTracks tracks)
            throws ReflectiveOperationException {
        this.tracks = tracks;
        Class<?> owner = loader.loadClass(QS + "animator.PanelSplitOpenAnimator");
        Class<?> split = loader.loadClass(SHADE + "SecPanelSplitHelper");
        Class<?> state = loader.loadClass(QS + "animator.QsAnimatorState");
        updateAnimators = method(owner, "updateAnimators");
        qs = field(owner.getSuperclass(), "mQs");
        headerAlpha = field(owner, "headerAnimator");
        headerTranslation = field(owner, "clockDateAnimator");
        unusedHeaderTrack = field(owner, "buttonAnimator");
        blurTrack = field(owner, "blurViewAnimator");
        views = new Field[] {field(owner, "header"), field(owner, "clockDateContainer"),
                field(owner, "buttonContainer"), field(owner, "blurParent"),
                field(owner, "largeShadowView"), field(owner, "smallShadowView")};
        enabled = field(split, "isEnabled");
        detailShowing = field(state, "isDetailShowing");
        mode = field(owner, "secQsUiDisplayModeInteractor");
        isPopOver = method(mode.getType(), "isPopOverEnabled");
    }

    static void install(SamsungMusicWavesHook module, ClassLoader loader, NativeTouchTracks tracks) {
        try {
            QsOpenMotionHooks hooks = new QsOpenMotionHooks(loader, tracks);
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
    }

    private void retime(Object owner) throws ReflectiveOperationException {
        if (qs.get(owner) == null || !enabled.getBoolean(null) || detailShowing.getBoolean(null)
                || (boolean) isPopOver.invoke(mode.get(owner))) return;

        Object[] wrappers = new Object[views.length];
        View[] targets = new View[views.length];
        for (int i = 0; i < views.length; i++) {
            wrappers[i] = views[i].get(owner);
            targets[i] = tracks.view(wrappers[i]);
        }

        Object alpha = tracks.builder(PanelMotion.HEADER_FADE_END, easing);
        Object translation = tracks.builder(1f, easing);
        boolean hasHeader = false;
        for (int i = 0; i < 3; i++) {
            View view = targets[i];
            if (view == null || alreadyTargeted(targets, i, 0)) continue;
            boolean inherits = inheritsTransform(targets, i, 0, 3);
            tracks.add(alpha, wrappers[i], "alpha", inherits ? 1f : 0f, 1f);
            tracks.add(translation, wrappers[i], "translationY", offset(view, inherits), 0f);
            if (i != 0) {
                tracks.add(translation, wrappers[i], "translationX", 0f, 0f);
                tracks.add(translation, wrappers[i], "scaleX", 1f, 1f);
                tracks.add(translation, wrappers[i], "scaleY", 1f, 1f);
            }
            hasHeader = true;
        }

        Object blur = null;
        if (targets[3] != null && targets[4] != null && targets[5] != null && blurTrack.get(owner) != null) {
            Object builder = tracks.builder(1f, easing);
            for (int i = 3; i < targets.length; i++) {
                if (alreadyTargeted(targets, i, 3)) continue;
                boolean inherits = inheritsTransform(targets, i, 3, targets.length);
                tracks.add(builder, wrappers[i], "translationY", offset(targets[i], inherits), 0f);
            }
            blur = tracks.build(builder);
        }

        // These slots all take the same incoming expansion fraction. Two tracks permit different
        // spans for opacity and travel without sampling either curve or adding frame callbacks.
        if (hasHeader) {
            Object alphaTrack = tracks.build(alpha);
            Object translationTrack = tracks.build(translation);
            headerAlpha.set(owner, alphaTrack);
            headerTranslation.set(owner, translationTrack);
            unusedHeaderTrack.set(owner, null);
        }
        if (blur != null) blurTrack.set(owner, blur);
    }

    private static boolean alreadyTargeted(View[] targets, int index, int first) {
        for (int i = first; i < index; i++) {
            if (targets[i] == targets[index]) return true;
        }
        return false;
    }

    private static boolean inheritsTransform(View[] targets, int index, int first, int end) {
        for (ViewParent parent = targets[index].getParent(); parent != null; parent = parent.getParent()) {
            for (int i = first; i < end; i++) {
                if (parent == targets[i]) return true;
            }
        }
        return false;
    }

    private static float offset(View view, boolean inherits) {
        return inherits ? 0f : -PanelMotion.HEADER_TRAVEL_DP * view.getResources().getDisplayMetrics().density;
    }

    static void installTogetherSpring(SamsungMusicWavesHook module, ClassLoader loader) {
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
