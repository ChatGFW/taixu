package com.ai.assistance.shower;

import android.content.AttributionSource;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Build;

/**
 * 伪装成 com.android.shell，便于 DisplayManager 以 shell 身份建虚拟屏。
 */
final class ShellContext extends ContextWrapper {
    ShellContext(Context base) {
        super(base);
    }

    @Override
    public String getPackageName() {
        return "com.android.shell";
    }

    @Override
    public String getOpPackageName() {
        return "com.android.shell";
    }

    @Override
    public Context getApplicationContext() {
        return this;
    }

    @Override
    public Context createPackageContext(String packageName, int flags) {
        return this;
    }

    @Override
    public AttributionSource getAttributionSource() {
        if (Build.VERSION.SDK_INT < 31) {
            return super.getAttributionSource();
        }
        return new AttributionSource.Builder(android.os.Process.myUid())
                .setPackageName("com.android.shell")
                .build();
    }
}
