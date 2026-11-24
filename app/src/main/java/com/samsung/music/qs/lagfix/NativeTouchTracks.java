package com.samsung.music.qs.lagfix;

import static com.samsung.music.qs.lagfix.NativePanels.field;
import static com.samsung.music.qs.lagfix.NativePanels.method;

import android.view.View;
import android.view.animation.Interpolator;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class NativeTouchTracks {
    private final Constructor<?> constructor;
    private final Field endDelay, interpolator;
    private final Method addFloat, build, getView;

    NativeTouchTracks(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> builder = loader.loadClass("com.android.systemui.qs.TouchAnimator$Builder");
        Class<?> wrapper = loader.loadClass("com.android.systemui.qs.animator.QSAnimView");
        constructor = builder.getDeclaredConstructor();
        constructor.setAccessible(true);
        endDelay = field(builder, "mEndDelay");
        interpolator = field(builder, "mInterpolator");
        addFloat = method(builder, "addFloat", Object.class, String.class, float[].class);
        build = method(builder, "build");
        getView = method(wrapper, "getView");
    }

    Object builder(float span, Interpolator easing) throws ReflectiveOperationException {
        Object instance = constructor.newInstance();
        endDelay.setFloat(instance, 1f - span);
        interpolator.set(instance, easing);
        return instance;
    }

    void add(Object builder, Object wrapper, String property, float from, float to)
            throws ReflectiveOperationException {
        addFloat.invoke(builder, wrapper, property, new float[] {from, to});
    }

    Object build(Object builder) throws ReflectiveOperationException {
        return build.invoke(builder);
    }

    View view(Object wrapper) throws ReflectiveOperationException {
        return wrapper == null ? null : (View) getView.invoke(wrapper);
    }
}
