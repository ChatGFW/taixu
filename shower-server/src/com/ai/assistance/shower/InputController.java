package com.ai.assistance.shower;

import android.os.SystemClock;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import java.lang.reflect.Method;

final class InputController {
    private static final int MODE_ASYNC = 0;
    private static final int MODE_WAIT_FOR_RESULT = 1;

    private final Object inputManager;
    private final Method injectInputEvent;
    private final Method setDisplayIdMethod;
    private int displayId;
    private volatile Runnable afterInject;

    InputController() {
        try {
            Class<?> cls = Class.forName("android.hardware.input.InputManager");
            Method getInstance = cls.getDeclaredMethod("getInstance");
            getInstance.setAccessible(true);
            inputManager = getInstance.invoke(null);
            injectInputEvent = cls.getDeclaredMethod("injectInputEvent", InputEvent.class, int.class);
            injectInputEvent.setAccessible(true);
            Method setDisplayId;
            try {
                setDisplayId = InputEvent.class.getMethod("setDisplayId", int.class);
            } catch (NoSuchMethodException e) {
                setDisplayId = null;
            }
            setDisplayIdMethod = setDisplayId;
        } catch (Exception e) {
            throw new RuntimeException("Init InputController failed", e);
        }
    }

    void setDisplayId(int id) {
        displayId = id;
        ServerLog.d("InputController.setDisplayId: " + id, null);
    }

        void setAfterInject(Runnable runnable) {
        afterInject = runnable;
    }

    void inject(InputEvent event, int mode) {
        try {
            if (setDisplayIdMethod != null && displayId != 0) {
                setDisplayIdMethod.invoke(event, displayId);
            }
            injectInputEvent.invoke(inputManager, event, mode);
            Runnable hook = afterInject;
            if (hook != null) {
                try {
                    hook.run();
                } catch (Throwable ignored) {
                }
            }
        } catch (Exception e) {
            ServerLog.d("InputController.inject failed: " + e.getMessage(), e);
        }
    }

    void key(int keyCode, int metaState) {
        long now = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, metaState);
        down.setSource(257);
        inject(down, MODE_WAIT_FOR_RESULT);
        KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, metaState);
        up.setSource(257);
        inject(up, MODE_WAIT_FOR_RESULT);
    }

    void touch(int action, float x, float y, long downTime, long eventTime,
               float pressure, float size, int metaState, float xPrecision, float yPrecision,
               int deviceId, int edgeFlags) {
        long down = Math.max(0L, downTime);
        long when = Math.max(down, eventTime);
        MotionEvent ev = MotionEvent.obtain(
                down, when, action, x, y, pressure, size, metaState, xPrecision, yPrecision, deviceId, edgeFlags);
        ev.setSource(4098);
        inject(ev, MODE_ASYNC);
        ev.recycle();
    }

        void injectMotion(MotionEvent event) {
        if (event == null) return;
        inject(event, MODE_ASYNC);
    }

    void tap(float x, float y) {
        long now = SystemClock.uptimeMillis();
        touch(MotionEvent.ACTION_DOWN, x, y, now, now, 1f, 1f, 0, 1f, 1f, 0, 0);
        touch(MotionEvent.ACTION_UP, x, y, now, now + 20, 1f, 1f, 0, 1f, 1f, 0, 0);
    }

    void swipe(float x1, float y1, float x2, float y2, long durationMs) {
        long start = SystemClock.uptimeMillis();
        long dur = Math.max(1L, durationMs);
        touch(MotionEvent.ACTION_DOWN, x1, y1, start, start, 1f, 1f, 0, 1f, 1f, 0, 0);
        int steps = 12;
        for (int i = 1; i < steps; i++) {
            float t = i / (float) steps;
            touch(MotionEvent.ACTION_MOVE,
                    x1 + (x2 - x1) * t, y1 + (y2 - y1) * t,
                    start, start + (long) (dur * t), 1f, 1f, 0, 1f, 1f, 0, 0);
        }
        touch(MotionEvent.ACTION_UP, x2, y2, start, start + dur, 1f, 1f, 0, 1f, 1f, 0, 0);
    }
}
