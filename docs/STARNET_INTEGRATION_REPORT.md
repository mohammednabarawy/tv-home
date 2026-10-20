# StarNet Twin 4000 Plus — Receiver Integration Compatibility Report

**Device Model:** StarNet Twin 4000 Plus  
**SoC:** Amlogic S905X3  
**OS Version:** Android 9 (API Level 28)  
**Build ID:** `eng.freesa.20260915.172905`  
**Platform:** winsat  
**Target Package:** `com.mihir.tvhome`  
**Evaluation Date:** October 20, 2026  
**Probe Source:** Live ADB runtime audit (`192.168.8.210:5555`) & component dumpsys  

---

## 1. Executive Summary

This integration audit documents the exact capabilities, interfaces, and content exposure of all installed media and system applications on the StarNet Twin 4000 Plus receiver running Android 9.

### Key Architectural Findings:
1. **Android TV TIF / TvContract Framework:**
   - Android TV Preview Channels (`content://android.media.tv/preview_program`): **0 records found**.
   - Watch Next Programs (`content://android.media.tv/watch_next_program`): **0 records found**.
   - Traditional TV Channels (`content://android.media.tv/channel`): **0 records found**.
   - None of the third-party IPTV, streaming, or media players publish to the native Android 8+ Leanback channel recommendation database on this firmware build.
2. **IPTV Applications (Nova STB, Marvel Active, Dates+):**
   - No exported media content providers, public catalog endpoints, or deep-linking search query protocols are exposed.
   - Applications enforce internal subscription and player state without publishing to the Android system.
   - Under the **Verified Content Only** policy, all three applications are classified as **Level C (App-level integration)**. Fabricating mock movie or series posters under their name is strictly prohibited.
3. **Media Centers (Kodi, Jellyfin, Stremio):**
   - Kodi v21.2 and Jellyfin v0.19.10 expose internal services, but do not publish to system content providers unprompted. Kodi's web/JSON-RPC server (port 8080) is disabled by default on clean installs. They operate primarily at **Level C (App-level shortcut)** with an opt-in architecture for authorized local servers.
4. **Satellite & Stock Hardware Launcher:**
   - `com.tosmart.glide/.DvbAlias` successfully opens the hardware tuner and satellite stream without interfering with the stock launcher. The stock launcher `com.tosmart.glide/.init.InitActivity` remains completely intact and safe.

---

## 2. Integration Levels Defined

In accordance with Section 5 of the design specification, every package is evaluated and classified into one of four verified levels:

- **Level A — Full Integration:** Catalog, artwork, direct title launching, and playback/watch progress tracking.
- **Level B — Catalog Integration:** Genuine titles and artwork with verified availability, but limited playback progress integration.
- **Level C — App-Level Integration:** Reliable app launching, category routing, dynamic package detection, but no accessible individual media catalog.
- **Level D — Unsupported:** Integration unavailable, unsafe, or requiring elevated root/credential extraction.

---

## 3. Comprehensive Application Compatibility Matrix

| Application | Package | Version | Launch Activity | Published Channels / TIF | Accessible Catalog / API | Deep Linking | Watch Next Progress | Integration Level | Recommended Implementation |
|---|---|---|---|---|---|---|---|---|---|
| **StarNet Live Satellite TV** | `com.tosmart.glide` | 1.0 (firmware) | `.DvbAlias` | None (`READ_PROGRAM_INDEX` signature protected) | Hardware Tuner Only | Direct tuner launch | N/A (Live Broadcast) | **Level C** (Hardware DVB) | Dedicated "Live TV" tab & hero card launching `com.tosmart.glide/.DvbAlias`. |
| **Nova STB** | `com.logicyel.novastb` | 3.354 | `.utv.StartActivity` | None | None (Internal Picasso/Firebase only) | None exported | None | **Level C** (App-level) | App card with direct launch, category tagging ("IPTV & Live Channels"). No fake movie titles. |
| **Marvel Active** | `com.marvelactivecode.marvel` | 10.0.0 | `.MainActivity` | None | None (Flutter shell, FileProvider only) | None exported | None | **Level C** (App-level) | App card with direct launch, category tagging ("IPTV & Cinema"). |
| **Dates+** | `com.datesplus.iptv` | 1.0.34 | `com.intelligent.tv.start.StartActivity` | None | `com.datesplus.ott.provider` returns empty subpaths | None exported (`LiveStreamActivity` private) | None | **Level C** (App-level) | App card with direct launch, category tagging ("IPTV & VOD"). |
| **SmartTube** | `io.gh.reisxd.tizentube.fortaleza` | 23.36 | `org.chromium.chrome.browser.ChromeTabbedActivity` | None | None | Standard video URLs via Intent | None exported | **Level C** (App-level + VIEW intent) | Quick-launch tile in Favorites, native app handoff for video browsing. |
| **YouTube TV** | `com.google.android.youtube.tv` | 2.12.08 | `com.google.android.apps.youtube.tv.activity.ShellActivity` | None | None | Standard `vnd.youtube` VIEW intent | None exported | **Level C** (App-level + VIEW intent) | Quick-launch tile in Favorites, zero API keys or cloud console setup required. |
| **Kodi** | `org.xbmc.kodi` | 21.2.0 | `.Splash` | None | JSON-RPC (HTTP 8080 / TCP 9090) disabled by default | JSON-RPC `Player.Open` when enabled | JSON-RPC `VideoLibrary.GetInProgress` when enabled | **Level C** (Default) / **Level A** (Opt-in) | App card default. Opt-in local JSON-RPC architecture when user enables HTTP server in Kodi settings. |
| **Jellyfin** | `org.jellyfin.androidtv` | 0.19.10 | `.ui.startup.StartupActivity` | None | REST API requires user server URL & token | Deep links supported via item URI | Server API when authenticated | **Level C** (Default) / **Level A** (Opt-in) | App card default. Opt-in configuration for user Jellyfin server. |
| **Stremio** | `com.stremio.one` | 1.11.2 | `com.stremio.tv.MainActivity` | None | Addon transport requires user authentication | Internal deep links | None exported | **Level C** (App-level) | App card with direct launch. Never display unverified web torrents. |
| **Netflix** | `com.netflix.mediaclient` | 8.121.0 | `.ui.launch.UIWebViewActivity` | None | None (DRM-encrypted private sandbox) | Standard Netflix URI scheme | Protected sandbox | **Level C** (App-level) | App card with direct launch. |
| **FileManagerPro** | `com.tosmart.filemanagerpro` | 1.0.1 | `.MainActivity` | None | Storage provider | Storage explorer intents | N/A | **Level C** (Utility) | Utility shortcut in Apps grid. |
| **AppStore** | `com.excellence.appstore` | 2.0.8 | `.MainActivity` | None | Store package provider | Package install intents | N/A | **Level C** (Utility) | Store shortcut in Apps grid. |
| **Downloader** | `com.esaba.downloader` | 1.4.4 | `.MainActivity` | None | Internal web cache | URL download intents | N/A | **Level C** (Utility) | Utility shortcut in Apps grid. |
| **MX Player Pro** | `com.mxtech.videoplayer.pro` | 1.74.3 | `com.mxtech.videoplayer.ActivityMediaList` | None | Content provider for local media | Direct file playback intent | Video resume bookmark | **Level C** (Media Player) | Media player shortcut in Apps grid. |

---

## 4. Verification Evidence Details

### 4.1 Android TV TvContract Audit
Querying Android TV content providers from the device shell yielded the following results:
- `content://android.media.tv/channel` &rarr; `No result found.`
- `content://android.media.tv/preview_program` &rarr; `No result found.`
- `content://android.media.tv/watch_next_program` &rarr; `No result found.`
- `content://android.media.tv/program` &rarr; `No result found.`

The StarNet Twin 4000 Plus Android 9 firmware (`eng.freesa.20260915`) includes the standard Android TV frameworks, but third-party applications do not push content channels or watch next items to TvContract.

### 4.2 Nova STB (`com.logicyel.novastb`)
- **Package Manifest Inspection:**
  - Launch Intent: `android.intent.action.MAIN` + `android.intent.category.LAUNCHER` targeting `com.logicyel.utv.StartActivity`.
  - Content Providers: Declares `com.squareup.picasso.PicassoProvider` and `com.google.firebase.provider.FirebaseInitProvider`.
  - Exported Components: No exported search activities, no query providers, and no public media intents.
  - **Verdict:** Level C. The application can only be cleanly launched to let the user view their subscribed live streams and VOD inside Nova's interface.

### 4.3 Marvel Active (`com.marvelactivecode.marvel`)
- **Package Manifest Inspection:**
  - Framework: Flutter-based Android application.
  - Launch Intent: `android.intent.action.MAIN` + `android.intent.category.LAUNCHER` targeting `com.marvelactivecode.marvel.MainActivity`.
  - Content Providers: Declares internal `androidx.core.content.FileProvider` and Firebase initialization.
  - Exported Components: Zero exported media query endpoints or public deep-links.
  - **Verdict:** Level C. Direct launch only.

### 4.4 Dates+ (`com.datesplus.iptv`)
- **Package Manifest Inspection:**
  - Launch Intent: `com.intelligent.tv.start.StartActivity`.
  - Content Providers: Declares authority `com.datesplus.ott.provider`.
  - Query Tests: Running `content query --uri content://com.datesplus.ott.provider/` and various path permutations (`/channel`, `/movie`, `/series`) returns `No result found.`
  - Activities: `LiveStreamActivity` only accepts internal private action `aim_to_record`.
  - **Verdict:** Level C. Direct launch only.

### 4.5 Kodi (`org.xbmc.kodi`)
- **Package Manifest Inspection:**
  - Version: 21.2.0 (Omega).
  - Netstat Probe: `netstat -tlpn` shows no active listening socket on port 8080 or 9090.
  - JSON-RPC State: Kodi's remote control / web server is disabled by default until the user enables "Allow remote control via HTTP" in Kodi Settings &rarr; Services &rarr; Control.
  - **Verdict:** Level C default. The launcher should support optional JSON-RPC discovery if the user enables it, but never assume it is active.

### 4.6 YouTube & SmartTube
- **Installed Packages:**
  - Official TV client: `com.google.android.youtube.tv` (v2.12.08)
  - Open source TV client: `io.gh.reisxd.tizentube.fortaleza` (v23.36)
- **User Preference Policy:**
  - Neither app publishes Leanback recommendation channels on this firmware.
  - The launcher prioritizes installed apps in the Favorites dock and provides instant launch without requiring YouTube Data API keys or Google Cloud projects.

---

## 5. Architectural Recommendations for StarNet Home 3.0

1. **Strict "Verified Content Only" Enforcement:**
   - Because no installed IPTV app exposes title-level metadata, the launcher will **never** display fake movie or series posters under the names of Nova, Marvel, or Dates+.
   - If a provider has no verified items, its row is omitted entirely to prevent empty or broken UI shelves.
2. **Dynamic "Continue Watching" Row:**
   - The Continue Watching shelf must remain **completely hidden** when no authenticated, genuine playback records exist.
3. **Discover Tab Subscription Hub:**
   - Rather than presenting public TMDb trending posters that the user cannot stream, the **Discover** tab will serve as a **Subscription & App Discovery Hub**, grouping installed services into verified categories:
     - **Live TV & Broadcast:** StarNet Live Satellite TV, Nova STB.
     - **Cinema & VOD:** Marvel Active, Dates+, Netflix, Stremio.
     - **Streaming & Video Hubs:** SmartTube, YouTube TV, Kodi, Jellyfin.
     - **Media Players & Tools:** MX Player Pro, Downloader, FileManagerPro, AppStore.
4. **Cinematic Hero:**
   - Dynamic context switching:
     - When focusing or browsing Live TV / Satellite: displays the verified high-resolution satellite TV backdrop (`sat_backdrop.jpg`) with direct `▶ Watch Live TV` action.
     - When focusing or browsing installed streaming apps: displays polished ambient backdrops with clean typography and `▶ Open App` action.
     - Smooth fallback to the bundled landscape photography rotation.
5. **Preservation of Core Receiver Features:**
   - Hardware tuner launching via `com.tosmart.glide/.DvbAlias` remains primary.
   - Physical remote HOME key returning to original Glide is preserved.
   - Zero background polling loops, zero network locks on UI thread.
