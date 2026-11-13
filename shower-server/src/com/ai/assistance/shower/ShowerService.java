package com.ai.assistance.shower;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.Surface;
import java.util.concurrent.ConcurrentHashMap;

final class ShowerService extends IShowerService.Stub {
    private static final int LAUNCH_FLAGS = 0x10200000;

    private final Context context;
    private final ConcurrentHashMap<Integer, DisplaySession> sessions;
    private final InputController mainInput;
    private final TaskReclaimer taskReclaimer;

    ShowerService(Context context, ConcurrentHashMap<Integer, DisplaySession> sessions, InputController mainInput) {
        this.context = context;
        this.sessions = sessions;
        this.mainInput = mainInput;
        this.taskReclaimer = new TaskReclaimer(context);
        if (mainInput != null) {
            mainInput.setAfterInject(new Runnable() {
                @Override
                public void run() {
                    // main display 无 VD 拉回
                }
            });
        }
    }

    private InputController inputFor(int displayId) {
        if (displayId == 0) return mainInput;
        DisplaySession session = sessions.get(displayId);
        if (session == null) {
            throw new IllegalStateException("No InputController for displayId=" + displayId);
        }
        return session.input;
    }

    private void wireInputHooks(final int displayId, InputController input) {
        input.setAfterInject(new Runnable() {
            @Override
            public void run() {
                taskReclaimer.scheduleAfterInput(displayId);
            }
        });
    }

    @Override
    public int ensureDisplay(int width, int height, int dpi, int bitrateKbps) {
        try {
            DisplaySession session = DisplaySession.create(context, width, height, dpi);
            sessions.put(session.displayId, session);
            wireInputHooks(session.displayId, session.input);

            if (!session.hasSecondaryHome) {
                launchBackdrop(session.displayId);
            } else {
                ServerLog.d(
                        "skip backdrop: secondary home on display=" + session.displayId,
                        null);
                // 保险：显式拉起已解析的 secondary home
                String comp = SecondaryHome.flattenComponent(context);
                if (comp != null) {
                    amStartOnDisplay(session.displayId, comp);
                }
            }
            return session.displayId;
        } catch (Throwable t) {
            ServerLog.d("ensureDisplay failed: " + t.getMessage(), t);
            return -1;
        }
    }

    private void launchBackdrop(int displayId) {
        String pkg = firstTargetPackage();
        if (pkg == null || pkg.isEmpty()) {
            ServerLog.d("launchBackdrop skipped: no target package", null);
            return;
        }
        // 动态 secondary（非 launcher3 精确命中但仍有 SECONDARY_HOME 时）再试一次
        String secondary = SecondaryHome.flattenComponent(context);
        if (secondary != null && amStartOnDisplay(displayId, secondary)) {
            return;
        }
        String launcher = pkg + "/top.wkbin.taixu.runtime.virtualdisplay.TwinLauncherActivity";
        String backdrop = pkg + "/top.wkbin.taixu.runtime.virtualdisplay.TwinBackdropActivity";
        if (!amStartOnDisplay(displayId, launcher)) {
            amStartOnDisplay(displayId, backdrop);
        }
    }

    /** Soft Home 落点：系统 secondary 或 TwinLauncher。 */
    private void bringHome(int displayId) {
        DisplaySession session = sessions.get(displayId);
        if (session != null && session.hasSecondaryHome) {
            String comp = SecondaryHome.flattenComponent(context);
            if (comp != null) {
                amStartOnDisplay(displayId, comp);
                return;
            }
        }
        String pkg = firstTargetPackage();
        if (pkg == null) return;
        amStartOnDisplay(
                displayId,
                pkg + "/top.wkbin.taixu.runtime.virtualdisplay.TwinLauncherActivity");
    }

    private static boolean amStartOnDisplay(int displayId, String component) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "am", "start", "--display", String.valueOf(displayId),
                    "-f", "0x10200000",
                    "-n", component);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int code = p.waitFor();
            ServerLog.d(
                    "amStartOnDisplay display=" + displayId + " component=" + component
                            + " exit=" + code,
                    null);
            return code == 0;
        } catch (Throwable t) {
            ServerLog.d("amStartOnDisplay failed: " + t.getMessage(), t);
            return false;
        }
    }

    private static String firstTargetPackage() {
        String raw = Main.targetPackages;
        if (raw == null || raw.isEmpty()) return null;
        int comma = raw.indexOf(',');
        return comma < 0 ? raw.trim() : raw.substring(0, comma).trim();
    }

    @Override
    public void destroyDisplay(int displayId) {
        DisplaySession session = sessions.remove(displayId);
        if (session != null) {
            session.release();
            ServerLog.d("Destroyed display " + displayId, null);
        }
    }

    @Override
    public void setDisplaySurface(int displayId, Surface surface) {
        DisplaySession session = sessions.get(displayId);
        if (session == null) {
            ServerLog.d("setDisplaySurface unknown displayId=" + displayId, null);
            return;
        }
        session.setClientSurface(surface);
    }

    @Override
    public void setVideoSink(int displayId, IBinder sink) {
        ServerLog.d("setVideoSink ignored (direct Surface pipeline) display=" + displayId, null);
    }

    @Override
    public void launchApp(String packageName, int displayId) {
        try {
            taskReclaimer.noteGuestPackage(packageName);
            taskReclaimer.takeBackSpilled(displayId);
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(packageName);
            if (launch == null) {
                ServerLog.d("launchApp: no launch intent for " + packageName, null);
                return;
            }
            ComponentName component = launch.getComponent();
            if (component == null) {
                ServerLog.d("launchApp: no component for " + packageName, null);
                return;
            }
            launch.addFlags(LAUNCH_FLAGS);
            boolean started = false;
            try {
                Object opts = Class.forName("android.app.ActivityOptions")
                        .getMethod("makeBasic")
                        .invoke(null);
                opts.getClass().getMethod("setLaunchDisplayId", int.class).invoke(opts, displayId);
                Bundle bundle = (Bundle) opts.getClass().getMethod("toBundle").invoke(opts);
                context.startActivity(launch, bundle);
                started = true;
                ServerLog.d("launchApp startActivity display=" + displayId + " pkg=" + packageName, null);
            } catch (Throwable reflection) {
                ServerLog.d("launchApp startActivity failed: " + reflection.getMessage(), reflection);
            }
            try {
                String shortName = component.flattenToShortString();
                ProcessBuilder pb = new ProcessBuilder(
                        "am", "start",
                        "--display", String.valueOf(displayId),
                        "-a", "android.intent.action.MAIN",
                        "-c", "android.intent.category.LAUNCHER",
                        "-f", "0x" + Integer.toHexString(LAUNCH_FLAGS),
                        "-n", shortName);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                p.waitFor();
                ServerLog.d(
                        "launchApp am --display " + displayId + " n=" + shortName
                                + " exit=" + p.exitValue(),
                        null);
                started = started || p.exitValue() == 0;
            } catch (Throwable amErr) {
                ServerLog.d("launchApp am failed: " + amErr.getMessage(), amErr);
            }
            if (!started) {
                ServerLog.d("launchApp all paths failed for " + packageName, null);
            }
            taskReclaimer.scheduleAfterInput(displayId);
        } catch (Throwable t) {
            ServerLog.d("launchApp failed: " + t.getMessage(), t);
        }
    }

    @Override
    public void tap(int displayId, float x, float y) {
        inputFor(displayId).tap(x, y);
    }

    @Override
    public void swipe(int displayId, float x1, float y1, float x2, float y2, long durationMs) {
        inputFor(displayId).swipe(x1, y1, x2, y2, durationMs);
    }

    @Override
    public void touchDown(int displayId, float x, float y) {
        long now = SystemClock.uptimeMillis();
        inputFor(displayId).touch(0, x, y, now, now, 1f, 1f, 0, 1f, 1f, 0, 0);
    }

    @Override
    public void touchMove(int displayId, float x, float y) {
        long now = SystemClock.uptimeMillis();
        inputFor(displayId).touch(2, x, y, now, now, 1f, 1f, 0, 1f, 1f, 0, 0);
    }

    @Override
    public void touchUp(int displayId, float x, float y) {
        long now = SystemClock.uptimeMillis();
        inputFor(displayId).touch(1, x, y, now, now, 1f, 1f, 0, 1f, 1f, 0, 0);
    }

    @Override
    public void injectTouchEvent(
            int displayId, int action, float x, float y, long downTime, long eventTime,
            float pressure, float size, int metaState, float xPrecision, float yPrecision,
            int deviceId, int edgeFlags
    ) {
        inputFor(displayId).touch(
                action, x, y, downTime, eventTime, pressure, size, metaState,
                xPrecision, yPrecision, deviceId, edgeFlags);
    }

    @Override
    public void injectKey(int displayId, int keyCode) {
        inputFor(displayId).key(keyCode, 0);
        if (keyCode == KeyEvent.KEYCODE_HOME) {
            bringHome(displayId);
        }
    }

    @Override
    public void injectKeyWithMeta(int displayId, int keyCode, int metaState) {
        inputFor(displayId).key(keyCode, metaState);
        if (keyCode == KeyEvent.KEYCODE_HOME) {
            bringHome(displayId);
        }
    }

    @Override
    public byte[] requestScreenshot(int displayId) {
        DisplaySession session = sessions.get(displayId);
        if (session != null) {
            try {
                byte[] jpeg = session.captureJpeg();
                if (jpeg != null && jpeg.length > 0) {
                    ServerLog.d(
                            "requestScreenshot ImageReader ok display=" + displayId
                                    + " bytes=" + jpeg.length,
                            null);
                    return jpeg;
                }
            } catch (Throwable t) {
                ServerLog.d("requestScreenshot ImageReader failed: " + t.getMessage(), t);
            }
        }
        try {
            java.io.File file = new java.io.File(
                    "/data/local/tmp/shower_cap_" + displayId + ".jpg");
            ProcessBuilder pb = new ProcessBuilder(
                    "screencap", "-d", String.valueOf(displayId),
                    "-p", file.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int code = p.waitFor();
            if (code != 0 || !file.exists() || file.length() == 0L) {
                ServerLog.d(
                        "requestScreenshot screencap failed display=" + displayId
                                + " exit=" + code + " size=" + file.length(),
                        null);
                return null;
            }
            byte[] bytes = readAll(file);
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            ServerLog.d(
                    "requestScreenshot screencap ok display=" + displayId + " bytes=" + bytes.length,
                    null);
            return bytes;
        } catch (Throwable t) {
            ServerLog.d("requestScreenshot failed: " + t.getMessage(), t);
            return null;
        }
    }

    private static byte[] readAll(java.io.File file) throws java.io.IOException {
        java.io.FileInputStream in = new java.io.FileInputStream(file);
        try {
            byte[] buf = new byte[(int) file.length()];
            int off = 0;
            while (off < buf.length) {
                int n = in.read(buf, off, buf.length - off);
                if (n < 0) break;
                off += n;
            }
            return buf;
        } finally {
            in.close();
        }
    }
}
