# StarNet Home 3.1 — receiver validation, 2026-10-20

## Delivered scope

- Arabic-first RTL Home/Discover/Apps/Settings, native Arabic string resources and Tajawal typography.
- Continuous cinematic background, compact horizontal Favorites, restrained focus animation, labeled Live TV card and quick menu.
- Preferred YouTube client normalization so editing Favorites operates on the visible client rather than hidden aliases.
- Selectable Android HOME handler; launcher switching from Settings and quick menu; explicit Glide shortcut independent of the current HOME choice.
- Foreground idle slideshow and Android DreamService; off/2/5/10-minute Home idle choices, preview, first-key wake and 30-second photo rotation.
- All 13 original images from `D:\Wallpapers` copied to the app-owned receiver folder, with sampled HD decoding. Original source files are unchanged, and personal images are not committed or included in the APK.

## Physical receiver evidence

StarNet Twin 4000 Plus / AmlogicS905X3, Android 9/API 28, 1920×1080; authorized ADB at the existing owner-provided address.

| Check | Result |
| --- | --- |
| APK build | Passed local resource/Java/DEX/signing build, version `3.1-premium-ar`, min/target API 28 |
| Update | In-place `adb install -r`; same local signing key, no uninstall or data reset |
| UI regression | 19 assertions passed in `tools/test-premium-ui.py`; latest results and screenshots under `shots/regression-20261020/final-ui/` |
| RTL | Physical left/right navigation, Arabic tab order, fixed favorite widths and Arabic dialogs verified |
| App inventory | Nova remains accessible in All Apps; unsupported Continue Watching is absent |
| Menus | Favorite MENU opens its management dialog without a competing quick menu; quick menu exposes Glide and HOME settings |
| Screensaver preview | Decoder successfully displayed a Full HD image; first OK woke it without reopening it |
| HOME switching | Selected StarNet Home in Android Home app picker, verified HOME resolved to `.MainActivity`, pressed HOME, then selected Glide again and verified its original component |
| Android screensaver | Selected `com.mihir.tvhome/.screensaver.PhotoDreamService` through Android Settings; Start now ran it and `dumpsys dreams` confirmed the active service |
| Custom images | All 13 receiver copies have SHA-256 hashes identical to `D:\Wallpapers`; verification saved locally |
| Large image | Runtime decoder displayed a 1920×1100 sampled image from the custom set, which includes the 12000×6871 original |
| Automatic Home idle | Set 2 minutes, sent no remote input for 125 seconds, observed the fullscreen custom wallpaper and a new slideshow-start log |
| Rotation | A second, visibly different custom wallpaper appeared after another 35 seconds; screenshots show GTA artwork followed by The Witcher artwork |
| Wake | First OK returned to MainActivity; no application was launched by that wake key |
| Idle preference | Restored the normal 5-minute Home screensaver delay |
| Packaging | Font resources, font license and 12 fallback HD photographs are included; the 13 personal originals remain external to the APK |

The initial 19-check sweep was followed by a focus-memory adjustment and focused favorite reorder/navigation-return checks (`youtube-before-move.png`, `youtube-moved-left.png`, `youtube-restored.png`, `remembered-favorite.png`). After the IPTV regression, the final source was rebuilt, installed in place and all 19 checks passed again at `shots/regression-20261020/final-ui/`. The test now normalizes to the right edge before checking Left, because the remembered favorite may legitimately be the leftmost Add App tile.

## Current operational state and limits

- Glide is left as the selected default HOME. The owner can choose **ستارنت هوم** at **الإعدادات → المشغّل الافتراضي → Home app** and switch back at any time.
- Android's selected screensaver is StarNet's photo service. The pre-existing Android screen-off timeout (one hour) and charging/docking policy were not changed. The separate Home idle timer defaults to five minutes and stops when another application is foreground.
- No firmware, tuner services, original applications, IPTV credentials or system package enablement were changed. The subsequent IPTV/channel/EPG regression is recorded in [IPTV_CHANNEL_EPG_REGRESSION_2026-10-20.md](IPTV_CHANNEL_EPG_REGRESSION_2026-10-20.md), including subscription and capture limitations. Receiver reboot and an exhaustive check of every provider stream are outside that matrix.
- The firmware's UI-automation hierarchy is intermittent, especially over fullscreen photography. Tests retry hierarchy capture; photo rendering is checked using screenshots and decoder logs, and waking is checked after returning to the Settings UI.
- Initial Arabic/font integration errors were repaired during development. The shipped build uses Android font resources with a compatibility fallback, and the passing checks occurred after those repairs.
- The implementation and sanitized validation records are included in the requested commit on `feature/starnet-subscription-aware-3.0`. Git history and the associated GitHub Actions run are the authoritative publication record; private receiver evidence remains ignored.

## Artifacts

- APK: `launcher/build/tvhome-starnet-android9.apk`.
- Guide: `docs/PREMIUM_ARABIC.md`.
- UI smoke test: `tools/test-premium-ui.py`.
- Wallpaper transfer: `tools/install-screensaver-images.ps1`.
- Private/local evidence: `shots/premium-20261020/`, including `idle-validation/`, `wallpaper-verification.json`, HOME picker screenshots and final UI snapshots.
