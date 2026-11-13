package com.ai.assistance.shower;

import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** 在 app_process 里伪造 ActivityThread，拿到可用 Context（旧 server w2.a/b 的精简版）。 */
final class ActivityThreadBootstrap {
    static final Class<?> ACTIVITY_THREAD;
    static final Object THREAD;
    static final ShellContext CONTEXT;

    static {
        try {
            ACTIVITY_THREAD = Class.forName("android.app.ActivityThread");
            Constructor<?> ctor = ACTIVITY_THREAD.getDeclaredConstructor();
            ctor.setAccessible(true);
            THREAD = ctor.newInstance();
            Field current = ACTIVITY_THREAD.getDeclaredField("sCurrentActivityThread");
            current.setAccessible(true);
            current.set(null, THREAD);
            try {
                Field system = ACTIVITY_THREAD.getDeclaredField("mSystemThread");
                system.setAccessible(true);
                system.setBoolean(THREAD, true);
            } catch (Throwable ignored) {
            }
            applyWorkarounds();
            CONTEXT = new ShellContext(resolveBaseContext());
            bindApplication(CONTEXT);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static void applyWorkarounds() {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                Constructor<?> cc = Class.forName("android.app.ConfigurationController")
                        .getDeclaredConstructor(Class.forName("android.app.ActivityThreadInternal"));
                cc.setAccessible(true);
                Object controller = cc.newInstance(THREAD);
                Field field = ACTIVITY_THREAD.getDeclaredField("mConfigurationController");
                field.setAccessible(true);
                field.set(THREAD, controller);
            } catch (Throwable ignored) {
            }
        }
        try {
            Class<?> bindData = Class.forName("android.app.ActivityThread$AppBindData");
            Constructor<?> bdCtor = bindData.getDeclaredConstructor();
            bdCtor.setAccessible(true);
            Object data = bdCtor.newInstance();
            ApplicationInfo info = new ApplicationInfo();
            info.packageName = "com.android.shell";
            Field appInfo = bindData.getDeclaredField("appInfo");
            appInfo.setAccessible(true);
            appInfo.set(data, info);
            Field bound = ACTIVITY_THREAD.getDeclaredField("mBoundApplication");
            bound.setAccessible(true);
            bound.set(THREAD, data);
        } catch (Throwable ignored) {
        }
    }

    private static Context resolveBaseContext() throws Exception {
        Method getSystemContext = ACTIVITY_THREAD.getDeclaredMethod("getSystemContext");
        getSystemContext.setAccessible(true);
        Context ctx = (Context) getSystemContext.invoke(THREAD);
        if (ctx != null) return ctx;
        Method getSystemUi = ACTIVITY_THREAD.getDeclaredMethod("getSystemUiContext");
        getSystemUi.setAccessible(true);
        ctx = (Context) getSystemUi.invoke(THREAD);
        if (ctx != null) return ctx;
        throw new IllegalStateException("Unable to resolve system context");
    }

    private static void bindApplication(ShellContext shell) throws Exception {
        Application app = new Application();
        Field base = ContextWrapper.class.getDeclaredField("mBase");
        base.setAccessible(true);
        base.set(app, shell);
        Field initial = ACTIVITY_THREAD.getDeclaredField("mInitialApplication");
        initial.setAccessible(true);
        initial.set(THREAD, app);
    }

    private ActivityThreadBootstrap() {}
}
