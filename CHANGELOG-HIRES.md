# Metrolist + slskd high-res source (HIRES changelog)

Self-hosted slskd as an opt-in high-resolution audio source. YouTube Music stays the default; with slskd disabled (default) the app behaves exactly as before.

## App changes (`app/src/main/...`)

- Settings: `SlskdEnabledKey` (default false), `SlskdBaseUrlKey`, `SlskdApiKeyKey`
  (`constants/PreferenceKeys.kt`); `SlskdSettings` screen under Integrations with host field,
  masked API-key field, Test-connection (`GET api/v0/session/enabled` + `GET api/v0/session`)
  and enable toggle (`ui/screens/settings/integrations/SlskdSettings.kt`, route
  `settings/integrations/slskd`).
- API client `music/slskd/SlskdApiClient.kt` (Ktor/OkHttp, `X-API-Key` verbatim, no new deps):
  search start/poll/await/cancel/delete, batch enqueue (client-generated id: a 409
  regenerates silently, max 2 attempts; the real "already queued" case arrives as HTTP
  200 + failures and is attached to, never shown as an error), batch poll, per-user
  downloads, downloads-directory listing, `Range: bytes=0-0` probe (bodies released
  without materializing), and the progressive stream URL
  `GET api/v0/transfers/downloads/stream/{username}/{id}`.
  `searchTimeout` is sent in **milliseconds** (verified: slskd passes the value straight into
  Soulseek.NET's ms field despite documenting seconds).
  Server URL must be `http(s)://host:port` with no query/fragment and no `/api` path
  segment (a plain reverse-proxy prefix stays allowed); only genuine IO failures map to
  "network error", programming bugs rethrow and surface as "unexpected".
- Ranking `music/slskd/SlskdRanker.kt`: lossless (flac/wav) first, then bitrate, sample
  rate, bit depth, size, queue length, upload speed, free slot. Top 20, never auto-picks.
  AIFF is excluded (no Media3 extractor can parse it); mka/webm accepted via Matroska.
- Playback `playback/MusicService.kt`: resolver override branch (null loudness, client
  `slskd`, API-key header). slskd bytes are partitioned under their own cache key
  (`slskd:<transferId>`) so neither source's spans poison the other; the switch path
  clears player spans, URL cache, and the offline download cache before re-resolving.
  slskd failure retries the stream once, then drops the override to YouTube (deterministic
  container errors skip the retry); quality change clears the override; toasts announce
  every switch (`slskd_now_playing`, `slskd_fell_back`, `slskd_reverted`). Extractors
  registered: Matroska, fMP4, MP4, FLAC, WAV, Ogg, MP3, ADTS. No Room schema change, no
  `DownloadUtil` change, no `YTItem` change.
- UI `ui/menu/SlskdAction.kt`: "Play high-res via slskd" in Song/YouTubeSong/Player/Queue
  menus (visible only when enabled + configured); picker dialog; buffering/progress dialog
  with queue position; cancel cancels the server transfer while the poll loop is active
  (once progressive streaming has started, the transfer intentionally keeps running as
  the playback source); downloaded-only and seek-limited notices; "Revert to YouTube".
  New `MediaItem`s are never created.

## Homepage slimming (app)

The home screen renders exactly three sections — speed dial, quick picks, recent songs —
and its load path makes **no network requests** (only the optional account-avatar lookup
when a cookie is set).

- Removed sections: Daily discover, Keep listening, Account playlists, Forgotten favorites,
  From the community, Similar recommendations, every official API feed section (with its
  chips row, podcast-chip handling and infinite scroll), and Mood & Genres.
- That removes the ~30 YouTube requests each home load fanned out: 5× `next` + 5× `related`
  (daily discover), 3× `artist` + 2× `next/related` + up to 5× `playlist` (community
  playlists), `explore`, the similar-recommendation artist/song/album calls, and the
  `next`+`related` pair inside quick picks.
- `HomeViewModel.load()` is local-only (Room); `getRandomItem()` and the shuffle FAB draw
  from quick picks + recent plays. Quick picks no longer enriches with YouTube "similar
  songs" (that call only ever added songs already in the local DB).
- New `DatabaseDao.recentSongs(limit)`: latest play per song, newest first, deduped.
  Speed dial auto-fills from pinned + recent + quick picks; section order shuffling
  (`RandomizeHomeOrderKey`) still applies, now across three sections.
- Kept: pull-to-refresh, wired sync, wrapped card (unchanged, date-gated off), shuffle FAB,
  recognition. Mood & Genres / New Release screens remain compiled and routable but are no
  longer linked from home.
- Tests: new `RecentSongsTest` (ordering by latest play, dedup, limit, never-played) and
  updated `HomeSpeedDialTest`; full app suite 203/203.

## Server changes (slskd-hires repo)

- `GET api/v0/files/downloads/files/{base64FilePath}` — authenticated ranged file serving.
- Download files opened `FileShare.Read` so the stream endpoint can read while writing.
- `GET api/v0/transfers/downloads/stream/{username}/{id}` — one URL serves the growing
  incomplete file (waits for future bytes, max ~120s idle) then the finished file; full
  Range support; clean 404 when nothing was served (never a short 200).

## Verification (this machine)

- slskd unit tests: 1319/1319 pass (incl. FilesController, stream-range, stream-endpoint,
  username-mismatch and transition tests).
- App unit tests: 45/45 slskd tests pass (ranker incl. AIFF-exclusion, error mapper,
  config incl. URL validation, 15 MockEngine client tests); full suite green at build time.
- Live server run: search 69 responses/119 files, enqueue → Completed/Succeeded 100%,
  mid-download `206` off the growing file, post-move transparency, clean 404s.
- `assembleFossDebug` succeeds.

## Perf note

The homepage slowdown was the feed fan-out: one home load issued ~30 YouTube requests
(daily discover, community playlists, similar recommendations, explore, plus the feed
itself) and rendered 10 section types with their shimmer hosts. That is gone — see
"Homepage slimming" above. Untouched hotspots, if more is ever wanted: the per-card
`database.song(...)` collect in home list rows and the N+1 Room re-queries behind them.
