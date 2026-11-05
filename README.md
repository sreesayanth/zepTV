# ZapTV

An open-source live TV player for Android TV and Google TV that feels like cable: it opens straight onto the last channel you watched, ▲/▼ changes channel instantly, and every screen works with a basic remote.

It ships with no channels of its own. It loads free playlists by URL. The default is the [iptv-org](https://github.com/iptv-org/iptv) Malayalam list.

> Private-use project. See [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) for the full plan and [UI_UX_DESIGN.md](UI_UX_DESIGN.md) for the design spec. An interactive prototype is in `design/zaptv-prototype.html`.

## What works now (v0.1)

- Opens on the last watched channel, with the channel banner and a remote hint.
- ▲/▼ (or CH+/CH−) change channel. Quick presses only move the banner, and the channel tunes once you stop.
- Number keys jump to a channel (cable-style numbers: News 101+, Music 301+, Devotional 501+, Education 601+).
- BACK returns to the previous channel. Pressing BACK twice quickly asks before exiting.
- OK opens the channel list with groups (Favourites, All, News, Entertainment…). Hold OK to add a favourite.
- ▶ opens Options: favourite, picture size, Up-button direction, stats for nerds, update channel list.
- If a stream fails, the next source is tried automatically. If all fail, a clear message counts down to the next channel.
- The channel list refreshes once a day. Numbers and favourites survive refreshes.

## Build

You need JDK 17+ and the Android SDK (platform 37, build-tools 37). Android Studio includes both.

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew :core:parser:test      # playlist parser tests
./gradlew assembleRelease        # smaller, optimised APK (currently signed with the debug key)
```

Movies & Series from Telegram (in progress, see [TELEGRAM_PLAN.md](TELEGRAM_PLAN.md)) needs your own free API credentials from [my.telegram.org](https://my.telegram.org) in `local.properties`, which is never committed:

```properties
TG_API_ID=1234567
TG_API_HASH=your_api_hash
```

Without them everything still builds and the library reports that it isn't set up in this build.

On this PC the toolchain lives in `C:\Users\DGNXT-LAP\zaptv-toolchain` (JDK, SDK, Gradle). Set `JAVA_HOME` to its `jdk` folder before running `gradlew`. `local.properties` points at its SDK.

## Install on the TV

1. On the TV: **Settings → System → About**, click **Android TV OS build** 7 times to turn on Developer options.
2. **Settings → System → Developer options**: turn on **USB debugging** (and **Network debugging** if it's there).
3. Note the TV's IP address (**Settings → Network & Internet**).
4. From the PC:
   ```bash
   adb connect <tv-ip>:5555
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
   Accept the prompt on the TV the first time. `adb` is in `zaptv-toolchain\android-sdk\platform-tools`.
5. ZapTV appears in the TV's app row.

Alternative without a PC: upload the APK somewhere, then install it with the free **Downloader** app on the TV.

## Project layout

```
app/                   Entry point, manifest, DI wiring, banner and icons
core/model/            Channel, Category, settings (plain Kotlin)
core/parser/           M3U parser and lineup builder (plain Kotlin, unit-tested)
core/database/         Room database: channels, sources, favourites
core/data/             Repositories: playlist download, channels, settings
core/player/           The single ExoPlayer, fast-start buffering, source failover
core/designsystem/     Colours, fonts, focus style, shared TV components
feature/live/          The live TV screen and all its overlays
design/                Interactive UI prototype
third_party/fonts/     Font licences (SIL Open Font License)
```

## Keep the public repo clean

Commit code and playlist URLs only. Personal channel lists (`my-channels.m3u`), signing keys and tokens stay out of git (see `.gitignore`).

## Licence

Not chosen yet. GPL-3.0 is recommended (see the plan, section 19). Bundled fonts are under the SIL Open Font License.
