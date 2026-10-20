# Google TV-inspired StarNet Home (experimental)

Based on the user-provided Android TV Home screenshot, adapted into the existing Java/Android 9 launcher. Inspiration only; no Google proprietary source code or movie imagery was copied.

## Visual changes
- Compact navigation tabs: Home, Live TV, Apps, Glide, Inputs, Wi-Fi and Settings; clock on right.
- Offline cinematic hero reuses the project's licensed local rotating wallpapers and its interactive featured app panel.
- Favorite apps retain the existing editable dock and plus shortcut.
- Two compact horizontal app rows below the hero: **LIVE TV & IPTV** (Glide DVB alias, Nova, Dates+, Marvel) and **STREAMING & MEDIA** (SmartTube, YouTube, Kodi, Stremio, Jellyfin, Netflix).
- All Installed Apps grid remains below the rows.
- Fixed-size category cards prevent oversized favorite tiles.
- D-pad directional focus moves explicitly from tabs → hero → dock → category rows → All Apps. App rows horizontally scroll into view.
- No fake movies, playback progress, subscriptions, external recommendations, ads, or content requests are shown. A true Play Next integration would require an independently validated API and appropriate access to installed apps' recommendations.

## Safety
- Android 9 (API 28), StarNet S905X3, Glide package `com.tosmart.glide` remains installed.
- The DVB shortcut still invokes `com.tosmart.glide/.DvbAlias`.
- The new launcher remains an optional app (not the default HOME). The stock remote HOME and the Glide tab restore the original menu.
- No package disabling, debloat, system setting writes, firmware flash, or new network permissions.

## On-device acceptance tests
1. Install the signed workflow artifact. With a fresh GitHub debug key you may need to uninstall **only** `com.mihir.tvhome` before installing this build.
2. Check no navigation tabs, cards, or wallpaper overlap at your TV resolution.
3. Navigate right/left along category rows, down to All Apps, and up to favorites without losing focus.
4. Confirm DVB alias opens live channels, then switch channels, EPG, Exit, Home.
5. Confirm Nova, Dates+, Marvel, YouTube/SmartTube, Kodi/Stremio, and original Glide still work.
6. Reboot; verify Glide remains the original default HOME.

No live receiver test has been conducted for this new layout yet. GitHub Actions compilation is a separate verification step.
