package com.mihir.tvhome.screensaver;

import android.service.dreams.DreamService;

/** Android owns idle detection and playback suppression for the system screensaver. */
public final class PhotoDreamService extends DreamService {
    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);
        setContentView(new PhotoScreensaverView(this));
    }
}
