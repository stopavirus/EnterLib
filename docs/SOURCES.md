# Catalogue sources

## Redflix (redflix.club) — DISABLED
## PVRplay (pvrplay.site) — DISABLED
Neither site was inspected from the build session (no network access), and no official API, licensed feed or terms permitting
third-party integration has been verified. Until the owner supplies written permission or an official integration method, both stay
disabled (`DisabledSource`, never fetches). EnterLib will not scrape them, extract streams, or bypass access controls. If a permitted
feed (e.g. a licensed M3U or API) is provided, add a `CatalogSource` implementation and register it in `SourceFactory`.

## Enabled integrations
- **Jellyfin / Emby**: `/Items`, `/Shows/{id}/Episodes`, token in `X-Emby-Token`. Direct stream URL per item. (Emby uses the `/emby` prefix.)
- **Plex**: `/library/sections`, `/all?includeGuids=1`, `/allLeaves`, token in `X-Plex-Token`.
- **M3U/HLS/DASH**: user-provided playlist URLs. Titles like `Show S01E02 Name` are grouped into series.
- **TMDB**: poster/backdrop/overview/genres/cast/rating, matched only on exact normalised title. Requires your own key and attribution.

## Known limitations
- Playlists give little metadata; use TMDB enrichment.
- Regional rows (Hollywood, Bollywood, South Indian) are not derived; language rows (e.g. Tamil Content) appear when language metadata exists.
- No "Trending/Popular" rows: no reliable local popularity data exists.
- Sync of Jellyfin/Plex series makes one extra request per series.
