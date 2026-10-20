package com.mihir.tvhome.settings;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * Remote-friendly settings manager for StarNet Home 3.0.
 * Persists user customization:
 * - Enabled / disabled service packages
 * - Preferred YouTube application target
 * - Home screen row toggles
 */
public class LauncherSettings {

    private static final String PREF_NAME = "tvhome_user_settings";

    public static final String KEY_DISABLED_SERVICES = "disabled_services";
    public static final String KEY_PREFERRED_YOUTUBE = "preferred_youtube";

    public static final String YT_AUTO = "auto";
    public static final String YT_SMARTTUBE = "io.gh.reisxd.tizentube.fortaleza";
    public static final String YT_GOOGLE_TV = "com.google.android.youtube.tv";
    public static final String YT_STANDARD = "com.google.android.youtube";

    public static final String ROW_LIVE_TV = "row_live_tv";
    public static final String ROW_CINEMA = "row_cinema";
    public static final String ROW_STREAMING = "row_streaming";
    public static final String ROW_UTILITIES = "row_utilities";

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static Set<String> getDisabledServices(Context context) {
        String raw = getPrefs(context).getString(KEY_DISABLED_SERVICES, "");
        Set<String> set = new HashSet<>();
        if (!raw.isEmpty()) {
            for (String s : raw.split(",")) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) set.add(trimmed);
            }
        }
        return set;
    }

    public static boolean isServiceEnabled(Context context, String pkg) {
        if (pkg == null) return false;
        return !getDisabledServices(context).contains(pkg);
    }

    public static void setServiceEnabled(Context context, String pkg, boolean enabled) {
        Set<String> set = getDisabledServices(context);
        if (enabled) {
            set.remove(pkg);
        } else {
            set.add(pkg);
        }
        StringBuilder sb = new StringBuilder();
        for (String s : set) {
            if (sb.length() > 0) sb.append(",");
            sb.append(s);
        }
        getPrefs(context).edit().putString(KEY_DISABLED_SERVICES, sb.toString()).apply();
    }

    public static boolean toggleService(Context context, String pkg) {
        boolean currentlyEnabled = isServiceEnabled(context, pkg);
        setServiceEnabled(context, pkg, !currentlyEnabled);
        return !currentlyEnabled;
    }

    public static String getPreferredYouTubeSetting(Context context) {
        return getPrefs(context).getString(KEY_PREFERRED_YOUTUBE, YT_AUTO);
    }

    public static void setPreferredYouTubeSetting(Context context, String setting) {
        getPrefs(context).edit().putString(KEY_PREFERRED_YOUTUBE, setting).apply();
    }

    public static boolean isHomeRowVisible(Context context, String rowId) {
        // All rows default to true (visible)
        return getPrefs(context).getBoolean("show_" + rowId, true);
    }

    public static boolean toggleHomeRow(Context context, String rowId) {
        boolean current = isHomeRowVisible(context, rowId);
        getPrefs(context).edit().putBoolean("show_" + rowId, !current).apply();
        return !current;
    }
}
