package dev.codex.felixblur;

import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.graphics.Color;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class FelixBlurHook implements IXposedHookLoadPackage {
    private static final String LAUNCHER = "com.google.android.apps.nexuslauncher";
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String BLUR_SUPPORT_PROPERTY =
            "ro.surface_flinger.supports_background_blur";
    private static final String DISABLE_BLUR_PROPERTY = "persist.sysui.disableBlur";
    private static final String LAUNCHER_BLUR_PROPERTY = "ro.launcher.blur.appLaunch";
    private static final Uri SETTINGS_URI = Uri.parse("content://dev.codex.felixblur.settings");

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            if (LAUNCHER.equals(lpparam.packageName)) {
                hookFrameworkBlurGates(lpparam.packageName);
                if (LAUNCHER.equals(lpparam.processName)) {
                    hookLauncher(lpparam.classLoader);
                }
            } else if (SYSTEM_UI.equals(lpparam.packageName)) {
                hookFrameworkBlurGates(lpparam.packageName);
                if (SYSTEM_UI.equals(lpparam.processName)) {
                    hookSystemUi(lpparam.classLoader);
                }
            }
        } catch (Throwable throwable) {
            XposedBridge.log("FelixBlur: package hook failed: " + throwable);
        }
    }

    private static void hookFrameworkBlurGates(String packageName) {
        final int blurExpansionId = Resources.getSystem().getIdentifier(
                "config_enableBlurExpansion", "bool", "android");
        final int appLaunchBlurId = Resources.getSystem().getIdentifier(
                "config_enableAppLaunchBlur", "bool", "android");
        if (blurExpansionId == 0) {
            XposedBridge.log("FelixBlur: config_enableBlurExpansion resource not found in "
                    + packageName);
        }

        XposedHelpers.findAndHookMethod(Resources.class, "getBoolean", int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        int resourceId = ((Integer) param.args[0]).intValue();
                        if (resourceId == blurExpansionId || resourceId == appLaunchBlurId) {
                            param.setResult(true);
                        }
                    }
                });

        Class<?> systemProperties = XposedHelpers.findClass("android.os.SystemProperties", null);
        XposedHelpers.findAndHookMethod(systemProperties, "getBoolean", String.class,
                boolean.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        String key = (String) param.args[0];
                        if (BLUR_SUPPORT_PROPERTY.equals(key) || LAUNCHER_BLUR_PROPERTY.equals(key)) {
                            param.setResult(true);
                        } else if (DISABLE_BLUR_PROPERTY.equals(key)) {
                            param.setResult(false);
                        }
                    }
                });

        try {
            Class<?> activityManager = XposedHelpers.findClass("android.app.ActivityManager", null);
            XposedHelpers.findAndHookMethod(activityManager, "isHighEndGfx",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult(true);
                        }
                    });
        } catch (Throwable throwable) {
            XposedBridge.log("FelixBlur: isHighEndGfx hook unavailable: " + throwable);
        }

        XposedBridge.log("FelixBlur: framework blur gates installed in " + packageName);
    }

    private static void hookLauncher(ClassLoader classLoader) {
        Class<?> helper = XposedHelpers.findClass(
                "com.android.launcher3.util.QuickstepBackgroundBlurHelper", classLoader);

        XposedHelpers.findAndHookMethod(helper, "isBlurEnabled", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.setResult(true);
            }
        });

        Class<?> utilities = XposedHelpers.findClass("com.android.launcher3.Utilities", classLoader);
        XposedHelpers.findAndHookMethod(
                utilities,
                "shouldReduceWorkspaceBlurUsage",
                Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.setResult(false);
                    }
                });

        try {
            Class<?> panelColors = XposedHelpers.findClass(
                    "com.android.launcher3.util.BlurBackgroundHelper$Companion", classLoader);
            XposedHelpers.findAndHookMethod(panelColors, "getBlurPanelColorWithSE0",
                    Context.class, boolean.class, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            Context context = (Context) param.args[0];
                            String key = launcherColorArea();
                            param.setResult(scaleAlpha((Integer) param.getResult(), setting(context, key)));
                        }
                    });
            XposedBridge.log("FelixBlur: Launcher folder/menu transparency hook installed");
        } catch (Throwable throwable) {
            XposedBridge.log("FelixBlur: Launcher transparency hook unavailable: " + throwable);
        }

        XposedBridge.log("FelixBlur: Launcher hooks installed");
    }

    private static void hookSystemUi(ClassLoader classLoader) {
        try {
            Class<?> powerDelegate = XposedHelpers.findClass(
                    "com.android.systemui.globalactions.GlobalActionsDialogLite$ActionsDialogLiteDelegate",
                    classLoader);
            XposedBridge.hookAllConstructors(powerDelegate, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    setBooleanFieldIfPresent(param.thisObject, "mShowBlur", true);
                }
            });
        } catch (Throwable throwable) {
            XposedBridge.log("FelixBlur: Power Menu hook unavailable: " + throwable);
        }

        tryHookVolumeBinder(
                classLoader,
                "com.android.systemui.volume.dialog.sliders.ui.VolumeDialogSlidersViewBinder");
        tryHookVolumeBinder(
                classLoader,
                "com.android.systemui.volume.dialog.ringer.ui.binder.VolumeDialogRingerViewBinder");

        hookTransparencyControls(classLoader);

        XposedBridge.log("FelixBlur: SystemUI hooks installed");
    }

    private static void hookTransparencyControls(ClassLoader loader) {
        try {
            Class<?> colors = XposedHelpers.findClass(
                    "com.android.systemui.common.shared.colors.BlurPanelColors", loader);
            XposedHelpers.findAndHookMethod(colors, "panelWithSE0", Context.class, boolean.class,
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            String area = stackArea();
                            int alpha = area.equals("volume") ? setting(p.args[0], "volume")
                                    : area.equals("power") ? setting(p.args[0], "power")
                                    : setting(p.args[0], "shade");
                            p.setResult(scaleAlpha((Integer) p.getResult(), alpha));
                        }
                    });
            XposedBridge.log("FelixBlur: panel transparency hook installed");
        } catch (Throwable t) { XposedBridge.log("FelixBlur: panel color hook unavailable: " + t); }

        try {
            Class<?> notification = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.notification.row.NotificationBackgroundView", loader);
            XposedHelpers.findAndHookMethod(notification, "setTint", int.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    Object self = p.thisObject;
                    Context c = ((android.view.View) self).getContext();
                    p.args[0] = scaleAlpha((Integer) p.args[0], setting(c, "notifications"));
                }
            });
        } catch (Throwable t) { XposedBridge.log("FelixBlur: notification tint hook unavailable: " + t); }

        try {
            Class<?> dialog = XposedHelpers.findClass(
                    "com.android.systemui.globalactions.GlobalActionsDialogLite$ActionsDialogLiteDelegate", loader);
            for (Method m : dialog.getDeclaredMethods()) {
                if (!"setBlurRadius".equals(m.getName())) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (p.args.length > 0 && p.args[0] instanceof Integer) {
                            Context c = (Context) XposedHelpers.getObjectField(p.thisObject, "mContext");
                            p.args[0] = Math.round(((Integer) p.args[0]).intValue() * setting(c, "blur") / 100f);
                        }
                    }
                });
            }
        } catch (Throwable t) { XposedBridge.log("FelixBlur: power blur control unavailable: " + t); }

        try {
            Class<?> scrim = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.phone.ScrimController", loader);
            for (Method m : scrim.getDeclaredMethods()) {
                if (!"applyState".equals(m.getName()) && !"applyAndDispatchState".equals(m.getName())) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        try {
                            Context c = (Context) XposedHelpers.getObjectField(p.thisObject, "mContext");
                            float shade = setting(c, "shade") / 100f;
                            float lock = setting(c, "lockscreen") / 100f;
                            Object state = XposedHelpers.getObjectField(p.thisObject, "mState");
                            boolean keyguard = state != null && state.toString().contains("KEYGUARD");
                            String field = keyguard ? "mBehindAlpha" : "mNotificationsAlpha";
                            Field f = XposedHelpers.findField(p.thisObject.getClass(), field);
                            f.setFloat(p.thisObject, f.getFloat(p.thisObject) * (keyguard ? lock : shade));
                        } catch (Throwable ignored) { }
                    }
                });
            }
        } catch (Throwable t) { XposedBridge.log("FelixBlur: scrim transparency hook unavailable: " + t); }
    }

    private static int setting(Object context, String key) {
        try {
            Context c = context instanceof Context ? (Context) context : null;
            if (c == null) return 100;
            Bundle b = c.getContentResolver().call(SETTINGS_URI, "get", null, null);
            return b == null ? 100 : b.getInt(key, 100);
        } catch (Throwable ignored) { return 100; }
    }

    private static int scaleAlpha(int color, int factor) {
        return Color.argb(Math.max(0, Math.min(255, Math.round(Color.alpha(color) * factor / 100f))),
                Color.red(color), Color.green(color), Color.blue(color));
    }

    private static String stackArea() {
        for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
            String n = e.getClassName();
            if (n.contains("Volume")) return "volume";
            if (n.contains("GlobalActions")) return "power";
        }
        return "shade";
    }

    private static String launcherColorArea() {
        for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
            String n = e.getClassName();
            if (n.contains("folder.Folder")) return "folders";
            if (n.contains("OptionsPopup") || n.contains("WorkspaceTouch")) return "launcher_menu";
        }
        return "launcher_menu";
    }

    private static void tryHookVolumeBinder(ClassLoader classLoader, String className) {
        try {
            hookVolumeBinder(classLoader, className);
        } catch (Throwable throwable) {
            XposedBridge.log("FelixBlur: volume hook unavailable for " + className + ": "
                    + throwable);
        }
    }

    private static void hookVolumeBinder(ClassLoader classLoader, String className) {
        Class<?> binder = XposedHelpers.findClass(className, classLoader);
        XC_MethodHook forceViewModel = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    Object viewModel = XposedHelpers.getObjectField(param.thisObject, "viewModel");
                    setBooleanFieldIfPresent(viewModel, "showBlur", true);
                } catch (Throwable ignored) {
                    // Some synthetic/static methods have no binder instance.
                }
            }
        };

        for (Method method : binder.getDeclaredMethods()) {
            String name = method.getName();
            if ("bind".equals(name)
                    || "updateBackground".equals(name)
                    || "setIsBlurSupported".equals(name)) {
                XposedBridge.hookMethod(method, forceViewModel);
            }
        }
    }

    private static void setBooleanFieldIfPresent(Object instance, String fieldName, boolean value) {
        if (instance == null) {
            return;
        }
        try {
            Field field = XposedHelpers.findField(instance.getClass(), fieldName);
            field.setBoolean(instance, value);
        } catch (Throwable throwable) {
            XposedBridge.log("FelixBlur: cannot set " + instance.getClass().getName() + "."
                    + fieldName + ": " + throwable);
        }
    }
}
