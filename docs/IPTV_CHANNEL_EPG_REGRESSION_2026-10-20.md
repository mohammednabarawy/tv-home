# IPTV, channel and EPG receiver regression — 2026-10-20

Tested the installed `3.1-premium-ar` APK on the owner's StarNet Twin 4000 Plus, Android 9/API 28, 1920×1080. This is a representative integration regression across every installed IPTV service supported by the launcher and the DVB interface, not a certification of every provider stream. Screenshots, UI hierarchies and raw diagnostics stay in ignored `shots/regression-20261020/` because third-party apps display account information.

## Results

| Area | Observed result | Assessment |
| --- | --- | --- |
| Nova launch | Favorite opened Nova 3.354 and its live categories/channel grid. Reopening retained access without requesting credentials. | Pass |
| Nova channel selection | Selected Madrasetna and iEN TV HD; channel name/artwork changed. Madrasetna opened fullscreen but remained black; iEN remained buffering/black in the observed interval. | Navigation passed; playback not established |
| Nova EPG | Madrasetna player controls explicitly displayed `No EPG available.` | Missing provider data on the tested channel |
| Marvel launch | Favorite opened Marvel Active 10.0.0 and its existing account, live categories and channel list. | Pass |
| Marvel playback | beIN SPORTS 1 MINI rendered in preview and fullscreen. Two fullscreen captures show the football clock advancing from 13:39 to 13:43 with different frames. | Moving video confirmed |
| Marvel channel change | Switched to beIN SPORTS 2 MINI; preview and fullscreen displayed a different football feed (beIN SPORTS 2 watermark). | Pass |
| Playback inactivity | Left Marvel fullscreen untouched for 533 seconds; moving video remained foreground, with no Home screensaver interruption. Back returned to its channel list, then HOME returned to Arabic MainActivity. | Pass |
| Marvel EPG | EPG dialog for beIN SPORTS 1 MINI displayed `No EPG for this channel`. | Missing provider data on the tested channel |
| Dates+ launch | Home shelf opened Dates+ 1.0.34 and the existing account screen. | Pass |
| Dates+ live/EPG | Entering Live reached `LiveStreamActivity`, which displayed `Account expired. Please renew your SN or account.` Account page showed expiry 2026-06-23 and Valid 0 Days. | Blocked by existing subscription; channel playback and guide cannot be certified |
| DVB launch | Live TV favorite entered the real tuner through `.DvbAlias`; OK opened the Nilesat channel list. | Pass |
| DVB channel switching | Android channel-up/down changed the tuned channel; list snapshots identify AD Nat Geo HD and Madrastna 3 with different transponders. | Pass |
| DVB decoder | On Madrastna 2, MPEG-2 decoder reported 720×576, 25 fps, frame count increasing 44,095 → 44,950, with zero dropped frames, frame errors or hardware errors in both samples. | Active decoding confirmed |
| DVB picture/audio | ADB screenshots remained black despite advancing native decoder counters; the HDMI picture was not observed directly. Audio was not independently listened to. | Decoder evidence does not certify visible HDMI picture or audible output |
| DVB EPG | GUIDE opened `com.excellence.osd.epgpro.ui.EpgProActivity`. AD Nat Geo HD populated Arabic programme titles, daily times and a description; current/next entries were also visible in the channel list. | Pass for populated guide |
| DVB guide layouts | Green programme key changed EPGPro from daily list to time grid. The restored Madrastna 2 grid displayed `no info`, consistent with its absent schedule. | Layout navigation passed; schedule absent |
| Missing DVB EPG | Madrastna channels initially showed `No EPG`/Loading. Other channels subsequently populated, so missing schedule data is channel-dependent. | Do not treat all channels as having guide data |
| HOME integration | With Glide default, HOME returned from Nova, Marvel and Dates+ to Glide. With StarNet Home temporarily selected, HOME returned from DVB, Nova, Marvel and Dates+ to Arabic `MainActivity`. | Pass for both defaults |

## Evidence and reproduction

- Launch each service using its actual launcher card. Wait for its initial server/account loading to finish before selecting channels; these apps can take tens of seconds to populate after a new task.
- Nova: Live → Nova Educational → Madrasetna/iEN TV HD; OK for preview/fullscreen, OK for controls, Back to grid. Evidence: `nova-live.png`, `nova-controls.png`, `nova-ien-controls.png`, `nova-after-back.png`.
- Marvel: Live TV → beIN SPORTS 1 MINI → preview/fullscreen → EPG. Evidence: `marvel-bein-preview.png`, `marvel-fullscreen.png`, `marvel-controls.png`, `marvel-epg.png`, `marvel-second-fullscreen.png`, `marvel-soak-end.png`, `marvel-soak-next-frame.png`, `marvel-starnet-home.png`.
- Dates+: Live → expiration dialog → HOME. Evidence: `dates-entry.png`, `dates-live-settled.png`, `dates-starnet-home.png`. Account identifiers are deliberately omitted from this report.
- DVB: Live TV → OK for list, Back for fullscreen, GUIDE for EPGPro, channel-up/down to tune, HOME to return. Evidence: `satellite-channel-list.png`, `satellite-channel-up.png`, `satellite-channel-down.png`, `satellite-natgeo-epg.png`, `satellite-starnet-home.png`.
- Decoder evidence: read-only `/sys/class/vdec/vdec_status`, saved as `satellite-decoder-before.txt` and `satellite-decoder-after.txt`. Native video-layer capture limitations must be distinguished from an IPTV player remaining black without corresponding playback evidence.

## Scope and preserved state

No account renewal, login changes, provider URL extraction, firmware changes, channel scans, channel edits or package removals were performed. The launcher continues to hand off to the installed applications; their private players and guide databases remain owned by those applications. Individual channel metadata is not fabricated in Home or Discover.

Receiver reboot, standby/CEC, recording/timeshift, purchases and exhaustive stream availability are outside this regression. The system screensaver's pre-existing one-hour timeout was not shortened for these tests. Glide was restored as default HOME, the original satellite channel (43, Madrastna 2) was restored, the photo DreamService remains selected, and the Home idle preference remains five minutes.

## Final release checks

- Rebuilt and signed the final source, installed with `adb install -r`, and verified the installed APK is byte-identical to the local artifact.
- APK SHA-256: `8783f93a0758f635532809ee96db069830be9d694bfe09581417b5bbefcddb13`; size 5,482,276 bytes.
- Final `tools/test-premium-ui.py` run: **19/19 passed**, including Arabic RTL remote navigation, favorite menus, Discover/Apps/Settings, photo preview/wake and HOME registration. Evidence: `shots/regression-20261020/final-ui/results.json`.
- No new `com.mihir.tvhome` Java crash appears in the inspected crash buffer during this regression (started after 19:09). Historical launcher entries before 17:37 belong to the repaired development build. The firmware app store logged its own crashes at 21:11; these are separate from the launcher and were not modified.
- Script syntax/compilation and staged whitespace checks passed. Private photos, account screens, raw logs, APK build output and signing keys remain excluded from the commit.
