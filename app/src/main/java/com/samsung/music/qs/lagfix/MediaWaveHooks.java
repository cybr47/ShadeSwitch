package com.samsung.music.qs.lagfix;

import static com.samsung.music.qs.lagfix.NativePanels.field;
import static com.samsung.music.qs.lagfix.NativePanels.method;

import android.os.Looper;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.View;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Keeps playable media cards in the shade while deferring their decorative redraws during motion. */
final class MediaWaveHooks {
    private static final String SHADE = "com.android.systemui.shade.";

    private final SamsungMusicWavesHook module;
    private final Class<?> shadeWindow, shadeRoot;
    private final Field waveView, wavePlaying;
    private final Method doFrame;
    private final PanelMotionWindow motion;
    private boolean failed;

    private MediaWaveHooks(SamsungMusicWavesHook module, ClassLoader loader, PanelMotionWindow motion)
            throws ReflectiveOperationException {
        this.module = module;
        this.motion = motion;
        Class<?> wave = loader.loadClass("com.android.systemui.media.colorfulwave.WaveSeekbar");
        Class<?> frameCallback = field(wave, "frameCallback").getType();
        waveView = field(frameCallback, "this$0");
        wavePlaying = field(wave, "isWavePlaying");
        if (waveView.getType() != wave || wavePlaying.getType() != boolean.class
                || !Choreographer.FrameCallback.class.isAssignableFrom(frameCallback)) {
            throw new IllegalStateException("Unexpected media wave callback");
        }
        doFrame = method(frameCallback, "doFrame", long.class);
        shadeWindow = loader.loadClass(SHADE + "NotificationShadeWindowView");
        Class<?> windowController = loader.loadClass(SHADE + "NotificationShadeWindowControllerImpl");
        shadeRoot = field(windowController, "mWindowRootView").getType();
    }

    static void install(SamsungMusicWavesHook module, ClassLoader loader, PanelMotionWindow motion) {
        try {
            MediaWaveHooks hooks = new MediaWaveHooks(module, loader, motion);
            module.hook(hooks.doFrame).setId("defer-media-wave-redraw")
                    .intercept(hooks::waveFrame);
            module.note("Media wave redraw control installed");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            module.warn("Unable to install media wave redraw control", error);
        }
    }

    private Object waveFrame(XposedInterface.Chain chain) throws Throwable {
        if (active() && motion.isMoving(SystemClock.uptimeMillis())) {
            try {
                Object callback = chain.getThisObject();
                View view = (View) waveView.get(callback);
                if (wavePlaying.getBoolean(view) && !view.isPressed() && view.isAttachedToWindow()) {
                    View root = view.getRootView();
                    if (shadeRoot.isInstance(root) || shadeWindow.isInstance(root)) {
                        // Preserve the callback lifetime while letting the view reuse its display list.
                        Choreographer.getInstance().postFrameCallback((Choreographer.FrameCallback) callback);
                        return null;
                    }
                }
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
        module.warn("Media wave redraw control fell back to Samsung handling", error);
    }
}
