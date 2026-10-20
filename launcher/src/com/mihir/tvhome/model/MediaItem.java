package com.mihir.tvhome.model;

import android.content.Intent;
import android.graphics.drawable.Drawable;

/**
 * Verified media item model for StarNet Home 3.0.
 * Adheres strictly to the "Verified Content Only" policy:
 * Represents content confirmed to exist and be accessible on the device,
 * or an authentic application-level shortcut.
 */
public class MediaItem {

    public static final String TYPE_LIVE_TV = "live_tv";
    public static final String TYPE_MOVIE = "movie";
    public static final String TYPE_SERIES = "series";
    public static final String TYPE_APP = "app";
    public static final String TYPE_TOOL = "tool";

    public static final String PROVENANCE_DVB_HARDWARE = "DVB_HARDWARE";
    public static final String PROVENANCE_SYSTEM_TIF = "SYSTEM_TIF";
    public static final String PROVENANCE_APP_INTEGRATION = "APP_INTEGRATION";
    public static final String PROVENANCE_LOCAL_MEDIA = "LOCAL_MEDIA";

    private final String id;
    private final String type;
    private final String title;
    private final String subtitle;
    private final String synopsis;
    private final String sourcePackage;
    private final String sourceAppName;
    private final Intent launchIntent;
    private final float watchProgress; // -1 if not applicable, 0.0 to 1.0 if in progress
    private final boolean isVerifiedAvailable;
    private final String provenance;

    // Optional visual resources
    private Drawable iconDrawable;
    private int iconResId;
    private String backdropAssetPath;

    public MediaItem(Builder builder) {
        this.id = builder.id;
        this.type = builder.type;
        this.title = builder.title;
        this.subtitle = builder.subtitle;
        this.synopsis = builder.synopsis;
        this.sourcePackage = builder.sourcePackage;
        this.sourceAppName = builder.sourceAppName;
        this.launchIntent = builder.launchIntent;
        this.watchProgress = builder.watchProgress;
        this.isVerifiedAvailable = builder.isVerifiedAvailable;
        this.provenance = builder.provenance;
        this.iconDrawable = builder.iconDrawable;
        this.iconResId = builder.iconResId;
        this.backdropAssetPath = builder.backdropAssetPath;
    }

    public String getId() { return id; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public String getSynopsis() { return synopsis; }
    public String getSourcePackage() { return sourcePackage; }
    public String getSourceAppName() { return sourceAppName; }
    public Intent getLaunchIntent() { return launchIntent; }
    public float getWatchProgress() { return watchProgress; }
    public boolean isVerifiedAvailable() { return isVerifiedAvailable; }
    public String getProvenance() { return provenance; }
    public Drawable getIconDrawable() { return iconDrawable; }
    public int getIconResId() { return iconResId; }
    public String getBackdropAssetPath() { return backdropAssetPath; }

    public void setIconDrawable(Drawable drawable) { this.iconDrawable = drawable; }

    public static class Builder {
        private String id;
        private String type = TYPE_APP;
        private String title;
        private String subtitle = "";
        private String synopsis = "";
        private String sourcePackage;
        private String sourceAppName = "";
        private Intent launchIntent;
        private float watchProgress = -1f;
        private boolean isVerifiedAvailable = true;
        private String provenance = PROVENANCE_APP_INTEGRATION;
        private Drawable iconDrawable;
        private int iconResId;
        private String backdropAssetPath;

        public Builder(String id, String title) {
            this.id = id;
            this.title = title;
        }

        public Builder type(String type) { this.type = type; return this; }
        public Builder subtitle(String subtitle) { this.subtitle = subtitle; return this; }
        public Builder synopsis(String synopsis) { this.synopsis = synopsis; return this; }
        public Builder sourcePackage(String pkg) { this.sourcePackage = pkg; return this; }
        public Builder sourceAppName(String name) { this.sourceAppName = name; return this; }
        public Builder launchIntent(Intent intent) { this.launchIntent = intent; return this; }
        public Builder watchProgress(float progress) { this.watchProgress = progress; return this; }
        public Builder verifiedAvailable(boolean verified) { this.isVerifiedAvailable = verified; return this; }
        public Builder provenance(String provenance) { this.provenance = provenance; return this; }
        public Builder iconDrawable(Drawable drawable) { this.iconDrawable = drawable; return this; }
        public Builder iconResId(int resId) { this.iconResId = resId; return this; }
        public Builder backdropAssetPath(String path) { this.backdropAssetPath = path; return this; }

        public MediaItem build() {
            return new MediaItem(this);
        }
    }
}
