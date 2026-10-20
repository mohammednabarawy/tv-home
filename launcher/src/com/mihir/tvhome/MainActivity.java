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
import android.content.res.Configuration;
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

import com.mihir.tvhome.content.ContentManager;
import com.mihir.tvhome.content.TvProviderAuditor;
import com.mihir.tvhome.model.MediaItem;
import com.mihir.tvhome.model.MediaShelf;
import com.mihir.tvhome.settings.LauncherSettings;
import com.mihir.tvhome.screensaver.PhotoScreensaverView;

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
 * Arabic RTL TV home with a cinematic backdrop, compact favorites and installed-service shelves.
 * Plain Android views, offline artwork, optional HOME selection and an idle photo screensaver.
 */
public class MainActivity extends Activity {

    // Dock, hidden apps, Live TV and grid width come from res/values/config.xml.
    private String[] dockApps;
    private final Set<String> hidden = new HashSet<>();
    private String satelliteTvPkg, satelliteTvActivity, inputPickerPkg;
    private int cols;

    public static final class App {
        public String pkg, label, category;
        public ComponentName component;
        public boolean isSatellite;
        public boolean isPlus;
        public Drawable art;
        public boolean isBanner;
        public int color;
    }

    private final Map<String, Integer> colorCache = new HashMap<>();
    private final List<View> dockCards = new ArrayList<>();
    private final List<View> gridCards = new ArrayList<>();
    private final List<View> pills = new ArrayList<>();

    private HomeScrollView scroll;
    private LinearLayout content, dockRow;
    private HorizontalScrollView dockScroll;
    private FrameLayout cinematicBackdrop;
    private View toolbarScrim;
    private Runnable pendingHeroPreview;
    private String lastDockPackage;
    private FrameLayout rootView;
    private PhotoScreensaverView screensaverView;
    private boolean resumed;
    private Typeface arabicRegular, arabicBold;
    private int screensaverWakeKey = -1;
    private final Runnable idleScreensaver = this::showScreensaver;
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
    private TextView clock, ampm, title, appsLabel;
    private String wifiStatus = "";
    private int glowColor = 0xFF3A3A3A;
    private ValueAnimator glowAnim;
    private int lastFocusedId = View.NO_ID;
    private boolean firstShow = true;
    private int packageSeq;
    private PopupWindow inputsPopup;

    public static final int VIEW_HOME = 0;
    public static final int VIEW_DISCOVER = 1;
    public static final int VIEW_APPS = 2;
    public static final int VIEW_SETTINGS = 3;
    private int currentViewMode = VIEW_HOME;

    private View heroCard;
    private TextView heroBadge, heroTitle, heroSubtitle, heroActionBtn, heroFrameTag;
    private ImageView heroPreviewImage, heroArtwork;
    private View showcaseView, favoritesView, browseAppsButton, headerView;
    private TextView homeNavTab, discoverNavTab, appsNavTab, inputsNavTab, settingsTab;
    private LinearLayout discoverContainer, settingsContainer;
    private final List<List<View>> discoverRows = new ArrayList<>();
    private final List<List<View>> settingsRows = new ArrayList<>();
    private Bitmap currentWallpaperBitmap;
    private Bitmap satBackdropBitmap;
    private boolean isShowingSatBackdrop = false;
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
    protected void attachBaseContext(Context base) {
        Configuration config = new Configuration(base.getResources().getConfiguration());
        Locale arabic = Locale.forLanguageTag("ar");
        config.setLocale(arabic);
        config.setLayoutDirection(arabic);
        super.attachBaseContext(base.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            arabicRegular = getResources().getFont(R.font.tajawal_regular);
            arabicBold = getResources().getFont(R.font.tajawal_bold);
        } catch (RuntimeException unsupportedFont) {
            android.util.Log.w("StarNetHome", "Firmware font loader fallback", unsupportedFont);
            arabicRegular = Typeface.create("sans-serif", Typeface.NORMAL);
            arabicBold = Typeface.create("sans-serif", Typeface.BOLD);
        }
        dockApps = getResources().getStringArray(R.array.dock_apps);
        hidden.addAll(Arrays.asList(getResources().getStringArray(R.array.hidden_apps)));
        hidden.add(getPackageName());
        satelliteTvPkg = getString(R.string.satellite_tv_package);
        satelliteTvActivity = getString(R.string.satellite_tv_activity);
        inputPickerPkg = getString(R.string.input_picker_package);
        cols = getResources().getInteger(R.integer.columns);
        lastDockPackage = getPreferences(MODE_PRIVATE).getString("last_dock_package", null);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        setContentView(buildUi());
        loadSatBackdropAsync();
        new Thread(() -> TvProviderAuditor.runAudit(getApplicationContext()), "tvprovider-audit").start();
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
        resumed = true;
        resetIdleTimer();
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
        resumed = false;
        handler.removeCallbacks(idleScreensaver);
        dismissScreensaver();
        getPreferences(MODE_PRIVATE).edit().putString("last_dock_package", lastDockPackage).apply();
        handler.removeCallbacks(rotateWallpaper);
        if (pendingHeroPreview != null) handler.removeCallbacks(pendingHeroPreview);
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
        dismissScreensaver();
        // Home pressed while already home: jump back to Home view and first dock app.
        if (inputsPopup != null) inputsPopup.dismiss();
        setViewMode(VIEW_HOME);
    }

    @Override
    public void onBackPressed() {
        if (currentViewMode != VIEW_HOME) {
            setViewMode(VIEW_HOME);
            handler.post(() -> {
                if (!dockCards.isEmpty()) dockCards.get(0).requestFocus();
            });
        } else if (scroll.getScrollY() > 0 && !dockCards.isEmpty()) {
            dockCards.get(0).requestFocus();
        }
    }

    // ---------------------------------------------------------------- layout

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        rootView = root;
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(0xFF081321);
        cinematicBackdrop = new FrameLayout(this);
        heroArtwork = wallpaperLayer();
        heroArtwork.setScaleX(-1f);
        heroArtwork.setImageResource(R.drawable.cinematic_hero);
        cinematicBackdrop.addView(heroArtwork, new FrameLayout.LayoutParams(-1, -1));
        View sideFade = new View(this);
        sideFade.setBackground(new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT,
                new int[]{0xFA081321, 0xE6081321, 0x66081321, 0x18081321}));
        cinematicBackdrop.addView(sideFade, new FrameLayout.LayoutParams(-1, -1));
        View floorFade = new View(this);
        floorFade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00081321, 0x30081321, 0xE6081321, 0xFF081321}));
        cinematicBackdrop.addView(floorFade, new FrameLayout.LayoutParams(-1, -1));
        root.addView(cinematicBackdrop, new FrameLayout.LayoutParams(-1, -1));
        try {
            String[] found = getAssets().list("wallpapers");
            wallpapers = found == null ? new String[0] : found;
        } catch (java.io.IOException e) {
            wallpapers = new String[0];
            android.util.Log.w("StarNetHome", "Wallpaper assets unavailable; keeping packaged photo", e);
        }

        scroll = new HomeScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setFillViewport(true);
        scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> {
            cinematicBackdrop.setAlpha(Math.max(0.35f, 1f - (float) y / dp(500)));
            updateToolbarSurface();
        });
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        // Clip overflowing hero/media children to their allocated bounds on StarNet's renderer.
        content.setClipChildren(true);
        content.setClipToPadding(true);
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));

        hero = new FrameLayout(this);
        // Keep the photograph confined to the hero; child draw overflow causes wallpaper bands.
        hero.setClipChildren(true);
        hero.setClipToPadding(true);
        int screenH = getResources().getDisplayMetrics().heightPixels;
        homeHeroHeight = Math.max(dp(244), Math.round(screenH * 0.36f));
        content.addView(hero, new LinearLayout.LayoutParams(-1, homeHeroHeight));

        // Full-bleed photo fills the upper stage. It no longer overlays the app shelves.
        showcaseView = buildHeroCard();
        hero.addView(showcaseView, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setClipChildren(false);
        bottom.setClipToPadding(false);
        bottom.setPaddingRelative(dp(48), 0, 0, dp(16));
        favoritesView = bottom;
        content.addView(bottom, new LinearLayout.LayoutParams(-1, -2));

        title = text(13, "sans-serif-medium", 0xDBFFFFFF);
        title.setText(getString(R.string.favorites));
        bottom.addView(title, llp(-1, -2, 0, 0, 0, dp(7)));

        dockScroll = new HorizontalScrollView(this);
        dockScroll.setHorizontalScrollBarEnabled(false);
        dockScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        dockScroll.setClipToPadding(false);
        dockScroll.setClipChildren(false);
        bottom.addView(dockScroll, new LinearLayout.LayoutParams(-1, -2));

        dockRow = new LinearLayout(this);
        dockRow.setOrientation(LinearLayout.HORIZONTAL);
        dockRow.setGravity(Gravity.CENTER_VERTICAL);
        dockRow.setClipChildren(false);
        dockRow.setClipToPadding(false);
        dockRow.setPaddingRelative(dp(4), dp(7), dp(48), dp(9));
        dockScroll.addView(dockRow, new HorizontalScrollView.LayoutParams(-2, -2));

        channelContainer = new LinearLayout(this);
        channelContainer.setOrientation(LinearLayout.VERTICAL);
        channelContainer.setClipChildren(false);
        channelContainer.setClipToPadding(false);
        channelContainer.setPadding(dp(48), dp(4), dp(48), dp(30));
        content.addView(channelContainer, new LinearLayout.LayoutParams(-1, -2));

        discoverContainer = new LinearLayout(this);
        discoverContainer.setOrientation(LinearLayout.VERTICAL);
        discoverContainer.setClipChildren(false);
        discoverContainer.setClipToPadding(false);
        discoverContainer.setPadding(dp(48), dp(4), dp(48), dp(30));
        content.addView(discoverContainer, new LinearLayout.LayoutParams(-1, -2));
        discoverContainer.setVisibility(View.GONE);

        settingsContainer = new LinearLayout(this);
        settingsContainer.setOrientation(LinearLayout.VERTICAL);
        settingsContainer.setClipChildren(false);
        settingsContainer.setClipToPadding(false);
        settingsContainer.setPadding(dp(48), dp(4), dp(48), dp(40));
        settingsContainer.setBackgroundColor(0xFF081321);
        content.addView(settingsContainer, new LinearLayout.LayoutParams(-1, -2));
        settingsContainer.setVisibility(View.GONE);

        appsLabel = text(22, "sans-serif-medium", 0xFFE5E9F5);
        appsLabel.setText(getString(R.string.all_apps));
        appsLabel.setPadding(0, dp(15), 0, dp(5));
        content.addView(appsLabel, llp(-2, -2, dp(60), dp(8), 0, dp(18)));
        appsLabel.setVisibility(View.GONE);

        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        content.addView(grid, llp(-1, -2, dp(60), 0, dp(60), dp(100)));
        grid.setVisibility(View.GONE);

        toolbarScrim = new View(this);
        updateToolbarSurface();
        root.addView(toolbarScrim, lp(-1, dp(72), Gravity.TOP, 0, 0, 0, 0));

        View header = buildHeader();
        headerView = header;
        header.setElevation(dp(18));
        root.addView(header, lp(-1, dp(62), Gravity.TOP, dp(48), dp(5), dp(48), 0));
        return root;
    }

    private void updateToolbarSurface() {
        if (toolbarScrim == null) return;
        if (scroll.getScrollY() > dp(20) || currentViewMode == VIEW_APPS || currentViewMode == VIEW_SETTINGS) {
            toolbarScrim.setBackgroundColor(0xFF081321);
        } else {
            toolbarScrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xDB081321, 0x66081321, 0x00081321}));
        }
    }

    /** Clean top navigation inspired by Google TV / Android TV, with active underline tabs. */
    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClipChildren(false);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabs.setClipChildren(false);

        homeNavTab = navTab(getString(R.string.home));
        homeNavTab.setOnClickListener(v -> setViewMode(VIEW_HOME));
        // Press and hold OK on Home to change the landscape using bundled local photos.
        homeNavTab.setOnLongClickListener(v -> {
            nextWallpaper();
            Toast.makeText(this, getString(R.string.wallpaper_changed), Toast.LENGTH_SHORT).show();
            return true;
        });

        discoverNavTab = navTab(getString(R.string.discover));
        discoverNavTab.setOnClickListener(v -> setViewMode(VIEW_DISCOVER));

        appsNavTab = navTab(getString(R.string.apps));
        appsNavTab.setOnClickListener(v -> setViewMode(VIEW_APPS));

        for (TextView tab : new TextView[]{homeNavTab, discoverNavTab, appsNavTab}) {
            tabs.addView(tab, llp(-2, dp(37), 0, 0, dp(18), 0));
            pills.add(tab);
        }
        header.addView(tabs, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout status = new LinearLayout(this);
        status.setGravity(Gravity.CENTER_VERTICAL);

        inputsNavTab = navTab(getString(R.string.quick_menu));
        inputsNavTab.setOnClickListener(this::showInputs);
        status.addView(inputsNavTab, llp(-2, dp(37), 0, 0, dp(8), 0));
        pills.add(inputsNavTab);

        LinearLayout clockStack = new LinearLayout(this);
        clockStack.setOrientation(LinearLayout.VERTICAL);
        clockStack.setGravity(Gravity.CENTER);
        clock = text(19, "sans-serif", Color.WHITE);
        clock.setTextDirection(View.TEXT_DIRECTION_LTR);
        ampm = text(10, "sans-serif", 0xB3FFFFFF);
        LinearLayout timeLine = new LinearLayout(this);
        timeLine.setGravity(Gravity.CENTER);
        timeLine.addView(clock);
        timeLine.addView(ampm, llp(-2, -2, dp(4), 0, 0, 0));
        clockStack.addView(timeLine);
        status.addView(clockStack, llp(-2, -2, dp(12), 0, dp(8), 0));

        settingsTab = navTab(getString(R.string.settings));
        settingsTab.setOnClickListener(v -> setViewMode(VIEW_SETTINGS));
        status.addView(settingsTab);
        pills.add(settingsTab);

        header.addView(status);
        updateNavSelection();
        return header;
    }

    private void updateNavSelection() {
        updateNavTab(homeNavTab, currentViewMode == VIEW_HOME);
        updateNavTab(discoverNavTab, currentViewMode == VIEW_DISCOVER);
        updateNavTab(appsNavTab, currentViewMode == VIEW_APPS);
        updateNavTab(settingsTab, currentViewMode == VIEW_SETTINGS);
    }

    private void updateNavTab(TextView tab, boolean active) {
        if (tab == null) return;
        tab.setTextColor(active || tab.hasFocus() ? Color.WHITE : 0xB3FFFFFF);
        tab.setTypeface(active ? arabicBold : arabicRegular);
        int flags = tab.getPaintFlags();
        tab.setPaintFlags(active || tab.hasFocus()
                ? (flags | android.graphics.Paint.UNDERLINE_TEXT_FLAG)
                : (flags & ~android.graphics.Paint.UNDERLINE_TEXT_FLAG));
    }

    private TextView navTab(String label) {
        TextView tab = text(16, "sans-serif-medium", 0xC8FFFFFF);
        tab.setText(label);
        tab.setGravity(Gravity.CENTER);
        tab.setPadding(dp(10), 0, dp(10), 0);
        tab.setId(View.generateViewId());
        tab.setFocusable(true);
        tab.setClickable(true);
        tab.setBackground(new GradientDrawable());
        tab.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    v.performClick();
                    return true;
                }
            }
            return false;
        });
        tab.setOnFocusChangeListener((v, hasFocus) -> {
            tab.setScaleX(hasFocus ? 1.06f : 1f);
            tab.setScaleY(hasFocus ? 1.06f : 1f);
            if (hasFocus) {
                lastFocusedId = v.getId();
                if (tab == homeNavTab && currentViewMode != VIEW_HOME) {
                    setViewMode(VIEW_HOME);
                } else if (tab == discoverNavTab && currentViewMode != VIEW_DISCOVER) {
                    setViewMode(VIEW_DISCOVER);
                } else if (tab == appsNavTab && currentViewMode != VIEW_APPS) {
                    setViewMode(VIEW_APPS);
                } else if (tab == settingsTab && currentViewMode != VIEW_SETTINGS) {
                    setViewMode(VIEW_SETTINGS);
                }
            }
            if (tab == homeNavTab || tab == discoverNavTab || tab == appsNavTab || tab == settingsTab) {
                updateNavSelection();
            } else {
                tab.setTextColor(hasFocus ? Color.WHITE : 0xC8FFFFFF);
                int flags = tab.getPaintFlags();
                tab.setPaintFlags(hasFocus ?
                        (flags | android.graphics.Paint.UNDERLINE_TEXT_FLAG) :
                        (flags & ~android.graphics.Paint.UNDERLINE_TEXT_FLAG));
            }
        });
        return tab;
    }

    /** Switch between Home, Discover, All Apps, and Settings views. */
    private void setViewMode(int mode) {
        boolean leavingSettings = currentViewMode == VIEW_SETTINGS && mode != VIEW_SETTINGS;
        currentViewMode = mode;
        catalogMode = (mode == VIEW_APPS);
        if (leavingSettings) buildApps();
        updateNavSelection();
        if (cinematicBackdrop != null) cinematicBackdrop.setVisibility(
                mode == VIEW_APPS || mode == VIEW_SETTINGS ? View.GONE : View.VISIBLE);
        updateToolbarSurface();

        if (hero != null) {
            ViewGroup.LayoutParams params = hero.getLayoutParams();
            params.height = (mode == VIEW_APPS || mode == VIEW_SETTINGS) ? dp(82) : homeHeroHeight;
            hero.setLayoutParams(params);
        }

        if (showcaseView != null) showcaseView.setVisibility((mode == VIEW_APPS || mode == VIEW_SETTINGS) ? View.GONE : View.VISIBLE);
        if (favoritesView != null) favoritesView.setVisibility(mode == VIEW_HOME ? View.VISIBLE : View.GONE);
        if (channelContainer != null) channelContainer.setVisibility(mode == VIEW_HOME ? View.VISIBLE : View.GONE);
        if (discoverContainer != null) discoverContainer.setVisibility(mode == VIEW_DISCOVER ? View.VISIBLE : View.GONE);
        if (appsLabel != null) appsLabel.setVisibility(mode == VIEW_APPS ? View.VISIBLE : View.GONE);
        if (grid != null) grid.setVisibility(mode == VIEW_APPS ? View.VISIBLE : View.GONE);
        if (settingsContainer != null) settingsContainer.setVisibility(mode == VIEW_SETTINGS ? View.VISIBLE : View.GONE);

        if (scroll != null) scroll.scrollTo(0, 0);

        if (mode == VIEW_DISCOVER) {
            setupDiscoverHero();
        } else if (mode == VIEW_HOME) {
            if (!dockCards.isEmpty()) {
                App first = (App) dockCards.get(0).getTag();
                for (View card : dockCards) {
                    App candidate = (App) card.getTag();
                    if (candidate.pkg.equals(lastDockPackage)) { first = candidate; break; }
                }
                if (first != null) updateHero(first, false);
            }
        }

        wireFocus();

        View focused = getCurrentFocus();
        boolean inHeader = false;
        if (focused != null) {
            for (View p : pills) {
                if (focused == p) { inHeader = true; break; }
            }
        }

        if (!inHeader) {
            final int targetMode = mode;
            handler.post(() -> {
                if (targetMode == VIEW_APPS) {
                    if (!gridCards.isEmpty()) gridCards.get(0).requestFocus();
                } else if (targetMode == VIEW_DISCOVER) {
                    if (!discoverRows.isEmpty() && !discoverRows.get(0).isEmpty()) {
                        discoverRows.get(0).get(0).requestFocus();
                    } else if (heroActionBtn != null) {
                        heroActionBtn.requestFocus();
                    }
                } else if (targetMode == VIEW_SETTINGS) {
                    if (!settingsRows.isEmpty() && !settingsRows.get(0).isEmpty()) {
                        settingsRows.get(0).get(0).requestFocus();
                    }
                } else {
                    if (!dockCards.isEmpty()) {
                        dockCards.get(0).requestFocus();
                    } else if (heroActionBtn != null) {
                        heroActionBtn.requestFocus();
                    }
                }
            });
        }
    }

    private void setupDiscoverHero() {
        if (heroBadge == null) return;
        heroBadge.setText(getString(R.string.entertainment_badge));
        heroTitle.setText(getString(R.string.discover_title));
        heroSubtitle.setText(getString(R.string.discover_subtitle));
        heroActionBtn.setText(getString(R.string.explore_categories));
        heroActionBtn.setOnClickListener(v -> {
            if (!discoverRows.isEmpty() && !discoverRows.get(0).isEmpty()) {
                discoverRows.get(0).get(0).requestFocus();
            }
        });
        if (currentWallpaperBitmap != null && heroArtwork != null) {
            heroArtwork.setImageBitmap(currentWallpaperBitmap);
            isShowingSatBackdrop = false;
        }
    }

    /** Compatibility helper for previous catalog switches. */
    private void showAppCatalog(boolean showCatalog) {
        setViewMode(showCatalog ? VIEW_APPS : VIEW_HOME);
    }

    /** Keep an explicit entry point for the complete set of applications. */
    private void scrollToAllApps() { showAppCatalog(true); }

    private void openOriginalHome() {
        Intent glide = new Intent(Intent.ACTION_MAIN)
                .setComponent(new ComponentName("com.tosmart.glide", "com.tosmart.glide.init.InitActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (!launch(glide)) {
            Toast.makeText(this, getString(R.string.glide_fallback),
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
        panel.setClipChildren(true);
        panel.setClipToPadding(true);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setClipChildren(false);
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int copyWidth = Math.min(dp(530), Math.round(screenW * .52f));
        FrameLayout.LayoutParams copyLp = new FrameLayout.LayoutParams(
                copyWidth, -1, Gravity.START | Gravity.CENTER_VERTICAL);
        // Avoid overlap with fixed toolbar and low-lying favorite cards.
        copyLp.setMargins(0, dp(76), 0, dp(34));
        copyLp.setMarginStart(dp(52));

        heroBadge = text(11, "sans-serif-medium", 0xFFC8D6E5);
        heroBadge.setSingleLine(true);
        copy.addView(heroBadge, llp(-1, -2, 0, 0, 0, dp(5)));

        heroTitle = text(32, "sans-serif-medium", Color.WHITE);
        heroTitle.setShadowLayer(dp(10), 0, dp(2), 0xCC000000);
        heroTitle.setSingleLine(true);
        heroTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(heroTitle, llp(-1, -2, 0, 0, 0, dp(4)));

        heroSubtitle = text(14, "sans-serif", 0xDBDCE4EE);
        heroSubtitle.setMaxLines(1);
        heroSubtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heroSubtitle.setLineSpacing(dp(3), 1f);
        copy.addView(heroSubtitle, llp(-1, -2, 0, 0, dp(6), dp(12)));

        heroActionBtn = text(13, "sans-serif-medium", 0xFF121923);
        heroActionBtn.setPadding(dp(18), 0, dp(18), 0);
        heroActionBtn.setGravity(Gravity.CENTER);
        heroActionBtn.setFocusable(true);
        heroActionBtn.setId(View.generateViewId());
        GradientDrawable cta = new GradientDrawable();
        cta.setColor(0xFFF6F8FA);
        cta.setCornerRadius(dp(22));
        heroActionBtn.setBackground(cta);
        heroActionBtn.setOnClickListener(v -> launchApp(currentHeroApp));
        heroActionBtn.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    v.performClick();
                    return true;
                }
            }
            return false;
        });
        heroActionBtn.setOnFocusChangeListener((v, focused) -> {
            cta.setColor(focused ? 0xFFFFFFFF : 0xE7F5F8FA);
            v.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f)
                    .setDuration(140).start();
            if (focused) {
                lastFocusedId = v.getId();
                scroll.smoothScrollTo(0, 0);
            }
        });
        copy.addView(heroActionBtn, new LinearLayout.LayoutParams(-2, dp(34)));
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
        panel.addView(heroPreviewBox, lp(dp(78), dp(62), Gravity.BOTTOM | Gravity.END, 0, 0, dp(55), dp(30)));
        panel.addView(heroFrameTag, lp(dp(88), dp(20), Gravity.BOTTOM | Gravity.END, 0, 0, dp(50), dp(7)));

        // The small app badge was visually distracting over landscape artwork.
        heroPreviewBox.setVisibility(View.GONE);
        heroFrameTag.setVisibility(View.GONE);
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
        heroActionBtn.setOnClickListener(v -> launchApp(currentHeroApp));
        heroPreviewImage.setImageDrawable(app.art);
        heroFrameTag.setText(getAppFrameTag(app));

        if (app.isSatellite && satBackdropBitmap != null && heroArtwork != null) {
            if (!isShowingSatBackdrop) setHeroArtwork(satBackdropBitmap, animate);
            isShowingSatBackdrop = true;
        } else if (isShowingSatBackdrop && currentWallpaperBitmap != null && heroArtwork != null) {
            setHeroArtwork(currentWallpaperBitmap, animate);
            isShowingSatBackdrop = false;
        }
    }

    private void setHeroArtwork(Bitmap bitmap, boolean animate) {
        heroArtwork.animate().cancel();
        heroArtwork.setImageBitmap(bitmap);
        heroArtwork.setAlpha(animate ? 0.6f : 1f);
        if (animate) heroArtwork.animate().alpha(1f).setDuration(280).start();
    }

    private void previewHero(App app) {
        if (pendingHeroPreview != null) handler.removeCallbacks(pendingHeroPreview);
        pendingHeroPreview = () -> {
            if (currentViewMode == VIEW_HOME) updateHero(app, true);
        };
        handler.postDelayed(pendingHeroPreview, 180);
    }

    private void updateHeroForMediaItem(MediaItem item) {
        if (item == null || heroCard == null) return;
        currentHeroApp = null;
        String badge = item.getSourceAppName() != null && !item.getSourceAppName().isEmpty()
                ? item.getSourceAppName().toUpperCase(Locale.getDefault()) : getString(R.string.service);
        heroBadge.setText("● " + badge);
        heroTitle.setText(item.getTitle() != null ? item.getTitle() : "");
        heroSubtitle.setText(item.getSynopsis() != null && !item.getSynopsis().isEmpty()
                ? item.getSynopsis() : item.getSubtitle());
        heroActionBtn.setText(MediaItem.TYPE_LIVE_TV.equals(item.getType()) ? getString(R.string.watch_live) : getString(R.string.open_app));
        heroActionBtn.setOnClickListener(v -> {
            if (item.getLaunchIntent() != null) launch(item.getLaunchIntent());
        });

        if ("sat_backdrop.jpg".equals(item.getBackdropAssetPath()) && satBackdropBitmap != null && heroArtwork != null) {
            heroArtwork.setImageBitmap(satBackdropBitmap);
            isShowingSatBackdrop = true;
        } else if (isShowingSatBackdrop && currentWallpaperBitmap != null && heroArtwork != null) {
            heroArtwork.setImageBitmap(currentWallpaperBitmap);
            isShowingSatBackdrop = false;
        }
    }

    private String getAppBadge(App app) {
        if (app.isSatellite) return getString(R.string.satellite_badge);
        if (app.isPlus) return getString(R.string.customize_badge);
        if (app.pkg != null) {
            if (app.pkg.contains("youtube") || app.pkg.contains("tizentube")) return getString(R.string.video_badge);
            if (app.pkg.contains("novastb")) return getString(R.string.nova_badge);
            if (app.pkg.contains("datesplus")) return getString(R.string.dates_badge);
            if (app.pkg.contains("marvel")) return getString(R.string.marvel_badge);
            if (app.pkg.contains("stremio") || app.pkg.contains("kodi")) return getString(R.string.media_badge);
            if (app.pkg.contains("vending")) return getString(R.string.play_badge);
        }
        return getString(R.string.apps_badge);
    }

    private String getAppSubtitle(App app) {
        if (app.isSatellite) return getString(R.string.satellite_subtitle);
        if (app.isPlus) return getString(R.string.customize_subtitle);
        if (app.pkg != null) {
            if (app.pkg.contains("youtube") || app.pkg.contains("tizentube")) return getString(R.string.video_subtitle);
            if (app.pkg.contains("novastb")) return getString(R.string.nova_subtitle);
            if (app.pkg.contains("datesplus")) return getString(R.string.dates_subtitle);
            if (app.pkg.contains("marvel")) return getString(R.string.marvel_subtitle);
            if (app.pkg.contains("stremio")) return getString(R.string.stremio_subtitle);
            if (app.pkg.contains("kodi")) return getString(R.string.kodi_subtitle);
            if (app.pkg.contains("vending")) return getString(R.string.play_subtitle);
        }
        return getString(R.string.open_prefix) + (app.label != null ? app.label : getString(R.string.your_app)) + ".";
    }

    private String getAppActionText(App app) {
        if (app.isSatellite) return getString(R.string.watch_live);
        if (app.isPlus) return getString(R.string.add_favorite);
        return getString(R.string.open_app);
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
        if (satellite != null && LauncherSettings.isServiceEnabled(this, satellite.pkg)) dock.add(satellite);
        List<String> currentDock = getDockList();
        boolean youtubeAdded = false;
        String preferredYt = LauncherSettings.getPreferredYouTubeSetting(this);
        for (String p : currentDock) {
            if (!LauncherSettings.isServiceEnabled(this, p)) continue;
            if (p.equals("com.google.android.youtube.tv")
                    || p.equals("io.gh.reisxd.tizentube.fortaleza")
                    || p.equals("com.google.android.youtube")) {
                if (youtubeAdded) continue;
                String ytPkgToUse = p;
                if (!LauncherSettings.YT_AUTO.equals(preferredYt)) {
                    if (byPkg.containsKey(preferredYt) && LauncherSettings.isServiceEnabled(this, preferredYt)) ytPkgToUse = preferredYt;
                }
                if (byPkg.containsKey(ytPkgToUse)) {
                    dock.add(byPkg.remove(ytPkgToUse));
                    youtubeAdded = true;
                } else if (byPkg.containsKey(p)) {
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
            for (App app : rest) {
                if (dock.size() >= 6) break;
                if (LauncherSettings.isServiceEnabled(this, app.pkg)) dock.add(app);
            }
        }

        dockRow.removeAllViews();
        grid.removeAllViews();
        dockCards.clear();
        gridCards.clear();

        for (int i = 0; i < dock.size(); i++) {
            View tile = tile(dock.get(i), true);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(132), -2);
            p.setMargins(0, 0, dp(12), 0);
            dockRow.addView(tile, p);
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
        buildDiscoverRows(satellite, allAppsForChannels);
        buildSettingsView(allAppsForChannels, satellite);
        showAppCatalogVisibilityOnly();
        wireFocus();

        if (!dock.isEmpty()) {
            title.setText(getString(R.string.favorites));
            App featured = dock.get(0);
            for (App candidate : dock) {
                if (candidate.pkg.equals(lastDockPackage)) { featured = candidate; break; }
            }
            updateHero(featured, false);
        }
        View last = lastFocusedId == View.NO_ID ? null : findViewById(lastFocusedId);
        if (last != null) last.requestFocus();
        else if (!dockCards.isEmpty()) {
            View target = dockCards.get(0);
            for (View card : dockCards) {
                if (((App) card.getTag()).pkg.equals(lastDockPackage)) { target = card; break; }
            }
            target.requestFocus();
        }
    }

    /**
     * Offline-first channels: each item is an installed app or the observed DVB activity.
     * Deliberately never invents shows, movie posters, recommendations, or watch progress.
     */
    /**
     * Only show installed apps not already pinned to Favorites.
     * The Home screen never repeats the same app in several rows.
     */
    /** Group unpinned apps without displaying a pointless one-card category row. */
    private void buildChannelRows(Map<String, App> apps, App satellite, Set<String> favorites) {
        channelRows.clear();
        browseAppsButton = null;
        channelContainer.removeAllViews();
        Set<String> used = new HashSet<>();
        List<App> live = categoryApps(new String[]{
                "com.logicyel.novastb", "com.datesplus.iptv"
        }, apps, favorites, used);
        List<App> cinema = categoryApps(new String[]{
                "com.marvelactivecode.marvel", "com.netflix.mediaclient", "com.stremio.one"
        }, apps, favorites, used);
        List<App> streaming = categoryApps(new String[]{
                "io.gh.reisxd.tizentube.fortaleza", "com.google.android.youtube.tv",
                "com.google.android.youtube", "org.xbmc.kodi", "org.jellyfin.androidtv"
        }, apps, favorites, used);
        List<App> media = new ArrayList<>();
        if (LauncherSettings.isHomeRowVisible(this, LauncherSettings.ROW_LIVE_TV)) {
            if (live.size() >= 2) addChannelRow(getString(R.string.live_iptv), live);
            else media.addAll(live);
        }
        if (LauncherSettings.isHomeRowVisible(this, LauncherSettings.ROW_CINEMA)) {
            if (cinema.size() >= 2) addChannelRow(getString(R.string.cinema_on_demand), cinema);
            else media.addAll(cinema);
        }
        if (LauncherSettings.isHomeRowVisible(this, LauncherSettings.ROW_STREAMING)) {
            media.addAll(streaming);
        }
        if (!media.isEmpty()) addChannelRow(getString(R.string.media_video), media);
        List<App> extras = categoryApps(new String[]{
                "com.tosmart.filemanagerpro", "com.excellence.appstore",
                "com.android.vending", "com.android.chrome"
        }, apps, favorites, used);
        if (LauncherSettings.isHomeRowVisible(this, LauncherSettings.ROW_UTILITIES)) {
            if (extras.size() >= 2) addChannelRow(getString(R.string.more_explore), extras);
        }

        TextView browse = text(13, "sans-serif-medium", 0xFFBBDFFF);
        browse.setText(getString(R.string.browse_apps));
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

    private List<App> categoryApps(String[] packages, Map<String, App> apps,
                                   Set<String> favorites, Set<String> claimed) {
        List<App> found = new ArrayList<>();
        for (String pkg : packages) {
            if (!LauncherSettings.isServiceEnabled(this, pkg)) continue;
            App app = apps.get(pkg);
            if (app != null && !favorites.contains(pkg) && claimed.add(pkg)) found.add(app);
        }
        return found;
    }

    private void addChannelRow(String heading, List<App> visibleApps) {
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
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(184), dp(128));
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
        card.setContentDescription(app.label);
        card.setPadding(dp(2), dp(2), dp(2), dp(2));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x6610192B);
        bg.setCornerRadius(dp(6));
        bg.setStroke(dp(1), 0x18FFFFFF);
        card.setBackground(bg);

        ImageView image = new ImageView(this);
        image.setImageDrawable(app.art);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setPadding(app.isBanner ? 0 : dp(13), app.isBanner ? 0 : dp(10),
                app.isBanner ? 0 : dp(13), app.isBanner ? 0 : dp(10));
        card.addView(image, new LinearLayout.LayoutParams(-1, dp(98)));
        TextView name = text(11, "sans-serif-medium", Color.WHITE);
        name.setText(app.label);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        name.setPadding(dp(9), 0, dp(9), 0);
        card.addView(name, new LinearLayout.LayoutParams(-1, dp(27)));

        card.setOnClickListener(v -> launchApp(app));
        card.setOnFocusChangeListener((v, focused) -> {
            bg.setStroke(dp(focused ? 2 : 1), focused ? 0xFFFFFFFF : 0x23FFFFFF);
            v.animate().scaleX(focused ? 1.045f : 1f).scaleY(focused ? 1.045f : 1f)
                    .setDuration(155).start();
            if (focused) {
                lastFocusedId = v.getId();
                revealInRow(horizontal, v);
                int y = channelContainer.getTop() + section.getTop() - dp(155);
                scroll.smoothScrollTo(0, Math.max(0, y));
                previewHero(app);
            }
        });
        return card;
    }

    private void buildDiscoverRows(App satellite, Map<String, App> allApps) {
        discoverRows.clear();
        discoverContainer.removeAllViews();

        List<MediaShelf> shelves = ContentManager.buildDiscoverShelves(this, satellite, allApps);
        for (MediaShelf shelf : shelves) {
            LinearLayout section = new LinearLayout(this);
            section.setOrientation(LinearLayout.VERTICAL);
            section.setClipChildren(false);
            section.setClipToPadding(false);

            TextView label = text(14, "sans-serif-medium", 0xFFE8E8F5);
            label.setText(shelf.getTitle());
            section.addView(label, llp(-1, -2, 0, dp(4), 0, dp(2)));


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
            for (MediaItem item : shelf.getItems()) {
                View card = discoverTile(item, horizontal, section);
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(184), dp(128));
                cp.setMargins(dp(4), dp(4), dp(12), dp(6));
                strip.addView(card, cp);
                focusRow.add(card);
            }

            section.addView(horizontal, new LinearLayout.LayoutParams(-1, -2));
            discoverContainer.addView(section, llp(-1, -2, 0, 0, 0, dp(18)));
            discoverRows.add(focusRow);
        }
    }

    private View discoverTile(MediaItem item, HorizontalScrollView horizontal, View section) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setFocusable(true);
        card.setClickable(true);
        card.setId(View.generateViewId());
        card.setPadding(dp(2), dp(2), dp(2), dp(2));
        card.setTag(item);
        card.setContentDescription(item.getTitle());

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x6610192B);
        bg.setCornerRadius(dp(6));
        bg.setStroke(dp(1), 0x28FFFFFF);
        card.setBackground(bg);

        ImageView image = new ImageView(this);
        if (item.getIconDrawable() != null) {
            image.setImageDrawable(item.getIconDrawable());
        } else if (item.getIconResId() != 0) {
            image.setImageResource(item.getIconResId());
        }
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setPadding(dp(5), dp(5), dp(5), dp(5));
        card.addView(image, new LinearLayout.LayoutParams(-1, dp(98)));

        TextView name = text(12, "sans-serif-medium", Color.WHITE);
        name.setText(item.getTitle());
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        name.setPadding(dp(9), 0, dp(9), 0);
        card.addView(name, new LinearLayout.LayoutParams(-1, dp(22)));


        card.setOnClickListener(v -> {
            if (item.getLaunchIntent() != null) launch(item.getLaunchIntent());
        });
        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    if (item.getLaunchIntent() != null) launch(item.getLaunchIntent());
                    return true;
                }
            }
            return false;
        });

        card.setOnFocusChangeListener((v, focused) -> {
            bg.setStroke(dp(focused ? 2 : 1), focused ? 0xFFFFFFFF : 0x28FFFFFF);
            bg.setColor(focused ? 0xDD162A48 : 0x9910192B);
            v.animate().scaleX(focused ? 1.05f : 1f).scaleY(focused ? 1.05f : 1f)
                    .setDuration(155).start();
            if (focused) {
                lastFocusedId = v.getId();
                revealInRow(horizontal, v);
                int y = discoverContainer.getTop() + section.getTop() - dp(155);
                scroll.smoothScrollTo(0, Math.max(0, y));
                updateHeroForMediaItem(item);
                if (heroActionBtn != null) heroActionBtn.setNextFocusDownId(v.getId());
            }
        });
        return card;
    }

    /** Keep view visibility in sync after apps are rescanned. */
    private void showAppCatalogVisibilityOnly() {
        if (favoritesView != null) favoritesView.setVisibility(currentViewMode == VIEW_HOME ? View.VISIBLE : View.GONE);
        if (channelContainer != null) channelContainer.setVisibility(currentViewMode == VIEW_HOME ? View.VISIBLE : View.GONE);
        if (discoverContainer != null) discoverContainer.setVisibility(currentViewMode == VIEW_DISCOVER ? View.VISIBLE : View.GONE);
        if (grid != null) grid.setVisibility(currentViewMode == VIEW_APPS ? View.VISIBLE : View.GONE);
        if (appsLabel != null) appsLabel.setVisibility(currentViewMode == VIEW_APPS ? View.VISIBLE : View.GONE);
        if (settingsContainer != null) settingsContainer.setVisibility(currentViewMode == VIEW_SETTINGS ? View.VISIBLE : View.GONE);
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
            app.label = getString(R.string.live_satellite);
            app.category = Intent.CATEGORY_LAUNCHER;
            app.component = component;
            app.isSatellite = true;
            app.isBanner = false;
            app.art = getDrawable(R.drawable.ic_satellite);
            app.color = 0xFF287EB2;
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
        List<String> list = new ArrayList<>();
        if (saved != null) {
            for (String s : saved.split(",")) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) list.add(trimmed);
            }
        } else {
            Collections.addAll(list, getResources().getStringArray(R.array.dock_apps));
        }
        // Reorder/remove the displayed YouTube client, not a hidden package alias.
        String preferred = LauncherSettings.getPreferredYouTubeSetting(this);
        String youtube = null;
        for (String candidate : new String[]{preferred, LauncherSettings.YT_SMARTTUBE,
                LauncherSettings.YT_GOOGLE_TV, LauncherSettings.YT_STANDARD}) {
            if (!LauncherSettings.YT_AUTO.equals(candidate) && isInstalled(candidate)
                    && LauncherSettings.isServiceEnabled(this, candidate)) { youtube = candidate; break; }
        }
        Set<String> unique = new java.util.LinkedHashSet<>();
        for (String pkg : list) {
            boolean isYoutube = LauncherSettings.YT_SMARTTUBE.equals(pkg)
                    || LauncherSettings.YT_GOOGLE_TV.equals(pkg) || LauncherSettings.YT_STANDARD.equals(pkg);
            unique.add(isYoutube && youtube != null ? youtube : pkg);
        }
        return new ArrayList<>(unique);
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

    /** Persistent remote-friendly ordering, without modifying any installed application. */
    private boolean moveFavorite(String pkg, int direction) {
        List<String> list = getDockList();
        int current = list.indexOf(pkg);
        int target = current + direction;
        if (current < 0 || target < 0 || target >= list.size()) return false;
        Collections.swap(list, current, target);
        saveDockList(list);
        buildApps();
        // Restore focus to the moved favorite instead of resetting to the first tile.
        for (View candidate : dockCards) {
            Object tag = candidate.getTag();
            if (tag instanceof App && pkg.equals(((App) tag).pkg)) {
                candidate.requestFocus();
                break;
            }
        }
        return true;
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
        app.label = getString(R.string.add_to_dock);
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
            Toast.makeText(this, getString(R.string.all_pinned), Toast.LENGTH_SHORT).show();
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
        header.setText(getString(R.string.add_app));
        container.addView(header);

        TextView sub = text(12, "sans-serif", 0x99FFFFFF);
        sub.setText(getString(R.string.add_app_hint));
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
                Toast.makeText(MainActivity.this, getString(R.string.added_prefix) + app.label + getString(R.string.added_suffix), Toast.LENGTH_SHORT).show();
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

    /** Remote-friendly favorite controls: rearrange the dock without editing config.xml. */
    private void showRemoveDialog(App app) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0xD9000712);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(26), dp(22), dp(26), dp(22));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF142238);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), 0x55FFFFFF);
        box.setBackground(bg);

        TextView heading = text(20, "sans-serif-medium", Color.WHITE);
        heading.setText(getString(R.string.customize_title, android.text.BidiFormatter.getInstance(true).unicodeWrap(app.label)));
        box.addView(heading, llp(-1, -2, 0, 0, 0, dp(6)));

        TextView hint = text(12, "sans-serif", 0xCCFFFFFF);
        hint.setText(getString(R.string.customize_hint));
        hint.setMaxLines(2);
        box.addView(hint, llp(-1, -2, 0, 0, 0, dp(17)));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView left = favoriteDialogButton(getString(R.string.move_left));
        TextView right = favoriteDialogButton(getString(R.string.move_right));
        TextView remove = favoriteDialogButton(getString(R.string.remove));
        TextView cancel = favoriteDialogButton(getString(R.string.cancel));

        int current = getDockList().indexOf(app.pkg);
        int count = getDockList().size();
        left.setEnabled(current >= 0 && current < count - 1);
        right.setEnabled(current > 0);
        left.setAlpha(left.isEnabled() ? 1f : 0.35f);
        right.setAlpha(right.isEnabled() ? 1f : 0.35f);

        left.setOnClickListener(v -> {
            dialog.dismiss();
            if (moveFavorite(app.pkg, 1))
                Toast.makeText(this, getString(R.string.moved_left), Toast.LENGTH_SHORT).show();
        });
        right.setOnClickListener(v -> {
            dialog.dismiss();
            if (moveFavorite(app.pkg, -1))
                Toast.makeText(this, getString(R.string.moved_right), Toast.LENGTH_SHORT).show();
        });
        remove.setOnClickListener(v -> {
            dialog.dismiss();
            removeAppFromDock(app.pkg);
            Toast.makeText(this, getString(R.string.removed), Toast.LENGTH_SHORT).show();
        });
        cancel.setOnClickListener(v -> dialog.dismiss());
        for (TextView button : new TextView[]{left, right, remove, cancel}) {
            actions.addView(button, new LinearLayout.LayoutParams(0, dp(42), 1f));
        }
        box.addView(actions);
        overlay.addView(box, new FrameLayout.LayoutParams(dp(590), -2, Gravity.CENTER));
        dialog.setContentView(overlay);
        dialog.show();
        if (left.isEnabled()) left.requestFocus();
        else if (right.isEnabled()) right.requestFocus();
        else remove.requestFocus();
    }

    private TextView favoriteDialogButton(String name) {
        TextView button = text(12, "sans-serif-medium", Color.WHITE);
        button.setText(name);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(5), 0, dp(5), 0);
        button.setFocusable(true);
        button.setClickable(true);
        button.setId(View.generateViewId());
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(11));
        bg.setColor(0x263B6287);
        bg.setStroke(dp(1), 0x44FFFFFF);
        button.setBackground(bg);
        button.setOnFocusChangeListener((v, focus) -> {
            bg.setColor(focus ? 0xFFE9F2FF : 0x263B6287);
            button.setTextColor(focus ? 0xFF0C1A2C : Color.WHITE);
        });
        return button;
    }

    /** Open the real DVB alias, then use stock Glide as a recovery fallback. */
    private void openSatelliteTv() {
        Intent satellite = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(new ComponentName(satelliteTvPkg, satelliteTvActivity))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (!launch(satellite)) {
            android.widget.Toast.makeText(this, getString(R.string.satellite_fallback),
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
        card.setContentDescription(app.label);
        card.setElevation(dp(2));
        card.setClipToOutline(true);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(6));
            }
        });
        GradientDrawable bg = new GradientDrawable();
        bg.setOrientation(GradientDrawable.Orientation.TL_BR);
        bg.setColors(new int[]{darken(app.color, 0.65f), darken(app.color, 0.25f)});
        card.setBackground(bg);

        ImageView img = new ImageView(this);
        img.setImageDrawable(app.art);
        if (app.isBanner) {
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            card.addView(img, new FrameLayout.LayoutParams(-1, -1));
        } else {
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            card.addView(img, new FrameLayout.LayoutParams(-1, -1));
            img.setPadding(dp(20), dp(12), dp(20), dp(12));
        }
        if (app.isSatellite) {
            img.setPadding(0, 0, 0, 0);
            img.setLayoutParams(lp(dp(35), dp(35), Gravity.CENTER_VERTICAL | Gravity.START, dp(13), 0, 0, 0));
            TextView liveLabel = text(17, "sans-serif-medium", Color.WHITE);
            liveLabel.setText(getString(R.string.live_tv));
            liveLabel.setGravity(Gravity.CENTER_VERTICAL);
            card.addView(liveLabel, lp(-1, -1, Gravity.FILL, dp(55), 0, dp(5), 0));
        }

        // Focus ring and sheen, drawn above the art.
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(dp(6));
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
        }
        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_MENU && inDock && !app.isSatellite && !app.isPlus) {
                    showRemoveDialog(app);
                    return true;
                }
            }
            return false;
        });
        card.setOnFocusChangeListener((v, has) -> {
            float s = has ? 1.05f : 1f;
            v.animate().scaleX(s).scaleY(s).translationZ(has ? dp(6) : 0)
                    .setInterpolator(new DecelerateInterpolator())
                    .setDuration(160).start();
            shine.animate().alpha(has ? 1f : 0f).setDuration(160).start();
            label.animate().alpha(has ? 1f : 0f).translationY(has ? dp(4) : 0).setDuration(200).start();
            if (!has) return;
            lastFocusedId = v.getId();
            if (inDock) {
                lastDockPackage = app.pkg;
                title.setText(getString(R.string.favorites));
                title.animate().alpha(1f).setDuration(200).start();
                previewHero(app);
                revealInRow(dockScroll, box);
                if (heroActionBtn != null) heroActionBtn.setNextFocusDownId(v.getId());
                for (View tab : pills) tab.setNextFocusDownId(v.getId());
                // Do not tint entire background when favorite changes.
                scroll.smoothScrollTo(0, 0);
            } else {
                // Category focus keeps the dark content surface unchanged.
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

        // Pills (Top Header Tabs)
        for (int i = 0; i < pills.size(); i++) {
            View p = pills.get(i);
            p.setNextFocusRightId(i == 0 ? p.getId() : pills.get(i - 1).getId());
            p.setNextFocusLeftId(i == pills.size() - 1 ? p.getId() : pills.get(i + 1).getId());
            p.setNextFocusUpId(p.getId());
            if (currentViewMode == VIEW_APPS && g > 0) {
                    p.setNextFocusDownId(gridCards.get(0).getId());
            } else if (currentViewMode == VIEW_DISCOVER) {
                if (heroBtnId != View.NO_ID) {
                    p.setNextFocusDownId(heroBtnId);
                } else if (!discoverRows.isEmpty() && !discoverRows.get(0).isEmpty()) {
                    p.setNextFocusDownId(discoverRows.get(0).get(0).getId());
                } else {
                    p.setNextFocusDownId(p.getId());
                }
            } else if (currentViewMode == VIEW_SETTINGS) {
                if (!settingsRows.isEmpty() && !settingsRows.get(0).isEmpty()) {
                    p.setNextFocusDownId(settingsRows.get(0).get(0).getId());
                } else {
                    p.setNextFocusDownId(p.getId());
                }
            } else {
                p.setNextFocusDownId(d > 0 ? lastDockFocusId() :
                        heroBtnId != View.NO_ID ? heroBtnId : p.getId());
            }
        }

        // Hero Action Button
        if (heroActionBtn != null) {
            heroActionBtn.setNextFocusUpId(topId != View.NO_ID ? topId : heroBtnId);
            heroActionBtn.setNextFocusLeftId(heroBtnId);
            heroActionBtn.setNextFocusRightId(heroBtnId);
            if (currentViewMode == VIEW_DISCOVER) {
                int firstDiscoverId = (!discoverRows.isEmpty() && !discoverRows.get(0).isEmpty())
                        ? discoverRows.get(0).get(0).getId() : heroBtnId;
                heroActionBtn.setNextFocusDownId(firstDiscoverId);
            } else {
                heroActionBtn.setNextFocusDownId(d > 0 ? lastDockFocusId() :
                        firstChannelId != View.NO_ID ? firstChannelId : heroBtnId);
            }
        }

        if (currentViewMode == VIEW_HOME) {
            for (int i = 0; i < d; i++) {
                View c = dockCards.get(i);
                c.setNextFocusUpId(heroBtnId != View.NO_ID ? heroBtnId : topId);
                c.setNextFocusRightId(i == 0 ? c.getId() : dockCards.get(i - 1).getId());
                c.setNextFocusLeftId(i == d - 1 ? c.getId() : dockCards.get(i + 1).getId());
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
                    c.setNextFocusRightId(i == 0 ? c.getId() : current.get(i - 1).getId());
                    c.setNextFocusLeftId(i == current.size() - 1 ? c.getId() : current.get(i + 1).getId());
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
        } else if (currentViewMode == VIEW_DISCOVER) {
            int lastDiscoverRow = discoverRows.size() - 1;
            for (int row = 0; row < discoverRows.size(); row++) {
                List<View> current = discoverRows.get(row);
                List<View> above = row == 0 ? Collections.<View>emptyList() : discoverRows.get(row - 1);
                List<View> below = row == lastDiscoverRow ? Collections.<View>emptyList() : discoverRows.get(row + 1);
                for (int i = 0; i < current.size(); i++) {
                    View c = current.get(i);
                    c.setNextFocusRightId(i == 0 ? c.getId() : current.get(i - 1).getId());
                    c.setNextFocusLeftId(i == current.size() - 1 ? c.getId() : current.get(i + 1).getId());
                    c.setNextFocusUpId(above.isEmpty() ? (heroBtnId != View.NO_ID ? heroBtnId : topId) :
                            above.get(Math.min(i, above.size() - 1)).getId());
                    c.setNextFocusDownId(below.isEmpty() ? c.getId() :
                            below.get(Math.min(i, below.size() - 1)).getId());
                }
            }
        } else if (currentViewMode == VIEW_APPS) {
            int lastRow = (g - 1) / cols;
            for (int j = 0; j < g; j++) {
                View c = gridCards.get(j);
                int r = j / cols, col = j % cols;
                c.setNextFocusUpId(r == 0 ? (topId != View.NO_ID ? topId : c.getId()) :
                        gridCards.get(j - cols).getId());
                c.setNextFocusDownId(j + cols < g ? gridCards.get(j + cols).getId() :
                        r < lastRow ? gridCards.get(g - 1).getId() : c.getId());
                c.setNextFocusRightId(col == 0 ? c.getId() : gridCards.get(j - 1).getId());
                c.setNextFocusLeftId(col == cols - 1 || j == g - 1 ? c.getId() : gridCards.get(j + 1).getId());
            }
        } else if (currentViewMode == VIEW_SETTINGS) {
            int lastSettingsRow = settingsRows.size() - 1;
            for (int row = 0; row < settingsRows.size(); row++) {
                List<View> current = settingsRows.get(row);
                List<View> above = row == 0 ? Collections.<View>emptyList() : settingsRows.get(row - 1);
                List<View> below = row == lastSettingsRow ? Collections.<View>emptyList() : settingsRows.get(row + 1);
                for (int i = 0; i < current.size(); i++) {
                    View c = current.get(i);
                    c.setNextFocusRightId(i == 0 ? c.getId() : current.get(i - 1).getId());
                    c.setNextFocusLeftId(i == current.size() - 1 ? c.getId() : current.get(i + 1).getId());
                    c.setNextFocusUpId(above.isEmpty() ? (topId != View.NO_ID ? topId : c.getId()) :
                            above.get(Math.min(i, above.size() - 1)).getId());
                    c.setNextFocusDownId(below.isEmpty() ? c.getId() :
                            below.get(Math.min(i, below.size() - 1)).getId());
                }
            }
        }
    }

    private int lastDockFocusId() {
        for (View card : dockCards) {
            if (((App) card.getTag()).pkg.equals(lastDockPackage)) return card.getId();
        }
        return dockCards.isEmpty() ? View.NO_ID : dockCards.get(0).getId();
    }

    private void revealInRow(HorizontalScrollView row, View child) {
        int left = child.getLeft(), right = child.getRight();
        int visibleLeft = row.getScrollX(), visibleRight = visibleLeft + row.getWidth();
        if (left < visibleLeft + dp(10)) row.smoothScrollTo(Math.max(0, left - dp(10)), 0);
        else if (right > visibleRight - dp(10)) row.smoothScrollTo(right - row.getWidth() + dp(10), 0);
        if (currentViewMode == VIEW_SETTINGS) revealSettingsCard(child);
    }

    private void revealSettingsCard(View child) {
        android.graphics.Rect bounds = new android.graphics.Rect();
        child.getDrawingRect(bounds);
        content.offsetDescendantRectToMyCoords(child, bounds);
        if (bounds.top < scroll.getScrollY() + dp(85) || bounds.bottom > scroll.getScrollY() + scroll.getHeight() - dp(20)) {
            scroll.smoothScrollTo(0, Math.max(0, bounds.top - dp(100)));
        }
    }

    @Override public void onUserInteraction() {
        super.onUserInteraction();
        resetIdleTimer();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) resetIdleTimer();
        else handler.removeCallbacks(idleScreensaver);
    }

    private void resetIdleTimer() {
        handler.removeCallbacks(idleScreensaver);
        int minutes = getPreferences(MODE_PRIVATE).getInt("screensaver_minutes", 5);
        if (resumed && screensaverView == null && minutes > 0) {
            handler.postDelayed(idleScreensaver, minutes * 60000L);
        }
    }

    private void showScreensaver() {
        if (!resumed || screensaverView != null) return;
        if (pendingHeroPreview != null) handler.removeCallbacks(pendingHeroPreview);
        screensaverView = new PhotoScreensaverView(this);
        screensaverView.setOnClickListener(v -> dismissScreensaver());
        rootView.addView(screensaverView, new FrameLayout.LayoutParams(-1, -1));
        screensaverView.setElevation(dp(30));
        handler.removeCallbacks(idleScreensaver);
        handler.removeCallbacks(rotateWallpaper);
    }

    private void dismissScreensaver() {
        if (screensaverView == null) return;
        rootView.removeView(screensaverView);
        screensaverView = null;
        if (resumed) scheduleWallpaper(WALLPAPER_INTERVAL_MS);
        resetIdleTimer();
    }

    private void openHomeSettings() {
        if (!launch(new Intent(Settings.ACTION_HOME_SETTINGS).setPackage("com.android.settings"))
                && !launch(new Intent(Settings.ACTION_HOME_SETTINGS))) launch(new Intent(Settings.ACTION_SETTINGS));
    }

    private void addScreensaverSettings() {
        TextView heading = text(14, "sans-serif-medium", Color.WHITE);
        heading.setText(getString(R.string.screensaver_name));
        settingsContainer.addView(heading, llp(-1, -2, 0, 0, 0, dp(10)));
        LinearLayout options = new LinearLayout(this);
        List<View> cards = new ArrayList<>();
        for (int minutes : new int[]{0, 2, 5, 10}) {
            String label = minutes == 0 ? getString(R.string.screensaver_off)
                    : minutes == 2 ? getString(R.string.screensaver_two_minutes) : getString(R.string.screensaver_delay, minutes);
            View card = buildSystemActionCard(label, "", v -> {
                getPreferences(MODE_PRIVATE).edit().putInt("screensaver_minutes", minutes).apply();
                for (View option : cards) {
                    ((TextView) ((LinearLayout) option).getChildAt(1)).setText(
                            ((Integer) option.getTag()) == minutes ? getString(R.string.selected) : getString(R.string.select_option));
                }
                resetIdleTimer();
            });
            card.setTag(minutes);
            ((TextView) ((LinearLayout) card).getChildAt(1)).setText(
                    getPreferences(MODE_PRIVATE).getInt("screensaver_minutes", 5) == minutes ? getString(R.string.selected) : getString(R.string.select_option));
            options.addView(card, llp(dp(190), dp(76), 0, 0, dp(12), dp(4)));
            cards.add(card);
        }
        settingsContainer.addView(options, llp(-1, -2, 0, 0, 0, dp(24)));
        settingsRows.add(cards);
        LinearLayout actions = new LinearLayout(this);
        List<View> actionCards = new ArrayList<>();
        actionCards.add(buildSystemActionCard(getString(R.string.screensaver_preview), getString(R.string.screensaver_wake), v -> showScreensaver()));
        actionCards.add(buildSystemActionCard(getString(R.string.system_screensaver), getString(R.string.system_screensaver_hint), v -> {
            if (!launch(new Intent(Settings.ACTION_DREAM_SETTINGS))) launch(new Intent(Settings.ACTION_SETTINGS));
        }));
        actionCards.add(buildSystemActionCard(getString(R.string.home_chooser), getString(R.string.home_chooser_hint), v -> openHomeSettings()));
        for (View card : actionCards) actions.addView(card, llp(dp(250), dp(88), 0, 0, dp(12), dp(4)));
        settingsContainer.addView(actions, llp(-1, -2, 0, 0, 0, dp(26)));
        settingsRows.add(actionCards);
    }

    // ---------------------------------------------------------------- settings & preferences

    private void buildSettingsView(Map<String, App> allApps, App satellite) {
        settingsRows.clear();
        settingsContainer.removeAllViews();

        TextView title = text(22, "sans-serif-medium", 0xFFE5E9F5);
        title.setText(getString(R.string.settings_title));
        settingsContainer.addView(title, llp(-1, -2, 0, dp(12), 0, dp(4)));

        TextView subtitle = text(12, "sans-serif", 0x8CBADBEA);
        subtitle.setText(getString(R.string.settings_description));
        settingsContainer.addView(subtitle, llp(-1, -2, 0, 0, 0, dp(24)));

        // 1. Installed Services
        TextView secServices = text(13, "sans-serif-medium", 0xDBFFFFFF);
        secServices.setText(getString(R.string.services_channels));
        settingsContainer.addView(secServices, llp(-1, -2, 0, 0, 0, dp(10)));

        HorizontalScrollView hsvServices = new HorizontalScrollView(this);
        hsvServices.setHorizontalScrollBarEnabled(false);
        hsvServices.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout stripServices = new LinearLayout(this);
        stripServices.setOrientation(LinearLayout.HORIZONTAL);
        hsvServices.addView(stripServices, new HorizontalScrollView.LayoutParams(-2, -2));

        List<View> serviceCards = new ArrayList<>();
        if (satellite != null) {
            View satCard = buildServiceToggleCard("com.tosmart.glide", getString(R.string.starnet_live), satellite.art, hsvServices);
            stripServices.addView(satCard, llp(dp(156), dp(112), 0, 0, dp(12), dp(4)));
            serviceCards.add(satCard);
        }
        String[] targetPkgs = new String[]{
                "com.logicyel.novastb", "com.marvelactivecode.marvel", "com.datesplus.iptv",
                "io.gh.reisxd.tizentube.fortaleza", "com.google.android.youtube.tv",
                "org.xbmc.kodi", "org.jellyfin.androidtv", "com.stremio.one", "com.netflix.mediaclient"
        };
        for (String pkg : targetPkgs) {
            App a = allApps.get(pkg);
            if (a != null) {
                View card = buildServiceToggleCard(pkg, a.label != null ? a.label : pkg, a.art, hsvServices);
                stripServices.addView(card, llp(dp(156), dp(112), 0, 0, dp(12), dp(4)));
                serviceCards.add(card);
            }
        }
        settingsContainer.addView(hsvServices, llp(-1, -2, 0, 0, 0, dp(26)));
        if (!serviceCards.isEmpty()) settingsRows.add(serviceCards);

        // 2. Preferred YouTube Application
        TextView secYt = text(13, "sans-serif-medium", 0xDBFFFFFF);
        secYt.setText(getString(R.string.youtube_default));
        settingsContainer.addView(secYt, llp(-1, -2, 0, 0, 0, dp(10)));

        HorizontalScrollView hsvYt = new HorizontalScrollView(this);
        hsvYt.setHorizontalScrollBarEnabled(false);
        hsvYt.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout stripYt = new LinearLayout(this);
        stripYt.setOrientation(LinearLayout.HORIZONTAL);
        hsvYt.addView(stripYt, new HorizontalScrollView.LayoutParams(-2, -2));

        List<View> ytCards = new ArrayList<>();
        View cSmart = buildYouTubeOptionCard(LauncherSettings.YT_SMARTTUBE, getString(R.string.smarttube), getString(R.string.smarttube_description), hsvYt);
        View cGoogle = buildYouTubeOptionCard(LauncherSettings.YT_GOOGLE_TV, getString(R.string.youtube_tv), getString(R.string.youtube_description), hsvYt);
        View cAuto = buildYouTubeOptionCard(LauncherSettings.YT_AUTO, getString(R.string.auto_detect), getString(R.string.auto_description), hsvYt);
        stripYt.addView(cSmart, llp(dp(220), dp(72), 0, 0, dp(12), dp(4)));
        stripYt.addView(cGoogle, llp(dp(220), dp(72), 0, 0, dp(12), dp(4)));
        stripYt.addView(cAuto, llp(dp(220), dp(72), 0, 0, dp(12), dp(4)));
        ytCards.add(cSmart);
        ytCards.add(cGoogle);
        ytCards.add(cAuto);
        settingsContainer.addView(hsvYt, llp(-1, -2, 0, 0, 0, dp(26)));
        settingsRows.add(ytCards);

        // 3. Home Screen Rows
        TextView secRows = text(13, "sans-serif-medium", 0xDBFFFFFF);
        secRows.setText(getString(R.string.home_rows));
        settingsContainer.addView(secRows, llp(-1, -2, 0, 0, 0, dp(10)));

        HorizontalScrollView hsvRows = new HorizontalScrollView(this);
        hsvRows.setHorizontalScrollBarEnabled(false);
        hsvRows.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout stripRows = new LinearLayout(this);
        stripRows.setOrientation(LinearLayout.HORIZONTAL);
        hsvRows.addView(stripRows, new HorizontalScrollView.LayoutParams(-2, -2));

        List<View> rowCards = new ArrayList<>();
        View rLive = buildRowToggleCard(LauncherSettings.ROW_LIVE_TV, getString(R.string.live_broadcast), hsvRows);
        View rCinema = buildRowToggleCard(LauncherSettings.ROW_CINEMA, getString(R.string.cinema_vod), hsvRows);
        View rStream = buildRowToggleCard(LauncherSettings.ROW_STREAMING, getString(R.string.stream_media), hsvRows);
        View rTools = buildRowToggleCard(LauncherSettings.ROW_UTILITIES, getString(R.string.utilities), hsvRows);
        stripRows.addView(rLive, llp(dp(190), dp(76), 0, 0, dp(12), dp(4)));
        stripRows.addView(rCinema, llp(dp(190), dp(76), 0, 0, dp(12), dp(4)));
        stripRows.addView(rStream, llp(dp(190), dp(76), 0, 0, dp(12), dp(4)));
        stripRows.addView(rTools, llp(dp(190), dp(76), 0, 0, dp(12), dp(4)));
        rowCards.add(rLive);
        rowCards.add(rCinema);
        rowCards.add(rStream);
        rowCards.add(rTools);
        settingsContainer.addView(hsvRows, llp(-1, -2, 0, 0, 0, dp(26)));
        settingsRows.add(rowCards);
        addScreensaverSettings();

        // 4. System Shortcuts & Diagnostics
        TextView secSys = text(13, "sans-serif-medium", 0xDBFFFFFF);
        secSys.setText(getString(R.string.system_diagnostics));
        settingsContainer.addView(secSys, llp(-1, -2, 0, 0, 0, dp(10)));

        LinearLayout stripSys = new LinearLayout(this);
        stripSys.setOrientation(LinearLayout.HORIZONTAL);

        List<View> sysCards = new ArrayList<>();
        View btnSysSettings = buildSystemActionCard(getString(R.string.system_settings_button), getString(R.string.system_settings_hint), v -> launch(new Intent(Settings.ACTION_SETTINGS)));
        View btnWifiSettings = buildSystemActionCard(getString(R.string.wifi_settings_button), getString(R.string.wifi_settings_hint), v -> launch(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        View btnAudit = buildSystemActionCard(getString(R.string.audit_button), getString(R.string.audit_hint), v -> {
            TvProviderAuditor.AuditReport rep = TvProviderAuditor.runAudit(this);
            Toast.makeText(this, getString(R.string.audit_result) + rep.callingUid + getString(R.string.audit_checked) + rep.entries.size() + getString(R.string.audit_uris), Toast.LENGTH_LONG).show();
        });
        stripSys.addView(btnSysSettings, llp(dp(220), dp(72), 0, 0, dp(12), dp(4)));
        stripSys.addView(btnWifiSettings, llp(dp(220), dp(72), 0, 0, dp(12), dp(4)));
        stripSys.addView(btnAudit, llp(dp(220), dp(72), 0, 0, dp(12), dp(4)));
        sysCards.add(btnSysSettings);
        sysCards.add(btnWifiSettings);
        sysCards.add(btnAudit);
        settingsContainer.addView(stripSys, llp(-1, -2, 0, 0, 0, dp(40)));
        settingsRows.add(sysCards);
    }

    private View buildServiceToggleCard(String pkg, String label, Drawable icon, HorizontalScrollView hsv) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setFocusable(true);
        card.setClickable(true);
        card.setContentDescription(label);
        card.setId(View.generateViewId());
        card.setPadding(dp(6), dp(8), dp(6), dp(6));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x9910192B);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), 0x28FFFFFF);
        card.setBackground(bg);

        ImageView iv = new ImageView(this);
        if (icon != null) iv.setImageDrawable(icon);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        card.addView(iv, new LinearLayout.LayoutParams(dp(42), dp(42)));

        TextView title = text(11, "sans-serif-medium", Color.WHITE);
        title.setText(label);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER);
        card.addView(title, llp(-1, -2, 0, dp(4), 0, dp(4)));

        TextView badge = text(10, "sans-serif-medium", Color.WHITE);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(8), dp(2), dp(8), dp(2));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(dp(8));
        badge.setBackground(badgeBg);
        card.addView(badge, llp(-2, -2, 0, 0, 0, 0));

        Runnable updateBadge = () -> {
            boolean enabled = LauncherSettings.isServiceEnabled(this, pkg);
            badge.setText(enabled ? getString(R.string.enabled) : getString(R.string.hidden));
            badgeBg.setColor(enabled ? 0xFF1B5E20 : 0xFF37474F);
        };
        updateBadge.run();

        card.setOnClickListener(v -> {
            boolean nowEnabled = LauncherSettings.toggleService(this, pkg);
            updateBadge.run();
            Toast.makeText(this, label + (nowEnabled ? getString(R.string.enabled_suffix) : getString(R.string.hidden_suffix)), Toast.LENGTH_SHORT).show();
        });
        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    v.performClick();
                    return true;
                }
            }
            return false;
        });

        card.setOnFocusChangeListener((v, focused) -> {
            bg.setStroke(dp(focused ? 2 : 1), focused ? 0xFFFFFFFF : 0x28FFFFFF);
            bg.setColor(focused ? 0xDD162A48 : 0x9910192B);
            v.animate().scaleX(focused ? 1.05f : 1f).scaleY(focused ? 1.05f : 1f).setDuration(155).start();
            if (focused) {
                lastFocusedId = v.getId();
                revealInRow(hsv, v);
            }
        });
        return card;
    }

    private View buildYouTubeOptionCard(String value, String titleStr, String descStr, HorizontalScrollView hsv) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setFocusable(true);
        card.setClickable(true);
        card.setContentDescription(titleStr);
        card.setId(View.generateViewId());
        card.setPadding(dp(12), dp(10), dp(12), dp(8));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x9910192B);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), 0x28FFFFFF);
        card.setBackground(bg);

        TextView title = text(12, "sans-serif-medium", Color.WHITE);
        title.setText(titleStr);
        card.addView(title);

        TextView desc = text(10, "sans-serif", 0x8CBADBEA);
        desc.setText(descStr);
        card.addView(desc, llp(-1, -2, 0, dp(2), 0, dp(4)));

        TextView status = text(10, "sans-serif-medium", 0xFF64B5F6);
        card.addView(status);

        Runnable refresh = () -> {
            boolean active = value.equals(LauncherSettings.getPreferredYouTubeSetting(this));
            status.setText(active ? getString(R.string.selected) : getString(R.string.select_option));
            status.setTextColor(active ? 0xFF81D4FA : 0x66FFFFFF);
            bg.setStroke(dp(active ? 2 : 1), active ? 0xFF4FC3F7 : 0x28FFFFFF);
        };
        refresh.run();
        card.setTag(refresh);

        card.setOnClickListener(v -> {
            LauncherSettings.setPreferredYouTubeSetting(this, value);
            if (!settingsRows.isEmpty() && settingsRows.size() > 1) {
                for (View c : settingsRows.get(1)) {
                    if (c.getTag() instanceof Runnable) ((Runnable) c.getTag()).run();
                }
            }
            Toast.makeText(this, titleStr + getString(R.string.youtube_selected), Toast.LENGTH_SHORT).show();
        });
        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    v.performClick();
                    return true;
                }
            }
            return false;
        });

        card.setOnFocusChangeListener((v, focused) -> {
            boolean active = value.equals(LauncherSettings.getPreferredYouTubeSetting(this));
            bg.setStroke(dp(focused ? 2 : (active ? 2 : 1)), focused ? 0xFFFFFFFF : (active ? 0xFF4FC3F7 : 0x28FFFFFF));
            bg.setColor(focused ? 0xDD162A48 : 0x9910192B);
            v.animate().scaleX(focused ? 1.04f : 1f).scaleY(focused ? 1.04f : 1f).setDuration(155).start();
            if (focused) {
                lastFocusedId = v.getId();
                revealInRow(hsv, v);
            }
        });
        return card;
    }

    private View buildRowToggleCard(String rowId, String label, HorizontalScrollView hsv) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setFocusable(true);
        card.setClickable(true);
        card.setContentDescription(label);
        card.setId(View.generateViewId());
        card.setPadding(dp(12), dp(10), dp(12), dp(8));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x9910192B);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), 0x28FFFFFF);
        card.setBackground(bg);

        TextView title = text(12, "sans-serif-medium", Color.WHITE);
        title.setText(label);
        card.addView(title);

        TextView badge = text(10, "sans-serif-medium", Color.WHITE);
        badge.setPadding(dp(8), dp(2), dp(8), dp(2));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(dp(8));
        badge.setBackground(badgeBg);
        card.addView(badge, llp(-2, -2, 0, dp(6), 0, 0));

        Runnable updateBadge = () -> {
            boolean visible = LauncherSettings.isHomeRowVisible(this, rowId);
            badge.setText(visible ? getString(R.string.show_on_home) : getString(R.string.hidden));
            badgeBg.setColor(visible ? 0xFF0D47A1 : 0xFF37474F);
        };
        updateBadge.run();

        card.setOnClickListener(v -> {
            boolean nowVisible = LauncherSettings.toggleHomeRow(this, rowId);
            updateBadge.run();
            Toast.makeText(this, label + (nowVisible ? getString(R.string.show_home_suffix) : getString(R.string.hide_home_suffix)), Toast.LENGTH_SHORT).show();
        });
        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    v.performClick();
                    return true;
                }
            }
            return false;
        });

        card.setOnFocusChangeListener((v, focused) -> {
            bg.setStroke(dp(focused ? 2 : 1), focused ? 0xFFFFFFFF : 0x28FFFFFF);
            bg.setColor(focused ? 0xDD162A48 : 0x9910192B);
            v.animate().scaleX(focused ? 1.04f : 1f).scaleY(focused ? 1.04f : 1f).setDuration(155).start();
            if (focused) {
                lastFocusedId = v.getId();
                revealInRow(hsv, v);
            }
        });
        return card;
    }

    private View buildSystemActionCard(String titleStr, String descStr, View.OnClickListener click) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setFocusable(true);
        card.setClickable(true);
        card.setContentDescription(titleStr);
        card.setId(View.generateViewId());
        card.setPadding(dp(12), dp(10), dp(12), dp(8));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x9910192B);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), 0x28FFFFFF);
        card.setBackground(bg);

        TextView title = text(12, "sans-serif-medium", Color.WHITE);
        title.setText(titleStr);
        card.addView(title);

        TextView desc = text(10, "sans-serif", 0x8CBADBEA);
        desc.setText(descStr);
        card.addView(desc, llp(-1, -2, 0, dp(4), 0, 0));

        card.setOnClickListener(click);
        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    v.performClick();
                    return true;
                }
            }
            return false;
        });

        card.setOnFocusChangeListener((v, focused) -> {
            bg.setStroke(dp(focused ? 2 : 1), focused ? 0xFFFFFFFF : 0x28FFFFFF);
            bg.setColor(focused ? 0xDD162A48 : 0x9910192B);
            v.animate().scaleX(focused ? 1.04f : 1f).scaleY(focused ? 1.04f : 1f).setDuration(155).start();
            if (focused) {
                lastFocusedId = v.getId();
                revealSettingsCard(v);
            }
        });
        return card;
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

        PopupWindow pw = new PopupWindow(list, dp(250), -2, true);
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
            rows.add(inputRow(getString(R.string.live_satellite), this::openSatelliteTv));
        if (inputs.isEmpty() && isInstalled(inputPickerPkg)) rows.add(inputRow(getString(R.string.all_sources), () -> openPackage(inputPickerPkg)));
        rows.add(inputRow(getString(R.string.original_glide), this::openOriginalHome));
        rows.add(inputRow(getString(R.string.home_chooser), this::openHomeSettings));
        updateWifi();
        rows.add(inputRow(getString(R.string.wifi_prefix) + wifiStatus, () -> launch(new Intent(Settings.ACTION_WIFI_SETTINGS))));
        rows.add(inputRow(getString(R.string.wallpaper_change), () -> {
            isShowingSatBackdrop = false;
            nextWallpaper();
        }));
        rows.add(inputRow(getString(R.string.android_settings), () -> launch(new Intent(Settings.ACTION_SETTINGS))));
        for (TextView r : rows) {
            r.setOnClickListener(v -> { pw.dismiss(); ((Runnable) v.getTag()).run(); });
            list.addView(r, new LinearLayout.LayoutParams(-1, dp(44)));
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
        boolean is24 = android.text.format.DateFormat.is24HourFormat(this);
        clock.setText(new SimpleDateFormat(is24 ? "H:mm" : "h:mm", Locale.getDefault()).format(now.getTime()));
        ampm.setText(is24 ? "" : (h < 12 ? "AM" : "PM"));
    }

    private void updateWifi() {
        String name = getString(R.string.wifi);
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wm != null && wm.getConnectionInfo() != null) {
                String s = wm.getConnectionInfo().getSSID();
                if (s != null && !s.contains("unknown ssid")) name = s.replace("\"", "");
                if (wm.getConnectionInfo().getNetworkId() == -1) name = getString(R.string.offline);
            }
        } catch (SecurityException ignored) { }
        wifiStatus = name;
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
        if (wallpapers.length == 0) return; // Drawable fallback is already displayed.
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

    /** Update only the hero photograph; the Apps and shelves always keep an opaque background. */
    private void showWallpaper(Bitmap bm) {
        if (bm == null || isDestroyed() || heroArtwork == null) return;
        currentWallpaperBitmap = bm;
        if (!isShowingSatBackdrop) {
            heroArtwork.animate().cancel();
            heroArtwork.setImageBitmap(bm);
            heroArtwork.setAlpha(0.65f);
            heroArtwork.animate().alpha(1f).setDuration(firstWallpaper ? 400 : 900).start();
        }
        firstWallpaper = false;
    }

    private void loadSatBackdropAsync() {
        new Thread(() -> {
            try (java.io.InputStream in = getAssets().open("sat_backdrop.jpg")) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inPreferredConfig = Bitmap.Config.RGB_565;
                satBackdropBitmap = BitmapFactory.decodeStream(in, null, o);
                runOnUiThread(() -> {
                    if (!isDestroyed() && currentHeroApp != null && currentHeroApp.isSatellite) {
                        updateHero(currentHeroApp, false);
                    }
                });
            } catch (Exception ignored) {}
        }).start();
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
        // Toolbar is a separate root overlay, not a child of the scrolling hero.
        View header = headerView;
        if (header == null) return;
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
        t.setTypeface(family.contains("medium") ? arabicBold : arabicRegular);
        t.setGravity(Gravity.START);
        t.setTextColor(color);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static FrameLayout.LayoutParams lp(int w, int h, int gravity, int l, int t, int r, int b) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w, h, gravity);
        p.setMargins(l, t, r, b);
        p.setMarginStart(l);
        p.setMarginEnd(r);
        return p;
    }

    private static LinearLayout.LayoutParams llp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(l, t, r, b);
        p.setMarginStart(l);
        p.setMarginEnd(r);
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
        if (e.getKeyCode() == screensaverWakeKey) {
            if (e.getAction() == KeyEvent.ACTION_UP) screensaverWakeKey = -1;
            return true;
        }
        if (screensaverView != null) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                screensaverWakeKey = e.getKeyCode();
                dismissScreensaver();
            }
            return true;
        }
        if (e.getKeyCode() == KeyEvent.KEYCODE_MENU) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                View focused = getCurrentFocus();
                if (dockCards.contains(focused) && focused.getTag() instanceof App) {
                    App app = (App) focused.getTag();
                    if (!app.isSatellite && !app.isPlus) {
                        showRemoveDialog(app);
                        return true;
                    }
                }
                showInputs(inputsNavTab);
            }
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
