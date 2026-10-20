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

## 2026-10-20: photographic build screenshot follow-up

The corrected resource renders, but the captured screen shows the toolbar entirely missing, and the same mountain photograph extends through favorites and lower app rows. The root of the issue is architectural: the wallpaper layers were in the full-screen root and the nav was inside the scrolling hero (behind other frames on the StarNet build).

This revision:
- Moves navigation into a fixed **root-level elevated header** above the scrolling content, with a high-contrast top scrim.
- Uses the photograph **only inside the cinematic hero**; favorites, channel rows and Apps catalog have a consistent opaque navy background.
- Separates favorites from the hero into its own compact section.
- Preserves the compiled drawable fallback, optional picture rotation, favorite actions, Glide recovery, and DVB alias.
- Removes the distracting floating app-icon preview on the landscape, without removing the hero's Live TV action.

Expected checks on the physical receiver: all top tabs and clock visible, main photograph restricted to upper stage, favorites on a clean navy surface, no overlap when Apps is selected, and OK/Back/D-pad still work. CI compilation is not a substitute for a screenshot test.

## Rendering containment fix from StarNet screenshots (2026-10-20)

The latest receiver screenshots show the top navigation and hero image rendering correctly, but a narrow strip of photographic imagery still appears between Favorites and Streaming & Media. When scrolling, some hero copy remains visible beneath the toolbar. This corresponds to disabled clipping (`setClipChildren(false)`) in both the ScrollView's content group and the photographic hero, transparent channel groups, and a partially transparent toolbar.

This revision enables clipping on the content layout, hero wrapper, and hero image panel; gives channel container and each row an opaque dark-navy background; and makes the fixed toolbar surface fully opaque. Satellite DVB and stock Glide remain untouched. Test visual clipping, D-pad focus scale at row edges, and scrolling before merge.
