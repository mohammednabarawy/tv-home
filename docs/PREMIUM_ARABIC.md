# StarNet Home 3.1 — Arabic premium edition

This edition adds an Arabic RTL interface, compact fixed-width Favorites, a continuous cinematic background, optional Android HOME registration, and offline Full HD photo screensavers. It uses the existing Java/Android 9 app and does not require accounts, API keys, or a network connection.

## Design and remote behavior

- Home, Discover and Apps appear from right to left. Receiver actions are in the quick menu.
- Each favorite is 132 dp wide regardless of the number of favorites; overflow scrolls horizontally.
- The hero occupies about 36% of the display height. Artwork extends behind the header and favorites and fades into the lower shelves.
- Left/right moves physically left/right in RTL rows. Returning from an app restores the last favorite. MENU on a favorite opens its management dialog; elsewhere it opens the quick menu.
- Long-press OK or use MENU to reorder ordinary favorites. Left moves an app physically left in the RTL row, and right moves it right. Favorites are never Android package removals.
- Arabic text uses bundled Tajawal Regular/Bold from the Google Fonts distribution. License: `launcher/assets/fonts/OFL-Tajawal.txt`. Android's system font is a compatibility fallback.
- Installed app logos remain unchanged; the Live TV tile has a distinct satellite icon. Existing bundled landscape photography and satellite illustration provide ambient artwork, not claims that particular movie titles are available.

## Choose or change the launcher

Open **الإعدادات → المشغّل الافتراضي**, or **الوصول السريع → المشغّل الافتراضي**. This opens Android's default-app settings. On the tested firmware select **Home app**, then **ستارنت هوم** to use this app as HOME, or **glide** to return to the manufacturer's launcher. Other firmware may first ask which Settings application to use.

The app registers a normal HOME handler but does not silently change the current default. **واجهة ستارنت الأصلية** explicitly opens `com.tosmart.glide/.init.InitActivity`, even when StarNet Home is the default. Live satellite TV still uses the separate `com.tosmart.glide/.DvbAlias` component.

Physical HOME follows whichever launcher the owner selects. Previous documentation describing builds without a HOME handler is historical and does not describe version 3.1.

## Photo screensavers

Two modes share the same offline slideshow:

1. **Home inactivity:** In **الإعدادات**, select off, 2, 5, or 10 minutes under **شاشة التوقف · خلفياتك**. The default is 5 minutes. The timer runs while this launcher is foreground and focused; it stops when another application or system screen is active. **معاينة شاشة التوقف** starts it immediately. The first remote key dismisses it without activating the item underneath.
2. **Android system screensaver:** Open **شاشة توقف أندرويد**, select **شاشة التوقف · خلفياتك**, and use Android's controls to set when it starts. Android owns system-wide idle detection, charging/docking rules, display sleep and whether playback suppresses a dream. On the owner's receiver this service was selected through Android Settings and verified with Start now. The existing system display timeout was not changed.

The owner's 13 pictures from `D:\Wallpapers` were copied unchanged into the app's own external-files directory on the receiver. The slideshow prefers those files, changes every 30 seconds with a fade, downsamples large 4K–12K originals for the HD display off the UI thread, and holds two displayed photos at a time. Dismissing it cancels rotation and releases image resources. The 12 bundled licensed nature photos are the fallback when no custom pictures exist; their credits remain in `launcher/WALLPAPER-CREDITS.md`.

The personal pictures are not added to Git or the APK. To copy more pictures:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/install-screensaver-images.ps1 -Device 192.168.8.210:5555 -Source D:\Wallpapers
```

This copies JPG/JPEG/PNG/WebP files to `/sdcard/Android/data/com.mihir.tvhome/files/screensaver` and does not delete or edit the source files. Close/reopen the screensaver to reload the folder. An in-place app update preserves these copies; uninstalling the app may remove its own data directory.

## Build and physical-device test

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File launcher/build-and-install.ps1 -Mode build
python tools/test-premium-ui.py --device 192.168.8.210:5555 --output shots/premium-validation
```

The smoke test requires this APK already installed and an authorized receiver. It navigates with D-pad keys, captures fresh UI hierarchies and 1920×1080 screenshots, checks RTL layout/card sizing, opens the menus and screensaver preview, and checks waking with OK. It does not save a favorite reorder or change the default HOME.

Build output: `launcher/build/tvhome-starnet-android9.apk`, version `3.1-premium-ar`, min/target API 28. Device screenshots and regression evidence are saved under ignored `shots/premium-20261020/`.

Final device-validation results are recorded separately in `PREMIUM_ARABIC_VALIDATION.md`.
