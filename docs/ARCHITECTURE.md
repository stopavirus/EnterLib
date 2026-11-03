# Architecture

```
core        pure Kotlin/JVM: CatalogItem model, CatalogSource interface, Matching (dedupe keys), Classifier, M3uParser, UrlValidator
catalog     pure Kotlin/JVM: EmbyJellyfinSource, PlexSource, M3uSource, TmdbEnricher, DisabledSource (Redflix, PVRplay)
data        Android lib: Room DB, ContentRepository (merge/dedupe), SyncEngine, SyncWorker/Scheduler, SettingsStore (encrypted prefs),
            PlaybackService, BackupManager, EnterLibGraph (service locator)
ui-common   Compose: theme, PosterCard/rows, Home/Browse/Detail/Library/Settings screens, ViewModels, Media3 PlayerScreen, nav graph
app-mobile  Scaffold + bottom navigation, portrait/landscape, forced landscape player
app-tv      Leanback manifest, top tab row, large focusable cards, D-pad friendly
```
The spec's separate `domain`, `playback` and `catalog` folders are consolidated: domain models live in `core`, playback in `data`/`ui-common`.

## Sync
`SyncEngine.syncAll()` → for each enabled source: `fetch()` → optional TMDB enrichment (only for new or incomplete titles, retried at most weekly)
→ `ContentRepository.upsert` (merge by normalised title + year + kind) → prune destinations not seen this run → mark titles with no
destinations unavailable → write `sync_history`. A failed fetch never prunes. An empty result never prunes.

## Dedup
Match key = `kind:normalisedTitle:year`. The same title from two sources becomes one card with two destinations. Episodes are keyed by
(title, season, episode), so seasons never merge. Limitation: titles with differing years or spellings across sources may not merge.

## Playback
Destinations are stored without credentials. Auth headers are added at play/image time from encrypted settings. Jellyfin/Emby/Plex use
direct streaming (`FILE`); if the device cannot decode the container, playback fails with a retry prompt (no server-side transcoding yet).
EXTERNAL destinations open the browser.
