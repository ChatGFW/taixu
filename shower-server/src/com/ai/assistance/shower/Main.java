package com.ai.assistance.shower;

import android.content.Intent;
import android.os.IBinder;
import android.os.Looper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * app_process 入口：启动 Binder 服务并通过 IActivityManager 把 Binder 交给宿主包。
 * 虚拟屏管线：VirtualDisplay → 客户端 Surface。
 */
public final class Main {
    static volatile String targetPackages;
    private final ConcurrentHashMap<Integer, DisplaySession> sessions = new ConcurrentHashMap<>();
    private final InputController mainInput;

    private Main() {
        InputController input = null;
        try {
            input = new InputController();
            input.setDisplayId(0);
            ServerLog.d("Main display InputController initialized", null);
        } catch (Throwable t) {
            ServerLog.d("Failed to init main display InputController: " + t.getMessage(), t);
        }
        this.mainInput = input;
        try {
            ShowerService service = new ShowerService(
                    ActivityThreadBootstrap.CONTEXT, sessions, mainInput);
            ServerLog.d("Skip ServiceManager.addService by design (Shizuku-style binder handoff)", null);
            sendBinderReady(service);
        } catch (Throwable t) {
            ServerLog.d("Failed to initialize Shower Binder service: " + t.getMessage(), t);
        }
    }

    private static void prepareMainLooper() {
        Looper.prepare();
        try {
            Field field = Looper.class.getDeclaredField("sMainLooper");
            field.setAccessible(true);
            field.set(null, Looper.myLooper());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static boolean broadcastViaActivityManager(Intent intent) {
        try {
            IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                    .getDeclaredMethod("getService", String.class)
                    .invoke(null, "activity");
            if (binder == null) {
                ServerLog.d("IActivityManager binder is null", null);
                return false;
            }
            Object am;
            try {
                am = Class.forName("android.app.IActivityManager$Stub")
                        .getDeclaredMethod("asInterface", IBinder.class)
                        .invoke(null, binder);
            } catch (Throwable ignored) {
                am = Class.forName("android.app.ActivityManagerNative")
                        .getDeclaredMethod("asInterface", IBinder.class)
                        .invoke(null, binder);
            }
            if (am == null) {
                ServerLog.d("Unable to obtain IActivityManager instance", null);
                return false;
            }
            ArrayList<Method> candidates = new ArrayList<>();
            for (Method method : am.getClass().getMethods()) {
                if (!"broadcastIntent".equals(method.getName())) continue;
                for (Class<?> p : method.getParameterTypes()) {
                    if (Intent.class.isAssignableFrom(p)) {
                        candidates.add(method);
                        break;
                    }
                }
            }
            if (candidates.isEmpty()) {
                ServerLog.d("No broadcastIntent method found on IActivityManager", null);
                return false;
            }
            Collections.sort(candidates, Comparator.comparingInt(m -> m.getParameterTypes().length));
            for (Method method : candidates) {
                try {
                    method.setAccessible(true);
                    method.invoke(am, buildArgs(method.getParameterTypes(), intent));
                    ServerLog.d("Sent SHOWER_BINDER_READY via IActivityManager."
                            + method.getParameterTypes().length + "args", null);
                    return true;
                } catch (Throwable t) {
                    ServerLog.d("broadcastIntent failed: " + t.getMessage(), t);
                }
            }
            return false;
        } catch (Throwable t) {
            ServerLog.d("sendBinderReadyViaActivityManager failed: " + t.getMessage(), t);
            return false;
        }
    }

    private static Object[] buildArgs(Class<?>[] types, Intent intent) {
        Object[] args = new Object[types.length];
        int intentIndex = -1;
        for (int i = 0; i < types.length; i++) {
            if (Intent.class.isAssignableFrom(types[i])) {
                intentIndex = i;
                break;
            }
        }
        boolean wroteCaller = false;
        int intCount = 0;
        for (int i = 0; i < types.length; i++) {
            Class<?> type = types[i];
            if (Intent.class.isAssignableFrom(type)) {
                args[i] = intent;
            } else if (type == String.class) {
                if (!wroteCaller && intentIndex >= 0 && i < intentIndex) {
                    args[i] = "com.android.shell";
                    wroteCaller = true;
                } else {
                    args[i] = null;
                }
            } else if (type == int.class) {
                if (intCount == 1) args[i] = -1;
                else if (intCount >= 2) args[i] = -2;
                else args[i] = 0;
                intCount++;
            } else if (type == boolean.class) {
                args[i] = false;
            } else if (type == long.class) {
                args[i] = 0L;
            } else if (type == float.class) {
                args[i] = 0f;
            } else if (type == double.class) {
                args[i] = 0d;
            } else {
                args[i] = null;
            }
        }
        return args;
    }

    private static void sendBinderReady(IBinder service) {
        try {
            Intent intent = new Intent("com.ai.assistance.operit.action.SHOWER_BINDER_READY");
            intent.addFlags(32);
            intent.putExtra("binder_container", new ShowerBinderContainer(service));
            ArrayList<String> packages = new ArrayList<>();
            String raw = targetPackages;
            if (raw != null && !raw.trim().isEmpty()) {
                for (String part : raw.split(",")) {
                    if (part == null) continue;
                    String pkg = part.trim();
                    if (!pkg.isEmpty() && !packages.contains(pkg)) packages.add(pkg);
                }
            }
            if (packages.isEmpty()) {
                if (!broadcastViaActivityManager(intent)) {
                    ServerLog.d("Failed to send SHOWER_BINDER_READY: ActivityManager path unavailable", null);
                }
                return;
            }
            boolean ok = false;
            for (String pkg : packages) {
                Intent copy = new Intent(intent);
                copy.setPackage(pkg);
                ok = broadcastViaActivityManager(copy) || ok;
            }
            if (!ok) {
                ServerLog.d("Failed to send SHOWER_BINDER_READY to target packages: " + packages, null);
            }
        } catch (Throwable t) {
            ServerLog.d("Failed to send SHOWER_BINDER_READY broadcast: " + t.getMessage(), t);
        }
    }

    public static void main(String... args) {
        if (args != null && args.length > 0 && args[0] != null && !args[0].trim().isEmpty()) {
            targetPackages = args[0].trim();
            ServerLog.d("Using target package arg from args: " + targetPackages, null);
        }
        try {
            prepareMainLooper();
            ServerLog.d("prepareMainLooper ok", null);
        } catch (Throwable t) {
            ServerLog.d("prepareMainLooper failed: " + t.getMessage(), t);
        }
        try {
            // 触发静态初始化（ActivityThread + ShellContext）
            ServerLog.d("bootstrap context=" + ActivityThreadBootstrap.CONTEXT.getPackageName(), null);
        } catch (Throwable t) {
            ServerLog.d("Workarounds.apply failed: " + t.getMessage(), t);
        }
        new Main();
        ServerLog.d("server started (direct Surface pipeline)", null);
        // 必须进入消息循环：TaskReclaimer 等 Handler 投递依赖主 Looper。
        try {
            Looper.loop();
        } catch (Throwable t) {
            ServerLog.d("Main Looper.loop exited: " + t.getMessage(), t);
        }
    }
}
