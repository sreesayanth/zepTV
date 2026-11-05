# Changelog

All notable changes to ZapTV are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses [Semantic Versioning](https://semver.org/).

## Unreleased

### Added
- Apache-2.0 licence and open-source project files: contributing guide, code of conduct, security policy, issue and pull request templates, CI.
- `core/telegram` (not yet connected to the app): Telegram sign-in by QR code or phone number, listing chats with videos, and streaming playback while downloading.
- `MediaNameParser`: reads title, year, season/episode, quality, codec, HDR, source, languages and split parts from video file names.

### Changed
- Plans and design specs moved to `docs/`.

## 0.1.0 - 2026-10-26 (not tagged)

### Added
- Live TV screen that opens on the last watched channel.
- ▲/▼ and CH+/CH− zapping with debounce, number entry, and BACK to recall the previous channel.
- Channel list with groups and favourites, and an Options panel.
- Automatic source failover and an "unavailable" countdown.
- Daily playlist refresh that keeps channel numbers and favourites.
