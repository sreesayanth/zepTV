<div align="center">

<img src="app/src/main/res/drawable-xhdpi/tv_banner.png" alt="ZapTV" width="320">

# ZapTV

**A free, open-source live TV player for Android TV and Google TV that feels like cable.**
Switch it on and a channel is already playing. Press ▲/▼ and the next one starts almost instantly.

[![CI](https://github.com/sreesayanth/zeptv/actions/workflows/ci.yml/badge.svg)](https://github.com/sreesayanth/zeptv/actions/workflows/ci.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
![Platform: Android TV](https://img.shields.io/badge/platform-Android%20TV%20%7C%20Google%20TV-3DDC84)
![Min SDK 24](https://img.shields.io/badge/minSdk-24-informational)
[![PRs welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md)

[Features](#features) · [Install](#install-on-your-tv) · [Build](#build-from-source) · [Docs](#documentation) · [Contributing](#contributing) · [Licence](#licence)

</div>

---

ZapTV is a **player**, not a content service. It ships with no channels of its own. It loads the community-maintained [iptv-org](https://github.com/iptv-org/iptv) playlists by URL: **Malayalam, English, Tamil and Hindi** are on by default, and Telugu, Kannada, Bengali, Marathi, Gujarati, Punjabi and worldwide English are one click away.

Every screen works with a basic TV remote: no mouse, no touchscreen, no on-screen typing for everyday use.

## Features

### Live TV (v0.1, working)

| | |
|---|---|
| **Instant start** | Opens on the last channel you watched, with the channel banner and a remote hint. |
| **Cable-style zapping** | ▲/▼ or CH+/CH− change channel. Quick presses move only the banner; the channel tunes once you stop. |
| **Channel numbers** | Type a number to jump. News 101+, Music 301+, Devotional 501+, Education 601+. |
| **Recall** | BACK returns to the previous channel. Pressing BACK twice quickly asks before exiting. |
| **Channel list** | OK opens groups (Favourites, All, News, Entertainment…). Hold OK to add a favourite. |
| **Options** | ▶ opens favourite, picture size, Up-button direction, stats for nerds, and update channel list. |
| **Self-healing** | If a stream fails, the next source is tried automatically. If all fail, a clear message counts down to the next channel. |
| **Many languages** | Malayalam, English, Tamil, Hindi, Telugu, Kannada, Bengali, Marathi, Gujarati, Punjabi and worldwide English. Turn each on or off in Options → Languages. Each language has its own numbers (see below), and a channel that is in several lists appears once. |
| **Fresh lineup** | The channel list refreshes daily. Your numbers and favourites survive refreshes. |
| **Private by design** | No accounts, no analytics, no tracking. Logs stay on the device. |

### Channel numbers

Each language has its own block of numbers, so turning languages on or off never renumbers anything:

| Language | Numbers | On by default |
|---|---|---|
| Malayalam | 1–999 | ✅ |
| English (India) | 1001–1999 | ✅ |
| Tamil | 2001–2999 | ✅ |
| Hindi | 3001–3999 | ✅ |
| Telugu · Kannada · Bengali · Marathi · Gujarati · Punjabi | 4001+ · 5001+ · 6001+ · 7001+ · 8001+ · 9001+ | |
| English (World), about 3,000 channels | 10001+ | |

Inside each block, categories follow the same cable-style pattern: Entertainment from **x001**, News **x101**, Movies **x201**, Music **x301**, Kids **x401**, Devotional **x501**, Education **x601**, Sports **x701**. Type **2101** on the remote for the first Tamil news channel, or **101** for Malayalam news.

### Movies & Series from Telegram (in progress)

An optional library for videos in **your own** Telegram chats and channels. It is hidden by default and never slows down live TV.

- Sign in by scanning a QR code with your phone: no typing on the remote.
- File names such as `Monsoon.Letters.2024.1080p.WEB-DL.Mal.x265.mkv` are parsed into title, year, season/episode, quality, codec and languages.
- Series are grouped into seasons and episodes. Your watch position is remembered.
- Videos play while they download, and seeking jumps the download.

Status and design: [docs/TELEGRAM_PLAN.md](docs/TELEGRAM_PLAN.md).

## Install on your TV

### Download

GitHub Actions builds the APK and publishes it on [GitHub Releases](https://github.com/sreesayanth/zepTV/releases):

| Build | When it's updated | Direct link (works in the TV's Downloader app) |
|---|---|---|
| **Latest build** | Every change merged to `main` | `https://github.com/sreesayanth/zepTV/releases/download/latest-build/ZapTV-latest.apk` |
| **Stable release** | Each version tag (v1.0.0…) | `https://github.com/sreesayanth/zepTV/releases/latest/download/ZapTV.apk` |

Tip: shorten the link with any URL shortener so it's quick to type with the remote.

### Install

1. On the TV, open **Settings → System → About** and click **Android TV OS build** 7 times to turn on Developer options.
2. In **Settings → System → Developer options**, turn on **USB debugging** (and **Network debugging** if it's there).
3. Note the TV's IP address (**Settings → Network & Internet**).
4. From your computer:
   ```bash
   adb connect <tv-ip>:5555
   adb install -r ZapTV-latest.apk      # or app/build/outputs/apk/debug/app-debug.apk if you built it
   ```
   Accept the prompt on the TV the first time.
5. ZapTV appears in the TV's app row. Pin it to your favourites.

**Without a computer:** install the free **Downloader** app on the TV, allow it to install unknown apps, and open one of the download links above.

**Fire TV:** works the same on Fire OS devices. Newer Fire TV devices running Vega OS don't run Android apps.

## Build from source

### Requirements

- JDK 17 or newer
- Android SDK with **platform 37** and **build-tools 37** ([Android Studio](https://developer.android.com/studio) includes both)

### Commands

```bash
git clone https://github.com/sreesayanth/zeptv.git
cd zeptv
./gradlew assembleDebug      # → app/build/outputs/apk/debug/app-debug.apk
./gradlew test               # all unit tests
./gradlew assembleRelease    # optimised APK (signed with the debug key unless you set up signing)
```

### Optional: Telegram API credentials

Movies & Series needs your own free API credentials from [my.telegram.org](https://my.telegram.org). Put them in `local.properties` at the project root. That file is git-ignored, so the credentials are never committed:

```properties
TG_API_ID=1234567
TG_API_HASH=your_api_hash
```

Without them everything still builds, and the library reports that it isn't set up in this build.

## Project layout

```
app/                   Entry point, manifest, dependency injection, banner and icons
core/model/            Channel, category and settings models (plain Kotlin)
core/parser/           M3U parser, lineup builder, media file-name parser (plain Kotlin, unit-tested)
core/database/         Room database: channels, sources, favourites
core/data/             Repositories: playlist download, channels, settings
core/player/           The single ExoPlayer, fast-start buffering, source failover
core/telegram/         TDLib client, Telegram library and streaming Media3 DataSource
core/designsystem/     Colours, fonts, focus style, shared TV components
feature/live/          The live TV screen and its overlays
design/                Interactive UI prototype (open in a browser)
docs/                  Plans and design specs
third_party/           Licences of bundled third-party assets
```

Built with Kotlin, Jetpack Compose for TV, Media3 ExoPlayer, Room, Hilt and [TDLib](https://github.com/tdlib/td) (through [tdl-coroutines](https://github.com/g000sha256/tdl-coroutines)).

## Documentation

| Document | What's in it |
|---|---|
| [docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md) | Vision, architecture, fast channel-switching engine, roadmap |
| [docs/UI_UX_DESIGN.md](docs/UI_UX_DESIGN.md) | Remote map, every screen, design tokens, TV and phone layouts |
| [docs/TELEGRAM_PLAN.md](docs/TELEGRAM_PLAN.md) | Movies & Series: how it works, data model, phases |
| [design/zaptv-prototype.html](design/zaptv-prototype.html) | Clickable prototype of the TV interface |
| [CHANGELOG.md](CHANGELOG.md) | What changed in each version |

## Responsible use

ZapTV contains no stream links, channels or media. You are responsible for the playlists and Telegram chats you load, and for making sure you have the right to watch them where you live. Please don't open issues or pull requests that add links to pay-TV or pirated content: they will be closed.

## Contributing

Bug reports, ideas and pull requests are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) first, and follow the [Code of Conduct](CODE_OF_CONDUCT.md).

Found a security problem? Please report it privately. See [SECURITY.md](SECURITY.md).

## Acknowledgements

- [iptv-org](https://github.com/iptv-org/iptv) for the community playlists ZapTV loads by default.
- [TDLib](https://github.com/tdlib/td) and [tdl-coroutines](https://github.com/g000sha256/tdl-coroutines) for the Telegram client.
- [AndroidX Media3](https://github.com/androidx/media) for playback.
- [TMPlayer](https://github.com/dracu-lah/TMPlayer), whose behaviour inspired the Telegram feature. No code was copied: ZapTV's implementation is written independently.
- Fonts: [Atkinson Hyperlegible](https://brailleinstitute.org/freefont) and [Barlow Semi Condensed](https://github.com/jpt/barlow), both under the SIL Open Font License.

## Licence

ZapTV is licensed under the [Apache License, Version 2.0](LICENSE). You may use, modify and distribute it, including in commercial and closed-source products, as long as you keep the licence and [NOTICE](NOTICE) file.

Bundled fonts are under the SIL Open Font License 1.1 (see [third_party/fonts](third_party/fonts)).
