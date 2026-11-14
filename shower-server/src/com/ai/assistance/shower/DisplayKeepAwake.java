package com.ai.assistance.shower;

import android.os.IBinder;
import android.os.SystemClock;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * trusted VD 上刷 userActivity，避免久置后合成休眠导致预览黑/注入无响应。
 */
final class DisplayKeepAwake {
    private static final Object LOCK = new Object();
    private static Object powerManager;
    private static Method userActivity;

    private DisplayKeepAwake() {}

    static void pulse(int displayId) {
        if (displayId <= 0) return;
        try {
            ensureResolved();
            Method method;
            Object pm;
            synchronized (LOCK) {
                method = userActivity;
                pm = powerManager;
            }
            if (method == null || pm == null) return;
            method.invoke(pm, displayId, SystemClock.uptimeMillis(), 2, 0);
        } catch (Throwable t) {
            ServerLog.d("keepAwake: " + t.getMessage(), t);
        }
    }

    private static void ensureResolved() throws Exception {
        synchronized (LOCK) {
            if (userActivity != null) return;
            Object stub = Class.forName("android.os.IPowerManager$Stub")
                    .getMethod("asInterface", IBinder.class)
                    .invoke(
                            null,
                            Class.forName("android.os.ServiceManager")
                                    .getMethod("getService", String.class)
                                    .invoke(null, "power"));
            Method[] methods = stub.getClass().getMethods();
            for (Method method : methods) {
                if (!"userActivity".equals(method.getName())) continue;
                Class<?>[] types = method.getParameterTypes();
                Class<?> i = Integer.TYPE;
                if (Arrays.equals(types, new Class<?>[]{i, Long.TYPE, i, i})) {
                    powerManager = stub;
                    userActivity = method;
                    return;
                }
            }
            ServerLog.d("keepAwake: userActivity(int,long,int,int) not found", null);
        }
    }
}
