package com.mihir.tvhome.content;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.mihir.tvhome.MainActivity;
import com.mihir.tvhome.R;
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
                                .sourceAppName(pkg != null ? pkg : context.getString(R.string.media))
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
        return new MediaShelf("continue_watching", context.getString(R.string.continue_watching),
                context.getString(R.string.continue_hint), true, items);
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

            liveItems.add(new MediaItem.Builder("live_satellite", context.getString(R.string.live_satellite))
                    .type(MediaItem.TYPE_LIVE_TV)
                    .subtitle(context.getString(R.string.tuner_short))
                    .synopsis(context.getString(R.string.tuner_description))
                    .sourcePackage("com.tosmart.glide")
                    .sourceAppName(context.getString(R.string.tuner))
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
                liveItems.add(createAppMediaItem(context, app, context.getString(R.string.live_channels)));
            }
        }

        if (!liveItems.isEmpty()) {
            shelves.add(new MediaShelf("shelf_live", context.getString(R.string.live_iptv),
                    "Satellite broadcast and installed IPTV portal services", false, liveItems));
        }

        // 2. Cinema & Video On Demand
        List<MediaItem> cinemaItems = new ArrayList<>();
        for (String pkg : CINEMA_PACKAGES) {
            if (!LauncherSettings.isServiceEnabled(context, pkg)) continue;
            MainActivity.App app = installedApps.get(pkg);
            if (app != null && claimed.add(pkg)) {
                String sub = context.getString(R.string.movies_series);
                if (pkg.contains("marvel")) sub = context.getString(R.string.marvel_cinema);
                if (pkg.contains("netflix")) sub = context.getString(R.string.netflix_sub);
                if (pkg.contains("stremio")) sub = context.getString(R.string.stremio_sub);
                cinemaItems.add(createAppMediaItem(context, app, sub));
            }
        }
        if (!cinemaItems.isEmpty()) {
            shelves.add(new MediaShelf("shelf_cinema", context.getString(R.string.cinema_on_demand),
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
                String sub = context.getString(R.string.stream_video);
                if (pkg.contains("tizentube")) sub = context.getString(R.string.smarttube_player);
                if (pkg.contains("youtube")) sub = context.getString(R.string.youtube_client);
                if (pkg.contains("kodi")) sub = context.getString(R.string.kodi_media);
                if (pkg.contains("jellyfin")) sub = context.getString(R.string.jellyfin_media);
                streamItems.add(createAppMediaItem(context, app, sub));
            }
        }
        if (!streamItems.isEmpty()) {
            shelves.add(new MediaShelf("shelf_streaming", context.getString(R.string.media_video),
                    "Online video platforms and media library centers", false, streamItems));
        }

        // 4. Tools & Utilities
        List<MediaItem> toolItems = new ArrayList<>();
        for (String pkg : TOOL_PACKAGES) {
            if (!LauncherSettings.isServiceEnabled(context, pkg)) continue;
            MainActivity.App app = installedApps.get(pkg);
            if (app != null && claimed.add(pkg)) {
                String sub = context.getString(R.string.receiver_utility);
                if (pkg.contains("mxtech")) sub = context.getString(R.string.mx_player);
                if (pkg.contains("downloader")) sub = context.getString(R.string.download_manager);
                if (pkg.contains("filemanagerpro")) sub = context.getString(R.string.file_manager);
                if (pkg.contains("appstore")) sub = context.getString(R.string.app_store);
                toolItems.add(createAppMediaItem(context, app, sub));
            }
        }
        if (!toolItems.isEmpty()) {
            shelves.add(new MediaShelf("shelf_tools", context.getString(R.string.receiver_tools),
                    "File management, app store, and media playback tools", false, toolItems));
        }

        return shelves;
    }

    private static MediaItem createAppMediaItem(Context context, MainActivity.App app, String subtitle) {
        Intent launch = new Intent(Intent.ACTION_MAIN)
                .addCategory(app.category != null ? app.category : Intent.CATEGORY_LAUNCHER)
                .setComponent(app.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        return new MediaItem.Builder("app_" + app.pkg, app.label != null ? app.label : app.pkg)
                .type(MediaItem.TYPE_APP)
                .subtitle(subtitle)
                .synopsis(context.getString(R.string.launch_prefix) + app.label + context.getString(R.string.receiver_suffix))
                .sourcePackage(app.pkg)
                .sourceAppName(app.label)
                .launchIntent(launch)
                .iconDrawable(app.art)
                .provenance(MediaItem.PROVENANCE_APP_INTEGRATION)
                .build();
    }
}
