package com.ai.assistance.shower;

import android.app.UiAutomation;
import android.os.Bundle;
import android.os.Looper;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * HostDisplay 风格：UiAutomation 找焦点可编辑节点，ACTION_SET_TEXT 直设。
 */
final class AccessibilityInput {
    private static final int ACTION_SET_TEXT = 0x200000;
    private static final String ARG_SET_TEXT = "ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE";
    /** 与 UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES 一致。 */
    private static final int FLAG_DONT_SUPPRESS_A11Y = 0x00000001;
    /** 与 UiAutomation.FLAG_RETRIEVE_INTERACTIVE_WINDOWS 一致；否则 getWindows 为空。 */
    private static final int FLAG_RETRIEVE_INTERACTIVE_WINDOWS = 0x00000002;

    private final Object lock = new Object();
    private UiAutomation uiAutomation;

    boolean setText(int displayId, String text, boolean replace) {
        if (text == null) text = "";
        try {
            UiAutomation ua = ensureUiAutomation();
            if (ua == null) return false;
            AccessibilityNodeInfo focus = findEditable(ua, displayId);
            if (focus == null) {
                ServerLog.d("setText: no editable focus display=" + displayId, null);
                return false;
            }
            String base = "";
            if (!replace && !focus.isShowingHintText()) {
                CharSequence cur = focus.getText();
                if (cur != null) base = cur.toString();
            }
            Bundle args = new Bundle();
            args.putCharSequence(ARG_SET_TEXT, base + text);
            boolean ok = focus.performAction(ACTION_SET_TEXT, args);
            ServerLog.d(
                    "setText display=" + displayId + " ok=" + ok
                            + " replace=" + replace + " len=" + text.length(),
                    null);
            return ok;
        } catch (Throwable t) {
            ServerLog.d("setText failed: " + t.getMessage(), t);
            return false;
        }
    }

    private UiAutomation ensureUiAutomation() {
        synchronized (lock) {
            if (uiAutomation != null) return uiAutomation;
        }
        try {
            FutureTask<UiAutomation> task = new FutureTask<>(new Callable<UiAutomation>() {
                @Override
                public UiAutomation call() throws Exception {
                    return connectUiAutomation(Looper.myLooper());
                }
            });
            Thread t = new Thread(task, "guest-uia");
            t.start();
            UiAutomation ua = task.get(10L, TimeUnit.SECONDS);
            synchronized (lock) {
                uiAutomation = ua;
            }
            return ua;
        } catch (Throwable e) {
            ServerLog.d("UiAutomation connect: " + e.getMessage(), e);
            return null;
        }
    }

    private static UiAutomation connectUiAutomation(Looper looper) throws Exception {
        Class<?> connCls = Class.forName("android.app.UiAutomationConnection");
        Object conn = connCls.getDeclaredConstructor().newInstance();
        Constructor<UiAutomation> ctor =
                UiAutomation.class.getDeclaredConstructor(Looper.class, Class.forName("android.app.IUiAutomationConnection"));
        ctor.setAccessible(true);
        UiAutomation ua = ctor.newInstance(looper != null ? looper : Looper.getMainLooper(), conn);
        Method connect = UiAutomation.class.getMethod("connect", int.class);
        connect.invoke(ua, FLAG_DONT_SUPPRESS_A11Y | FLAG_RETRIEVE_INTERACTIVE_WINDOWS);
        return ua;
    }

    private AccessibilityNodeInfo findEditable(UiAutomation ua, int displayId) {
        List<AccessibilityWindowInfo> windows = windowsOnDisplay(ua, displayId);
        if (windows == null || windows.isEmpty()) return null;
        AccessibilityNodeInfo focused = null;
        try {
            Method findFocus = UiAutomation.class.getMethod("findFocus", int.class);
            focused = (AccessibilityNodeInfo) findFocus.invoke(
                    ua, AccessibilityNodeInfo.FOCUS_INPUT);
        } catch (Throwable ignored) {
        }
        if (focused != null && focused.isEditable()) return focused;

        for (AccessibilityWindowInfo w : windows) {
            AccessibilityNodeInfo root = w.getRoot();
            if (root == null) continue;
            AccessibilityNodeInfo editable = findEditableRecursive(root, 0);
            if (editable != null) return editable;
        }
        return focused;
    }

    @SuppressWarnings("unchecked")
    private static List<AccessibilityWindowInfo> windowsOnDisplay(UiAutomation ua, int displayId) {
        try {
            Method m = UiAutomation.class.getMethod("getWindowsOnAllDisplays");
            Object sparse = m.invoke(ua);
            if (sparse == null) return null;
            Method get = sparse.getClass().getMethod("get", int.class);
            Object list = get.invoke(sparse, displayId);
            if (list instanceof List) return (List<AccessibilityWindowInfo>) list;
        } catch (Throwable t) {
            ServerLog.d("getWindowsOnAllDisplays: " + t.getMessage(), t);
        }
        try {
            List<AccessibilityWindowInfo> all = ua.getWindows();
            return all != null ? new ArrayList<>(all) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static AccessibilityNodeInfo findEditableRecursive(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 24) return null;
        if (node.isEditable() && node.isFocused()) return node;
        AccessibilityNodeInfo editable = null;
        if (node.isEditable()) editable = node;
        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            AccessibilityNodeInfo found = findEditableRecursive(child, depth + 1);
            if (found != null) return found;
        }
        return editable;
    }
}
