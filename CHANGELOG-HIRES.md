# Metrolist + slskd high-res source (HIRES changelog)

Self-hosted slskd as an opt-in high-resolution audio source. YouTube Music stays the default; with slskd disabled (default) the app behaves exactly as before.

## App changes (`app/src/main/...`)

- Settings: `SlskdEnabledKey` (default false), `SlskdBaseUrlKey`, `SlskdApiKeyKey`
  (`constants/PreferenceKeys.kt`); `SlskdSettings` screen under Integrations with host field,
  masked API-key field, Test-connection (`GET api/v0/session/enabled` + `GET api/v0/session`)
  and enable toggle (`ui/screens/settings/integrations/SlskdSettings.kt`, route
  `settings/integrations/slskd`).
- API client `music/slskd/SlskdApiClient.kt` (Ktor/OkHttp, `X-API-Key` verbatim, no new deps):
  search start/poll/await/cancel/delete, batch enqueue (409 → fresh id, one retry), batch poll,
  per-user downloads, downloads-directory listing, `Range: bytes=0-0` probe, and the
  progressive stream URL `GET api/v0/transfers/downloads/stream/{username}/{id}`.
  `searchTimeout` is sent in **milliseconds** (verified: slskd passes the value straight into
  Soulseek.NET's ms field despite documenting seconds).
- Ranking `music/slskd/SlskdRanker.kt`: lossless (flac/wav/aiff) first, then bitrate, sample
  rate, bit depth, size, queue length, upload speed, free slot. Top 20, never auto-picks.
- Playback `playback/MusicService.kt`: resolver override branch (null loudness, client
  `slskd`, API-key header, skips all caches); slskd failure drops the override and retries on
  YouTube; quality change clears the override; toasts announce every switch
  (`slskd_now_playing`, `slskd_fell_back`, `slskd_reverted`). No Room schema change, no
  `DownloadUtil` change, no `YTItem` change.
- UI `ui/menu/SlskdAction.kt`: "Play high-res via slskd" in Song/YouTubeSong/Player/Queue
  menus (visible only when enabled + configured); picker dialog; buffering/progress dialog
  with queue position; cancel also cancels the server transfer; downloaded-only and
  seek-limited notices; "Revert to YouTube". New `MediaItem`s are never created.
- Strings (English only): `slskd_*` in `res/values/metrolist_strings.xml`.

## Server changes (slskd-hires repo)

- `GET api/v0/files/downloads/files/{base64FilePath}` — authenticated ranged file serving.
- Download files opened `FileShare.Read` so the stream endpoint can read while writing.
- `GET api/v0/transfers/downloads/stream/{username}/{id}` — one URL serves the growing
  incomplete file (waits for future bytes, max ~120s idle) then the finished file; full
  Range support; clean 404 when nothing was served (never a short 200).

## Verification (this machine)

- slskd unit tests: 1317/1317 pass (incl. FilesController + stream-range + stream-endpoint tests).
- App unit tests: 26/26 slskd tests pass; full suite green at time of build.
- Live server run: search 69 responses/119 files, enqueue → Completed/Succeeded 100%,
  mid-download `206` off the growing file, post-move transparency, clean 404s.
- `assembleFossDebug` succeeds.

## Perf note

Homepage slowness is pre-existing, not from this integration: homepage collectors never read
slskd keys (all slskd reads live in settings/menus/playback paths and are gated on
`SlskdEnabledKey`). Measured hotspots live in `HomeViewModel` (serial YouTube fan-outs,
N+1 Room re-queries, unbounded liked-table scan) and `HomeScreen` (~37 collectors, per-card
Room collects, 2x2 thumbnail grids). No perf edits made in this change; see handoff for the
ranked list.
