package com.samsung.music.qs.lagfix;

import static com.samsung.music.qs.lagfix.NativePanels.field;
import static com.samsung.music.qs.lagfix.NativePanels.method;

import android.os.Looper;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/** Removes repeated layout work from the older split panel's frame dispatch. */
final class QsDispatchHooks {
    private static final String SHADE = "com.android.systemui.shade.";

    private QsDispatchHooks() {}

    static void install(SamsungMusicWavesHook module, ClassLoader loader) {
        installDuplicateFractionGate(module, loader);
        installConstraintCoalescing(module, loader);
        installAnimatorFanout(module, loader);
    }

    private static void installDuplicateFractionGate(SamsungMusicWavesHook module, ClassLoader loader) {
        try {
            Class<?> split = loader.loadClass(SHADE + "SecPanelSplitHelper");
            Method setFraction = method(split, "setDraggedFraction", float.class);
            Field fraction = field(split, "draggedFraction");
            Field state = field(split, "currentState");
            Field mode = field(split, "secQsUiDisplayModeInteractor");
            Method popOver = method(mode.getType(), "isPopOverEnabled");
            module.hook(setFraction).setId("split-unchanged-fraction").intercept(chain -> {
                Object panel = chain.getThisObject();
                float next = (float) chain.getArg(0);
                // Endpoints and pop-over have their own state updates.
                if (next > 0f && next < 1f && next == fraction.getFloat(panel)
                        && state.getInt(panel) == 2 && !(boolean) popOver.invoke(mode.get(panel))) {
                    return null;
                }
                return chain.proceed();
            });
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            module.warn("Unable to install split fraction deduplication", error);
        }
    }

    private static void installConstraintCoalescing(SamsungMusicWavesHook module, ClassLoader loader) {
        try {
            Class<?> event = loader.loadClass(SHADE + "PanelTransitionStateChangeEvent");
            Class<?> owner = loader.loadClass(SHADE + "SecNotificationsQSContainerController");
            Class<?> listener = field(owner, "panelTransitionStateListener").getType();
            Method deliver = method(listener, "onPanelTransitionStateChanged", event);
            Field controller = field(listener, "this$0");
            Field view = field(owner, "view");
            Field popOverManager = field(owner, "panelPopOverManager");
            Field mode = field(popOverManager.getType(), "secQsUiDisplayModeInteractor");
            Method popOver = method(mode.getType(), "isPopOverEnabled");
            Field enabled = field(event, "enabled");
            Field state = field(event, "state");
            Field fraction = field(event, "fraction");
            WeakHashMap<Object, LayoutSize> layouts = new WeakHashMap<>();
            module.hook(deliver).setId("split-constraint-phase").intercept(chain -> {
                if (Looper.myLooper() != Looper.getMainLooper()) return chain.proceed();
                Object listenerObject = chain.getThisObject();
                Object change = chain.getArg(0);
                float progress = fraction.getFloat(change);
                if (enabled.getBoolean(change) && state.getInt(change) == 2
                        && progress > 0f && progress < 1f) {
                    Object container = controller.get(listenerObject);
                    View host = (View) view.get(container);
                    if (host.isAttachedToWindow()
                            && !(boolean) popOver.invoke(mode.get(popOverManager.get(container)))) {
                        LayoutSize previous = layouts.get(listenerObject);
                        if (previous != null && previous.width == host.getWidth()
                                && previous.height == host.getHeight()) return null;
                        layouts.put(listenerObject, new LayoutSize(host.getWidth(), host.getHeight()));
                        return chain.proceed();
                    }
                }
                // A new transition, mode change, size change or endpoint can post fresh constraints.
                layouts.remove(listenerObject);
                return chain.proceed();
            });
            module.note("Split constraint coalescing installed");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            module.warn("Unable to install split constraint coalescing", error);
        }
    }

    private static void installAnimatorFanout(SamsungMusicWavesHook module, ClassLoader loader) {
        try {
            Class<?> manager = loader.loadClass("com.android.systemui.qs.animator.SecQSImplAnimatorManager");
            Field list = field(manager, "mList");
            Method execute = method(manager, "executeConsumer", Consumer.class);
            if (list.getType() != ArrayList.class) return;
            module.hook(execute).setId("qs-animator-fanout").intercept(chain -> {
                Object value = list.get(chain.getThisObject());
                if (!(value instanceof ArrayList<?> animators)) return chain.proceed();
                @SuppressWarnings("unchecked")
                Consumer<Object> dispatch = (Consumer<Object>) chain.getArg(0);
                // Preserve order, null filtering and ArrayList's concurrent modification checks.
                animators.spliterator().forEachRemaining(animator -> {
                    if (animator != null) dispatch.accept(animator);
                });
                return null;
            });
            module.note("Quick Settings animator fan-out hook installed");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            module.warn("Unable to install Quick Settings animator fan-out", error);
        }
    }

    private record LayoutSize(int width, int height) {}
}
