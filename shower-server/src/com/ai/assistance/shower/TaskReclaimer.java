package com.ai.assistance.shower;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 输入后把宿主误落 VD 的任务挪回主屏，把 guest 任务拉回虚拟屏。
 * opened-by：近期在 VD 上出现过的包（含 Chooser/子包）也视为 guest。
 */
final class TaskReclaimer {
    private static final long PULL_DELAY_MS = 1000L;
    private static final long OPENED_BY_WINDOW_MS = 5000L;
    private static final int ACTIVITY_TYPE_HOME = 1;

    private final Context context;
    private final Handler handler;
    private final Set<String> guestPackages = ConcurrentHashMap.newKeySet();
    /** 近期在 VD 上见过的包（opened-by）。 */
    private final Set<String> openedByPackages = ConcurrentHashMap.newKeySet();
    private final Set<Integer> spilledTaskIds = ConcurrentHashMap.newKeySet();
    private final Object atmLock = new Object();
    private Object activityTaskManager;
    private Field displayIdField;
    private Method getActivityType;
    private volatile int activeDisplayId = -1;
    private volatile long lastInputUptimeMs = 0L;
    private final Runnable pullRunnable = new Runnable() {
        @Override
        public void run() {
            int id = activeDisplayId;
            if (id > 0) {
                reclaim(id);
            }
        }
    };

    TaskReclaimer(Context context) {
        this.context = context;
        this.handler = new Handler(Looper.getMainLooper());
        try {
            displayIdField = Class.forName("android.app.TaskInfo").getField("displayId");
        } catch (Throwable t) {
            ServerLog.d("TaskReclaimer displayId field: " + t.getMessage(), t);
        }
        try {
            getActivityType = Class.forName("android.app.TaskInfo").getMethod("getActivityType");
        } catch (Throwable ignored) {
            getActivityType = null;
        }
    }

    void noteGuestPackage(String packageName) {
        if (packageName != null && !packageName.isEmpty()) {
            guestPackages.add(packageName);
        }
    }

    void scheduleAfterInput(int displayId) {
        if (displayId <= 0) return;
        activeDisplayId = displayId;
        lastInputUptimeMs = SystemClock.uptimeMillis();
        handler.removeCallbacks(pullRunnable);
        handler.postDelayed(pullRunnable, PULL_DELAY_MS);
    }

    private boolean withinOpenedByWindow() {
        return SystemClock.uptimeMillis() - lastInputUptimeMs <= OPENED_BY_WINDOW_MS;
    }

    private boolean isGuestPackage(String pkg) {
        return pkg != null
                && (guestPackages.contains(pkg) || openedByPackages.contains(pkg));
    }

        void takeBackSpilled(int displayId) {
        if (displayId <= 0 || spilledTaskIds.isEmpty()) return;
        Set<Integer> ids = new HashSet<>(spilledTaskIds);
        spilledTaskIds.clear();
        try {
            for (TaskSnap t : listTasks()) {
                if (ids.contains(t.taskId) && t.displayId == 0) {
                    moveRootTaskToDisplay(t.taskId, displayId);
                    ServerLog.d(
                            "took back spilled task=" + t.taskId + " -> display=" + displayId,
                            null);
                }
            }
        } catch (Throwable t) {
            ServerLog.d("takeBackSpilled: " + t.getMessage(), t);
        }
    }

    void reclaim(int displayId) {
        String host = hostPackage();
        if (host == null) return;
        try {
            List<TaskSnap> tasks = listTasks();
            // 0) 记下当前 VD 上的包 → opened-by（Chooser/子 Activity 包名）
            for (TaskSnap t : tasks) {
                if (t.displayId != displayId || t.isTwinHome) continue;
                for (String pkg : t.packages) {
                    if (pkg == null || pkg.equals(host)) continue;
                    openedByPackages.add(pkg);
                    guestPackages.add(pkg);
                }
            }
            // 1) 宿主非桌面/backdrop 任务若在 VD 上 → 挪回主屏
            for (TaskSnap t : tasks) {
                if (t.displayId != displayId) continue;
                if (!t.packages.contains(host)) continue;
                if (t.isTwinHome) continue;
                moveRootTaskToDisplay(t.taskId, 0);
                ServerLog.d(
                        "moved host task=" + t.taskId + " off display=" + displayId,
                        null);
            }
            // 2) guest / opened-by 任务若落到主屏 → 拉回 VD
            for (TaskSnap t : tasks) {
                if (t.displayId != 0) continue;
                if (t.packages.contains(host)) continue;
                boolean guest = false;
                for (String pkg : t.packages) {
                    if (isGuestPackage(pkg)) {
                        guest = true;
                        break;
                    }
                }
                if (!guest) continue;
                spilledTaskIds.add(t.taskId);
                moveRootTaskToDisplay(t.taskId, displayId);
                ServerLog.d(
                        "moved guest/opened-by task=" + t.taskId + " back to display=" + displayId
                                + " hot=" + withinOpenedByWindow(),
                        null);
            }
        } catch (Throwable t) {
            ServerLog.d("reclaim failed: " + t.getMessage(), t);
        }
    }

    private List<TaskSnap> listTasks() throws Exception {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        @SuppressWarnings("deprecation")
        List<ActivityManager.RunningTaskInfo> running = am.getRunningTasks(100);
        List<TaskSnap> out = new ArrayList<>();
        if (running == null) return out;
        for (ActivityManager.RunningTaskInfo info : running) {
            int displayId = 0;
            if (displayIdField != null) {
                try {
                    displayId = displayIdField.getInt(info);
                } catch (Throwable ignored) {
                }
            }
            boolean backdrop = isTwinHomeComponent(info);
            boolean homeType = false;
            if (getActivityType != null) {
                try {
                    Object type = getActivityType.invoke(info);
                    homeType = type instanceof Integer && ((Integer) type) == ACTIVITY_TYPE_HOME;
                } catch (Throwable ignored) {
                }
            }
            Set<String> pkgs = new HashSet<>();
            ComponentName base = info.baseActivity;
            ComponentName top = info.topActivity;
            Intent intent = info.baseIntent;
            if (base != null && base.getPackageName() != null) pkgs.add(base.getPackageName());
            if (top != null && top.getPackageName() != null) pkgs.add(top.getPackageName());
            if (intent != null && intent.getComponent() != null
                    && intent.getComponent().getPackageName() != null) {
                pkgs.add(intent.getComponent().getPackageName());
            }
            out.add(new TaskSnap(info.taskId, displayId, pkgs, backdrop || homeType));
        }
        return out;
    }

    private static boolean isTwinHomeComponent(ActivityManager.RunningTaskInfo info) {
        String[] names = {
                className(info.baseActivity),
                className(info.topActivity),
                info.baseIntent != null && info.baseIntent.getComponent() != null
                        ? info.baseIntent.getComponent().getClassName()
                        : null,
        };
        for (String n : names) {
            if (n == null) continue;
            if (n.endsWith(".TwinLauncherActivity") || n.endsWith(".TwinBackdropActivity")) {
                return true;
            }
            if (n.contains("SecondaryDisplayLauncher") || n.contains("占位 Activity")) {
                return true;
            }
        }
        return false;
    }

    private static String className(ComponentName cn) {
        return cn != null ? cn.getClassName() : null;
    }

    private void moveRootTaskToDisplay(int taskId, int displayId) {
        try {
            Object atm = activityTaskManager();
            if (atm == null) return;
            Method m = atm.getClass().getMethod(
                    "moveRootTaskToDisplay", int.class, int.class);
            m.invoke(atm, taskId, displayId);
        } catch (Throwable t) {
            ServerLog.d(
                    "moveRootTaskToDisplay task=" + taskId + " display=" + displayId
                            + ": " + t.getMessage(),
                    t);
        }
    }

    private Object activityTaskManager() {
        synchronized (atmLock) {
            if (activityTaskManager != null) return activityTaskManager;
            try {
                IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                        .getMethod("getService", String.class)
                        .invoke(null, "activity_task");
                activityTaskManager = Class.forName("android.app.IActivityTaskManager$Stub")
                        .getMethod("asInterface", IBinder.class)
                        .invoke(null, binder);
            } catch (Throwable t) {
                ServerLog.d("IActivityTaskManager: " + t.getMessage(), t);
            }
            return activityTaskManager;
        }
    }

    private static String hostPackage() {
        String raw = Main.targetPackages;
        if (raw == null || raw.isEmpty()) return null;
        int comma = raw.indexOf(',');
        return comma < 0 ? raw.trim() : raw.substring(0, comma).trim();
    }

    private static final class TaskSnap {
        final int taskId;
        final int displayId;
        final Set<String> packages;
        final boolean isTwinHome;

        TaskSnap(int taskId, int displayId, Set<String> packages, boolean isTwinHome) {
            this.taskId = taskId;
            this.displayId = displayId;
            this.packages = packages;
            this.isTwinHome = isTwinHome;
        }
    }
}
