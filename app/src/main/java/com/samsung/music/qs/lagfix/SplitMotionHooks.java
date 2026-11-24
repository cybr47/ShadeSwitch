package com.samsung.music.qs.lagfix;

import static com.samsung.music.qs.lagfix.NativePanels.field;
import static com.samsung.music.qs.lagfix.NativePanels.method;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.os.Looper;
import android.view.MotionEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import io.github.libxposed.api.XposedInterface;

/** Uses Samsung's split helper for every drag frame and for both release endpoints. */
final class SplitMotionHooks {
    private static final String SHADE = "com.android.systemui.shade.";

    private final SamsungMusicWavesHook module;
    private final Method updateDirection, initiateSlide, handleTouch, slide, release, applyFling;
    private final Method isPopOver;
    private final Field helper, direction, slop, intercepted, width, animator, fling, minVelocity;
    private final Field mode, callback, sliding;
    private final Object left, right, up, down;
    private final WeakHashMap<Object, Float> offsets = new WeakHashMap<>();
    private Object cancelingHelper;
    private boolean failed;

    private SplitMotionHooks(SamsungMusicWavesHook module, ClassLoader loader)
            throws ReflectiveOperationException {
        this.module = module;
        Class<?> handler = loader.loadClass(SHADE + "PanelSlideEventHandler");
        Class<?> split = loader.loadClass(SHADE + "SecPanelSplitHelper");
        Class<?> directions = loader.loadClass(SHADE + "PanelSlideEventHandler$Direction");
        Class<?> state = loader.loadClass("com.android.systemui.qs.animator.QsAnimatorState");
        updateDirection = method(handler, "updateDirection", float.class, float.class);
        initiateSlide = method(handler, "initiateSlide", MotionEvent.class);
        handleTouch = method(split, "handleTouch", MotionEvent.class);
        slide = method(split, "slide", float.class, directions, boolean.class);
        release = method(handler, "createSlideAnimatorAndRun", float.class, float.class);
        helper = field(handler, "secPanelSplitHelper");
        direction = field(handler, "direction");
        slop = field(handler, "touchSlop");
        intercepted = field(handler, "panelSliderIntercepted");
        width = field(handler, "maxDragWidth");
        animator = field(handler, "sliderAnimator");
        fling = field(handler, "flingAnimationUtils");
        applyFling = method(fling.getType(), "apply", Animator.class, float.class, float.class,
                float.class, float.class);
        minVelocity = field(fling.getType(), "mMinVelocityPxPerSecond");
        mode = field(split, "secQsUiDisplayModeInteractor");
        isPopOver = method(mode.getType(), "isPopOverEnabled");
        callback = field(handler, "panelSlideEventCallback");
        sliding = field(state, "isSliding");
        left = field(directions, "LEFT").get(null);
        right = field(directions, "RIGHT").get(null);
        up = field(directions, "UP").get(null);
        down = field(directions, "DOWN").get(null);
    }

    static void install(SamsungMusicWavesHook module, ClassLoader loader, boolean horizontal) {
        List<XposedInterface.HookHandle> handles = new ArrayList<>();
        try {
            SplitMotionHooks hooks = new SplitMotionHooks(module, loader);
            handles.add(module.hook(hooks.handleTouch).setId("split-gesture-lifetime")
                    .intercept(hooks::touch));
            if (horizontal) {
                handles.add(module.hook(hooks.initiateSlide).setId("split-drag-origin")
                        .intercept(hooks::resetOrigin));
                handles.add(module.hook(hooks.updateDirection).setId("split-drag-direction")
                        .intercept(hooks::classify));
                handles.add(module.hook(hooks.slide).setId("split-drag-axis-offset")
                        .intercept(hooks::applyOffset));
            }
            handles.add(module.hook(hooks.release).setId("split-drag-settle")
                    .intercept(hooks::settle));
            module.note("Split motion hooks installed (" + (horizontal ? "direction and settle" : "settle") + ")");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            for (XposedInterface.HookHandle handle : handles) handle.unhook();
            module.warn("Unable to install split motion hooks", error);
        }
    }

    private Object resetOrigin(XposedInterface.Chain chain) throws Throwable {
        if (active() && !offsets.isEmpty()) {
            offsets.clear();
        }
        return chain.proceed();
    }

    private Object touch(XposedInterface.Chain chain) throws Throwable {
        int action = ((MotionEvent) chain.getArg(0)).getActionMasked();
        if (!active() || (action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL)) {
            return chain.proceed();
        }
        Object split = chain.getThisObject();
        Object previous = cancelingHelper;
        if (action == MotionEvent.ACTION_CANCEL) cancelingHelper = split;
        try {
            return chain.proceed();
        } finally {
            cancelingHelper = previous;
            offsets.remove(split);
        }
    }

    private Object classify(XposedInterface.Chain chain) throws Throwable {
        if (!active()) return chain.proceed();
        Object handler = chain.getThisObject();
        float dx = (float) chain.getArg(0);
        float dy = (float) chain.getArg(1);
        try {
            Object split = helper.get(handler);
            int touchSlop = slop.getInt(handler);
            if (PanelMotion.favorsHorizontal(dx, dy, touchSlop) && !inPopOver(split)) {
                Object next = dx < 0f ? left : right;
                Object current = direction.get(handler);
                if (current == down && !PanelMotion.withinAxisHandoff(dx, dy, touchSlop)) {
                    return null;
                }
                if ((current == down || current == up) && intercepted.getBoolean(handler)) {
                    offsets.put(split, PanelMotion.axisHandoffOffset(dx, dy));
                }
                direction.set(handler, next);
                return null;
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            fail(error);
        }
        return chain.proceed();
    }

    private Object applyOffset(XposedInterface.Chain chain) throws Throwable {
        if (active() && !offsets.isEmpty() && (boolean) chain.getArg(2)) {
            Object axis = chain.getArg(1);
            Float offset = offsets.get(chain.getThisObject());
            if (offset != null && (axis == left || axis == right)) {
                return chain.proceed(new Object[] {(float) chain.getArg(0) + offset, axis, true});
            }
        }
        return chain.proceed();
    }

    private Object settle(XposedInterface.Chain chain) throws Throwable {
        if (!active()) return chain.proceed();
        Object handler = chain.getThisObject();
        Object split;
        Object axis;
        float travel;
        boolean eligible;
        try {
            split = helper.get(handler);
            axis = direction.get(handler);
            travel = width.getFloat(handler);
            eligible = travel > 0f && callback.get(handler) != null && !inPopOver(split)
                    && (axis == left || axis == right || axis == down || axis == up);
        } catch (ReflectiveOperationException | RuntimeException error) {
            fail(error);
            return chain.proceed();
        }
        if (!eligible) return chain.proceed();

        ValueAnimator animation;
        ValueAnimator previous;
        try {
            float velocity = (float) chain.getArg(0);
            float distance = (float) chain.getArg(1);
            Float offset = offsets.get(split);
            if (offset != null && (axis == left || axis == right)) distance += offset;
            Object physics = fling.get(handler);
            int sign = axis == left || axis == up ? -1 : 1;
            float target = PanelMotion.settleTarget(distance, velocity, travel,
                    minVelocity.getFloat(physics), sign, cancelingHelper == split);
            animation = ValueAnimator.ofFloat(distance, target);
            if (velocity == 0f || distance == target) {
                animation.setDuration(distance == target ? 0L : PanelMotion.settleDuration(distance, travel));
            } else {
                applyFling.invoke(physics, animation, distance, target, velocity, travel);
            }
            // Settle through Samsung's normal fraction and topology updates. An interrupted
            // animator keeps its last rendered frame, and only its owner can clear the field.
            animation.addUpdateListener(frame -> render(split, axis, frame));
            animation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator ended) {
                    finish(handler, ended);
                }
            });
            previous = (ValueAnimator) animator.get(handler);
        } catch (ReflectiveOperationException | RuntimeException error) {
            fail(error);
            return chain.proceed();
        }
        if (previous != null) previous.cancel();
        try {
            animator.set(handler, animation);
        } catch (IllegalAccessException error) {
            fail(error);
            return chain.proceed();
        }
        animation.start();
        return null;
    }

    private void render(Object split, Object axis, ValueAnimator frame) {
        try {
            slide.invoke(split, frame.getAnimatedValue(), axis, false);
        } catch (ReflectiveOperationException | RuntimeException error) {
            frame.cancel();
            fail(error);
        }
    }

    private void finish(Object handler, Animator ended) {
        try {
            if (animator.get(handler) == ended) {
                sliding.setBoolean(null, false);
                animator.set(handler, null);
            }
        } catch (IllegalAccessException error) {
            fail(error);
        }
    }

    private boolean inPopOver(Object split) throws ReflectiveOperationException {
        return (boolean) isPopOver.invoke(mode.get(split));
    }

    private boolean active() {
        return !failed && Looper.myLooper() == Looper.getMainLooper();
    }

    private void fail(Throwable error) {
        if (failed) return;
        failed = true;
        offsets.clear();
        module.warn("Split motion fell back to Samsung handling", error);
    }
}
