# ZapTV — Android TV IPTV App: Implementation Plan

> **Working name:** ZapTV (rename anytime)
> **Platform:** Android TV / Google TV (and Fire TV running Fire OS)
> **Distribution:** Open source on GitHub under the Apache License 2.0. Sideloaded APK, no Play Store.
> **Goal:** Free live TV channels (Malayalam first, then all of India and the world) with a **cable TV feel**: switch on the app, a channel is already playing, press **CH+ / CH−** and the next channel starts almost instantly.
> **Plan date:** 26 Oct 2026

---

## Progress (updated 26 Oct 2026)

| Phase | Status |
|---|---|
| **0. Setup** | ✅ Done. Multi-module Gradle project (AGP 9.4.1, Kotlin 2.4.21, Compose BOM 2026.09, tv-material 1.1.0, Media3 1.11.1, Room 2.8.5, Hilt 2.60.1), design tokens and fonts, TV banner and icons. Debug and release APKs build. |
| **1. Player core** | ✅ Mostly done. Single warm ExoPlayer with fast-start buffering, source failover, M3U parser (13 unit tests), cable numbering, live screen, banner, ▲/▼ zapping with debounce, number entry, recall, autoplay last channel, unavailable countdown. Not yet tried on a real TV. |
| **2. Lineup + browsing** | 🟡 Started. Channel list with groups and favourites, Options panel and daily refresh are done. **Multiple languages** done (Nov 2026): 11 built-in iptv-org lists, each in its own number block, chosen in Options → Languages, duplicates shown once. Still to do: custom M3U URLs, hide/reorder, custom numbers. |
| 3–6 | Not started. Next: preloading neighbour channels, network-loss recovery, EPG. |

UI/UX spec: [UI_UX_DESIGN.md](UI_UX_DESIGN.md). Build and install steps: [README.md](../README.md).

---

## Table of Contents

1. [Vision and success criteria](#1-vision-and-success-criteria)
2. [Research findings (read this first)](#2-research-findings-read-this-first)
3. [Open-source and private-use model](#3-open-source-and-private-use-model)
4. [Feature list (MVP → v1 → later)](#4-feature-list-mvp--v1--later)
5. [UX design: making it feel like cable TV](#5-ux-design-making-it-feel-like-cable-tv)
6. [Tech stack](#6-tech-stack)
7. [Architecture](#7-architecture)
8. [Data model](#8-data-model)
9. [The fast channel switching engine](#9-the-fast-channel-switching-engine-the-heart-of-the-app)
10. [Stream health and auto-failover](#10-stream-health-and-auto-failover)
11. [EPG (TV guide)](#11-epg-tv-guide)
12. [Channel sources and the optional lineup builder](#12-channel-sources-and-the-optional-lineup-builder)
13. [Android TV platform integration](#13-android-tv-platform-integration)
14. [Performance on cheap TV boxes](#14-performance-on-cheap-tv-boxes)
15. [Testing strategy](#15-testing-strategy)
16. [Open-source release and installing on your TV](#16-open-source-release-and-installing-on-your-tv)
17. [Roadmap and timeline](#17-roadmap-and-timeline)
18. [Risks and mitigations](#18-risks-and-mitigations)
19. [Decisions you need to make](#19-decisions-you-need-to-make)
20. [Sources](#20-sources)

---

## 1. Vision and success criteria

### The idea in one sentence
An open-source IPTV **player** for TV that opens straight into live TV, switches channels as fast as a set-top box, and loads free channel playlists (iptv-org Malayalam/India lists, your own M3U files) by URL.

### What "like cable TV" means (measurable targets)

| Experience | Target |
|---|---|
| App launch → last watched channel playing | **< 3 s** (cold start on a mid-range box) |
| CH+ / CH− → first video frame (neighbour channel, preloaded) | **p50 < 1.0 s, p95 < 2.0 s** |
| CH+ / CH− → first frame (not preloaded) | **p50 < 2.0 s** |
| Channel banner (name, number, now/next) appears after a key press | **< 100 ms** (instant, before video loads) |
| Open channel list / EPG overlay | **< 150 ms**, smooth 60 fps scrolling |
| Dead channel | Detected and skipped automatically; never a black screen with no message |
| Crash-free sessions | **> 99.5 %** |
| Memory on a 2 GB RAM box | **< 250 MB** |

### Who it's for
- **You and your family first** (private use): free Malayalam news, devotional and entertainment channels on the big TV, without juggling five broadcaster apps.
- Anyone who finds the GitHub repo and wants a fast, remote-friendly, TiviMate-style player for their own M3U playlists.

---

## 2. Research findings (read this first)

### 2.1 Kappa TV status
**Kappa TV** (Mathrubhumi's Malayalam youth music channel) **stopped broadcasting as a TV channel on 25 June 2026** so Mathrubhumi could focus on digital platforms. Its content continues digitally (the *Kappa Originals* music label and its online channels). In the app, Kappa should be a **"digital channel" entry that opens its official YouTube channel**, not an HLS stream. The old Kappa stream URLs in public lists will probably go dead.

### 2.2 Where free channels come from
| Source | What it is | Use in app |
|---|---|---|
| **iptv-org/iptv** (GitHub, 60k+ stars) | Community list of *publicly available* stream links. Playlists by country/language/category, e.g. `https://iptv-org.github.io/iptv/languages/mal.m3u` (74 Malayalam entries on 26 Oct 2026), `.../countries/in.m3u` | **Main source.** Loaded by URL at runtime as a preset (see 2.3). |
| **iptv-org/api** | JSON database: `channels.json`, `streams.json` (with `referrer`, `user_agent`, `quality`), `logos.json`, `guides.json`, `categories.json`, `languages.json`, `blocklist.json` | Logos, alternate URLs, EPG mapping; feeds the optional lineup builder. |
| **iptv-org/epg** | EPG grabber (Node.js) for thousands of channels. **There are no ready-made hosted guides; you run it yourself** (Docker or GitHub Actions). | Our free EPG backend (section 11). |
| **Prasar Bharati WAVES** | Govt OTT with 140+ TV channels including **all 35 Doordarshan channels**, mostly free | DD Malayalam etc. Deep link to WAVES or use official streams if their terms allow. |
| **Broadcasters' own free streams / FAST partners** | Many Malayalam news channels stream free on their own websites, apps and YouTube | Most reliable URLs; add them to your own lineup. |
| **YouTube official live streams** | Most Malayalam news channels run 24/7 YouTube lives | **Deep link to the YouTube app only.** Never extract the raw stream; that breaks YouTube's ToS. |

### 2.3 Note about the public Malayalam list
The iptv-org Malayalam playlist mixes official free streams (most news channels, DD, devotional) with **pay channels** such as *Asianet HD, Mazhavil Manorama HD, Sun Surya HD, Star Sports, Sony Sports Ten*. Those pay-channel links are usually unofficial restreams, so they:
- break or change often (expect them to be the least reliable entries), and
- shouldn't be **committed into the public GitHub repo**, because GitHub takes down repos after DMCA notices. That has happened to IPTV projects before.

**So:** the repo ships only the playlist *URLs* (loaded at runtime), not copies of stream links. What you load on your own TV is your choice.

### 2.4 How the best IPTV players are built (2026)
- **TiviMate** (market leader on Android TV): fast zapping, EPG grid, favourites, catch-up, search, multi-playlist, parental lock, recording, multiview. It is **a player only and ships no content**. That is the legal model that survives.
- **OwnTV** (open source, Compose for TV): **dual engine**. Media3 ExoPlayer handles live TV because *"it opens HLS almost instantly"*, and libmpv/FFmpeg is the fallback for odd formats.
- **TvNexa** (open source): Clean Architecture (Presentation / Domain / Data), MVVM, Hilt, Room.
- The common stack is **Kotlin + Compose for TV + Media3 ExoPlayer + Hilt + Room + OkHttp**.

### 2.5 Fast zapping: what the research says
- Normal set-top-box zap time is **1–2 s**. That's our benchmark.
- **Media3 `DefaultPreloadManager`** (added 2025, improved in **Media3 1.11.0, Aug 2026**) preloads media sources ranked by **distance from the current item**. A channel list is exactly that kind of one-dimensional list, so we preload CH+1 and CH−1.
- **Tune the `LoadControl`** for live: start playback after about 1 s of buffer instead of the default.
- Keep **one warm player and one Surface**. Never recreate them on zap.
- Do **perceived speed** tricks: show the banner and logo instantly, and debounce rapid key presses.

---

## 3. Open-source and private-use model

No store, no store review. The app is built for your own TVs and published on GitHub so others can use or improve it.

### How it works
1. **The app is a player.** It contains no stream links. On first run it offers **preset playlist URLs** (iptv-org Malayalam, India, News, ...) plus "Add your own M3U" (URL, USB/local file, or a file on your phone/PC over LAN).
2. **Your personal channel list** lives in a private file (`my-channels.m3u`) on your TV, or in a **private** GitHub repo/Gist the app loads by URL. It never goes in the public repo.
3. **YouTube-only channels** (e.g. Kappa after the shutdown) open in the YouTube TV app through a deep link. That is simpler and more reliable than extracting stream URLs.
4. **Public repo hygiene:** commit code, presets that are only URLs, and test streams from public test sources (e.g. Apple/Mux sample HLS). No pay-channel links, no API keys, no personal playlists.
5. **License:** **Apache-2.0** (decided Nov 2026): anyone may use, modify and redistribute the code, including in closed-source apps. See [LICENSE](../LICENSE).

### Recommended Malayalam starter lineup (from the iptv-org Malayalam list)
News: 24 News, Asianet News, Manorama News, Mathrubhumi News, Kairali News, Media One, Reporter TV, News18 Kerala, Janam TV.
Public/education: DD Malayalam, Kite Victers (Kerala Govt education channel).
Entertainment/others: Amrita TV, Kairali TV, Jaihind TV, Safari TV, devotional channels (Shalom, Goodness TV, Harvest TV, ...).
Digital-only (YouTube deep link): Kappa (Kappa Originals) and any channel with only a YouTube live.

---

## 4. Feature list (MVP → v1 → later)

### P0: MVP (must have for first release)
- Fullscreen live player that **auto-plays the last channel on launch**.
- **CH+ / CH−** with D-pad Up/Down and the remote's channel keys. **Number keys** for direct channel entry.
- **Channel banner overlay**: number, logo, name, now/next programme, quality badge, time.
- **Mini channel list** overlay (OK button) with group filter (News, Entertainment, Devotional, ...).
- **Favourites** (long-press OK) and a Favourites group.
- **Last channel / recall** button (jump back to the previous channel).
- **Preset playlist URLs** (iptv-org Malayalam/India) + **add your own M3U** (URL or local file), with auto-refresh.
- **Stream health**: auto-retry, alternate URLs, a clear "channel unavailable" card and auto-skip.
- Basic settings: channel-change direction, playlist manager, language (English / Malayalam).

### P1: v1.0 (makes it great)
- **EPG**: now/next in the banner + full **TV guide grid** (Guide key).
- **Preloading neighbour channels** for near-instant zapping.
- **Search** (channel name, programme title), including voice search.
- Audio track / subtitle selection, aspect ratio (fit / fill / zoom).
- **Parental lock** (PIN for chosen groups/channels).
- Hide channels/groups, reorder, **custom channel numbers**.
- **Pause live TV / rewind** within the stream's DVR window when the stream has one.
- Home screen integration (Android TV channel row / Watch Next).
- FFmpeg audio extension (AC-3/E-AC-3/MP2 on boxes without hardware decoders).
- Sleep timer.

### P2: later
- XMLTV EPG from user-supplied URLs; Xtream Codes login (for users' own legitimate providers).
- **TV Input Framework** integration so channels appear in the TV's built-in "Live TV" app (great on Sony/TCL/Philips TVs).
- Picture-in-picture, multiview (2–4 channels; limited by hardware decoders).
- Catch-up (when the source supports it), reminders for programmes.
- Phone companion app / remote, Chromecast.
- Cloud backup of favourites and settings.

### Out of scope for v1
- Recording to USB storage (doable for private use; revisit after v1).
- Built-in VOD/movies catalogue.

---

## 5. UX design: making it feel like cable TV

### 5.1 Principles
1. **Video first.** The app's home screen *is* the playing channel. Menus are overlays on top of the video, never separate pages that stop playback.
2. **Every key does something predictable**, the same as a set-top box.
3. **Never a blank screen.** While loading, show the logo, name, number and programme; on error, show a card with a "next channel" hint.
4. **Big, readable, high contrast.** 10-foot UI: minimum 18 sp body text, a clear focus ring, overscan-safe margins (48 dp sides, 27 dp top/bottom).
5. **Remember everything**: last channel, last group, volume per channel (optional), last position in the list.

### 5.2 Remote key mapping

| Key | Action (on fullscreen player) |
|---|---|
| **D-pad Up / CH+** | Next channel (direction configurable) |
| **D-pad Down / CH−** | Previous channel |
| **OK / Center** | Open mini channel list (current group) |
| **Long-press OK** | Toggle favourite |
| **D-pad Left** | Channel list with groups panel |
| **D-pad Right** | Quick menu: audio, subtitles, aspect, favourite, info |
| **0–9** | Direct number entry (on-screen digits, auto-tune after 2 s or on OK) |
| **Back** | Close overlay → (no overlay) recall previous channel → second Back within 2 s → exit prompt |
| **Guide** (or Menu → Guide) | EPG grid |
| **Info** | Banner with now/next and programme description |
| **Last channel key** | Recall previous channel |
| **Red / Green / Yellow / Blue** | Favourites / Groups / Search / Guide (configurable) |
| **Play/Pause** | Pause live (timeshift when available) |
| **Menu / Settings** | Settings |

Compose keys: `Key.DirectionUp`, `Key.ChannelUp`, `Key.Zero..Key.Nine`, `Key.NumPad0..`, `Key.Guide`, `Key.Info`, `Key.LastChannel`, `Key.ProgramRed...`, `Key.MediaPlayPause`.

### 5.3 Screens (overlay mockups)

**A. Channel banner (shown on every zap, auto-hides after 4 s)**
```
┌──────────────────────────────────────────────────────────────────────┐
│                                                                      │
│                         ( live video )                               │
│                                                                      │
│ ┌──────────────────────────────────────────────────────────────────┐ │
│ │ 102  [LOGO]  Asianet News          HD  ★        09:42 PM         │ │
│ │ NOW  09:30–10:00  News Hour            ██████░░░░░░  40%         │ │
│ │ NEXT 10:00–10:30  Kerala Tonight                                 │ │
│ └──────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────┘
```

**B. Mini channel list (OK button, video keeps playing)**
```
┌───────────────┬──────────────────────────────────────────────────────┐
│ GROUPS        │  101  24 News              Breaking News             │
│ ★ Favourites  │▶ 102  Asianet News         News Hour                 │
│   All         │  103  Manorama News        Prime Debate              │
│ ▸ News        │  104  Mathrubhumi News     Super Prime Time          │
│   Entertainmt │  105  Kairali News         News Night                │
│   Devotional  │  ...                                                 │
│   Kids        │                 ( video continues in background )    │
└───────────────┴──────────────────────────────────────────────────────┘
```

**C. EPG grid (Guide key)**
```
┌──────────────┬─────────────┬─────────────┬─────────────┬────────────┐
│ Today 21:42  │ 21:30       │ 22:00       │ 22:30       │ 23:00      │
├──────────────┼─────────────┴─────┬───────┴─────────────┼────────────┤
│ 101 24 News  │ Breaking News     │ Night News          │ Debate     │
│ 102 Asianet  │ News Hour   │ Kerala Tonight            │ Headlines  │
│ 103 Manorama │ Prime Debate                    │ Counterpoint        │
└──────────────┴───────────────────────────────────────────────────────┘
  [mini video preview top-right while browsing]
```

**D. Number entry**
```
                                                      ┌───────┐
                                                      │ 1 0 _ │  → tunes to 10x after 2 s
                                                      └───────┘
```

### 5.4 Cable-style channel numbering (default, user-editable)
| Range | Group |
|---|---|
| 1–99 | Entertainment |
| 100–199 | News |
| 200–299 | Movies |
| 300–399 | Music |
| 400–499 | Kids |
| 500–599 | Devotional |
| 600–699 | Education / Govt |
| 700+ | Other languages / user playlists |

Numbers stay **stable** across playlist refreshes (matched by `tvg-id`, then name), just like real cable. Users can renumber.

**Languages (built).** Each language list gets its own block of 1,000 numbers and repeats the pattern above inside it, so turning languages on and off never renumbers anything:

| Numbers | Language | On by default |
|---|---|---|
| 1–999 | Malayalam | ✅ |
| 1001–1999 | English (India) | ✅ |
| 2001–2999 | Tamil | ✅ |
| 3001–3999 | Hindi | ✅ |
| 4001–4999 | Telugu | |
| 5001–5999 | Kannada | |
| 6001–6999 | Bengali | |
| 7001–7999 | Marathi | |
| 8001–8999 | Gujarati | |
| 9001–9999 | Punjabi | |
| 10001–19999 | English (World), about 3,000 channels | |

So Tamil News starts at 2101 and Hindi Devotional at 3501. A full category block (Hindi has more than 99 news channels) continues at x900+. "English (India)" is the iptv-org English list filtered to Indian channels (tvg-id ending in `.in`). A channel that is in several lists (e.g. DD National) appears once, in the lowest-numbered language. The catalogue lives in `core/model/.../PlaylistPreset.kt`; never reorder it or reuse an id, because that would renumber channels.

### 5.5 First-run onboarding (3 screens max)
1. Language of the app (English / മലയാളം).
2. Pick playlists: preset tiles (Malayalam, India, News, ...) and/or "Add my own M3U".
3. **Straight into live TV.**

---

## 6. Tech stack

| Layer | Choice | Why |
|---|---|---|
| Language | **Kotlin 2.x** (latest stable), Coroutines + Flow | Standard for Android |
| UI | **Jetpack Compose** + **Compose for TV** `androidx.tv:tv-material:1.1.0` (stable, May 2026) | Modern TV components, focus handling. Use standard `androidx.compose.foundation` lazy lists; the old `tv-foundation` TvLazy* lists are deprecated |
| Player | **Media3 ExoPlayer 1.11.1** (stable, Sep 2026): `media3-exoplayer`, `-exoplayer-hls`, `-exoplayer-dash`, `-datasource-okhttp`, `-session`, `-ui-compose-material3` | Fastest HLS start, PreloadManager, live tuning, HLS Content Steering |
| Extra formats | Media3 `-exoplayer-rtsp`, `-datasource-rtmp`; FFmpeg audio decoder extension (P1); optional libmpv fallback engine (P2, like OwnTV) | Covers odd streams (TS, RTMP, AC-3) |
| DI | **Hilt** | Standard, testable |
| Database | **Room** (+ FTS for search) | Channels, EPG, favourites, history |
| Settings | **DataStore (Proto or Preferences)** | Async, safe |
| Network | **OkHttp 4/5** + kotlinx.serialization | Shared connection pool and DNS cache with the player |
| Images | **Coil 3** | Logo loading with memory/disk cache |
| Background jobs | **WorkManager** | Playlist refresh, EPG download, health checks |
| Home screen | `androidx.tvprovider` | Channel row / Watch Next |
| Performance | **Baseline Profiles** + Macrobenchmark, R8 full mode | Faster cold start on weak boxes |
| Logs/metrics | **Local only**: rolling log file + zap-time stats stored on device, viewable in the "Stats for nerds" overlay and exportable. No Firebase, no tracking | Private use; no `google-services.json` or keys in the open-source repo |
| Build | Gradle Kotlin DSL + version catalog (`libs.versions.toml`), **minSdk 24 (Android 7.0)**, target the latest SDK | minSdk 24 covers most cheap Android TV boxes still in use |
| CI | GitHub Actions: build, unit tests, lint, **signed release APK → GitHub Releases** | Free; tag `v1.0.0` → APK appears for download |

> Before starting, check the latest stable versions on the AndroidX release pages. The versions above are the latest stable as of Oct 2026 (Media3 1.12.0 is in alpha).

---

## 7. Architecture

### 7.1 Layered (Clean Architecture + MVVM / UDF)
```
┌────────────────────────────── UI (Compose for TV) ──────────────────────────────┐
│  LiveScreen + overlays (Banner, MiniList, NumberEntry, QuickMenu)               │
│  GuideScreen   BrowseScreen   SearchScreen   PlaylistsScreen   SettingsScreen   │
│        ▲ StateFlow<UiState>                       │ user intents (events)       │
├────────┴──────────────────── ViewModels ──────────┴─────────────────────────────┤
│  LiveViewModel (zap logic)  GuideViewModel  BrowseViewModel  SettingsViewModel  │
├────────────────────────────── Domain (use cases) ───────────────────────────────┤
│  ZapToChannel  GetChannelLineup  GetNowNext  ToggleFavourite  RefreshPlaylists  │
├────────────────────────────── Data (repositories) ──────────────────────────────┤
│  ChannelRepository  PlaylistRepository  EpgRepository  SettingsRepository       │
│        │                   │                  │                │                │
│      Room DB          M3U parser +       XMLTV parser +     DataStore           │
│                       OkHttp download    OkHttp (gzip)                          │
├──────────────────────────────── Player core ────────────────────────────────────┤
│  PlayerController (single ExoPlayer)  ZapEngine  PreloadCoordinator             │
│  StreamHealthMonitor  MediaSessionService  ZapMetrics                           │
└─────────────────────────────────────────────────────────────────────────────────┘
          ▲                                          ▲
   M3U playlists / lineup.json (by URL)      EPG guide.xml.gz (GitHub Actions)
```

### 7.2 Module / folder structure
```
IPTV-TV-App/
├── IMPLEMENTATION_PLAN.md
├── app/                          # MainActivity, NavHost, Application, DI graph
├── core/
│   ├── model/                    # Channel, Program, Playlist, StreamSource (pure Kotlin)
│   ├── common/                   # Dispatchers, Result, time utils
│   ├── parser/                   # M3U + XMLTV streaming parsers (pure Kotlin, heavily unit-tested)
│   ├── database/                 # Room DB, entities, DAOs, migrations
│   ├── network/                  # OkHttp client, playlist/lineup fetch, APK updater
│   ├── data/                     # Repositories
│   ├── player/                   # PlayerController, ZapEngine, preload, health, metrics
│   └── designsystem/             # TV theme, colours, typography, focus styles, components
├── feature/
│   ├── live/                     # Fullscreen player + all overlays
│   ├── guide/                    # EPG grid
│   ├── browse/                   # Groups, favourites, search
│   ├── playlists/                # Onboarding, add/edit sources
│   └── settings/
├── baselineprofile/              # Baseline profile generator + macrobenchmarks
└── lineup/                       # OPTIONAL helper (best kept in a separate PRIVATE repo)
    ├── lineup.yaml               # which channels you want, numbers, groups
    ├── build_lineup.py           # iptv-org API → filtered, probed lineup JSON
    ├── epg/                      # iptv-org/epg channels config
    └── .github/workflows/        # nightly lineup + EPG jobs
```

### 7.3 Key state: `LiveUiState`
```kotlin
data class LiveUiState(
    val current: ChannelUi?,          // what is playing
    val pending: ChannelUi?,          // what the banner shows during rapid zapping
    val lineup: List<ChannelUi>,      // current group, ordered by number
    val nowNext: NowNext?,
    val overlay: Overlay,             // None | Banner | MiniList | QuickMenu | NumberEntry(digits)
    val playback: PlaybackStatus,     // Loading | Playing | Buffering | Error(reason, canRetry)
    val previousChannelId: String?,   // for recall/last-channel
)
```

---

## 8. Data model

### Room entities (simplified)
```kotlin
@Entity data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String?,                 // null for local file
    val type: PlaylistType,           // PRESET | M3U_URL | M3U_FILE | XTREAM (P2)
    val epgUrl: String?,              // url-tvg / x-tvg-url from the M3U header
    val refreshHours: Int = 24,
    val lastRefreshed: Long?,
    val etag: String?,
)

@Entity(indices = [Index("playlistId"), Index("tvgId"), Index("number")])
data class ChannelEntity(
    @PrimaryKey val id: String,       // stable hash(playlistId + tvgId|name)
    val playlistId: String,
    val number: Int,                  // cable-style, stable
    val name: String,
    val groupTitle: String,
    val logoUrl: String?,
    val tvgId: String?,               // EPG key
    val language: String?,
    val youtubeUrl: String?,          // digital-only channels → deep link
    val hidden: Boolean = false,
    val locked: Boolean = false,      // parental lock
)

@Entity(indices = [Index("channelId")])
data class StreamSourceEntity(        // a channel can have several URLs (failover)
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: String,
    val url: String,
    val userAgent: String?,
    val referrer: String?,
    val quality: String?,             // 1080p / 720p / 576p
    val priority: Int,
    val health: Health,               // UNKNOWN | OK | SLOW | DEAD
    val lastOkAt: Long?, val lastFailAt: Long?, val avgStartMs: Int?,
)

@Entity(primaryKeys = ["channelId"]) data class FavouriteEntity(val channelId: String, val position: Int)

@Entity(indices = [Index(value = ["tvgId", "start"])])
data class ProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tvgId: String, val start: Long, val stop: Long,
    val title: String, val description: String?, val category: String?, val iconUrl: String?,
)

@Entity data class WatchHistoryEntity(@PrimaryKey val channelId: String, val lastWatchedAt: Long, val totalSeconds: Long)
```

### M3U attributes to support
`#EXTM3U url-tvg="..." x-tvg-url="..."`
`#EXTINF:-1 tvg-id="" tvg-name="" tvg-logo="" tvg-chno="" group-title="" catchup="" catchup-days="" catchup-source="", Channel Name`
`#EXTVLCOPT:http-user-agent=...`, `#EXTVLCOPT:http-referrer=...`, `#EXTGRP:...`, `#KODIPROP:...` (parse and keep; DRM-related props can be ignored in v1).

Parser requirements: **streaming** (line by line, never load a 50 MB file into memory), tolerant of broken lines, BOM, CRLF, missing attributes; batch insert in a Room transaction of about 500 rows at a time; must handle 20k+ entries in < 5 s on a TV box.

---

## 9. The fast channel switching engine (the heart of the app)

### 9.1 Zap timeline and where time goes
```
Key press ─► banner shown (0 ms) ─► DNS + TCP/TLS ─► fetch master .m3u8 ─► fetch media .m3u8
          ─► fetch first segment(s) ─► decoder init ─► first frame rendered
             └─ 50–300 ms ─┘   └── 100–400 ms ──┘   └── 300–1500 ms ──┘   └ 100–300 ms ┘
```
Every technique below removes or overlaps one of those steps.

### 9.2 Techniques (all of them together)

1. **One warm ExoPlayer, one SurfaceView, never recreated.** Zap = `setMediaSource(...)` + `prepare()` on the same instance. Recreating players or surfaces costs hundreds of ms and causes black flashes. Use `SurfaceView` (not TextureView) for efficiency and HDR.
2. **Live-tuned `LoadControl`.** Start rendering once ~1 s of media is buffered:
   ```kotlin
   val loadControl = DefaultLoadControl.Builder()
       .setBufferDurationsMs(
           /* minBufferMs = */ 15_000,
           /* maxBufferMs = */ 30_000,
           /* bufferForPlaybackMs = */ 1_000,
           /* bufferForPlaybackAfterRebufferMs = */ 2_500,
       )
       .setPrioritizeTimeOverSizeThresholds(true)
       .build()
   ```
   Expose "Fast start / Balanced / Stable" presets in settings for poor connections.
3. **Preload neighbours with Media3 `DefaultPreloadManager`.** Rank channels by distance from the current channel number and preload **CH+1 and CH−1** (and the recall channel). For **live** streams, preload up to *source prepared / tracks selected* (manifest fetched, tracks chosen), **not** buffered video. Buffered live data goes stale within seconds, but the manifest round trips are the slow part. Update the ranking with `setCurrentPlayingIndex(...)` + `invalidate()` on every zap. Disable preloading on metered or slow networks (setting). Check the exact API in the Media3 1.11 PreloadManager docs.
4. **Debounce rapid zapping without losing responsiveness.**
   - Single press: tune **immediately**.
   - Rapid presses (another press within 400 ms): update only the banner (`pending`), then tune once the user pauses for 350 ms. This avoids firing ten network requests while someone scrolls from 101 to 110.
   ```kotlin
   fun zap(delta: Int) {
       val now = SystemClock.uptimeMillis()
       pendingIndex = (pendingIndex + delta).mod(lineup.size)
       showBanner(lineup[pendingIndex])                 // instant feedback
       zapJob?.cancel()
       val rapid = now - lastZapAt < 400
       lastZapAt = now
       zapJob = viewModelScope.launch {
           if (rapid) delay(350)
           player.tune(lineup[pendingIndex])           // ZapEngine handles preload hit/miss
       }
   }
   ```
5. **Perceived speed.** While loading, show the channel logo, name, number and current programme over a dark gradient. Users perceive "instant" when *something meaningful* appears within 100 ms.
6. **Shared OkHttp client** (`OkHttpDataSource.Factory`) with connection pooling, HTTP/2, and a DNS cache. Neighbour channels on the same CDN reuse warm connections. **Pre-resolve DNS** for preloaded neighbours.
7. **Per-channel headers.** Build each `MediaSource` with its own `DataSource.Factory` carrying the stream's `User-Agent` / `Referer` (from M3U `#EXTVLCOPT` or iptv-org `streams.json`).
8. **Pick the fastest healthy source.** Each channel can have multiple URLs; prefer the one with the best recent `avgStartMs` (section 10).
9. **Start at the live edge.** Use Media3's live configuration defaults and handle `BehindLiveWindowException` by `seekToDefaultPosition()` + `prepare()`.
10. **Hardware decoding by default**, with a per-channel "software decoder" fallback toggle for broken streams. Optionally test **tunneled playback** on devices that support it (lower latency A/V sync on some TVs), behind a setting.
11. **Fast app start.** Baseline Profiles; defer non-critical init (analytics, WorkManager scheduling) until after the first frame; read the last channel from DataStore and **start loading its stream in `Application.onCreate`** in parallel with UI inflation.

### 9.3 Measuring zap time (built in from day 1)
Use `AnalyticsListener.onRenderedFirstFrame`: `zapMs = firstFrameTime − keyPressTime`. Log p50/p95 per device model, per channel, and preload hit vs miss. Show it in a hidden **"Stats for nerds"** overlay (resolution, bitrate, buffer, zap ms, source URL host). This is how you prove the app is "as fast as cable".

---

## 10. Stream health and auto-failover

| Situation | Behaviour |
|---|---|
| HTTP 403/404/410 or manifest parse error | Try next source URL for the channel → if all fail mark `DEAD`, show **"Channel unavailable · ▲ next channel"** card, optionally auto-skip after 3 s |
| Network drop | Pause-free spinner; `ConnectivityManager.NetworkCallback` → auto-resume when back; exponential backoff retries (1 s, 2 s, 4 s, max 30 s) |
| Stalls / repeated rebuffering | Drop to lower quality variant (ABR does this), then try alternate source |
| `BehindLiveWindowException` | `seekToDefaultPosition()` + `prepare()` |
| Stream returns but is frozen | Watchdog: no position progress for 10 s while "playing" → reload |

**Background health check (WorkManager, every 6–12 h on unmetered network):** fetch each source's playlist (and first segment header) with a 5 s timeout; update `health`, `avgStartMs`. Option to **"Hide dead channels"**. If you use the optional lineup builder (section 12), it also probes your list nightly, so most dead links are gone before you switch on the TV.

---

## 11. EPG (TV guide)

### Free EPG backend (no server cost)
1. Fork/use **iptv-org/epg** in a GitHub repo.
2. Create a `channels.xml` with only the channels in your lineup (Malayalam first). Use iptv-org `guides.json` to find which EPG site covers each channel.
3. A GitHub Actions cron job runs nightly: `npm run grab --- --channels=channels.xml --output=guide.xml --days=3 --maxConnections=5` → gzip → publish to **GitHub Pages** as `guide.xml.gz`.
4. The app downloads it via WorkManager every 12 h (ETag / If-Modified-Since → usually a 304).

### In the app
- **Streaming XMLTV parser** (`XmlPullParser` over a `GZIPInputStream`), batch inserts, and only programmes for channels the user has. Delete programmes older than 6 h.
- Match by `tvg-id`, then fall back to normalised channel name.
- **Now/next** query: `WHERE tvgId=? AND stop > now ORDER BY start LIMIT 2` (indexed).
- **EPG grid**: lazy 2D layout (channels × 30-min slots), virtualised horizontally and vertically, mini video preview in the corner, "jump to now", ± day navigation.
- Users can add their own XMLTV URL (the `url-tvg` from M3U headers is picked up automatically).

---

## 12. Channel sources and the optional lineup builder

### Level 1: zero backend (MVP)
The app loads playlists straight from URLs. Built-in presets:
| Preset | URL |
|---|---|
| Malayalam | `https://iptv-org.github.io/iptv/languages/mal.m3u` |
| India | `https://iptv-org.github.io/iptv/countries/in.m3u` |
| News (world) | `https://iptv-org.github.io/iptv/categories/news.m3u` |
| Everything | `https://iptv-org.github.io/iptv/index.m3u` |
| Your own | any M3U URL / local file |

The app can merge several playlists into one lineup, **de-duplicate** channels by `tvg-id` (keeping the extra URLs as failover sources), and apply your custom numbers and hidden channels on top.

### Level 2: personal lineup builder (recommended once MVP works)
A small "backend" made only of a **private** GitHub repo + Actions (the free tier is enough). It gives you a cleaner, pre-tested channel list and the EPG:

```
            nightly GitHub Action (private repo)
iptv-org API ───────────────┐
(channels/streams/logos/     ▼
 guides.json)        build_lineup.py ──► pick channels from lineup.yaml
                             │            ──► probe every stream (timeout 5 s)
your own extra ──────────────┘            ──► rank sources by start time
URLs (lineup.yaml)                        ──► output lineup.v1.json
                                                   │
                                                   ▼
                          private Gist / raw URL with token, or a GitHub Pages site:
                          /lineup.v1.json, /guide.xml.gz, /config.json
                                                   │
                                     app (WorkManager, ETag, every 12–24 h)
```

**`lineup.v1.json` (shape):**
```json
{
  "version": 42,
  "generatedAt": "2026-10-26T02:00:00Z",
  "channels": [
    {
      "id": "AsianetNews.in",
      "number": 102,
      "name": "Asianet News",
      "group": "News",
      "language": "mal",
      "logo": "https://.../asianetnews.png",
      "tvgId": "AsianetNews.in@SD",
      "sources": [
        { "url": "https://.../playlist.m3u8", "quality": "1080p", "ua": null, "referrer": null }
      ],
      "youtube": null
    }
  ]
}
```
- `config.json`: remote feature flags, latest APK version + download URL (in-app update check), switch to hide any channel.
- Serve over HTTPS only. If the lineup is private, the app stores the access token in encrypted storage on the TV.

---

## 13. Android TV platform integration

### Manifest essentials
```xml
<uses-feature android:name="android.software.leanback" android:required="true" />
<uses-feature android:name="android.hardware.touchscreen" android:required="false" />

<application android:banner="@drawable/tv_banner" ...>   <!-- 320x180, includes app name -->
  <activity android:name=".MainActivity" android:exported="true"
            android:screenOrientation="landscape"
            android:configChanges="keyboard|keyboardHidden|navigation|screenSize|smallestScreenSize|screenLayout|orientation">
    <intent-filter>
      <action android:name="android.intent.action.MAIN" />
      <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
    </intent-filter>
  </activity>
</application>
```
Set `leanback required="false"` only if you also want a phone build later. The `LEANBACK_LAUNCHER` filter and the 320×180 banner are what make a sideloaded app **show up on the TV home screen** (without them you'd need a "sideload launcher" app). Everything must work with the D-pad, since TVs have no touch.

### Integrations
| Feature | API | Priority |
|---|---|---|
| Media controls, voice "pause", remote play/pause | `MediaSessionService` (Media3 session) | P0 |
| Home screen row ("ZapTV Favourites") + Watch Next | `androidx.tvprovider` preview programs | P1 |
| Voice/global search ("watch Asianet News") | Searchable + deep link intents | P1 |
| Picture-in-picture | `enterPictureInPictureMode` | P2 |
| Appear in the TV's built-in Live TV app / input list | **TV Input Framework** (`TvInputService` publishing channels and programmes to TvProvider) | P2 |
| Start on boot (set-top-box mode) | `BOOT_COMPLETED` receiver. Works on many boxes; Google TV restricts background activity starts, so make it an optional setting | P2 |

---

## 14. Performance on cheap TV boxes

Many Kerala homes use 1–2 GB RAM Android boxes or older smart TVs. Design for them.
- **Baseline Profiles + R8** for startup and scroll performance.
- Logos: request small sizes, decode at display size, Coil memory cache capped (~32 MB).
- Lazy lists with stable `key`s; avoid recomposing the overlay on every player position tick (separate fast-changing state such as the progress bar into its own small composable).
- Keep a single player; release preloaded sources on `onTrimMemory`.
- Room queries on `Dispatchers.IO`; EPG import in WorkManager, never on the UI path.
- Test on a **low-end device every week**, not only on the emulator.

---

## 15. Testing strategy

| Type | What | Tools |
|---|---|---|
| Unit | M3U parser (100+ real-world fixture files, broken files), XMLTV parser, numbering logic, zap debounce logic, failover decisions | JUnit5, Turbine, kotlinx-coroutines-test |
| Integration | Repositories with in-memory Room; download + parse pipeline with MockWebServer | Robolectric/AndroidX Test |
| UI | D-pad navigation flows: zap, number entry, open list, favourites, guide | Compose UI test (`performKeyInput`) |
| Player | Local HLS test streams (static and live-simulated) to check zap time, failover, behind-live-window | ExoPlayer test utils, MockWebServer |
| Performance | Cold start, zap time, list scrolling jank | Macrobenchmark, Baseline Profile |
| Devices | Chromecast with Google TV / Google TV Streamer, Mi Box / Mi TV Stick, a cheap 2 GB Android box, a Sony or TCL Google TV, Fire TV Stick (Fire OS), Android TV emulator 1080p & 4K | Real devices via `adb connect` |
| Family test | Install on every TV at home for 1–2 weeks of real use (parents, kids). Note every moment someone is confused or has to ask for help | APK + exported logs |

---

## 16. Open-source release and installing on your TV

### GitHub repo setup
- Public repo with `README.md` (screenshots, install steps), `LICENSE`, `CONTRIBUTING.md`, issue templates.
- `.gitignore` covers `local.properties`, keystores, `*.jks`, personal playlists (`my-channels.m3u`), tokens.
- **Release signing:** keep the keystore out of the repo. Store it as a GitHub Actions secret (base64) so CI can sign APKs. Keep a backup: if you lose the key, existing installs can't be updated without uninstalling first.
- Tag `v1.0.0` → GitHub Actions builds, signs and attaches `ZapTV-v1.0.0.apk` to a GitHub Release.

### Installing on Android TV / Google TV (sideloading)
1. TV: **Settings → System → About → click "Android TV OS build" 7 times** to enable Developer options. Then allow **"Install unknown apps"** for the app you'll install with.
2. Install the free **Downloader** app (or use `adb install`, or send the APK with a file-sharing app).
3. Open the GitHub Release APK link (or a short link to it) in Downloader → Install.
4. Because the app has a `LEANBACK_LAUNCHER` filter and banner, it appears in the TV's app row. Pin it to the favourites row.
5. **Updates:** the app checks `config.json` / the GitHub Releases API on start and shows "Update available". The user confirms, it downloads the APK and opens the system installer (`REQUEST_INSTALL_PACKAGES` permission).

**Developer loop:** `adb connect <tv-ip>:5555`, then run straight from Android Studio onto the TV over Wi-Fi.

**Fire TV:** sideloading works the same way on Fire OS devices. Newer Fire TV devices on Amazon's **Vega OS** do **not** run Android apps.

---

## 17. Roadmap and timeline

Estimate for **one developer**, full-time. Double it for part-time.

| Phase | Weeks | Deliverables | Exit criteria |
|---|---|---|---|
| **0. Setup** | 1 | Android Studio project, modules, version catalog, Hilt, Room, CI (APK build), TV emulator + one real box, design tokens, GitHub repo + license | App launches on your TV with a placeholder screen; CI green |
| **1. Player core** | 2–3 | `PlayerController` (single ExoPlayer, OkHttp, LoadControl), M3U parser + import, preset playlists, fullscreen live screen, CH+/CH−, banner, number entry, recall, autoplay last channel | Can watch and zap through the Malayalam playlist with the remote only |
| **2. Lineup + browsing** | 4–5 | Multi-playlist merge + de-duplication, custom numbering, mini channel list, groups, favourites, hide/reorder, playlist auto-refresh. Optional: private lineup builder repo | Your own family lineup plays in your chosen order |
| **3. Speed + reliability** | 6–7 | PreloadManager neighbours, debounce, failover, health checks, network-loss recovery, zap metrics + stats overlay, Baseline Profile | p50 zap < 1 s on Ethernet for neighbours; no black screens in a 1-hour zapping test |
| **4. EPG** | 8–9 | EPG pipeline (GitHub Actions), XMLTV parser, now/next in banner, guide grid, search | Guide shows 3 days for your Malayalam channels |
| **5. Polish + platform** | 10–11 | Settings, parental lock, audio/subtitle/aspect, Malayalam UI strings, MediaSession, home screen row, onboarding, in-app updater | Parents can use it end-to-end with only the remote, without help |
| **6. Family test + v1.0** | 12 | 1–2 weeks of real use on all home TVs, fixes, README + screenshots, signed release | `v1.0.0` on GitHub Releases |
| **v1.x** | after launch | Pause live/timeshift, recording to USB, TIF integration, PiP, multiview, Xtream/XMLTV user sources, phone remote | — |

---

## 18. Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Free streams die or change URLs often | Bad experience | Playlist auto-refresh, nightly probing (lineup builder), multiple sources per channel, in-app failover and hiding |
| GitHub DMCA takedown of the public repo | Repo disabled | Keep only code and playlist **URLs** in the public repo; personal lineups stay in private storage (section 3) |
| Streams with geo-block / referrer / token protection | Won't play | Per-stream `User-Agent`/`Referer` support covers most; otherwise use the YouTube deep link for that channel |
| Slow zapping on weak boxes or Wi-Fi | Core promise fails | Preloading, LoadControl presets, metrics per device, "Stable" preset, Ethernet recommendation |
| Unsupported codecs (HEVC on old boxes, AC-3 audio) | No video/sound | Format capability check, FFmpeg audio extension, optional libmpv fallback, mark channel incompatible |
| Huge playlists (`index.m3u` has 10k+ entries) | Slow/out of memory | Streaming parser, batched DB writes, paging in lists |
| EPG gaps for Malayalam channels | Empty guide | Multiple EPG sites in iptv-org/epg, name-based matching, graceful "No information" display |
| Kappa-style channel shutdowns | Dead entries | `closed` / `replaced_by` fields from iptv-org `channels.json`; auto-hide dead channels |
| Lost signing key | Can't update installed apps | Back up the keystore in two places (password manager + offline) |

---

## 19. Decisions you need to make

1. **App name**: "ZapTV" is a placeholder.
2. ~~**License**~~ ✅ Decided: **Apache-2.0** (anyone can reuse, even closed-source).
3. **Languages**: Malayalam-first (recommended), then Tamil/Hindi/English?
4. **Default zap direction**: Up = next channel (TiviMate style) or Up = previous? (Configurable either way; pick the default.)
5. **Phone app too?** If yes, keep `leanback required="false"` and plan a phone UI later.
6. **Which TV devices do you own?** That's the device test matrix and decides how hard to optimise for low-end hardware.

### Immediate next steps
1. Install the latest **Android Studio** and the Android TV emulator image. Enable Developer options + ADB debugging on your TV.
2. Approve this plan (or tell me what to change).
3. I scaffold **Phase 0** in this folder: Gradle project, modules, version catalog, Hilt/Room setup, TV theme, `git init` + license + README, and a "Hello TV" screen that plays the Malayalam playlist with CH+/CH− working.

---

## 20. Sources

- Kappa TV (shut down 25 June 2026): https://en.wikipedia.org/wiki/Kappa_TV
- iptv-org playlists & legal note: https://github.com/iptv-org/iptv
- iptv-org API (channels/streams/guides/blocklist): https://github.com/iptv-org/api
- iptv-org EPG grabber: https://github.com/iptv-org/epg
- Prasar Bharati WAVES OTT: https://prasarbharati.gov.in/waves/ · https://www.exchange4media.com/digital-news/waves-ott-has-crossed-84-lakh-users-govt-153011.html
- Media3 release notes (1.11.1 stable, 1.12.0-alpha01): https://developer.android.com/jetpack/androidx/releases/media3
- Media3 PreloadManager: https://developer.android.com/media/media3/exoplayer/preloading-media/preloadmanager · https://android-developers.googleblog.com/2025/09/introducing-preloading-with-media3.html
- ExoPlayer buffering strategy (Akamai): https://akamai.com/blog/performance/enhancing-video-streaming-quality-for-exoplayer-part-2-exoplayers-buffering-strategy-how-to-lower
- Fast channel zapping pattern: https://developer.quickplay.com/docs/products/player/android/fast-channel-zapping
- Fast zapping whitepaper (HESP Alliance): https://hespalliance.org/fast-zapping-whitepaper
- Compose for TV releases (tv-material 1.1.0): https://developer.android.com/jetpack/androidx/releases/tv
- TV Input Framework: https://developer.android.com/training/tv/tif
- Open-source references: OwnTV https://github.com/oerd/OwnTV · TvNexa https://github.com/sergio11/tvnexa_androidtv
- TiviMate feature overview: https://www.apkmirror.com/apk/armobsoft-fze/tivimate-iptv-player-android-tv/
