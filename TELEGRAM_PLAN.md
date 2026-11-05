# ZapTV — Movies & Series from Telegram: Plan

> **Goal:** Watch movies and series that are in your own Telegram chats and channels inside ZapTV, the way [TMPlayer](https://github.com/dracu-lah/TMPlayer) does, **without changing how live TV looks or feels**.
> **Plan date:** 2 Nov 2026 · Reference studied: TMPlayer `main` (TDLib + a streaming Media3 `DataSource`).

---

## Progress (2 Nov 2026)

| Step | Status |
|---|---|
| T0 Decide | ✅ Clean-room implementation (TMPlayer used only as a behaviour reference). Credentials read from `local.properties` → `BuildConfig`. ABI splits not yet added (module not wired into `app` yet). |
| T1 Telegram core | 🟡 `core/telegram`: `TelegramClient` (lazy TDLib start, QR / phone / code / password sign-in states, log out, stop), `TelegramLibrary` (chats with videos, paged videos incl. video files sent as documents). Compiles; **not yet run against a real account** (needs API keys). |
| T2 Streaming | 🟡 `DownloadWindow` (9 unit tests) + `TelegramDataSource` (`tg://message/<chat>/<msg>`). Seek matrix not yet run on a device. |
| N1 Parser | ✅ `MediaNameParser` in `core/parser` (15 unit tests): title, year, SxxEyy / 1x05 / Ep 05 / എപ്പിസോഡ്, quality, codec, HDR, source, languages, split parts, caption fallback. |
| T3–T5, N2–N6 | Not started. Next: Room tables, `feature/library` screens, VOD player, wiring into `app`. |

Note: TDLib's `MessageVideo.alternativeVideos` gives Telegram's own extra qualities for some videos; the Versions picker (§4A.3) should list them too.

---

## 1. The one rule: live TV stays exactly as it is

ZapTV's promise is "switch on, a channel is playing, ▲/▼ zaps". The Telegram library must not get in the way of that.

| Guarantee | How |
|---|---|
| App still opens straight onto the last channel | Library is never the start screen. TDLib is **not started at launch**, only when the library is first opened. |
| Launch stays < 3 s, memory < 250 MB on a 2 GB box | TDLib and its natives load lazily; only one ExoPlayer decodes at a time (live is stopped while a movie plays). |
| No new keys taken from live TV | ▲▼ ◀ OK BACK keep their meanings. The library is reached from **Options (▶)**, the one place that already holds "more". |
| Nothing new to see if you don't want it | Settings → **Movies & Series: Off** (default). Off = no Options row, no TDLib started, nothing on screen. |
| Same look | Same tokens, fonts, focus style (cream fill + gold ring), 48/27 dp safe area, English + Malayalam strings. No new colours. |
| BACK always gets you home | BACK from the library → live TV resumes the channel you left, with its banner. |

---

## 2. How it works (what we learn from TMPlayer)

1. **TDLib** (Telegram's official client library) handles login, chats and files. Use it through **[tdl-coroutines](https://github.com/g000sha256/tdl-coroutines)**, which ships prebuilt natives for every ABI, so nothing is compiled here.
   - Compatibility with ZapTV: tdl-coroutines 15.0.0 needs Kotlin ≥ 2.4 → ZapTV has **2.4.21 ✓**. TMPlayer runs Media3 **1.11.1**, same as ZapTV ✓.
2. **Streaming while downloading.** TDLib can download a file **from any byte offset** (`downloadFile(offset=…)`) and reports which contiguous range is on disk (`downloadOffset`, `downloadedPrefixSize`). A small Media3 `DataSource` (`TelegramDataSource`, URI `tg://file/<fileId>`) does:
   - `open(dataSpec)`: get file size, start a TDLib download at `dataSpec.position` (unless already on disk).
   - `read()`: block the loader thread until the bytes at the current position are on disk, read from the partial file.
   - Seek = ExoPlayer reopens at a new position → TDLib moves its download there (last request wins, no debounce needed).
   - The window maths (how many bytes are readable, whether to move the download) lives in a **pure Kotlin class with unit tests**.
3. **Only video, never messages.** Use `searchChatMessages` with the **video** filter + **document** filter (so `.mkv` sent as files are included). Text never shows.
4. **One cached video at a time** (cheap TV boxes have 8 GB storage). Playing the next video clears the previous cache.
5. **Sign in with a QR code** (`requestQrCodeAuthentication`): on the phone, Telegram → Settings → Devices → Link Desktop Device, scan the TV. No typing on the remote. Two-step password: a short on-screen-keyboard step (reuse the Search keyboard, §3.10).

### Licence note (important)
TMPlayer is **GPL-3.0-or-later**. ZapTV's licence isn't chosen yet (the main plan recommends GPL-3.0).
- If ZapTV goes **GPL-3.0**, you may reuse TMPlayer code with credit.
- Otherwise write it **clean room** from the TDLib docs (as TMPlayer itself did for its `TdDataSource`) and only use TMPlayer as a reference for behaviour.
Decide this before writing code (§9).

### Credentials
Each build needs your own free `api_id` / `api_hash` from [my.telegram.org](https://my.telegram.org). Put them in `local.properties` (already git-ignored) → `BuildConfig`. Never commit them. With none present the build still works; the Settings row just says "Not set up in this build".

---

## 3. Where it lives in the code

New modules only. Existing live modules get a handful of small, additive changes.

```
core/telegram/     NEW  TDLib client (tdl-coroutines), auth state, chats, video search, file download
                        TelegramDataSource + DownloadWindow (pure, unit-tested)
core/model/        +    Movie, Series, Episode, VideoItem, WatchProgress (plain Kotlin)
core/parser/       +    EpisodeNameParser: "S02E04", "2x04", "Season 2 Episode 4", year, quality tags (unit-tested)
core/database/     +    tg_chat, tg_video, watch_progress tables (Room migration 1 → 2)
core/player/       +    VodPlayerController: separate ExoPlayer for on-demand video
                        (seek-friendly buffering, audio/subtitle tracks). Live PlayerController is untouched.
feature/library/   NEW  Library home, chat videos, series page, sign-in, VOD player overlay
feature/live/      ~    One new Options row "Movies & Series"; stop/resume live player around the library
app/               ~    Top-level destination switch (Live | Library); BuildConfig credentials
```

**Navigation:** `MainActivity` today shows only `LiveRoute`. Add a tiny top-level state (`Destination.Live` / `Destination.Library`) held in an activity-scoped ViewModel. No navigation library needed; the library has its own small back stack inside `feature/library`.

**Player:** keep the live `PlayerController` exactly as tuned for fast zapping. When the library opens: `liveController.stop()` (keep the instance warm, no decoder held). Movie playback uses `VodPlayerController` with a `DefaultMediaSourceFactory` built on `TelegramDataSource.Factory`. On exit: release the VOD player, `liveController.play(lastChannel)`.

**APK size:** TDLib adds tens of MB of native code per ABI. Add **ABI splits** (`armeabi-v7a`, `arm64-v8a`) so each TV downloads only its own. Optionally a `lite` product flavour without `core/telegram` for people who only want live TV.

---

## 4. UI / UX design

> Full spec: [UI_UX_DESIGN.md §6 (TV) and §7 (phone)](UI_UX_DESIGN.md). Design canvas: https://claude.ai/artifact/H3TkHfg3ctWRGNj1r4tuPT

Everything follows UI_UX_DESIGN.md (tokens, type, 38/46 dp rows, focus, motion). New screens only.

### 4.1 Entry point
- **Options (▶)** gets one row, placed after "TV guide": **🎬 Movies & Series ›**. Shown only when the setting is on.
- Big remotes: the **MENU**-long-press or a free colour key could jump straight in later; not needed for v1 (rule 2: smallest remote reaches everything).
- **Settings** gets: *Movies & Series ‹ Off / On ›*, *Telegram account ›* (signed in as …, Sign out), *Chats shown in library ›*, *Download whole video first ‹ Off / On ›*, *Clear video cache*.

### 4.2 First time (sign-in, 2 screens, no typing)
1. Full screen 97% ink: left half QR code (white card, 240 dp), right half 3 numbered steps "Open Telegram on your phone → Settings → Devices → Link Desktop Device → Scan". QR refreshes itself when it expires.
2. **Pick chats:** grid of your chats/channels that contain videos, with check circles (same style as first-run tile picker §3.12). Continue.

### 4.3 Library home (full screen, video-first spirit kept with a mini preview)
- Header (top 27 dp safe area): "Movies & Series" (26 dp) · clock · the last channel's logo + a "BACK Live TV" chip. (No live mini-preview like the TV guide has: live is stopped here to save memory on 2 GB boxes.)
- Rows (horizontal, D-pad):
  1. **Continue watching** — wide cards with progress bar (gold), "38 min left".
  2. **Series** — grouped by parsed show name, poster card, "S1–S3 · 24 episodes".
  3. **Movies** — poster cards (thumbnail from the Telegram video, title cleaned from filename, year, size).
  4. **Chats** — one row per chosen chat, "newest first".
- Card focus: cream fill + gold ring, scale 1.03, 120 ms. Card 160 × 240 dp posters, 280 × 158 dp wide cards.
- Footer hints (13 dp muted): OK Play · ▶ More · BACK Live TV.

### 4.4 Series page
- Left: poster + title + seasons count. Season tabs (◀ ▶). Episode list rows 46 dp: "E04 · Title · 42 min" with watched tick / progress line. OK plays, hold OK marks watched.

### 4.5 Movie player (VOD) — remote map
| Key | Action |
|---|---|
| OK | Play / pause, shows controls |
| ◀ ▶ | Seek −10 s / +10 s (hold: accelerating, preview time in a gold pill) |
| ▲ | Controls bar: Audio · Subtitles · Picture size · Speed |
| ▼ | Hide controls / (in series) Next episode card |
| BACK | Controls open → close them; otherwise stop and return to the library (position saved) |
| CH+ / CH− | Next / previous episode (series only) |

- Bottom bar reuses the **banner** panel style (864 dp, radius 16, blur): title, time bar 4 dp gold, elapsed / remaining, "Downloaded 42% · 3.1 MB/s" in muted 12 dp (like Stats for nerds).
- Buffering: same gold spinner ring as channel loading (§3.3) with "Loading · {title}".
- Error: same centre card as Unavailable (§3.4) — "Can't play this video", OK Try again, BACK Library.
- Next-episode countdown card (bottom-right, 8 s) with "OK Play now · BACK Cancel".
- Resume prompt when a position is saved: "Resume from 41:20" (focused) / "Start over".

### 4.6 What live TV users will notice
Only one extra row in Options and three rows in Settings. Nothing else changes.

---

## 4A. Netflix-style library (catalog, metadata, versions)

Android only (no iOS for now). Turns "a file in a chat" into a **title** with artwork, seasons and quality versions.

### 4A.1 Catalog layer (`core/metadata`, new module)

```
Title (movie | series, tmdbId, imdbId)
 └─ Season → Episode
     └─ Version (quality, codec, HDR, audio langs, size, source chat)
         └─ Parts (Part 1/2 or .001/.002 → played as one video)
```

1. **Parse** file name + message caption (caption first: channels often write "Title (2024)"): title, year, `S01E05` / `1x05` / `Ep 05` / `Episode 5` / എപ്പിസോഡ്, quality (2160p/1080p/720p/480p), codec (x264/x265/HEVC/AV1), HDR/DV, source (WEB-DL, HDRip), audio (Mal/Tam/Hin/Eng, Dual Audio), part number. Pure Kotlin in `core/parser`, unit-tested on real names.
2. **Match** on TMDB (`/search/movie|tv` with title + year, then `/external_ids` for the IMDb id). Series fallback: TVmaze (no key). Each match has a confidence score; low confidence → "Unmatched" shelf.
3. **Fix match:** hold OK on a card → search list → pick. Saved per file and never asked again.
4. **Ratings:** IMDb rating via **OMDb** (free key, 1,000 requests/day, cached 7 days). Never scrape IMDb.
5. **Cache:** Room tables + artwork disk cache (default 200 MB cap, LRU). Works offline once fetched.
6. **Attribution:** TMDB logo + "This product uses the TMDB API but is not endorsed or certified by TMDB" on About.

Keys: `TMDB_API_KEY`, `OMDB_API_KEY` in `local.properties` → `BuildConfig`. Without them the library still works with Telegram thumbnails and parsed names.

### 4A.2 Series grouping
- Group by matched series id, else by normalised parsed name.
- Forum topics in a group = seasons when names say so.
- Episode titles and stills from TMDB/TVmaze; episodes **not in your chats** show greyed.
- "New episode" badge from TDLib `updateNewMessage` in chosen chats (no polling).

### 4A.3 Versions and quality
- Same title found in several messages/chats → **one card**, many versions.
- **Versions picker** on the detail page and in the player: `4K HEVC HDR · 6.2 GB`, `1080p · 2.1 GB`, `720p · 900 MB`.
- **Auto** (default): bitrate = size ÷ duration; pick the highest version whose bitrate × 1.3 < measured Telegram download speed, and that the device can decode (`MediaCodecList`: HEVC/AV1/4K/HDR support). Unsupported versions are marked "Won't play smoothly on this TV".
- **Switch mid-playback:** map position by time (versions share a duration ±2 s), open the new file at that time; one short spinner.
- **Split parts:** a joined `DataSource` / Media3 playlist so Part 1 + Part 2 play with one timebar and one saved position.
- Badges: 4K · HDR · DV · 5.1 · HEVC · Dual audio.

### 4A.4 Home and detail
- **Featured title** (top of library home): backdrop, title logo, IMDb rating, year · runtime · genres, 2-line overview, Play / More info. Rotates every 10 s among recent or highly rated titles.
- **Rows:** Continue watching · New in your chats · New episodes · Top rated (IMDb ≥ 7.5) · per language · per genre · Because you watched X (TMDB recommendations ∩ your catalog) · Collections (TMDB `belongs_to_collection`) · actor rows.
- **Detail page:** backdrop, rating, cast with photos, overview, versions, audio/subtitle languages, More like this, Trailer (opens YouTube app).
- **My List** and thumbs up/down (feed "Because you watched").
- **Profiles:** Kids profile (kids chats + certification ≤ U/A), optional PIN for others; per-profile progress.

### 4A.5 Extras that use offset streaming
1. **Preview on focus:** after 1.5 s on a card, play 20 s muted from ~10 % into the real file (Setting: Previews On/Off; off on metered networks).
2. **Instant next episode:** prefetch the first ~2 min of the next episode while watching.
3. **Seek thumbnails** from already-downloaded ranges.
4. **Skip intro:** mark once per series → button on every episode.
5. **Subtitles:** auto-attach `.srt` posted near the video; OpenSubtitles search by IMDb id; sync offset ±.
6. **Chat as a TV channel:** any chosen chat can become channel **901+** in the live lineup. Plays its videos back to back with a NOW/NEXT schedule computed from durations, so ▲▼ zapping lands on it like any channel. Off by default per chat.
7. **Google TV home:** Watch Next row + Assistant "Play X on ZapTV".
8. **Overnight downloads** of queued seasons on Wi-Fi.

### 4A.6 Order
| # | Feature | Est. |
|---|---|---|
| N1 | Parser + catalog tables + TMDB/OMDb match + Fix match | 4–5 days |
| N2 | Series grouping + detail page | 3 days |
| N3 | Versions, Auto quality, device check, split parts | 4 days |
| N4 | Featured title, smart rows, My List | 3 days |
| N5 | Next-episode prefetch, subtitles, skip intro | 3 days |
| N6 | Previews on focus, chat-as-channel, Google TV rows, profiles | 5 days |

Design: canvas row 3 (TV home, detail + versions, channel 901, phone detail) and UI_UX_DESIGN.md §6.7–6.9.

---

## 5. Data model (Room, migration 1 → 2)

```kotlin
@Entity(tableName = "tg_chat")       // chats the user chose to show
data class TgChatEntity(@PrimaryKey val chatId: Long, val title: String, val enabled: Boolean, val order: Int)

@Entity(tableName = "tg_video", primaryKeys = ["chatId", "messageId"])
data class TgVideoEntity(
    val chatId: Long, val messageId: Long, val fileId: Int, val fileName: String,
    val title: String, val year: Int?, val seriesKey: String?, val season: Int?, val episode: Int?,
    val durationSec: Int, val sizeBytes: Long, val mimeType: String, val thumbFileId: Int?, val date: Long,
)

@Entity(tableName = "watch_progress", primaryKeys = ["chatId", "messageId"])
data class WatchProgressEntity(val chatId: Long, val messageId: Long, val positionMs: Long, val durationMs: Long, val updatedAt: Long, val watched: Boolean)
```

`fileId` is session-local in TDLib, so it is refreshed from the message (`getMessage`) before playback; the stable key is `(chatId, messageId)`.

---

## 6. Phases

| Phase | What | Done when | Est. |
|---|---|---|---|
| **T0. Decide** | Licence (§2), credentials, ABI splits | `local.properties` has API id/hash; APK splits build | 0.5 day |
| **T1. Telegram core** | `core/telegram`: TDLib client lifecycle (lazy), QR auth state machine, 2FA, chat list, video search with paging | Unit/instrumented test logs in on a TV and lists videos of a chat | 3–4 days |
| **T2. Streaming** | `DownloadWindow` (pure, tests) + `TelegramDataSource`; one-file cache policy | **Seek matrix** (below) passes on the cheapest TV box | 3–4 days |
| **T3. VOD player** | `VodPlayerController`, controls overlay, tracks/subtitles, resume, live stop/resume handover | Movie plays, BACK returns to live channel within 3 s | 3 days |
| **T4. Library UI** | Sign-in, chat picker, home rows, series page, Options row, Settings rows, Malayalam strings | Whole flow works with the Chromecast remote only | 4–5 days |
| **T5. Polish** | Episode parser, next-episode autoplay, "Download whole video first", thumbnails, optional TMDB posters | Series auto-group correctly on your real chats | 2–3 days |

### Seek matrix (definition of done for T2, adapted from TMPlayer)
1. Seek 10 min into an undownloaded part of a large MKV → plays again in ≤ 4 s.
2. Seek back into a watched part → < 1 s, from disk.
3. Ten fast alternating ◀/▶ presses → no crash, no stuck buffer.
4. MP4 with its index at the end plays and seeks.
5. Seek to the last 30 s → plays to the end, returns cleanly.
6. Network cut during a seek → spinner shows 0 KB/s, recovers when Wi-Fi returns.

### Live-TV regression checks (every phase)
- Cold start → last channel playing < 3 s with Telegram **on** and **off**.
- ▲/▼ zap times unchanged (p50 < 1 s preloaded).
- Memory after Library → BACK → live < 250 MB on a 2 GB box.

---

## 7. Risks

| Risk | Mitigation |
|---|---|
| APK grows a lot (TDLib natives) | ABI splits; optional `lite` flavour |
| 1–2 GB RAM boxes run out of memory | Live player stopped in library; one cached video; TDLib started lazily and closed (`close`) when leaving the library for > 10 min |
| 8 GB storage fills | One-video cache, `optimizeStorage` on start, "Clear video cache" in Settings |
| Audio codecs (DTS/TrueHD/E-AC3) silent on cheap sticks | Later: Media3 FFmpeg audio extension (TMPlayer uses NextLib `1.11.1-0.16.0`, matches our Media3) |
| Telegram account safety | Use your own `api_id`, official TDLib, read-only use of your own chats. Never automate joining/forwarding. |
| Content rights | The app only plays what is already in the user's own Telegram. Don't ship or recommend channel links for pirated films. |

---

## 8. Cheaper alternative (if you want it in a day)

Add an Options row **"Open TMPlayer"** that launches TMPlayer with an intent if it is installed (else shows "Install TMPlayer" with a QR to its download page). Zero risk to ZapTV, but it is two apps and two looks. The plan above is the "one app, one remote feel" version.

---

## 9. Decisions you need to make

1. **Licence:** GPL-3.0 (reuse TMPlayer code with credit) or clean-room implementation?
2. **Default:** keep *Movies & Series* **Off** by default (recommended), or On?
3. **Posters:** Telegram thumbnails only (no extra service), or also TMDB (needs a free API key)?
4. **APK:** one APK with ABI splits (recommended), or also a `lite` live-only build?
