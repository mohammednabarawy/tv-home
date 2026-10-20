# StarNet Home continuation — 2026-10-20

This is the initial recovery checkpoint. Later Arabic UI, HOME selection, screensaver and receiver regression results are documented in [PREMIUM_ARABIC_VALIDATION.md](PREMIUM_ARABIC_VALIDATION.md) and [IPTV_CHANNEL_EPG_REGRESSION_2026-10-20.md](IPTV_CHANNEL_EPG_REGRESSION_2026-10-20.md). Statements about uncommitted work and outstanding tests below describe that earlier checkpoint.

## Recovered project context

Source: [shared ChatGPT conversation, مراجعة مشروع UrTV](https://chatgpt.com/share/6ad77368-d7d0-83eb-9cf0-5a2e3238fe84).
The matching local chat was also located through the Codex chat tools. The shared page ends with the Phase 2 instructions; the local chat contains newer uploaded reports whose assistant review text was not available through the chat reader.

- Workspace: `D:\Coding\tv-home`.
- Existing branch: `feature/starnet-subscription-aware-3.0`.
- Starting commit: `350ee7bb823db859b040f3247faac19eee2e8b7f`.
- Product: lightweight Google TV-inspired Java launcher for StarNet Twin 4000 Plus, Android 9/API 28.
- Show installed, user-enabled services. Installation alone does not prove a subscription or title availability.
- Use the installed YouTube-capable app without API keys.
- Nova, Marvel and Dates+ remain app shortcuts unless a supported catalog interface is demonstrated. Metadata may enrich an accessible title; it does not establish availability.
- Preserve Glide as default HOME and satellite access through `com.tosmart.glide/.DvbAlias`.
- Preserve app inventory, favorites, tuner, firmware and system packages.

## Continuation changes

Changes in `launcher/src/com/mihir/tvhome/MainActivity.java`:

1. Apply service visibility settings to Favorites, including the satellite shortcut, preferred YouTube substitution and automatically populated favorites. All Apps continues to use the full app inventory.
2. Refresh launcher rows whenever Settings is left, including through the top navigation. Previously only Back rebuilt them.
3. Run the startup TvProvider audit on a worker thread using the application context.

The manual audit button still runs synchronously; this patch changes the startup path only.

## Current verification

Performed on the physical receiver through the existing authorized ADB connection:

| Check | Observed result |
| --- | --- |
| Device API/display | API 28; 1920×1080 |
| Local build | PowerShell build completed; min/target API 28 |
| Signing | New APK certificate matched the installed APK |
| Installation | `adb install -r` succeeded; no uninstall or data reset |
| Startup audit | Actual UID 10057; worker thread differs from main thread in logcat |
| TV-provider queries | All four queries returned valid cursors with 0 rows (`SUCCESS_EMPTY`) |
| Hide Nova, leave through Home tab | Nova card absent from Home screenshot |
| Discover after hiding Nova | XML assertion confirmed Nova absent |
| Restore Nova | XML assertion confirmed its setting enabled; D-pad focus reached Nova and OK launched `com.logicyel.novastb/com.logicyel.utv.StartActivity` |
| All Apps | Full inventory still used by code; Nova visible in the Apps screenshot after restoring its setting. The attempted text assertion during the hidden state was inconclusive because the unfocused grid has no app-label text in the accessibility dump |
| Physical HOME key | Returned from Nova to Glide's `.home.HomeActivity` |
| Default HOME resolution | Remained `com.tosmart.glide/.init.InitActivity` |
| DVB component | `am start -W` succeeded and resolved to Glide's `.home.HomeActivity`; channel switching, EPG and audiovisual playback were not independently verified in this continuation |
| Final state | Original Nova enabled setting restored; launcher brought to foreground |
| Diff whitespace | `git diff --check` passed |

Live query logs corroborate the earlier report's empty results. The checked-in JSON report has a timestamp inconsistent with today's date; use the current device logs for this run. Internal JSON could not be pulled through `run-as` because the installed package is not debuggable.

Memory snapshots: baseline total PSS 73,431 KB; updated launcher 65,906 KB after navigation. These are different runtime states, not a controlled performance comparison. ActivityManager also recorded two external force-stop requests during testing; uninterrupted stability and performance measurements remain outstanding. The crash buffer showed a vendor boot-wizard crash on the package-update broadcast, also present before this update; no launcher Java crash was observed in the inspected crash buffer.

## Local artifacts

- Built and installed APK: `launcher/build/tvhome-starnet-android9.apk`.
- Original installed APK backup: `shots/continuation-20261020/baseline.apk`.
- Screenshots and accessibility snapshots: `shots/continuation-20261020/` (ignored by Git).
- Useful screenshots: `baseline.png`, `hidden-home.png`, `apps.png`, `home-final.png`.

Changes are local and uncommitted. No push, pull request, merge or new GitHub Actions run was performed.

## Next work

1. Finish the focused remote regression matrix: row toggles, preferred YouTube selection, favorite reordering, service persistence across process restart, and switching Settings to Discover/Apps using D-pad navigation.
2. Validate reboot, offline behavior and controlled startup/memory measurements when the receiver has no competing automation.
3. Review Home and Discover visuals against the original reference. Retain app-level fallback while title catalogs are unavailable.
4. Before adding genuine Continue Watching, connect its data path to the UI: `queryWatchNext` currently has no callers, and its per-record service, progress and launch validation needs review before any nonempty result is treated as verified content.
5. Correct stale compatibility documentation before publication. Build success and empty provider results do not prove every receiver feature works.
