package com.mihir.tvhome.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a content shelf / horizontal channel row in StarNet Home 3.0.
 */
public class MediaShelf {

    private final String id;
    private final String title;
    private final String subtitle;
    private final boolean isContinueWatching;
    private final List<MediaItem> items;

    public MediaShelf(String id, String title, String subtitle, boolean isContinueWatching, List<MediaItem> items) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.isContinueWatching = isContinueWatching;
        this.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public boolean isContinueWatching() { return isContinueWatching; }
    public List<MediaItem> getItems() { return Collections.unmodifiableList(items); }
    public int size() { return items.size(); }
    public boolean isEmpty() { return items.isEmpty(); }
}
