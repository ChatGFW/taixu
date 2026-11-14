package com.ai.assistance.shower;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.util.List;

final class SecondaryHome {
    static final String LAUNCHER3_SECONDARY =
            "com.android.launcher3.secondarydisplay.SecondaryDisplayLauncher";

    private SecondaryHome() {}

    /** @return secondary home 全类名，没有则 null */
    static String resolveActivityName(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo ri = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            if (ri == null || ri.activityInfo == null || ri.activityInfo.packageName == null) {
                return null;
            }
            String homePkg = ri.activityInfo.packageName;
            Intent secondary = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_SECONDARY_HOME)
                    .setPackage(homePkg);
            List<ResolveInfo> list = pm.queryIntentActivities(secondary, 0);
            if (list == null || list.isEmpty()) return null;
            ActivityInfo info = list.get(0).activityInfo;
            return info != null ? info.name : null;
        } catch (Throwable t) {
            ServerLog.d("secondary home resolve: " + t.getMessage(), t);
            return null;
        }
    }

        static boolean matchesLauncher3(String activityName) {
        return LAUNCHER3_SECONDARY.equals(activityName);
    }

    /** pkg/class 短组件名，供 am start -n。 */
    static String flattenComponent(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo ri = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            if (ri == null || ri.activityInfo == null) return null;
            String pkg = ri.activityInfo.packageName;
            String name = resolveActivityName(context);
            if (pkg == null || name == null) return null;
            return pkg + "/" + name;
        } catch (Throwable t) {
            return null;
        }
    }
}
