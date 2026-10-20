package com.mihir.tvhome.content;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.mihir.tvhome.MainActivity;
import com.mihir.tvhome.model.MediaItem;
import com.mihir.tvhome.model.MediaShelf;
import com.mihir.tvhome.settings.LauncherSettings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Content aggregation manager for StarNet Home 3.0.
 * Enforces the strict "Verified Content Only" policy:
 * - Genuine records only: no fabricated trending movie posters or synthetic progress.
 * - Dynamic app detection: verified installed packages only.
 * - Safe TIF query fallback: hides empty shelves gracefully without exceptions.
 * - Remote customizable: respects user service enablement and player preferences.
 */
public class ContentManager {

    private static final String TAG = "ContentManager";

    // Known target package categories
    public static final String[] LIVE_TV_PACKAGES = {
            "com.tosmart.glide",
            "com.logicyel.novastb",
            "com.datesplus.iptv"
    };

    public static final String[] CINEMA_PACKAGES = {
            "com.marvelactivecode.marvel",
            "com.netflix.mediaclient",
            "com.stremio.one"
    };

    public static final String[] STREAMING_PACKAGES = {
            "io.gh.reisxd.tizentube.fortaleza",
            "com.google.android.youtube.tv",
            "com.google.android.youtube",
            "org.xbmc.kodi",
            "org.jellyfin.androidtv"
    };

    public static final String[] TOOL_PACKAGES = {
            "com.mxtech.videoplayer.pro",
            "com.esaba.downloader",
            "com.tosmart.filemanagerpro",
            "com.excellence.appstore"
    };

    /**
     * Query Android TV Watch Next programs.
     * Returns null if no authentic records exist (ensuring the shelf is completely hidden).
     */
    public static MediaShelf queryWatchNext(Context context) {
        Uri uri = Uri.parse("content://android.media.tv/watch_next_program");
        List<MediaItem> items = new ArrayList<>();
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int colTitle = cursor.getColumnIndex("title");
                int colPackage = cursor.getColumnIndex("package_name");
                int colUri = cursor.getColumnIndex("intent_uri");
                int colPos = cursor.getColumnIndex("last_playback_position_millis");
                int colDur = cursor.getColumnIndex("duration_millis");

                do {
                    String title = colTitle >= 0 ? cursor.getString(colTitle) : null;
                    String pkg = colPackage >= 0 ? cursor.getString(colPackage) : null;
                    String intentUri = colUri >= 0 ? cursor.getString(colUri) : null;
                    long pos = colPos >= 0 ? cursor.getLong(colPos) : 0;
                    long dur = colDur >= 0 ? cursor.getLong(colDur) : 0;

                    if (title != null && !title.trim().isEmpty()) {
                        float progress = (dur > 0 && pos > 0) ? Math.min(1.0f, (float) pos / dur) : -1f;
                        Intent intent = null;
                        if (intentUri != null) {
                            try { intent = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME); }
                            catch (Exception ignored) {}
                        }
                        if (intent == null && pkg != null) {
                            intent = context.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
                            if (intent == null) intent = context.getPackageManager().getLaunchIntentForPackage(pkg);
                        }

                        items.add(new MediaItem.Builder("watch_next_" + items.size(), title)
                                .type(MediaItem.TYPE_MOVIE)
                                .sourcePackage(pkg)
                                .sourceAppName(pkg != null ? pkg : "Media")
                                .launchIntent(intent)
                                .watchProgress(progress)
                                .provenance(MediaItem.PROVENANCE_SYSTEM_TIF)
                                .verifiedAvailable(true)
                                .build());
                    }
                } while (cursor.moveToNext());
            }
        } catch (Throwable t) {
            Log.d(TAG, "TvContract watch_next query unavailable: " + t.getMessage());
        }

        if (items.isEmpty()) return null;
        return new MediaShelf("continue_watching", "CONTINUE WATCHING",
                "Resume your in-progress movies, series and videos", true, items);
    }

    /**
     * Build categorized shelves for the Discover screen.
     * All items correspond to authentic, installed, user-enabled applications on the receiver.
     */
    public static List<MediaShelf> buildDiscoverShelves(
            Context context,
            MainActivity.App satelliteApp,
            Map<String, MainActivity.App> installedApps) {

        List<MediaShelf> shelves = new ArrayList<>();
        Set<String> claimed = new HashSet<>();

        // 1. Live TV & Broadcast
        List<MediaItem> liveItems = new ArrayList<>();
        if (satelliteApp != null && LauncherSettings.isServiceEnabled(context, "com.tosmart.glide")) {
            Intent satIntent = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(satelliteApp.component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

            liveItems.add(new MediaItem.Builder("live_satellite", "Live Satellite TV")
                    .type(MediaItem.TYPE_LIVE_TV)
                    .subtitle("StarNet DVB Hardware Tuner")
                    .synopsis("Watch free-to-air and satellite channels with hardware signal decoding and EPG.")
                    .sourcePackage("com.tosmart.glide")
                    .sourceAppName("StarNet Tuner")
                    .launchIntent(satIntent)
                    .iconDrawable(satelliteApp.art)
                    .backdropAssetPath("sat_backdrop.jpg")
                    .provenance(MediaItem.PROVENANCE_DVB_HARDWARE)
                    .build());
        }

        for (String pkg : LIVE_TV_PACKAGES) {
            if ("com.tosmart.glide".equals(pkg)) continue;
            if (!LauncherSettings.isServiceEnabled(context, pkg)) continue;
            MainActivity.App app = installedApps.get(pkg);
            if (app != null && claimed.add(pkg)) {
                liveItems.add(createAppMediaItem(app, "Live Channels & Broadcast Streams"));
            }
        }

        if (!liveItems.isEmpty()) {
            shelves.add(new MediaShelf("shelf_live", "LIVE TV & BROADCAST",
                    "Satellite broadcast and installed IPTV portal services", false, liveItems));
        }

        // 2. Cinema & Video On Demand
        List<MediaItem> cinemaItems = new ArrayList<>();
        for (String pkg : CINEMA_PACKAGES) {
            if (!LauncherSettings.isServiceEnabled(context, pkg)) continue;
            MainActivity.App app = installedApps.get(pkg);
            if (app != null && claimed.add(pkg)) {
                String sub = "Movies, Series & On-Demand";
                if (pkg.contains("marvel")) sub = "Marvel Active Cinema & VOD";
                if (pkg.contains("netflix")) sub = "Subscription Movies & Originals";
                if (pkg.contains("stremio")) sub = "Video Add-on Entertainment";
                cinemaItems.add(createAppMediaItem(app, sub));
            }
        }
        if (!cinemaItems.isEmpty()) {
            shelves.add(new MediaShelf("shelf_cinema", "CINEMA & VIDEO ON DEMAND",
                    "Dedicated movie, series, and video-on-demand services", false, cinemaItems));
        }

        // 3. Streaming & Media Hubs (Order respects preferred YouTube setting)
        List<MediaItem> streamItems = new ArrayList<>();
        String preferredYt = LauncherSettings.getPreferredYouTubeSetting(context);
        List<String> streamOrder = new ArrayList<>();
        if (LauncherSettings.YT_GOOGLE_TV.equals(preferredYt)) {
            streamOrder.add(LauncherSettings.YT_GOOGLE_TV);
            streamOrder.add(LauncherSettings.YT_SMARTTUBE);
            streamOrder.add(LauncherSettings.YT_STANDARD);
        } else if (LauncherSettings.YT_STANDARD.equals(preferredYt)) {
            streamOrder.add(LauncherSettings.YT_STANDARD);
            streamOrder.add(LauncherSettings.YT_SMARTTUBE);
            streamOrder.add(LauncherSettings.YT_GOOGLE_TV);
        } else {
            // Auto / SmartTube preferred
            streamOrder.add(LauncherSettings.YT_SMARTTUBE);
            streamOrder.add(LauncherSettings.YT_GOOGLE_TV);
            streamOrder.add(LauncherSettings.YT_STANDARD);
        }
        streamOrder.add("org.xbmc.kodi");
        streamOrder.add("org.jellyfin.androidtv");

        for (String pkg : streamOrder) {
            if (!LauncherSettings.isServiceEnabled(context, pkg)) continue;
            MainActivity.App app = installedApps.get(pkg);
            if (app != null && claimed.add(pkg)) {
                String sub = "Streaming Video & Channels";
                if (pkg.contains("tizentube")) sub = "SmartTube 4K Player";
                if (pkg.contains("youtube")) sub = "YouTube TV Client";
                if (pkg.contains("kodi")) sub = "Home Theater & Local Media";
                if (pkg.contains("jellyfin")) sub = "Personal Media Streaming";
                streamItems.add(createAppMediaItem(app, sub));
            }
        }
        if (!streamItems.isEmpty()) {
            shelves.add(new MediaShelf("shelf_streaming", "STREAMING & HOME MEDIA",
                    "Online video platforms and media library centers", false, streamItems));
        }

        // 4. Tools & Utilities
        List<MediaItem> toolItems = new ArrayList<>();
        for (String pkg : TOOL_PACKAGES) {
            if (!LauncherSettings.isServiceEnabled(context, pkg)) continue;
            MainActivity.App app = installedApps.get(pkg);
            if (app != null && claimed.add(pkg)) {
                String sub = "Receiver Utility";
                if (pkg.contains("mxtech")) sub = "Hardware-Accelerated Video Player";
                if (pkg.contains("downloader")) sub = "Direct Web Download Manager";
                if (pkg.contains("filemanagerpro")) sub = "Storage & USB File Manager";
                if (pkg.contains("appstore")) sub = "Application Store";
                toolItems.add(createAppMediaItem(app, sub));
            }
        }
        if (!toolItems.isEmpty()) {
            shelves.add(new MediaShelf("shelf_tools", "RECEIVER TOOLS & UTILITIES",
                    "File management, app store, and media playback tools", false, toolItems));
        }

        return shelves;
    }

    private static MediaItem createAppMediaItem(MainActivity.App app, String subtitle) {
        Intent launch = new Intent(Intent.ACTION_MAIN)
                .addCategory(app.category != null ? app.category : Intent.CATEGORY_LAUNCHER)
                .setComponent(app.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        return new MediaItem.Builder("app_" + app.pkg, app.label != null ? app.label : app.pkg)
                .type(MediaItem.TYPE_APP)
                .subtitle(subtitle)
                .synopsis("Launch " + app.label + " on your StarNet receiver.")
                .sourcePackage(app.pkg)
                .sourceAppName(app.label)
                .launchIntent(launch)
                .iconDrawable(app.art)
                .provenance(MediaItem.PROVENANCE_APP_INTEGRATION)
                .build();
    }
}
