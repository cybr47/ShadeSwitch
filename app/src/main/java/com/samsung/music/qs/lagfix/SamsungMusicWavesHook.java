package com.samsung.music.qs.lagfix;

import android.content.SharedPreferences;
import android.util.Log;
import io.github.libxposed.api.XposedModule;

/** ShadeSwitch entry point for the SystemUI process. */
public final class SamsungMusicWavesHook extends XposedModule {
    private boolean targetProcess;
    private boolean installed;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        targetProcess = "com.android.systemui".equals(param.getProcessName());
        if (BuildConfig.DEBUG) {
            debug("Module loaded: process=" + param.getProcessName()
                    + "; target=" + targetProcess + "; version=" + BuildConfig.VERSION_NAME
                    + "; framework=" + getFrameworkName() + " " + getFrameworkVersion()
                    + "; api=" + getApiVersion(), null);
        }
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!targetProcess || installed
                || !"com.android.systemui".equals(param.getPackageName())) {
            return;
        }
        installed = true;
        boolean vertical = readPreference(ShadeSwitchPrefs.KEY_VERTICAL_SWIPE);
        boolean hideQuickQs = readPreference(ShadeSwitchPrefs.KEY_HIDE_QUICK_QS_ROW);

        note("Loaded in SystemUI, module version " + BuildConfig.VERSION_NAME
                + " (" + BuildConfig.BUILD_TYPE + ", gestures="
                + (vertical ? "header-only-vertical" : "horizontal")
                + ", hideQuickQsRow=" + hideQuickQs + ")");

        ClassLoader loader = param.getClassLoader();
        installPanelMotion(loader, vertical);
        MediaWaveHooks.install(this, loader);
        if (vertical) NoSwipePanelHooks.install(this, loader);
        if (hideQuickQs) TogetherQuickQsHooks.install(this, loader);
    }

    private void installPanelMotion(ClassLoader loader, boolean vertical) {
        try {
            Class<?> split = loader.loadClass("com.android.systemui.shade.SecPanelSplitHelper");
            if (NativePanels.hasContainerIntercept(split)) {
                note("Using One UI 9.5 native panel motion");
                return;
            }
            NativePanels.method(split, "onIntercept", android.view.MotionEvent.class);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn("Unsupported Samsung panel motion contract", error);
            return;
        }
        SplitMotionHooks.install(this, loader, !vertical);
        try {
            NativeTouchTracks tracks = new NativeTouchTracks(loader);
            QsOpenMotionHooks.install(this, loader, tracks);
            SplitHeaderMotionHooks.install(this, loader, tracks);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn("Unable to bind Samsung panel animation tracks", error);
        }
        QsOpenMotionHooks.installTogetherSpring(this, loader);
        QsDispatchHooks.install(this, loader);
    }

    private boolean readPreference(String key) {
        try {
            SharedPreferences prefs = getRemotePreferences(ShadeSwitchPrefs.PREFS_GROUP);
            if (prefs != null) return prefs.getBoolean(key, false);
        } catch (Throwable error) {
            warn("Unable to read remote preference " + key, error);
        }
        return false;
    }

    void debug(String message, Throwable error) {
        if (BuildConfig.DEBUG) log(Log.INFO, "ShadeSwitch", message, error);
    }

    void note(String message) {
        log(Log.INFO, "ShadeSwitch", message, null);
    }

    void warn(String message, Throwable error) {
        log(Log.WARN, "ShadeSwitch", message, error);
    }
}
