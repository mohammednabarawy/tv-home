package com.mihir.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Apple TV style home screen: clock top-left, pills top-right, the focused dock app's name over an
 * ambient glow, a dock of pinned apps, and every other app in a grid that scrolls up on Down.
 * Plain framework views only (no AndroidX), no network, no background work.
 */
public class MainActivity extends Activity {

    // Dock, hidden apps, Live TV and grid width come from res/values/config.xml.
    private String[] dockApps;
    private final Set<String> hidden = new HashSet<>();
    private String satelliteTvPkg, satelliteTvActivity, inputPickerPkg;
    private int cols;

    static final class App {
        String pkg, label, category;
        ComponentName component;
        boolean isSatellite;
        boolean isPlus;
        Drawable art;
        boolean isBanner;
        int color;
    }

    private final Map<String, Integer> colorCache = new HashMap<>();
    private final List<View> dockCards = new ArrayList<>();
    private final List<View> gridCards = new ArrayList<>();
    private final List<View> pills = new ArrayList<>();

    private HomeScrollView scroll;
    private LinearLayout content, dockRow;
    private FrameLayout hero;
    private LinearLayout grid;
    private LinearLayout channelContainer;
    private final List<List<View>> channelRows = new ArrayList<>();
    private View glow;
    private ImageView wallBack, wallFront;
    private View gridShade;
    private String[] wallpapers = new String[0];
    private int wallIndex = -1;
    private long wallChangedAt;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable rotateWallpaper = this::nextWallpaper;
    static final long WALLPAPER_INTERVAL_MS = 10 * 60 * 1000;
    private TextView greeting, clock, ampm, date, title, wifiPill, appsLabel;
    private int glowColor = 0xFF3A3A3A;
    private ValueAnimator glowAnim;
    private int lastFocusedId = View.NO_ID;
    private boolean firstShow = true;
    private int packageSeq;
    private PopupWindow inputsPopup;

    private View heroCard;
    private TextView heroBadge, heroTitle, heroSubtitle, heroActionBtn, heroFrameTag;
    private ImageView heroPreviewImage, heroArtwork;
    private View showcaseView, favoritesView, browseAppsButton;
    private boolean catalogMode = false;
    private int homeHeroHeight;
    private FrameLayout heroPreviewBox;
    private App currentHeroApp;

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateClock(); updateWifi(); }
    };
    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { buildApps(); }
    };

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dockApps = getResources().getStringArray(R.array.dock_apps);
        hidden.addAll(Arrays.asList(getResources().getStringArray(R.array.hidden_apps)));
        hidden.add(getPackageName());
        satelliteTvPkg = getString(R.string.satellite_tv_package);
        satelliteTvActivity = getString(R.string.satellite_tv_activity);
        inputPickerPkg = getString(R.string.input_picker_package);
        cols = getResources().getInteger(R.integer.columns);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        setContentView(buildUi());
        buildApps();

        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        registerReceiver(packages, pf);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter tf = new IntentFilter(Intent.ACTION_TIME_TICK);
        tf.addAction(Intent.ACTION_TIME_CHANGED);
        tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        tf.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(tick, tf);
        updateClock();
        updateWifi();
        View last = lastFocusedId == View.NO_ID ? null : findViewById(lastFocusedId);
        if (last == null && !dockCards.isEmpty()) last = dockCards.get(0);
        if (last != null) last.requestFocus();
        // Catch installs/removals that happened while another app was in front.
        if (getPackageManager().getChangedPackages(packageSeq) != null) buildApps();
        if (firstShow) { firstShow = false; playEntrance(); }
        // New photo on return if the current one has been up for the full interval.
        if (wallIndex < 0 || SystemClock.elapsedRealtime() - wallChangedAt >= WALLPAPER_INTERVAL_MS) nextWallpaper();
        else scheduleWallpaper(WALLPAPER_INTERVAL_MS - (SystemClock.elapsedRealtime() - wallChangedAt));
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(rotateWallpaper);
        unregisterReceiver(tick);
        if (inputsPopup != null) inputsPopup.dismiss();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packages);
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already home: jump back to the first dock app.
        if (inputsPopup != null) inputsPopup.dismiss();
        showAppCatalog(false);
    }

    @Override
    public void onBackPressed() {
        if (catalogMode) {
            showAppCatalog(false);
        } else if (scroll.getScrollY() > 0 && !dockCards.isEmpty()) {
            dockCards.get(0).requestFocus();
        }
    }

    // ---------------------------------------------------------------- layout

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        // Wallpaper: two stacked layers so a new photo can crossfade in over the old one.
        wallBack = wallpaperLayer();
        wallFront = wallpaperLayer();
        root.addView(wallBack, new FrameLayout.LayoutParams(-1, -1));
        root.addView(wallFront, new FrameLayout.LayoutParams(-1, -1));
        // Scrim: darker at the top (clock, pills) and bottom (dock) so text stays readable.
        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x56000000, 0x14000000, 0x33000516, 0xD9000714, 0xF9000714}));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        gridShade = new View(this);
        gridShade.setBackgroundColor(0xB3000000);
        gridShade.setAlpha(0f);
        root.addView(gridShade, new FrameLayout.LayoutParams(-1, -1));
        try {
            wallpapers = getAssets().list("wallpapers");
        } catch (java.io.IOException ignored) { }

        glow = new View(this);
        root.addView(glow, new FrameLayout.LayoutParams(-1, -1));
        setGlow(glowColor);

        scroll = new HomeScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setFillViewport(true);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));

        // Shorter cinematic stage: the first horizontal channel is visible without a full-page scroll.
        hero = new FrameLayout(this);
        hero.setClipChildren(false);
        int screenH = getResources().getDisplayMetrics().heightPixels;
        homeHeroHeight = Math.max(dp(385), Math.round(screenH * 0.68f));
        content.addView(hero, new LinearLayout.LayoutParams(-1, homeHeroHeight));

        hero.addView(buildHeader(), lp(-1, -2, Gravity.TOP, dp(48), dp(36), dp(48), 0));

        // Full-bleed photographic artwork (not a bordered promotional placeholder).
        showcaseView = buildHeroCard();
        hero.addView(showcaseView, lp(-1, -1, Gravity.TOP, 0, dp(90), 0, dp(136)));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setClipChildren(false);
        bottom.setClipToPadding(false);
        favoritesView = bottom;
        hero.addView(bottom, lp(-1, -2, Gravity.BOTTOM, dp(48), 0, dp(48), dp(15)));

        title = text(12, "sans-serif-medium", 0xD9FFFFFF);
        title.setLetterSpacing(0.2f);
        title.setShadowLayer(dp(10), 0, 0, 0x66000000);
        title.setSingleLine(true);
        bottom.addView(title, new LinearLayout.LayoutParams(-2, -2));
        ((LinearLayout.LayoutParams) title.getLayoutParams()).setMargins(dp(8), 0, 0, dp(10));

        dockRow = new LinearLayout(this);
        dockRow.setOrientation(LinearLayout.HORIZONTAL);
        dockRow.setGravity(Gravity.CENTER);
        dockRow.setClipChildren(false);
        dockRow.setClipToPadding(false);
        dockRow.setPadding(dp(2), dp(5), dp(2), dp(5));
        GradientDrawable band = new GradientDrawable();
        band.setCornerRadius(dp(12));
        band.setColor(0x10000000);
        dockRow.setBackground(band);
        bottom.addView(dockRow, new LinearLayout.LayoutParams(-1, -2));

        channelContainer = new LinearLayout(this);
        channelContainer.setOrientation(LinearLayout.VERTICAL);
        channelContainer.setClipChildren(false);
        channelContainer.setClipToPadding(false);
        channelContainer.setPadding(dp(48), dp(3), dp(48), dp(30));
        content.addView(channelContainer, new LinearLayout.LayoutParams(-1, -2));

        appsLabel = text(22, "sans-serif-medium", 0xFFE5E9F5);
        appsLabel.setText("All Apps");
        appsLabel.setPadding(0, dp(15), 0, dp(5));
        content.addView(appsLabel, llp(-2, -2, dp(60), dp(8), 0, dp(18)));
        appsLabel.setVisibility(View.GONE);

        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        content.addView(grid, llp(-1, -2, dp(60), 0, dp(60), dp(100)));
        grid.setVisibility(View.GONE);
        return root;
    }

    /** Clean top navigation inspired by Android TV, no permanent pill outlines. */
    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClipChildren(false);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabs.setClipChildren(false);
        TextView homeTab = navTab("Home");
        homeTab.setOnClickListener(v -> showAppCatalog(false));
        TextView liveTab = navTab("Live TV");
        liveTab.setOnClickListener(v -> openSatelliteTv());
        TextView appsTab = navTab("Apps");
        appsTab.setOnClickListener(v -> showAppCatalog(true));
        TextView glideTab = navTab("Original Glide");
        glideTab.setOnClickListener(v -> openOriginalHome());
        TextView inputTab = navTab("Inputs");
        inputTab.setOnClickListener(v -> showInputs(v));
        for (TextView tab : new TextView[]{homeTab, liveTab, appsTab, glideTab, inputTab}) {
            tabs.addView(tab, llp(-2, dp(37), 0, 0, dp(18), 0));
            pills.add(tab);
        }
        header.addView(tabs, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout status = new LinearLayout(this);
        status.setGravity(Gravity.CENTER_VERTICAL);
        wifiPill = navTab("Wi-Fi");
        wifiPill.setOnClickListener(v -> launch(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        status.addView(wifiPill);
        pills.add(wifiPill);

        LinearLayout clockStack = new LinearLayout(this);
        clockStack.setOrientation(LinearLayout.VERTICAL);
        clockStack.setGravity(Gravity.CENTER);
        greeting = text(9, "sans-serif", 0x99FFFFFF);
        clock = text(24, "sans-serif-medium", Color.WHITE);
        ampm = text(10, "sans-serif", 0xB3FFFFFF);
        date = text(9, "sans-serif", 0xB3FFFFFF);
        LinearLayout timeLine = new LinearLayout(this);
        timeLine.setGravity(Gravity.CENTER);
        timeLine.addView(clock);
        timeLine.addView(ampm, llp(-2, -2, dp(4), 0, 0, 0));
        clockStack.addView(timeLine);
        clockStack.addView(date);
        status.addView(clockStack, llp(-2, -2, dp(12), 0, dp(8), 0));
        TextView settingsTab = navTab("⚙");
        settingsTab.setOnClickListener(v -> launch(new Intent(Settings.ACTION_SETTINGS)));
        status.addView(settingsTab);
        pills.add(settingsTab);
        header.addView(status);
        return header;
    }

    private TextView navTab(String label) {
        TextView tab = text(13, "sans-serif-medium", 0xC8FFFFFF);
        tab.setText(label);
        tab.setGravity(Gravity.CENTER);
        tab.setPadding(dp(10), 0, dp(10), 0);
        tab.setId(View.generateViewId());
        tab.setFocusable(true);
        tab.setClickable(true);
        tab.setBackground(new GradientDrawable());
        tab.setOnFocusChangeListener((v, hasFocus) -> {
            tab.setTextColor(hasFocus ? Color.WHITE : 0xC8FFFFFF);
            tab.setScaleX(hasFocus ? 1.06f : 1f);
            tab.setScaleY(hasFocus ? 1.06f : 1f);
            tab.setPaintFlags(hasFocus ?
                    (tab.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG) :
                    (tab.getPaintFlags() & ~android.graphics.Paint.UNDERLINE_TEXT_FLAG));
            if (hasFocus) lastFocusedId = v.getId();
        });
        return tab;
    }

    /** Switch between elegant home and dedicated complete app catalog. */
    private void showAppCatalog(boolean showCatalog) {
        catalogMode = showCatalog;
        if (hero != null) {
            ViewGroup.LayoutParams params = hero.getLayoutParams();
            params.height = catalogMode ? dp(100) : homeHeroHeight;
            hero.setLayoutParams(params);
        }
        if (showcaseView != null) showcaseView.setVisibility(catalogMode ? View.GONE : View.VISIBLE);
        if (favoritesView != null) favoritesView.setVisibility(catalogMode ? View.GONE : View.VISIBLE);
        if (channelContainer != null) channelContainer.setVisibility(catalogMode ? View.GONE : View.VISIBLE);
        if (appsLabel != null) appsLabel.setVisibility(catalogMode ? View.VISIBLE : View.GONE);
        if (grid != null) grid.setVisibility(catalogMode ? View.VISIBLE : View.GONE);
        if (scroll != null) scroll.scrollTo(0, 0);
        wireFocus();
        if (catalogMode) {
            if (!gridCards.isEmpty()) gridCards.get(0).requestFocus();
        } else if (!dockCards.isEmpty()) {
            dockCards.get(0).requestFocus();
        } else if (heroActionBtn != null) {
            heroActionBtn.requestFocus();
        }
    }

    /** Keep an explicit entry point for the complete set of applications. */
    private void scrollToAllApps() { showAppCatalog(true); }

    private void openOriginalHome() {
        Intent glide = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (!launch(glide)) {
            Toast.makeText(this, "Use your remote HOME to return to Glide",
                    Toast.LENGTH_LONG).show();
        }
    }

    private TextView pill(String label, String glyph) {
        TextView p = text(11, "sans-serif-medium", 0xE6FFFFFF);
        p.setText(glyph + "  " + label);
        p.setGravity(Gravity.CENTER);
        p.setPadding(dp(14), 0, dp(14), 0);
        p.setFocusable(true);
        p.setId(View.generateViewId());
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(15));
        bg.setColor(0x66000000);
        bg.setStroke(dp(1), 0x33FFFFFF);
        p.setBackground(bg);
        p.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? 0xF2FFFFFF : 0x66000000);
            ((TextView) v).setTextColor(has ? 0xFF111111 : 0xE6FFFFFF);
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f)
                    .setInterpolator(new OvershootInterpolator(2f)).setDuration(220).start();
            if (has) { lastFocusedId = v.getId(); scroll.smoothScrollTo(0, 0); fadeGlow(1f); }
        });
        return p;
    }

    // ---------------------------------------------------------------- hero stage

    /** Immersive photographic hero. Uses local licensed wallpaper, not a fake live preview. */
    private View buildHeroCard() {
        FrameLayout panel = new FrameLayout(this);
        panel.setBackgroundColor(0xFF091421);
        panel.setClipChildren(false);

        heroArtwork = new ImageView(this);
        heroArtwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        panel.addView(heroArtwork, new FrameLayout.LayoutParams(-1, -1));

        View vignette = new View(this);
        vignette.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFB050E1B, 0xED06111E, 0xA0081424, 0x1A051020}));
        panel.addView(vignette, new FrameLayout.LayoutParams(-1, -1));

        View bottomFade = new View(this);
        bottomFade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00030A14, 0x12030A14, 0xEF030A14}));
        panel.addView(bottomFade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setClipChildren(false);
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int copyWidth = Math.min(dp(530), Math.round(screenW * .55f));
        FrameLayout.LayoutParams copyLp = new FrameLayout.LayoutParams(
                copyWidth, -1, Gravity.START | Gravity.CENTER_VERTICAL);
        copyLp.setMargins(dp(56), 0, 0, 0);

        heroBadge = text(12, "sans-serif-medium", 0xFF65CAFF);
        heroBadge.setLetterSpacing(0.1f);
        heroBadge.setSingleLine(true);
        copy.addView(heroBadge, llp(-1, -2, 0, 0, 0, dp(10)));

        heroTitle = text(37, "sans-serif", Color.WHITE);
        heroTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        heroTitle.setShadowLayer(dp(10), 0, dp(2), 0xCC000000);
        heroTitle.setSingleLine(true);
        heroTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(heroTitle, llp(-1, -2, 0, 0, 0, dp(8)));

        heroSubtitle = text(15, "sans-serif", 0xE6FFFFFF);
        heroSubtitle.setMaxLines(2);
        heroSubtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heroSubtitle.setLineSpacing(dp(3), 1f);
        copy.addView(heroSubtitle, llp(-1, -2, 0, 0, dp(6), dp(18)));

        heroActionBtn = text(15, "sans-serif-medium", 0xFF121923);
        heroActionBtn.setPadding(dp(22), 0, dp(22), 0);
        heroActionBtn.setGravity(Gravity.CENTER);
        heroActionBtn.setFocusable(true);
        heroActionBtn.setId(View.generateViewId());
        GradientDrawable cta = new GradientDrawable();
        cta.setColor(0xFFF6F8FA);
        cta.setCornerRadius(dp(22));
        heroActionBtn.setBackground(cta);
        heroActionBtn.setOnClickListener(v -> launchApp(currentHeroApp));
        heroActionBtn.setOnFocusChangeListener((v, focused) -> {
            cta.setColor(focused ? 0xFFFFFFFF : 0xE7F5F8FA);
            v.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f)
                    .setDuration(140).start();
            if (focused) {
                lastFocusedId = v.getId();
                scroll.smoothScrollTo(0, 0);
            }
        });
        copy.addView(heroActionBtn, new LinearLayout.LayoutParams(-2, dp(42)));
        panel.addView(copy, copyLp);

        // Small *app identity* tile, not a fake TV/film preview screen.
        heroPreviewBox = new FrameLayout(this);
        GradientDrawable previewBg = new GradientDrawable();
        previewBg.setColor(0xCC09121E);
        previewBg.setCornerRadius(dp(14));
        previewBg.setStroke(dp(1), 0x55FFFFFF);
        heroPreviewBox.setBackground(previewBg);
        heroPreviewImage = new ImageView(this);
        heroPreviewImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        heroPreviewImage.setPadding(dp(7), dp(7), dp(7), dp(7));
        heroPreviewBox.addView(heroPreviewImage, new FrameLayout.LayoutParams(-1, -1));
        heroFrameTag = text(10, "sans-serif-medium", Color.WHITE);
        heroFrameTag.setText("");
        heroFrameTag.setGravity(Gravity.CENTER);
        panel.addView(heroPreviewBox, lp(dp(78), dp(62), Gravity.BOTTOM | Gravity.RIGHT, 0, 0, dp(55), dp(30)));
        panel.addView(heroFrameTag, lp(dp(88), dp(20), Gravity.BOTTOM | Gravity.RIGHT, 0, 0, dp(50), dp(7)));

        heroCard = panel;
        return panel;
    }

    private void updateHero(App app, boolean animate) {
        if (app == null || heroCard == null) return;
        currentHeroApp = app;
        heroBadge.setText(getAppBadge(app));
        heroTitle.setText(app.label == null ? "" : app.label);
        heroSubtitle.setText(getAppSubtitle(app));
        heroActionBtn.setText(getAppActionText(app));
        heroPreviewImage.setImageDrawable(app.art);
        heroFrameTag.setText(getAppFrameTag(app));
    }

    private String getAppBadge(App app) {
        if (app.isSatellite) return "● LIVE SATELLITE TV";
        if (app.isPlus) return "＋ QUICK CUSTOMIZE";
        if (app.pkg != null) {
            if (app.pkg.contains("youtube") || app.pkg.contains("tizentube")) return "▶ STREAMING & VIDEO";
            if (app.pkg.contains("novastb") || app.pkg.contains("datesplus")) return "⚡ NOVA IPTV PORTAL";
            if (app.pkg.contains("marvel")) return "★ MARVEL ACTIVE";
            if (app.pkg.contains("stremio") || app.pkg.contains("kodi")) return "🎬 MOVIES & SERIES";
            if (app.pkg.contains("vending")) return "🛒 GOOGLE PLAY STORE";
        }
        return "● INSTALLED APPLICATION";
    }

    private String getAppSubtitle(App app) {
        if (app.isSatellite) return "Watch satellite channels through your StarNet DVB receiver. Original tuner and channel settings are preserved.";
        if (app.isPlus) return "Pin another favorite application to your dock for instant 1-click access";
        if (app.pkg != null) {
            if (app.pkg.contains("youtube") || app.pkg.contains("tizentube")) return "Watch trending videos, subscribed channels, and music playlists";
            if (app.pkg.contains("novastb")) return "Nova STB IPTV live channels, sports broadcasts, and series";
            if (app.pkg.contains("datesplus")) return "Dates+ live streaming and international television channels";
            if (app.pkg.contains("marvel")) return "Marvel Active cinema, premium sports, and on-demand entertainment";
            if (app.pkg.contains("stremio")) return "Organize and stream movies, TV shows, and video add-ons";
            if (app.pkg.contains("kodi")) return "Open-source home theater media hub and local playback";
            if (app.pkg.contains("vending")) return "Discover and install new Android TV apps and games";
        }
        return "Launch " + (app.label != null ? app.label : "application") + " on your StarNet receiver";
    }

    private String getAppActionText(App app) {
        if (app.isSatellite) return "▶  Watch Live TV";
        if (app.isPlus) return "＋  Add Favorite";
        return "▶  Open App";
    }

    private String getAppFrameTag(App app) {
        if (app.isSatellite) return "🔴 LIVE";
        if (app.isPlus) return "DOCK";
        if (app.pkg != null) {
            if (app.pkg.contains("youtube") || app.pkg.contains("tizentube")) return "4K VIDEO";
            if (app.pkg.contains("novastb") || app.pkg.contains("datesplus")) return "IPTV HD";
            if (app.pkg.contains("marvel")) return "CINEMA";
            if (app.pkg.contains("stremio") || app.pkg.contains("kodi")) return "MEDIA";
            if (app.pkg.contains("vending")) return "STORE";
        }
        return "APP";
    }

    private void launchApp(App app) {
        if (app == null) return;
        if (app.isSatellite) {
            openSatelliteTv();
        } else if (app.isPlus) {
            showAppPicker();
        } else {
            launch(new Intent(Intent.ACTION_MAIN)
                    .addCategory(app.category)
                    .setComponent(app.component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        }
    }

    // ---------------------------------------------------------------- apps

    private void buildApps() {
        PackageManager pm = getPackageManager();
        android.content.pm.ChangedPackages changed = pm.getChangedPackages(packageSeq);
        if (changed != null) packageSeq = changed.getSequenceNumber();
        // StarNet hybrid receivers expose some applications only as regular Android apps.
        Map<String, App> byPkg = new HashMap<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                ActivityInfo ai = ri.activityInfo;
                if (hidden.contains(ai.packageName) || byPkg.containsKey(ai.packageName)) continue;
                App a = new App();
                a.pkg = ai.packageName;
                a.category = category;
                a.label = String.valueOf(ri.loadLabel(pm));
                a.component = new ComponentName(ai.packageName, ai.name);
                Drawable banner = ai.loadBanner(pm);
                if (banner == null) banner = ai.applicationInfo.loadBanner(pm);
                a.isBanner = banner != null;
                a.art = banner != null ? banner : ri.loadIcon(pm);
                a.color = sampleColor(a.pkg, a.art);
                byPkg.put(a.pkg, a);
            }
        }

        // Preserve the installed-app inventory for the Apps tab; home rows filter pinned favorites.
        Map<String, App> allAppsForChannels = new HashMap<>(byPkg);
        List<App> dock = new ArrayList<>();
        // Satellite playback is an exported Glide activity-alias, not a separate Android app.
        // Keep Glide itself in the regular app list as an additional safe escape hatch.
        App satellite = buildSatelliteEntry(pm);
        if (satellite != null) dock.add(satellite);
        List<String> currentDock = getDockList();
        boolean youtubeAdded = false;
        for (String p : currentDock) {
            if (p.equals("com.google.android.youtube.tv") || p.equals("io.gh.reisxd.tizentube.fortaleza")) {
                if (youtubeAdded) continue;
                if (byPkg.containsKey(p)) {
                    dock.add(byPkg.remove(p));
                    youtubeAdded = true;
                }
            } else if (byPkg.containsKey(p)) {
                dock.add(byPkg.remove(p));
            }
        }
        dock.add(buildPlusEntry());
        List<App> rest = new ArrayList<>(allAppsForChannels.values());
        Collections.sort(rest, (x, y) -> x.label.compareToIgnoreCase(y.label));
        // Ensure the dock works even if upstream Android TV app IDs aren't installed.
        if (dock.size() <= 1 && !rest.isEmpty()) {
            int count = Math.min(5, rest.size());
            dock.addAll(new ArrayList<>(rest.subList(0, count)));
        }

        dockRow.removeAllViews();
        grid.removeAllViews();
        dockCards.clear();
        gridCards.clear();

        for (int i = 0; i < dock.size(); i++) {
            View tile = tile(dock.get(i), true);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
            p.setMargins(i == 0 ? 0 : dp(6), 0, i == dock.size() - 1 ? 0 : dp(6), 0);
            dockRow.addView(tile, p);
        }
        // If the firmware exposes only a couple of favorites, do not stretch them
        // to half-screen giant cards: reserve at least five equal-width slots.
        for (int i = dock.size(); i < 5; i++) {
            dockRow.addView(new View(this), new LinearLayout.LayoutParams(0, dp(1), 1f));
        }

        LinearLayout row = null;
        for (int i = 0; i < rest.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                grid.addView(row, llp(-1, -2, 0, 0, 0, dp(26)));
            }
            View tile = tile(rest.get(i), false);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
            p.setMargins(i % cols == 0 ? 0 : dp(10), 0, i % cols == cols - 1 ? 0 : dp(10), 0);
            row.addView(tile, p);
        }
        // Pad the last row so tiles keep their width.
        if (row != null) for (int i = rest.size() % cols; i != 0 && i < cols; i++) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, 1, 1f);
            p.setMargins(dp(10), 0, i == cols - 1 ? 0 : dp(10), 0);
            row.addView(new View(this), p);
        }
        Set<String> visibleFavorites = new HashSet<>();
        for (App featured : dock) {
            if (featured.pkg != null && !featured.isPlus) visibleFavorites.add(featured.pkg);
        }
        buildChannelRows(allAppsForChannels, satellite, visibleFavorites);
        showAppCatalogVisibilityOnly();
        wireFocus();

        if (!dock.isEmpty()) {
            title.setText("FAVORITE APPS");
            setGlow(dock.get(0).color);
            updateHero(dock.get(0), false);
        }
        View last = lastFocusedId == View.NO_ID ? null : findViewById(lastFocusedId);
        if (last != null) last.requestFocus();
        else if (!dockCards.isEmpty()) dockCards.get(0).requestFocus();
    }

    /**
     * Offline-first channels: each item is an installed app or the observed DVB activity.
     * Deliberately never invents shows, movie posters, recommendations, or watch progress.
     */
    /**
     * Only show installed apps not already pinned to Favorites.
     * The Home screen never repeats the same app in several rows.
     */
    private void buildChannelRows(Map<String, App> apps, App satellite, Set<String> favorites) {
        channelRows.clear();
        browseAppsButton = null;
        channelContainer.removeAllViews();

        addChannelRow("MORE LIVE TV & IPTV", new String[]{
                "com.logicyel.novastb", "com.datesplus.iptv",
                "com.marvelactivecode.marvel"
        }, apps, favorites);
        addChannelRow("STREAMING & MEDIA", new String[]{
                "io.gh.reisxd.tizentube.fortaleza", "com.google.android.youtube.tv",
                "com.google.android.youtube", "org.xbmc.kodi",
                "com.stremio.one", "org.jellyfin.androidtv", "com.netflix.mediaclient"
        }, apps, favorites);
        addChannelRow("TOOLS & UTILITIES", new String[]{
                "com.tosmart.filemanagerpro", "com.excellence.appstore",
                "com.android.vending", "com.android.chrome",
                "com.tosmart.glide"
        }, apps, favorites);

        TextView browse = text(13, "sans-serif-medium", 0xFFBBDFFF);
        browse.setText("Browse all installed apps   →");
        browse.setPadding(dp(16), dp(11), dp(16), dp(11));
        browse.setFocusable(true);
        browse.setClickable(true);
        browse.setId(View.generateViewId());
        browse.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x262A4260);
        bg.setCornerRadius(dp(22));
        browse.setBackground(bg);
        browse.setOnClickListener(v -> showAppCatalog(true));
        browse.setOnFocusChangeListener((v, focused) -> {
            bg.setColor(focused ? 0xFF375B86 : 0x262A4260);
            if (focused) {
                lastFocusedId = v.getId();
                int y = channelContainer.getTop() + v.getTop() - dp(150);
                scroll.smoothScrollTo(0, Math.max(0, y));
            }
        });
        browseAppsButton = browse;
        channelContainer.addView(browse, llp(-2, -2, 0, dp(2), 0, dp(14)));
    }

    private void addChannelRow(String heading, String[] packages,
                               Map<String, App> apps, Set<String> favorites) {
        List<App> visibleApps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String pkg : packages) {
            App app = apps.get(pkg);
            if (app != null && !favorites.contains(app.pkg) && seen.add(app.pkg)) {
                visibleApps.add(app);
            }
        }
        if (visibleApps.isEmpty()) return;

        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setClipChildren(false);
        section.setClipToPadding(false);
        TextView label = text(14, "sans-serif-medium", 0xFFE8E8F5);
        label.setText(heading);
        section.addView(label, llp(-1, -2, 0, dp(4), 0, dp(9)));

        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        horizontal.setOverScrollMode(View.OVER_SCROLL_NEVER);
        horizontal.setClipChildren(false);
        horizontal.setClipToPadding(false);
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.setClipChildren(false);
        horizontal.addView(strip, new HorizontalScrollView.LayoutParams(-2, -2));

        List<View> focusRow = new ArrayList<>();
        for (App app : visibleApps) {
            View card = channelTile(app, horizontal, section);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(156), dp(115));
            cp.setMargins(dp(4), dp(4), dp(12), dp(6));
            strip.addView(card, cp);
            focusRow.add(card);
        }
        section.addView(horizontal, new LinearLayout.LayoutParams(-1, -2));
        channelContainer.addView(section, llp(-1, -2, 0, 0, 0, dp(16)));
        channelRows.add(focusRow);
    }

    private View channelTile(App app, HorizontalScrollView horizontal, View section) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setFocusable(true);
        card.setClickable(true);
        card.setId(View.generateViewId());
        card.setPadding(dp(2), dp(2), dp(2), dp(2));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x9910192B);
        bg.setCornerRadius(dp(11));
        bg.setStroke(dp(1), 0x23FFFFFF);
        card.setBackground(bg);

        ImageView image = new ImageView(this);
        image.setImageDrawable(app.art);
        image.setScaleType(app.isBanner ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        image.setPadding(app.isBanner ? 0 : dp(13), app.isBanner ? 0 : dp(10),
                app.isBanner ? 0 : dp(13), app.isBanner ? 0 : dp(10));
        card.addView(image, new LinearLayout.LayoutParams(-1, dp(84)));
        TextView name = text(11, "sans-serif-medium", Color.WHITE);
        name.setText(app.label);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER);
        card.addView(name, new LinearLayout.LayoutParams(-1, dp(27)));

        card.setOnClickListener(v -> launchApp(app));
        card.setOnFocusChangeListener((v, focused) -> {
            bg.setStroke(dp(focused ? 2 : 1), focused ? 0xFFFFFFFF : 0x23FFFFFF);
            v.animate().scaleX(focused ? 1.045f : 1f).scaleY(focused ? 1.045f : 1f)
                    .setDuration(155).start();
            if (focused) {
                lastFocusedId = v.getId();
                horizontal.smoothScrollTo(Math.max(0, v.getLeft() - dp(44)), 0);
                int y = channelContainer.getTop() + section.getTop() - dp(155);
                scroll.smoothScrollTo(0, Math.max(0, y));
            }
        });
        return card;
    }

    /** Keep view visibility in sync after apps are rescanned. */
    private void showAppCatalogVisibilityOnly() {
        if (channelContainer != null) channelContainer.setVisibility(catalogMode ? View.GONE : View.VISIBLE);
        if (grid != null) grid.setVisibility(catalogMode ? View.VISIBLE : View.GONE);
        if (appsLabel != null) appsLabel.setVisibility(catalogMode ? View.VISIBLE : View.GONE);
    }

    /** A StarNet satellite entry using the owner's observed Glide DVB activity alias. */
    private App buildSatelliteEntry(PackageManager pm) {
        if (satelliteTvPkg.isEmpty() || satelliteTvActivity.isEmpty()) return null;
        ComponentName component = new ComponentName(satelliteTvPkg, satelliteTvActivity);
        try {
            ActivityInfo ai = pm.getActivityInfo(component, 0);
            if (!ai.enabled || !ai.applicationInfo.enabled) return null;
            App app = new App();
            app.pkg = satelliteTvPkg;
            app.label = "Live Satellite TV";
            app.category = Intent.CATEGORY_LAUNCHER;
            app.component = component;
            app.isSatellite = true;
            Drawable banner = ai.loadBanner(pm);
            if (banner == null) banner = ai.applicationInfo.loadBanner(pm);
            if (banner != null) {
                app.isBanner = true;
                app.art = banner;
                app.color = sampleColor("starnet-satellite", app.art);
            } else {
                app.isBanner = false;
                app.art = getDrawable(R.drawable.ic_satellite);
                app.color = 0xFF0288D1;
            }
            return app;
        } catch (PackageManager.NameNotFoundException e) {
            return null; // Different Glide firmware: never guess another activity.
        }
    }

    private static final String PREF_NAME = "starnet_dock_prefs";
    private static final String KEY_DOCK = "dock_packages";

    private List<String> getDockList() {
        SharedPreferences sp = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String saved = sp.getString(KEY_DOCK, null);
        if (saved != null) {
            List<String> list = new ArrayList<>();
            for (String s : saved.split(",")) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) list.add(trimmed);
            }
            return list;
        }
        List<String> def = new ArrayList<>();
        Collections.addAll(def, getResources().getStringArray(R.array.dock_apps));
        return def;
    }

    private void saveDockList(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(list.get(i));
        }
        getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_DOCK, sb.toString())
                .apply();
    }

    private void addAppToDock(String pkg) {
        List<String> list = getDockList();
        if (!list.contains(pkg)) {
            list.add(pkg);
            saveDockList(list);
            buildApps();
        }
    }

    private void removeAppFromDock(String pkg) {
        List<String> list = getDockList();
        if (list.remove(pkg)) {
            saveDockList(list);
            buildApps();
        }
    }

    /** An 'Add to Dock' shortcut placed at the end of the dock with a '+' icon. */
    private App buildPlusEntry() {
        App app = new App();
        app.pkg = "com.mihir.tvhome.plus";
        app.label = "Add to Dock";
        app.isPlus = true;
        app.color = 0xFF3A3A3C;
        app.art = getDrawable(R.drawable.ic_plus);
        app.isBanner = false;
        return app;
    }

    private void showAppPicker() {
        PackageManager pm = getPackageManager();
        Map<String, App> allAvailable = new HashMap<>();
        List<String> currentDock = getDockList();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                ActivityInfo ai = ri.activityInfo;
                if (hidden.contains(ai.packageName) || allAvailable.containsKey(ai.packageName)) continue;
                if (currentDock.contains(ai.packageName)) continue;
                if (ai.packageName.equals(satelliteTvPkg)) continue;
                if (ai.packageName.equals(getPackageName())) continue;

                App a = new App();
                a.pkg = ai.packageName;
                a.category = category;
                a.label = String.valueOf(ri.loadLabel(pm));
                a.component = new ComponentName(ai.packageName, ai.name);
                Drawable banner = ai.loadBanner(pm);
                if (banner == null) banner = ai.applicationInfo.loadBanner(pm);
                a.isBanner = banner != null;
                a.art = banner != null ? banner : ri.loadIcon(pm);
                a.color = sampleColor(a.pkg, a.art);
                allAvailable.put(a.pkg, a);
            }
        }

        List<App> candidates = new ArrayList<>(allAvailable.values());
        Collections.sort(candidates, (x, y) -> x.label.compareToIgnoreCase(y.label));

        if (candidates.isEmpty()) {
            Toast.makeText(this, "All available apps are already in the dock", Toast.LENGTH_SHORT).show();
            return;
        }

        Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0xD9000000);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(36), dp(28), dp(36), dp(28));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF0202024);
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), 0x33FFFFFF);
        container.setBackground(bg);

        TextView header = text(22, "sans-serif-medium", Color.WHITE);
        header.setText("Add App to Dock");
        container.addView(header);

        TextView sub = text(12, "sans-serif", 0x99FFFFFF);
        sub.setText("Select an app to pin to your home screen dock");
        container.addView(sub, llp(-2, -2, 0, dp(4), 0, dp(18)));

        ScrollView scroller = new ScrollView(this);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);

        List<View> itemViews = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            App app = candidates.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(10), dp(16), dp(10));
            row.setFocusable(true);
            row.setClickable(true);
            row.setId(View.generateViewId());

            GradientDrawable rowBg = new GradientDrawable();
            rowBg.setColor(0x1AFFFFFF);
            rowBg.setCornerRadius(dp(12));
            row.setBackground(rowBg);

            ImageView icon = new ImageView(this);
            icon.setImageDrawable(app.art);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            row.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));

            TextView label = text(15, "sans-serif", 0xE6FFFFFF);
            label.setText(app.label);
            label.setSingleLine(true);
            row.addView(label, llp(-1, -2, dp(18), 0, 0, 0));

            row.setOnFocusChangeListener((v, hasFocus) -> {
                rowBg.setColor(hasFocus ? 0xFFFFFFFF : 0x1AFFFFFF);
                label.setTextColor(hasFocus ? 0xFF111111 : 0xE6FFFFFF);
                v.animate().scaleX(hasFocus ? 1.03f : 1f).scaleY(hasFocus ? 1.03f : 1f).setDuration(160).start();
            });

            row.setOnClickListener(v -> {
                dialog.dismiss();
                addAppToDock(app.pkg);
                Toast.makeText(MainActivity.this, "Added " + app.label + " to Dock", Toast.LENGTH_SHORT).show();
            });

            list.addView(row, llp(-1, -2, 0, dp(4), 0, dp(4)));
            itemViews.add(row);
        }

        for (int i = 0; i < itemViews.size(); i++) {
            View v = itemViews.get(i);
            v.setNextFocusUpId(i > 0 ? itemViews.get(i - 1).getId() : v.getId());
            v.setNextFocusDownId(i < itemViews.size() - 1 ? itemViews.get(i + 1).getId() : v.getId());
            v.setNextFocusLeftId(v.getId());
            v.setNextFocusRightId(v.getId());
        }

        scroller.addView(list, new FrameLayout.LayoutParams(-1, -2));
        container.addView(scroller, new LinearLayout.LayoutParams(-1, dp(340)));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(540), -2, Gravity.CENTER);
        overlay.addView(container, lp);

        dialog.setContentView(overlay);
        dialog.show();

        if (!itemViews.isEmpty()) {
            itemViews.get(0).requestFocus();
        }
    }

    private void showRemoveDialog(App app) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0xB3000000);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(32), dp(24), dp(32), dp(24));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF0202024);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), 0x33FFFFFF);
        box.setBackground(bg);

        TextView t = text(20, "sans-serif-medium", Color.WHITE);
        t.setText("Remove from Dock?");
        box.addView(t);

        TextView sub = text(13, "sans-serif", 0x99FFFFFF);
        sub.setText("'" + app.label + "' will remain available in All Apps.");
        box.addView(sub, llp(-2, -2, 0, dp(6), 0, dp(22)));

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END);

        TextView cancelBtn = text(13, "sans-serif-medium", 0xCCFFFFFF);
        cancelBtn.setText("Cancel");
        cancelBtn.setPadding(dp(20), dp(10), dp(20), dp(10));
        cancelBtn.setFocusable(true);
        cancelBtn.setClickable(true);
        cancelBtn.setId(View.generateViewId());
        GradientDrawable cancelBg = new GradientDrawable();
        cancelBg.setColor(0x26FFFFFF);
        cancelBg.setCornerRadius(dp(10));
        cancelBtn.setBackground(cancelBg);
        cancelBtn.setOnFocusChangeListener((v, hasFocus) -> {
            cancelBg.setColor(hasFocus ? 0xFFFFFFFF : 0x26FFFFFF);
            cancelBtn.setTextColor(hasFocus ? 0xFF111111 : 0xCCFFFFFF);
        });
        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        TextView removeBtn = text(13, "sans-serif-medium", Color.WHITE);
        removeBtn.setText("Remove");
        removeBtn.setPadding(dp(20), dp(10), dp(20), dp(10));
        removeBtn.setFocusable(true);
        removeBtn.setClickable(true);
        removeBtn.setId(View.generateViewId());
        GradientDrawable remBg = new GradientDrawable();
        remBg.setColor(0xFFD32F2F);
        remBg.setCornerRadius(dp(10));
        removeBtn.setBackground(remBg);
        removeBtn.setOnFocusChangeListener((v, hasFocus) -> {
            remBg.setColor(hasFocus ? 0xFFFF5252 : 0xFFD32F2F);
            removeBtn.animate().scaleX(hasFocus ? 1.05f : 1f).scaleY(hasFocus ? 1.05f : 1f).setDuration(150).start();
        });
        removeBtn.setOnClickListener(v -> {
            dialog.dismiss();
            removeAppFromDock(app.pkg);
            Toast.makeText(MainActivity.this, "Removed from Dock", Toast.LENGTH_SHORT).show();
        });

        cancelBtn.setNextFocusRightId(removeBtn.getId());
        removeBtn.setNextFocusLeftId(cancelBtn.getId());

        btnRow.addView(cancelBtn, llp(-2, -2, 0, 0, dp(12), 0));
        btnRow.addView(removeBtn, llp(-2, -2, 0, 0, 0, 0));
        box.addView(btnRow, llp(-1, -2, 0, dp(10), 0, 0));

        overlay.addView(box, new FrameLayout.LayoutParams(dp(440), -2, Gravity.CENTER));
        dialog.setContentView(overlay);
        dialog.show();
        cancelBtn.requestFocus();
    }

    /** Open the real DVB alias, then use stock Glide as a recovery fallback. */
    private void openSatelliteTv() {
        Intent satellite = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(new ComponentName(satelliteTvPkg, satelliteTvActivity))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (!launch(satellite)) {
            android.widget.Toast.makeText(this, "Satellite shortcut unavailable; opening Glide",
                    android.widget.Toast.LENGTH_LONG).show();
            openOriginalHome();
        }
    }

    /** A 16:9 card with the app's banner; the name shows under it while focused. */
    private View tile(App app, boolean inDock) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipChildren(false);
        box.setClipToPadding(false);

        FrameLayout card = new AspectFrame(this);
        card.setId(View.generateViewId());
        card.setFocusable(true);
        card.setClickable(true);
        card.setTag(app);
        card.setClipToOutline(true);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(10));
            }
        });
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(app.isBanner ? 0xFF1C1C1E : darken(app.color, 0.45f));
        card.setBackground(bg);

        ImageView img = new ImageView(this);
        img.setImageDrawable(app.art);
        if (app.isBanner) {
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(img, new FrameLayout.LayoutParams(-1, -1));
        } else {
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            card.addView(img, new FrameLayout.LayoutParams(-1, -1));
            img.setPadding(dp(22), dp(14), dp(22), dp(14));
        }

        // Focus ring and sheen, drawn above the art.
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(dp(10));
        ring.setStroke(dp(2), 0xE6FFFFFF);
        ring.setOrientation(GradientDrawable.Orientation.TL_BR);
        ring.setColors(new int[]{0x26FFFFFF, 0x00FFFFFF, 0x00FFFFFF});
        View shine = new View(this);
        shine.setBackground(ring);
        shine.setAlpha(0f);
        card.addView(shine, new FrameLayout.LayoutParams(-1, -1));

        box.addView(card, new LinearLayout.LayoutParams(-1, -2));

        TextView label = text(11, "sans-serif-medium", Color.WHITE);
        label.setText(app.label);
        label.setSingleLine(true);
        label.setGravity(Gravity.CENTER);
        label.setAlpha(0f);
        if (!inDock) box.addView(label, llp(-1, -2, 0, dp(10), 0, 0));

        card.setOnClickListener(v -> launchApp(app));
        if (inDock && !app.isSatellite && !app.isPlus) {
            card.setOnLongClickListener(v -> {
                showRemoveDialog(app);
                return true;
            });
            card.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_MENU) {
                    showRemoveDialog(app);
                    return true;
                }
                return false;
            });
        }
        card.setOnFocusChangeListener((v, has) -> {
            float s = has ? (inDock ? 1.12f : 1.1f) : 1f;
            v.animate().scaleX(s).scaleY(s).translationZ(has ? dp(14) : 0)
                    .setInterpolator(has ? new OvershootInterpolator(1.8f) : new DecelerateInterpolator())
                    .setDuration(has ? 320 : 200).start();
            shine.animate().alpha(has ? 1f : 0f).setDuration(160).start();
            label.animate().alpha(has ? 1f : 0f).translationY(has ? dp(4) : 0).setDuration(200).start();
            if (!has) return;
            lastFocusedId = v.getId();
            if (inDock) {
                title.setText("FAVORITE APPS");
                title.animate().alpha(1f).setDuration(200).start();
                updateHero(app, true);
                if (heroActionBtn != null) heroActionBtn.setNextFocusDownId(v.getId());
                animateGlow(app.color);
                fadeGlow(1f);
                scroll.smoothScrollTo(0, 0);
            } else {
                fadeGlow(0.35f);
                title.animate().alpha(0f).setDuration(200).start();
                scrollToRow(box);
            }
        });

        (inDock ? dockCards : gridCards).add(card);
        return box;
    }

    /** Explicit four-way remote navigation across top tabs, hero, favorites and channel rows. */
    private void wireFocus() {
        int d = dockCards.size(), g = gridCards.size();
        int heroBtnId = heroActionBtn != null ? heroActionBtn.getId() : View.NO_ID;
        int topId = !pills.isEmpty() ? pills.get(0).getId() : View.NO_ID;
        int firstChannelId = channelRows.isEmpty() ? View.NO_ID : channelRows.get(0).get(0).getId();
        int browseId = browseAppsButton != null ? browseAppsButton.getId() : View.NO_ID;
        int lastChannelRow = channelRows.size() - 1;
        for (int i = 0; i < pills.size(); i++) {
            View p = pills.get(i);
            p.setNextFocusLeftId(i == 0 ? p.getId() : pills.get(i - 1).getId());
            p.setNextFocusRightId(i == pills.size() - 1 ? p.getId() : pills.get(i + 1).getId());
            p.setNextFocusUpId(p.getId());
            p.setNextFocusDownId(catalogMode && g > 0 ? gridCards.get(0).getId() :
                    heroBtnId != View.NO_ID ? heroBtnId : p.getId());
        }
        if (heroActionBtn != null) {
            heroActionBtn.setNextFocusUpId(topId != View.NO_ID ? topId : heroBtnId);
            heroActionBtn.setNextFocusDownId(d > 0 ? dockCards.get(0).getId() :
                    firstChannelId != View.NO_ID ? firstChannelId : heroBtnId);
            heroActionBtn.setNextFocusLeftId(heroBtnId);
            heroActionBtn.setNextFocusRightId(heroBtnId);
        }
        for (int i = 0; i < d; i++) {
            View c = dockCards.get(i);
            c.setNextFocusUpId(heroBtnId != View.NO_ID ? heroBtnId : topId);
            c.setNextFocusLeftId(i == 0 ? c.getId() : dockCards.get(i - 1).getId());
            c.setNextFocusRightId(i == d - 1 ? c.getId() : dockCards.get(i + 1).getId());
            c.setNextFocusDownId(firstChannelId != View.NO_ID ?
                    channelRows.get(0).get(Math.min(i, channelRows.get(0).size() - 1)).getId() :
                    browseId != View.NO_ID ? browseId : c.getId());
        }
        for (int row = 0; row < channelRows.size(); row++) {
            List<View> current = channelRows.get(row);
            List<View> above = row == 0 ? dockCards : channelRows.get(row - 1);
            List<View> below = row == lastChannelRow ?
                    (browseAppsButton == null ? Collections.<View>emptyList() :
                            Collections.singletonList(browseAppsButton)) : channelRows.get(row + 1);
            for (int i = 0; i < current.size(); i++) {
                View c = current.get(i);
                c.setNextFocusLeftId(i == 0 ? c.getId() : current.get(i - 1).getId());
                c.setNextFocusRightId(i == current.size() - 1 ? c.getId() : current.get(i + 1).getId());
                c.setNextFocusUpId(above.isEmpty() ? heroBtnId :
                        above.get(Math.min(i, above.size() - 1)).getId());
                c.setNextFocusDownId(below.isEmpty() ? c.getId() :
                        below.get(Math.min(i, below.size() - 1)).getId());
            }
        }
        if (browseAppsButton != null) {
            browseAppsButton.setNextFocusUpId(lastChannelRow >= 0 ?
                    channelRows.get(lastChannelRow).get(0).getId() :
                    d > 0 ? dockCards.get(0).getId() : topId);
            browseAppsButton.setNextFocusDownId(browseId);
            browseAppsButton.setNextFocusLeftId(browseId);
            browseAppsButton.setNextFocusRightId(browseId);
        }

        int lastRow = (g - 1) / cols;
        for (int j = 0; j < g; j++) {
            View c = gridCards.get(j);
            int r = j / cols, col = j % cols;
            c.setNextFocusUpId(r == 0 ? (topId != View.NO_ID ? topId : c.getId()) :
                    gridCards.get(j - cols).getId());
            c.setNextFocusDownId(j + cols < g ? gridCards.get(j + cols).getId() :
                    r < lastRow ? gridCards.get(g - 1).getId() : c.getId());
            c.setNextFocusLeftId(col == 0 ? c.getId() : gridCards.get(j - 1).getId());
            c.setNextFocusRightId(col == cols - 1 || j == g - 1 ? c.getId() : gridCards.get(j + 1).getId());
        }
    }

    /** Catalog scroll independent of Home hero dimensions. */
    private void scrollToRow(View tileBox) {
        View row = (View) tileBox.getParent();
        int y = grid.getTop() + row.getTop() - dp(125);
        int max = Math.max(0, content.getHeight() - scroll.getHeight());
        scroll.smoothScrollTo(0, Math.max(0, Math.min(y, max)));
    }

    // ---------------------------------------------------------------- inputs

    private void showInputs(View anchor) {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(0xF02A2A2E);
        bg.setStroke(dp(1), 0x26FFFFFF);
        list.setBackground(bg);

        PopupWindow pw = new PopupWindow(list, dp(200), -2, true);
        List<TextView> rows = new ArrayList<>();

        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        List<TvInputInfo> inputs = new ArrayList<>();
        if (tim != null) for (TvInputInfo t : tim.getTvInputList()) {
            if (t.isPassthroughInput() && !t.isHidden(this)) inputs.add(t);
        }
        Collections.sort(inputs, (x, y) -> inputLabel(x).compareToIgnoreCase(inputLabel(y)));
        for (TvInputInfo t : inputs) {
            rows.add(inputRow(inputLabel(t), () -> {
                Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(t.getId()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (!launch(i)) openPackage(inputPickerPkg);
            }));
        }
        if (buildSatelliteEntry(getPackageManager()) != null)
            rows.add(inputRow("Live Satellite TV", this::openSatelliteTv));
        if (inputs.isEmpty() && isInstalled(inputPickerPkg)) rows.add(inputRow("All sources…", () -> openPackage(inputPickerPkg)));

        if (rows.isEmpty()) rows.add(inputRow("Settings", () -> launch(new Intent(Settings.ACTION_SETTINGS))));
        for (TextView r : rows) {
            r.setOnClickListener(v -> { pw.dismiss(); ((Runnable) v.getTag()).run(); });
            list.addView(r, new LinearLayout.LayoutParams(-1, dp(36)));
        }
        for (int i = 0; i < rows.size(); i++) {
            TextView r = rows.get(i);
            r.setNextFocusUpId(rows.get(Math.max(0, i - 1)).getId());
            r.setNextFocusDownId(rows.get(Math.min(rows.size() - 1, i + 1)).getId());
            r.setNextFocusLeftId(r.getId());
            r.setNextFocusRightId(r.getId());
        }
        pw.setOutsideTouchable(true);
        pw.setElevation(dp(16));
        pw.setOnDismissListener(() -> { inputsPopup = null; anchor.requestFocus(); });
        inputsPopup = pw;
        pw.showAsDropDown(anchor, 0, dp(8), Gravity.START);
        rows.get(0).requestFocus();
    }

    private String inputLabel(TvInputInfo t) {
        CharSequence c = t.loadCustomLabel(this);
        if (c == null || c.length() == 0) c = t.loadLabel(this);
        return String.valueOf(c);
    }

    private TextView inputRow(String label, Runnable action) {
        TextView r = text(13, "sans-serif", Color.WHITE);
        r.setText(label);
        r.setTag(action);
        r.setId(View.generateViewId());
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(14), 0, dp(14), 0);
        r.setFocusable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        bg.setColor(Color.TRANSPARENT);
        r.setBackground(bg);
        r.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? 0xF2FFFFFF : Color.TRANSPARENT);
            r.setTextColor(has ? 0xFF111111 : Color.WHITE);
        });
        return r;
    }

    // ---------------------------------------------------------------- status

    private void updateClock() {
        Calendar now = Calendar.getInstance();
        int h = now.get(Calendar.HOUR_OF_DAY);
        greeting.setText(h < 5 ? "GOOD NIGHT" : h < 12 ? "GOOD MORNING" : h < 17 ? "GOOD AFTERNOON" : "GOOD EVENING");
        boolean is24 = android.text.format.DateFormat.is24HourFormat(this);
        clock.setText(new SimpleDateFormat(is24 ? "H:mm" : "h:mm", Locale.getDefault()).format(now.getTime()));
        ampm.setText(is24 ? "" : (h < 12 ? "AM" : "PM"));
        date.setText(new SimpleDateFormat("EEEE  d MMMM", Locale.getDefault()).format(now.getTime()).toUpperCase(Locale.getDefault()));
    }

    private void updateWifi() {
        String name = "Wi-Fi";
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wm != null && wm.getConnectionInfo() != null) {
                String s = wm.getConnectionInfo().getSSID();
                if (s != null && !s.contains("unknown ssid")) name = s.replace("\"", "");
                if (wm.getConnectionInfo().getNetworkId() == -1) name = "Offline";
            }
        } catch (SecurityException ignored) { }
        wifiPill.setText("◉  " + name);
    }

    // ---------------------------------------------------------------- glow

    private void setGlow(int color) {
        glowColor = color;
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientCenter(0.5f, 0.78f);
        g.setGradientRadius(getResources().getDisplayMetrics().widthPixels * 0.62f);
        g.setColors(new int[]{withAlpha(color, 0.18f), withAlpha(color, 0.05f), Color.TRANSPARENT});
        glow.setBackground(g);
    }

    private void animateGlow(int to) {
        if (glowAnim != null) glowAnim.cancel();
        glowAnim = ValueAnimator.ofObject(new ArgbEvaluator(), glowColor, to);
        glowAnim.setDuration(450);
        glowAnim.addUpdateListener(a -> setGlow((int) a.getAnimatedValue()));
        glowAnim.start();
    }

    private void fadeGlow(float alpha) {
        glow.animate().alpha(alpha).setDuration(300).start();
        // The grid sits over the photo's busiest middle part, so dim it while browsing apps.
        gridShade.animate().alpha(alpha < 1f ? 1f : 0f).setDuration(300).start();
    }

    // ---------------------------------------------------------------- wallpaper

    private ImageView wallpaperLayer() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return v;
    }

    /** Decode the next photo off the main thread, then crossfade it in over the current one. */
    private void nextWallpaper() {
        handler.removeCallbacks(rotateWallpaper);
        if (wallpapers.length == 0) return;
        if (wallIndex < 0) wallIndex = getPreferences(MODE_PRIVATE).getInt("wallpaper", -1);
        wallIndex = (wallIndex + 1) % wallpapers.length;
        getPreferences(MODE_PRIVATE).edit().putInt("wallpaper", wallIndex).apply();
        wallChangedAt = SystemClock.elapsedRealtime();
        String name = "wallpapers/" + wallpapers[wallIndex];
        new Thread(() -> {
            Bitmap bm = null;
            try (java.io.InputStream in = getAssets().open(name)) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inPreferredConfig = Bitmap.Config.RGB_565;
                bm = BitmapFactory.decodeStream(in, null, o);
            } catch (java.io.IOException ignored) { }
            Bitmap result = bm;
            runOnUiThread(() -> showWallpaper(result));
        }).start();
        scheduleWallpaper(WALLPAPER_INTERVAL_MS);
    }

    private void showWallpaper(Bitmap bm) {
        if (bm == null || isDestroyed()) return;
        // Swap roles: the old front becomes the back, the new photo fades in on top.
        ImageView old = wallFront;
        wallFront = wallBack;
        wallBack = old;
        ViewGroup root = (ViewGroup) wallFront.getParent();
        root.removeView(wallFront);
        root.addView(wallFront, 1, new FrameLayout.LayoutParams(-1, -1));
        wallFront.setImageBitmap(bm);
        if (heroArtwork != null) heroArtwork.setImageBitmap(bm);
        wallFront.setAlpha(0f);
        wallFront.animate().alpha(1f).setDuration(firstWallpaper ? 600 : 2000).withEndAction(() -> {
            wallBack.setImageDrawable(null); // release the previous photo
        }).start();
        firstWallpaper = false;
    }

    private boolean firstWallpaper = true;

    private void scheduleWallpaper(long delay) {
        handler.removeCallbacks(rotateWallpaper);
        handler.postDelayed(rotateWallpaper, Math.max(1000, delay));
    }

    /** Average the banner's vivid pixels, then push saturation so the glow reads as a colour. */
    private int sampleColor(String pkg, Drawable d) {
        Integer cached = colorCache.get(pkg);
        if (cached != null) return cached;
        int color = 0xFF3A3A3A;
        try {
            Bitmap bm = Bitmap.createBitmap(24, 14, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bm);
            d.setBounds(0, 0, 24, 14);
            d.draw(c);
            float[] hsv = new float[3];
            double r = 0, g = 0, b = 0, w = 0;
            for (int y = 0; y < 14; y++) for (int x = 0; x < 24; x++) {
                int p = bm.getPixel(x, y);
                if (Color.alpha(p) < 128) continue;
                Color.colorToHSV(p, hsv);
                double weight = 0.05 + hsv[1] * hsv[2];
                r += Color.red(p) * weight; g += Color.green(p) * weight; b += Color.blue(p) * weight; w += weight;
            }
            bm.recycle();
            if (w > 0) {
                Color.colorToHSV(Color.rgb((int) (r / w), (int) (g / w), (int) (b / w)), hsv);
                if (hsv[1] > 0.12f) { hsv[1] = Math.max(hsv[1], 0.75f); hsv[2] = 0.85f; }
                else { hsv[1] = 0f; hsv[2] = 0.45f; }
                color = Color.HSVToColor(hsv);
            }
        } catch (RuntimeException ignored) { }
        colorCache.put(pkg, color);
        return color;
    }

    // ---------------------------------------------------------------- entrance

    private void playEntrance() {
        View header = hero.getChildAt(0);
        header.setAlpha(0f);
        header.setTranslationY(-dp(12));
        header.animate().alpha(1f).translationY(0).setDuration(500).setStartDelay(80).start();
        title.setAlpha(0f);
        title.animate().alpha(1f).setDuration(500).setStartDelay(200).start();
        for (int i = 0; i < dockRow.getChildCount(); i++) {
            View t = dockRow.getChildAt(i);
            t.setAlpha(0f);
            t.setTranslationY(dp(24));
            t.animate().alpha(1f).translationY(0).setInterpolator(new DecelerateInterpolator(2f))
                    .setDuration(450).setStartDelay(160 + i * 60L).start();
        }
    }

    // ---------------------------------------------------------------- helpers

    private boolean launch(Intent i) {
        try {
            if (!i.hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean isInstalled(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void openPackage(String pkg) {
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i != null) launch(i);
    }

    private TextView text(float sp, String family, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        t.setTextColor(color);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static FrameLayout.LayoutParams lp(int w, int h, int gravity, int l, int t, int r, int b) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w, h, gravity);
        p.setMargins(l, t, r, b);
        return p;
    }

    private static LinearLayout.LayoutParams llp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(l, t, r, b);
        return p;
    }

    private static int withAlpha(int c, float a) {
        return Color.argb(Math.round(a * 255), Color.red(c), Color.green(c), Color.blue(c));
    }

    private static int darken(int c, float f) {
        return Color.rgb((int) (Color.red(c) * f), (int) (Color.green(c) * f), (int) (Color.blue(c) * f));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        // Menu key on the remote opens the Inputs dropdown.
        if (e.getKeyCode() == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP && !pills.isEmpty()) {
            showInputs(pills.get(0));
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    /** 16:9 container. */
    static final class AspectFrame extends FrameLayout {
        AspectFrame(Context c) { super(c); }
        @Override protected void onMeasure(int w, int h) {
            int width = MeasureSpec.getSize(w);
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(width * 9 / 16, MeasureSpec.EXACTLY));
        }
    }

    /** ScrollView that never auto-scrolls to the focused child; the activity scrolls it by row. */
    static final class HomeScrollView extends android.widget.ScrollView {
        HomeScrollView(Context c) { super(c); }
        @Override protected int computeScrollDeltaToGetChildRectOnScreen(android.graphics.Rect r) { return 0; }
    }
}
