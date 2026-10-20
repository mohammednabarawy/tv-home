# Cinematic StarNet Home redesign

## Rationale
Based on the owner's screenshot feedback (October 2026), the initial Google TV-inspired UI still looked too much like stacked boxes: dark empty hero, thick favorites band, duplicated apps in three sections, and an endless All Apps grid.

## Implemented
- A full-bleed, locally bundled 12-image photographic hero replacing the bordered placeholder. Text has a left-to-right legibility gradient; app artwork is a small badge rather than a fake movie trailer preview.
- Light-weight underlined-on-focus navigation: Home / Live TV / Apps / Original Glide / Inputs, plus clock and settings.
- Low-profile transparent favorites dock with existing Add (+) and long-press remove workflows unchanged.
- Horizontal shelves **only for apps not already pinned to Favorites**: More Live TV & IPTV, Streaming & Media, Tools & Utilities.
- Separate full-screen-ish Apps catalog: tapping Apps or Browse all installed apps hides the Home hero and rows, while keeping header/navigation and a complete installed app grid.
- Back from catalog returns to Home. D-pad focus map rebuilt when changing tabs, with an explicit Browse All Apps action below shelves.
- No fabricated Play Next recommendations or provider media metadata. The photographic banner is decorative and offline, not a preview of a live stream.
- Original Glide remains the default HOME; DVB alias, tuner apps, firmware and IPTV services are untouched.

## Verification
Run GitHub Actions **StarNet Android 9 test APK** and verify compilation/signing. Physical receiver test must confirm background visibility, D-pad focus, apps catalog, returns to Glide and DVB TV, and no cropped hero content at the current HDMI display density. This is a non-destructive test app only.

## Safe install
If an older CI-generated APK has a different signature, uninstall **only** `com.mihir.tvhome`, then install the new test APK; never remove `com.tosmart.glide` or any OEM package.

## 2026-10-20: confirmed on-device image regression and fix

User's screenshot shows a solid dark hero despite a photographic resource being expected. The earlier implementation relied solely on `AssetManager.list("wallpapers")` and async bitmap loading; absence/decoding failures could leave the hero blank. This patch adds a **compile-time Android drawable** `res/drawable-nodpi/cinematic_hero.jpg` copied from the repo's existing locally bundled `assets/wallpapers/01.jpg`, rendered immediately on both home and hero, with optional slideshow updates only after successful decode. The CI job now verifies that this resource was actually packaged into the APK, not just compiled in Java. Also merges one-card rows into a shared More to Explore shelf.

APK compilation and photo packaging checks are necessary but not equivalent to confirming display output. On-device screenshot, low-light image visibility, DPI cropping, and remote navigation are required prior to merge.
