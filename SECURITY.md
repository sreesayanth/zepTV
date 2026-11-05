# Security Policy

## Supported versions

ZapTV is in early development. Security fixes are made on the `main` branch and included in the next release.

| Version | Supported |
|---|---|
| `main` / latest release | ✅ |
| Older releases | ❌ |

## Reporting a vulnerability

**Please don't report security problems in public issues.**

Report them privately through GitHub: go to the repository's **Security** tab and choose **[Report a vulnerability](https://github.com/sreesayanth/zeptv/security/advisories/new)**.

Please include:

- what the problem is and what an attacker could do with it
- steps to reproduce, or a proof of concept
- the ZapTV version and device you tested on

You can expect an acknowledgement within 7 days. We'll keep you updated while we work on a fix and credit you in the release notes, unless you'd rather stay anonymous.

## Scope

Especially relevant areas:

- Telegram sign-in and session storage (`core/telegram`)
- Handling of untrusted playlist files and stream URLs (`core/parser`, `core/player`)
- Anything that could leak Telegram API credentials or user data
