# EnterLib

Ad-free, privacy-first entertainment library for **Android phones/tablets** and **Android TV / Google TV**.
EnterLib builds one unified catalogue (movies, series → seasons → episodes, documentaries, anime, kids, …) from
sources **you are authorised to use**, with a Netflix-style browsing UI. It contains no ads, no analytics and no tracking.

> Status: first complete implementation. It has **not yet been compiled or run**; see `docs/TEST_REPORT.md`.
> Push to GitHub and let the workflow build it, then fix whatever the first CI run reports.

## Sources
| Source | Status |
|---|---|
| Jellyfin, Emby, Plex (your own server) | Implemented (metadata + direct playback) |
| M3U / HLS / DASH playlists you have rights to | Implemented |
| TMDB | Implemented as metadata enrichment only (needs your own API key) |
| Redflix, PVRplay | **Disabled.** No verified API, licence or terms permitting integration. No scraping. See `docs/SOURCES.md` |

## Modules
`core` (models, CatalogSource, matching, M3U parser) · `catalog` (source adapters) · `data` (Room, sync, settings, playback, backup) ·
`ui-common` (Compose screens, ViewModels, player) · `app-mobile` · `app-tv`.

## Build
`gradle :app-mobile:assembleDebug :app-tv:assembleDebug` (JDK 17, Android SDK 34, Gradle 8.7). CI does this and uploads
`EnterLib-Mobile.apk` and `EnterLib-TV.apk` as the **EnterLib-APKs** artifact. Details: `docs/BUILD.md`.

## Docs
`docs/ARCHITECTURE.md`, `SOURCES.md`, `USER_GUIDE.md`, `PRIVACY.md`, `BUILD.md`, `TROUBLESHOOTING.md`, `TEST_REPORT.md`.
