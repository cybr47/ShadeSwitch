package com.samsung.music.qs.lagfix;

import static com.samsung.music.qs.lagfix.NativePanels.field;
import static com.samsung.music.qs.lagfix.NativePanels.method;

import android.os.Build;
import android.os.Looper;
import io.github.libxposed.api.XposedInterface;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.WeakHashMap;

/** Delivers the media pager's listening threshold as Compose state. */
final class QsMediaVisibilityHooks {
    private static final String COMPOSE = "androidx.compose.runtime.";
    private static final String AUDITED_BUILD = "S931BXXUCDZIF";
    private static final Float VISIBLE = 0f;
    private static final Float HIDDEN = 1f;

    private final SamsungMusicWavesHook module;
    private final Class<?> function, mutableState;
    private final Field slideState;
    private final Method pageContent, deriveState, getValue;
    private final Object equalityPolicy;
    private final WeakHashMap<Object, WeakReference<Object>> projections = new WeakHashMap<>();
    private boolean failed;

    private QsMediaVisibilityHooks(SamsungMusicWavesHook module, ClassLoader loader)
            throws ReflectiveOperationException {
        this.module = module;
        String quickSettings = "com.android.systemui.samsung.quicksetting.ui.";
        Class<?> pager = loader.loadClass(quickSettings + "tiles.MediaPlayerKt$$ExternalSyntheticLambda9");
        Class<?> playerModel = loader.loadClass(quickSettings + "media.QSMediaPlayerViewModel");
        Class<?> state = loader.loadClass(COMPOSE + "State");
        mutableState = loader.loadClass(COMPOSE + "MutableState");
        function = loader.loadClass("kotlin.jvm.functions.Function0");
        slideState = field(pager, "f$8");
        if (field(pager, "f$0").getType() != playerModel || slideState.getType() != mutableState) {
            throw new NoSuchFieldException("Unexpected QS media pager state");
        }
        pageContent = method(pager, "invoke", Object.class, Object.class, Object.class, Object.class);
        getValue = method(state, "getValue");
        Class<?> snapshot = loader.loadClass(COMPOSE + "SnapshotStateKt");
        Class<?> policy = loader.loadClass(COMPOSE + "SnapshotMutationPolicy");
        deriveState = method(snapshot, "derivedStateOf", policy, function);
        equalityPolicy = method(snapshot, "structuralEqualityPolicy").invoke(null);
    }

    static void install(SamsungMusicWavesHook module, ClassLoader loader) {
        // The field is an R8-generated capture. Its type alone cannot establish what it stores.
        if (!AUDITED_BUILD.equals(Build.VERSION.INCREMENTAL)
                && !Build.DISPLAY.endsWith("." + AUDITED_BUILD)) return;
        try {
            QsMediaVisibilityHooks hooks = new QsMediaVisibilityHooks(module, loader);
            module.hook(hooks.pageContent).setId("qs-media-listening-state").intercept(hooks::pageContent);
            module.note("QS media listening state projection installed");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            module.warn("Unable to bind QS media listening state", error);
        }
    }

    private Object pageContent(XposedInterface.Chain chain) throws Throwable {
        if (!failed && Looper.myLooper() == Looper.getMainLooper()) {
            try {
                Object page = chain.getThisObject();
                Object source = slideState.get(page);
                if (source != null && !(Proxy.isProxyClass(source.getClass())
                        && Proxy.getInvocationHandler(source) instanceof ListeningState)) {
                    slideState.set(page, project(source));
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                failed = true;
                module.warn("QS media listening state fell back to Samsung handling", error);
            }
        }
        return chain.proceed();
    }

    private Object project(Object source) throws ReflectiveOperationException {
        WeakReference<Object> reference = projections.get(source);
        Object cached = reference == null ? null : reference.get();
        if (cached != null) return cached;
        Object calculation = Proxy.newProxyInstance(function.getClassLoader(), new Class<?>[] {function},
                (proxy, method, args) -> {
                    if (!method.getName().equals("invoke")) return proxyObjectMethod(proxy, method, args);
                    return listeningFraction(invoke(getValue, source, null));
                });
        Object derived = deriveState.invoke(null, equalityPolicy, calculation);
        Object projected = Proxy.newProxyInstance(mutableState.getClassLoader(),
                new Class<?>[] {mutableState}, new ListeningState(source, derived));
        projections.put(source, new WeakReference<>(projected));
        return projected;
    }

    // The pager compares its slide value with 1f to start or stop seek-bar polling.
    static Object listeningFraction(Object value) {
        if (value instanceof Float fraction) return fraction < 1f ? VISIBLE : HIDDEN;
        return value;
    }

    private final class ListeningState implements InvocationHandler {
        private final Object source, derived;

        ListeningState(Object source, Object derived) {
            this.source = source;
            this.derived = derived;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) return proxyObjectMethod(proxy, method, args);
            if (!failed && (method.getName().equals("getValue") || method.getName().equals("component1"))) {
                return QsMediaVisibilityHooks.invoke(getValue, derived, null);
            }
            return QsMediaVisibilityHooks.invoke(method, source, args);
        }
    }

    private static Object invoke(Method method, Object receiver, Object[] args) throws Throwable {
        try {
            return method.invoke(receiver, args);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }

    private static Object proxyObjectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> proxy.getClass().getName();
            default -> throw new IllegalStateException(method.getName());
        };
    }
}
