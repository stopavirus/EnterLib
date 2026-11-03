# Test report (honest status)
**Nothing has been executed yet.** The authoring environment had Java 21 only: no Gradle, no Android SDK, no network, no device.

Written, not yet run (run by CI):
- `core`: matching/normalisation, dedupe keys, classifier, URL validation, M3U parsing, no-fake-content checks.
- `catalog`: Jellyfin and Plex mapping (MockWebServer), TMDB fill-only-missing and mismatch rejection, disabled sources never fetch, HTTP errors.
- `data` (Robolectric + in-memory Room): two-source dedupe, season/episode separation, unsafe URL rejection, prune/unavailable, search,
  watchlist, continue watching, backup round trip.

Not covered: UI/navigation tests, TV D-pad tests, real playback, background WorkManager execution, real Jellyfin/Plex/Emby servers,
real-device testing. Playback is unverified against any real authorised source. Automatic sync is implemented but unverified.
APKs: none built yet.
