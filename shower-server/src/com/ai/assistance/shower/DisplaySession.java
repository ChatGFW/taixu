package com.ai.assistance.shower;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.view.Surface;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

/**
 * VirtualDisplay + ImageReader（usage 514）；trusted 15689 / secondary-home 16201 /
 * API34|81920 / untrusted 8521；IME+windowing；客户端 Surface 直挂。
 */
final class DisplaySession {
    private static final long OFFSCREEN_USAGE = 514L;
    private static final int FLAGS_TRUSTED = 15689;
    private static final int FLAGS_SECONDARY_HOME = 16201;
    private static final int FLAGS_API34 = 81920;
    private static final int FLAGS_UNTRUSTED = 8521;
    private static final String DISPLAY_NAME = "taixu-twin";

    final int displayId;
    final int width;
    final int height;
    final int dpi;
    final InputController input;
    /** trusted && 系统 secondary-home 可用 → 跳过自定义 backdrop */
    final boolean hasSecondaryHome;
    private final VirtualDisplay virtualDisplay;
    private final ImageReader imageReader;
    private final HandlerThread offscreenThread;
    private final Object offscreenLock = new Object();
    private Image latestOffscreen;
    private Surface clientSurface;
    private final boolean trusted;

    private DisplaySession(
            int displayId,
            int width,
            int height,
            int dpi,
            VirtualDisplay virtualDisplay,
            ImageReader imageReader,
            HandlerThread offscreenThread,
            InputController input,
            boolean trusted,
            boolean hasSecondaryHome
    ) {
        this.displayId = displayId;
        this.width = width;
        this.height = height;
        this.dpi = dpi;
        this.virtualDisplay = virtualDisplay;
        this.imageReader = imageReader;
        this.offscreenThread = offscreenThread;
        this.input = input;
        this.trusted = trusted;
        this.hasSecondaryHome = hasSecondaryHome;
    }

    static DisplaySession create(Context context, int width, int height, int dpi) throws Exception {
        int w = ((width + 15) / 16) * 16;
        int h = ((height + 15) / 16) * 16;

        HandlerThread offscreen = new HandlerThread("guest-offscreen");
        offscreen.start();
        Handler handler = new Handler(offscreen.getLooper());
        final ImageReader reader;
        if (Build.VERSION.SDK_INT >= 29) {
            reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2, OFFSCREEN_USAGE);
        } else {
            reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2);
        }
        Surface placeholder = reader.getSurface();
        String owner = resolveOwnerPackage(context);
        DisplayManager dm = createOwnedDisplayManager(context, owner);

        String secondaryName = SecondaryHome.resolveActivityName(context);
        boolean secondaryHome = SecondaryHome.matchesLauncher3(secondaryName)
                || (secondaryName != null && !secondaryName.isEmpty());
        ServerLog.d(
                "secondary home name=" + secondaryName + " enableFlags=" + secondaryHome,
                null);

        boolean trusted = true;
        VirtualDisplay vd;
        try {
            vd = dm.createVirtualDisplay(
                    DISPLAY_NAME, w, h, dpi, placeholder, trustedFlags(secondaryHome));
        } catch (SecurityException se) {
            ServerLog.d("trusted display refused, creating an untrusted one: " + se.getMessage(), se);
            trusted = false;
            secondaryHome = false;
            vd = dm.createVirtualDisplay(
                    DISPLAY_NAME, w, h, dpi, placeholder, FLAGS_UNTRUSTED);
        }
        if (vd == null || vd.getDisplay() == null) {
            reader.close();
            offscreen.quitSafely();
            throw new IllegalStateException("createVirtualDisplay returned null");
        }
        int id = vd.getDisplay().getDisplayId();
        applyImePolicy(id);
        applyWindowingMode(id);

        InputController input = new InputController();
        input.setDisplayId(id);
        boolean hasSecondary = trusted && secondaryHome;
        ServerLog.d(
                "Created virtual display id=" + id + " size=" + w + "x" + h
                        + " owner=" + owner + " trusted=" + trusted
                        + " secondaryHome=" + hasSecondary
                        + " name=" + DISPLAY_NAME,
                null);
        final DisplaySession session = new DisplaySession(
                id, w, h, dpi, vd, reader, offscreen, input, trusted, hasSecondary);
        reader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader imageReader) {
                session.acceptOffscreenFrame(imageReader);
            }
        }, handler);
        return session;
    }

    private void acceptOffscreenFrame(ImageReader imageReader) {
        Image img;
        try {
            img = imageReader.acquireLatestImage();
        } catch (Throwable ignored) {
            return;
        }
        if (img == null) return;
        synchronized (offscreenLock) {
            if (latestOffscreen != null) {
                try {
                    latestOffscreen.close();
                } catch (Throwable ignored) {
                }
            }
            latestOffscreen = img;
        }
    }

    private static int trustedFlags(boolean secondaryHome) {
        int flags = secondaryHome ? FLAGS_SECONDARY_HOME : FLAGS_TRUSTED;
        if (Build.VERSION.SDK_INT >= 34) {
            flags |= FLAGS_API34;
        }
        return flags;
    }

    private static String resolveOwnerPackage(Context context) {
        int uid = Process.myUid();
        if (uid == 1000) return "android";
        if (uid == 2000) return "com.android.shell";
        String pkg = context.getPackageName();
        return pkg != null ? pkg : "com.android.shell";
    }

    private static DisplayManager createOwnedDisplayManager(Context context, String ownerPackage)
            throws Exception {
        Context owned = new OwnerPackageContext(context, ownerPackage);
        Constructor<DisplayManager> ctor = DisplayManager.class.getDeclaredConstructor(Context.class);
        ctor.setAccessible(true);
        return ctor.newInstance(owned);
    }

    private static void applyImePolicy(int displayId) {
        try {
            Object wmg = windowManagerGlobal();
            Method m = wmg.getClass().getMethod("setDisplayImePolicy", int.class, int.class);
            m.invoke(wmg, displayId, 0);
        } catch (Throwable t) {
            ServerLog.d("ime policy: " + t.getMessage(), t);
        }
    }

    private static void applyWindowingMode(int displayId) {
        try {
            Object wmg = windowManagerGlobal();
            Method m = wmg.getClass().getMethod("setWindowingMode", int.class, int.class);
            m.invoke(wmg, displayId, 1);
        } catch (Throwable t) {
            ServerLog.d("windowing mode: " + t.getMessage(), t);
        }
    }

    private static Object windowManagerGlobal() throws Exception {
        Class<?> cls = Class.forName("android.view.WindowManagerGlobal");
        Method get = cls.getMethod("getInstance");
        return get.invoke(null);
    }

    synchronized void setClientSurface(Surface surface) {
        clientSurface = surface;
        Surface target = surface != null ? surface : imageReader.getSurface();
        virtualDisplay.setSurface(target);
        ServerLog.d(
                "setClientSurface display=" + displayId + " attached=" + (surface != null)
                        + " trusted=" + trusted + " secondaryHome=" + hasSecondaryHome,
                null);
    }

    /**
     * 有客户端 Surface 时禁止切 ImageReader（避免预览黑屏连锁）。
     */
    synchronized byte[] captureJpeg() {
        if (clientSurface != null) {
            ServerLog.d(
                    "captureJpeg skipped: client surface attached display=" + displayId
                            + " (avoid black preview)",
                    null);
            return null;
        }
        try {
            Image snapshot = null;
            long deadline = System.currentTimeMillis() + 700L;
            while (System.currentTimeMillis() < deadline) {
                synchronized (offscreenLock) {
                    if (latestOffscreen != null) {
                        snapshot = latestOffscreen;
                        break;
                    }
                }
                try {
                    Thread.sleep(40L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            if (snapshot == null) {
                ServerLog.d("captureJpeg: no ImageReader frame display=" + displayId, null);
                return null;
            }
            return imageToJpeg(snapshot, 55);
        } catch (Throwable t) {
            ServerLog.d("captureJpeg failed: " + t.getMessage(), t);
            return null;
        }
    }

    private static byte[] imageToJpeg(Image image, int quality) {
        int w = image.getWidth();
        int h = image.getHeight();
        Image.Plane[] planes = image.getPlanes();
        if (planes == null || planes.length == 0) return null;
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * w;
        Bitmap bitmap = Bitmap.createBitmap(w + rowPadding / pixelStride, h, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        Bitmap cropped = bitmap;
        if (bitmap.getWidth() != w) {
            cropped = Bitmap.createBitmap(bitmap, 0, 0, w, h);
            bitmap.recycle();
        }
        int maxEdge = 720;
        Bitmap scaled = cropped;
        if (w > maxEdge || h > maxEdge) {
            float scale = Math.min(maxEdge / (float) w, maxEdge / (float) h);
            int nw = Math.max(1, Math.round(w * scale));
            int nh = Math.max(1, Math.round(h * scale));
            scaled = Bitmap.createScaledBitmap(cropped, nw, nh, true);
            if (scaled != cropped) cropped.recycle();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out);
        scaled.recycle();
        return out.toByteArray();
    }

    synchronized void release() {
        try {
            virtualDisplay.release();
        } catch (Throwable t) {
            ServerLog.d("virtualDisplay.release failed: " + t.getMessage(), t);
        }
        synchronized (offscreenLock) {
            if (latestOffscreen != null) {
                try {
                    latestOffscreen.close();
                } catch (Throwable ignored) {
                }
                latestOffscreen = null;
            }
        }
        try {
            imageReader.close();
        } catch (Throwable t) {
            ServerLog.d("imageReader.close failed: " + t.getMessage(), t);
        }
        try {
            offscreenThread.quitSafely();
        } catch (Throwable t) {
            ServerLog.d("offscreenThread.quit failed: " + t.getMessage(), t);
        }
        clientSurface = null;
        input.setDisplayId(0);
    }

    private static final class OwnerPackageContext extends ContextWrapper {
        private final String ownerPackage;

        OwnerPackageContext(Context base, String ownerPackage) {
            super(base);
            this.ownerPackage = ownerPackage;
        }

        @Override
        public String getPackageName() {
            return ownerPackage;
        }

        @Override
        public String getOpPackageName() {
            return ownerPackage;
        }
    }
}
