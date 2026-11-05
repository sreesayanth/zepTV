# Contributing to ZapTV

Thanks for helping make ZapTV better. This guide explains how to report problems, suggest ideas and send code.

By taking part you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Ways to help

- **Report a bug:** open an issue with the *Bug report* template. Include your TV or device model, Android version and ZapTV version (TV **Settings → Apps → ZapTV**).
- **Suggest a feature:** open an issue with the *Feature request* template. Describe the problem before the solution.
- **Improve the docs:** typo fixes and clearer wording are always welcome.
- **Send code:** pick an issue labelled `good first issue` or `help wanted`, or open an issue first for anything large so we can agree on the approach.

## The one rule

**Live TV must stay fast and simple.** Every change is judged by whether the app still opens straight onto a playing channel and ▲/▼ still feels instant on a cheap TV box. New features go behind Options and must not slow down startup. See [docs/IMPLEMENTATION_PLAN.md §9](docs/IMPLEMENTATION_PLAN.md) and [docs/TELEGRAM_PLAN.md §1](docs/TELEGRAM_PLAN.md).

## Development setup

1. Install JDK 17+ and Android Studio (or the Android SDK with platform 37 and build-tools 37).
2. Fork and clone the repo, then open it in Android Studio.
3. Build and test:
   ```bash
   ./gradlew assembleDebug
   ./gradlew test
   ```
4. Test on a real Android TV or the Android TV emulator. Use only the remote's D-pad, OK, BACK and number keys.

Telegram features need your own API credentials in `local.properties` (see the [README](README.md#optional-telegram-api-credentials)). Never commit them.

## Pull requests

1. Create a branch from `main` (`fix/short-description` or `feat/short-description`).
2. Keep each pull request focused on one change.
3. Add or update unit tests for logic changes. Parsers and other plain-Kotlin code in `core/` should always have tests.
4. Make sure `./gradlew assembleDebug test` passes.
5. Update the docs and [CHANGELOG.md](CHANGELOG.md) (under *Unreleased*) when behaviour changes.
6. Fill in the pull request template, including how you tested on a TV or emulator.

### Code style

- Kotlin official code style (`kotlin.code.style=official`). Android Studio's default formatter is fine; [.editorconfig](.editorconfig) sets indentation.
- Clear names over comments. Comment *why*, not *what*.
- Every screen must be fully usable with a D-pad remote and must show a clear focus state.
- Keep feature code in `feature/*` modules and shared logic in `core/*`. Don't make `feature/live` depend on other features.

### Commit messages

Use a short imperative summary line (≤ 72 characters), for example `Fix banner flicker when zapping quickly`. Add detail in the body if needed.

## Licensing of contributions

ZapTV is licensed under [Apache-2.0](LICENSE). By submitting a contribution you agree that it is licensed under the same terms (Apache-2.0, section 5).

- Only submit code you wrote yourself or that comes from an Apache-2.0-compatible source, with attribution.
- **Don't copy, translate or paraphrase code from GPL projects** (including TMPlayer). ZapTV's Telegram module is a clean-room implementation and must stay that way.

## What we won't accept

- Links to pay-TV, pirated or otherwise unauthorised streams, in code, presets, tests or issues.
- Analytics, ads, trackers or anything that sends user data off the device.
- API keys, signing keys or personal playlists in the repository.

## Releasing (maintainers)

1. Update `versionCode` and `versionName` in `app/build.gradle.kts`, and move the *Unreleased* notes in `CHANGELOG.md` under the new version.
2. Commit, then tag and push: `git tag v1.0.0 && git push origin v1.0.0`.
3. The [Release workflow](.github/workflows/release.yml) runs the tests, builds the APK and attaches `ZapTV-v1.0.0.apk` to a GitHub Release.

To sign releases with a real key, add the `ZAPTV_KEYSTORE_BASE64`, `ZAPTV_KEYSTORE_PASSWORD`, `ZAPTV_KEY_ALIAS` and `ZAPTV_KEY_PASSWORD` repository secrets (see the comments at the top of the workflow). Keep a backup of the keystore: without it, installed copies can't be updated without uninstalling first. Locally, export the same variables with `ZAPTV_KEYSTORE_FILE` pointing at the keystore.
