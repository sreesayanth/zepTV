# ZapTV — UI/UX Design Spec

> Interactive prototype: [`design/zaptv-prototype.html`](../design/zaptv-prototype.html) (open it in a browser).
> All sizes are **Android TV dp**. A 1080p TV is **960 × 540 dp**. The same numbers go straight into Compose (`.dp` / `.sp`).

---

## 1. Six rules

1. **Video first.** Live TV is the home screen. The app opens on the last channel watched. Every menu is an overlay on top of the playing picture, which never stops.
2. **The smallest remote reaches everything.** The Google TV / Chromecast remote has only the D-pad, OK, BACK and HOME: no number, channel, Guide or colour keys. Every feature must work with those keys alone. Options (▶) holds TV guide, Search and Settings. Bigger remotes get shortcuts.
3. **Feedback within 100 ms.** The channel banner appears on the key press, before the stream loads.
4. **Never a black screen.** Loading, unavailable and digital-only channels each have a clear message and a way out.
5. **Readable from the sofa.** Nothing smaller than 12 dp; main text 15–21 dp; strong focus (cream fill + gold ring); a **Large text** setting (×1.14).
6. **Two languages, one layout.** Every string exists in English and Malayalam. Layouts allow ~30% longer Malayalam strings (ellipsis, never clipping mid-glyph).

---

## 2. Remote map

| Key | On live TV | In menus |
|---|---|---|
| ▲ ▼ | Next / previous channel (direction is a setting) | Move focus |
| ◀ | Channel list, focus on groups | Move; back one column |
| ▶ | Options panel | Move; change a value |
| OK | Channel list | Select |
| Hold OK (600 ms) | Add/remove favourite | Favourite the focused channel |
| BACK | Previous channel. Twice within 1 s → exit prompt | Close overlay / one step back |
| 0–9 | Number entry | Type in search |
| CH+ / CH− | Next / previous channel | Page up / down (8 rows) |
| GUIDE | TV guide | Toggle |
| INFO | Expanded banner (8 s) | — |
| MENU | Settings | Toggle |
| LAST | Previous channel | — |
| Red / Green / Yellow / Blue | Favourites / Groups / Search / Guide | Same |

Holding ▲/▼ repeats every ~170 ms after 450 ms, and the zap debounce takes care of the rest.

---

## 3. Screens

### 3.1 Live TV (home)
- Full-screen video, `SurfaceView`. Nothing else is shown until a key is pressed.
- On launch: last channel starts playing immediately, banner visible for 9 s, coach toast for 7 s: "▲ ▼ Channels · OK Channel list · ▶ Options".

### 3.2 Channel banner
- Position: left/right 48 dp, bottom 27 dp, 864 × ~140 dp, radius 16, panel colour, blur 10.
- Grid: logo 72 dp | main | clock. Main: **number** (30 dp gold, condensed, tabular) + **name** (21 dp bold) + HD badge + ★ if favourite + group chip when zapping inside a group (e.g. "News").
- NOW row: label, time range, title (bold), progress bar 110 × 4 dp, "18 min left". NEXT row: muted.
- Hint row (13 dp muted): ▲▼ Channels · OK Channel list · ▶ Options · BACK Previous channel.
- Shows on every zap *immediately* with the target channel (even during debounce); auto-hides after 4.5 s. INFO expands it with the description and stream details (8 s).
- Bottom scrim: 270 dp gradient to 72% black behind the banner.

### 3.3 Zapping and loading
- Single press: tune immediately. Presses < 400 ms apart: banner follows each press; tune 350 ms after the last.
- Loading: the old picture fades out (120 ms), centre shows the channel logo inside a gold spinner ring + "Loading · 102 Asianet News".
- If source 1 fails: "Trying another source…" (same screen, no error yet).

### 3.4 Unavailable channel
- Centre card 540 dp: red "Unavailable" pill, "{Channel} isn't available right now", one sentence saying both sources failed and it will be rechecked, hints ▲▼ Change channel · OK Try again, gold countdown "Next channel in 5 s" → auto-zap in the last direction.
- Channel is marked unavailable in the list (muted, "Unavailable"). Setting "Hide unavailable channels" removes it from zapping.

### 3.5 Digital-only channel (e.g. Kappa, which stopped broadcasting 25 Jun 2026)
- Keeps its number (301). Card: logo, gold "Digital channel" pill, one sentence of context, OK Open in YouTube (deep link to the YouTube TV app).

### 3.6 Number entry
- Top-right box: three digit cells 36 × 52 dp, 44 dp condensed gold digits, empty cells show "–".
- Under it: exact match name ("Manorama News"), or candidate numbers ("101, 102, 103…"), or "No channel 7".
- Gold bar shrinks over 2 s, then tunes. OK tunes now. BACK clears.

### 3.7 Channel list (OK / ◀)
- Left panel 560 dp over the video (video keeps playing on the right).
- Column 1 (172 dp): GROUPS — Favourites, All channels, News, Entertainment, Music, Devotional, Education, each with a count. Selected group = gold text.
- Column 2: group title + clock, 8 visible rows of 46 dp with ▲/▼ "more" markers. Row: number (17 dp, gold if playing) | logo 30 | name (15 dp bold) + now-title (13 dp muted) | ★ / playing bars | 2 dp progress line.
- Bottom-right "peek" card 330 dp: focused channel's now programme, time, time left, description, next.
- Footer: OK Watch · ◀ Groups · Hold OK Favourite. Empty favourites: "No favourites yet. Hold OK on any channel to add it here."
- OK tunes and closes the list; CH+/CH− page by 8; digits close the list and start number entry.

### 3.8 Options (▶)
- Right panel 340 dp. Header: logo 40 + "102 Asianet News" + "Options".
- Items (38 dp): Add/Remove favourite · TV guide · Search · Audio ‹ Malayalam › · Subtitles · Picture size ‹ Fit / Fill / Zoom › · Sleep timer ‹ Off / 30 / 60 / 90 min › · Stats for nerds ‹ Off / On › · Programme info · Settings.
- ◀ ▶ changes values in place; ◀ on an action row closes the panel.

### 3.9 TV guide
- Full screen (97% ink). Top 150 dp: details (pill "On now" red / "Later", title 26 dp, channel · time · "18 min left" / "Starts at 10:30 pm", 2-line description) + **mini live preview** 240 × 135 dp of the channel being watched.
- Grid: channel column 160 dp (number, logo 24, name), timeline 696 dp = 2 hours (4 × 30 min labels), 5 rows × 42 dp, programme cells radius 8 with 4 dp gaps; "on now" cells slightly lighter; **gold now-line** with a dot cap.
- ◀ ▶ moves programme by programme (cannot go before the current programme), window scrolls in 30-min steps; ▲ ▼ keeps the same time position across channels.
- OK on a current programme → watch; on a future one → toggle reminder (bell icon in the cell + toast).

### 3.10 Search
- Full screen. Left 392 dp: field (52 dp) + **alphabetical 6-column keyboard** (a–z, 0–9) + Space / Delete / Clear row. Right: results.
- Empty query shows **Recently watched**. Results: "Channels" then "On now and next" (programme title, channel, time). Max 7.
- ▶ from the last keyboard column jumps to results. Physical/phone keyboards type directly.

### 3.11 Settings (MENU / Options → Settings)
- Right panel 480 dp, rows 38 dp, values changed with ◀ ▶ and applied immediately:
  Channel lists › · Up button (▲ = next / previous channel) · Playback (Fast start / Balanced / Stable) · Hide unavailable channels · Parental lock · Language · ഭാഷ · Text size (Normal / Large) · Stats for nerds · Run first-time setup › · About.
- **Channel lists** sub-page: each list with source and last refresh, "+ Add a channel list" → **QR code + LAN address** (`192.168.1.20:8080`) so links are pasted from a phone, never typed with a remote.

### 3.12 First run (3 screens max, no typing)
1. Welcome · സ്വാഗതം — two big cards: English / മലയാളം.
2. "What would you like to watch?" — tiles with check circles: Malayalam (74 channels), All of India, World news, My own list → Continue.
3. "You're all set" — 4 remote hints → **Start watching** → live TV.
- Kasavu border (gold line + band) along the bottom edge; step dots.

### 3.13 Exit prompt
- BACK twice quickly on live TV → "Exit ZapTV?" with **Keep watching** focused, Exit second.

---

## 4. Design tokens

### Colour (TV is always dark)
| Token | Hex | Use |
|---|---|---|
| `tvBlack` | `#000000` | Video background |
| `nightInk` | `#070B0D` | Full-screen overlays (at 97%) |
| `panel` | `#090F11` @ 86% | Banner, cards, toasts (with 10 dp blur where supported) |
| `line` | white @ 9% | Dividers, panel borders |
| `kasavuWhite` | `#F4F1E8` | Primary text (≈16:1) |
| `mist` | `#A3AFAC` | Secondary text (≈8:1) |
| `kasavuGold` | `#E6BE5A` | Focus ring, now-line, channel numbers, progress (≈10:1) |
| `focusCream` | `#F4ECD6` | Focused item fill |
| `focusInk` | `#17130A` | Text on focus (≈15:1) |
| `focusMuted` | `#5E5440` | Secondary text on focus (≈6:1) |
| `liveRed` | `#E5484D` | LIVE / On now pill only |

Gold and cream come from the **kasavu** border of the Kerala saree.

### Type
| Role | Font | Size (dp) | Weight |
|---|---|---|---|
| Channel number / clock | Barlow Semi Condensed, tabular | 30 / 21 | 600 |
| Screen title | Atkinson Hyperlegible Next | 26 | 700 |
| Banner channel name | Atkinson Hyperlegible Next | 21 | 700 |
| Row title | Atkinson Hyperlegible Next | 15–17 | 700 |
| Body | Atkinson Hyperlegible Next | 15 | 400 |
| Meta | Atkinson Hyperlegible Next | 13 | 400 |
| Labels / hints (minimum) | Atkinson Hyperlegible Next, caps, +0.1em | 12 | 700 |
| Malayalam | Manjari (falls back per glyph) | same | same |

Bundle the fonts in `res/font` (all are OFL), so nothing downloads at runtime.

### Spacing, shape, elevation
- **Safe area:** 48 dp sides, 27 dp top/bottom. Nothing interactive or textual outside it.
- **Grid:** 8 dp (4 dp for tight gaps). List row 46 dp, menu row 38 dp, guide row 42 dp.
- **Radius:** 8 (cells), 10 (rows), 14 (cards), 16 (banner), 18 (centre cards), pill = full.
- **Focus:** fill `focusCream`, 2 dp `kasavuGold` ring, scale 1.03 (1.0 for guide cells), shadow 0/10/24 @ 45% black.

### Motion
| Moment | Value |
|---|---|
| Focus move | 120 ms ease-out |
| Overlay open | 180 ms fade + 8–16 dp slide (side panels slide from their edge) |
| Banner hide | 4.5 s (info 8 s) |
| Zap debounce | presses < 400 ms apart → tune 350 ms after the last |
| Number entry commit | 2 s |
| Unavailable auto-next | 5 s |
| Toast | 2.6 s |
| Reduced motion (Settings › Accessibility › Remove animations) | no slides/scales, instant changes |

---

## 5. Compose implementation notes

```kotlin
object ZapColors {
    val TvBlack = Color(0xFF000000)
    val NightInk = Color(0xFF070B0D)
    val Panel = Color(0xDB090F11)          // 86 %
    val Line = Color(0x17FFFFFF)           // 9 %
    val KasavuWhite = Color(0xFFF4F1E8)
    val Mist = Color(0xFFA3AFAC)
    val KasavuGold = Color(0xFFE6BE5A)
    val FocusCream = Color(0xFFF4ECD6)
    val FocusInk = Color(0xFF17130A)
    val FocusMuted = Color(0xFF5E5440)
    val LiveRed = Color(0xFFE5484D)
}
```

**Focusable rows** use tv-material `Surface(onClick = …)` with:
```kotlin
colors = ClickableSurfaceDefaults.colors(
    containerColor = Color.Transparent, contentColor = ZapColors.KasavuWhite,
    focusedContainerColor = ZapColors.FocusCream, focusedContentColor = ZapColors.FocusInk),
border = ClickableSurfaceDefaults.border(
    focusedBorder = Border(BorderStroke(2.dp, ZapColors.KasavuGold))),
scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
```

**Remote keys** on the live screen: `Modifier.onPreviewKeyEvent` on the root, handling `Key.DirectionUp/Down/Left/Right`, `Key.DirectionCenter`/`Key.Enter` (long-press via `nativeKeyEvent.repeatCount` / `isLongPress`), `Key.Back`, `Key.ChannelUp/Down`, `Key.Zero..Key.Nine`, `Key.Guide`, `Key.Info`, `Key.LastChannel`, `Key.ProgramRed…Blue`, `Key.Menu`, `Key.Search`.

**Text size**: multiply all `sp` values by a `LocalTextScale` (1.0 / 1.14) provided at the root.

**Strings**: `values/strings.xml` + `values-ml/strings.xml`; the in-app language setting uses `AppCompatDelegate.setApplicationLocales` (per-app language).

---

## 6. Movies & Series from Telegram (TV)

> Engineering plan: [TELEGRAM_PLAN.md](TELEGRAM_PLAN.md).

**Rule zero: live TV does not change.** The app still opens on the last channel; ▲▼ ◀ OK BACK keep their meanings. The library is one extra Options row, shown only when Settings › *Movies & Series* is On (default Off). Same tokens, fonts and focus style as §4; no new colours.

### 6.1 Entry (Options ▶)
- New row after *TV guide*: icon + **Movies & Series** + muted "Telegram" + ›. Row 38 dp (44 focused, standard focus).
- Settings adds: Movies & Series ‹ Off / On › · Telegram account › · Chats shown in library › · Download whole video first ‹ Off / On › · Clear video cache.

### 6.2 Sign in (first time, no typing)
- Full screen `nightInk`. Eyebrow "Movies & Series · Step 1 of 2" (gold, 12 dp caps). Title 26 dp, one-line privacy promise in `mist`: *only videos from chats you pick; never shows or sends messages*.
- Left: three numbered steps (30 dp gold-ring numbers, 17 dp text): Open Telegram → Settings › Devices › Link Desktop Device → point at this code.
- Right: QR on a white 260 dp card (radius 18), "New code in 0:24" countdown under it; refreshes itself.
- Two-step password: next screen reuses the Search keyboard (§3.10).
- Kasavu border and step dots as in first run (§3.12). BACK → live TV.

### 6.3 Pick chats (step 2 of 2)
- Title "Which chats have your movies and series?". 3-column grid of 84 dp tiles (radius 14): 48 dp initial avatar, chat name (15 dp bold), video count (13 dp), check circle (gold filled = chosen).
- Only chats that contain videos are listed. Footer: "Continue with N chats ›" pill.

### 6.4 Library home
- Header: "Movies & Series" (26 dp) · chip with last channel logo + "**BACK** Live TV · 102" · clock.
- Rows (◀▶ inside a row, ▲▼ between rows, 12 dp caps labels):
  1. **Continue watching** — 240 × 135 cards, radius 10, 4 dp gold progress at the bottom, title + "33 min left" under the card.
  2. **Series** — 112 × 168 posters, season-count pill top-left.
  3. **Movies** — same posters, year · size.
  4. **Chats** — one row per chosen chat, newest first.
- Card focus: 3 dp gold ring + scale 1.03 (no cream fill on artwork). Bottom fade to `nightInk` hints more rows.
- No live mini-preview here: the live player is stopped to keep memory low on 2 GB boxes.

### 6.5 Series page
- Left: 236 × 300 poster, "2 seasons · 16 episodes", source chat name.
- Right: title 26 dp; buttons (44 dp pills): **Resume S1 E4 · 33 min left** (focused by default), Mark season watched, Watch later.
- Season tabs (◀▶): active = gold text + 2 dp gold underline.
- Episode rows 46 dp: number (gold if in progress) · title · duration or "33 min left" · gold tick if watched · 2 dp progress line under in-progress rows.
- OK play · Hold OK mark watched · BACK library.

### 6.6 Movie player
- Bottom bar reuses the **channel banner** panel (left/right 48, bottom 27, radius 16, `panel`): "S1 E4" in gold condensed + title 21 dp bold + file info (MKV · 1080p · size) muted.
- Time bar 4 dp: gold = watched, `kasavuWhite` 45 % = downloaded so far, track white 18 %; 16 dp gold thumb; seek pill (gold, "+30 s") above the thumb while seeking.
- Controls row (38 dp pills): Play/Pause (focused) · Audio · Subtitles · Size · Speed · … · Next episode ›.
- Top-right chip: "Downloaded 55 % · 3.1 MB/s" (shown with controls, like Stats for nerds).
- Loading = channel loading spinner (§3.3). Error = Unavailable card (§3.4) with "Can't play this video", OK Try again.
- Resume prompt when a position exists: **Resume from 41:20** (focused) / Start over. Next-episode card bottom-right, 8 s countdown.

| Key | Player |
|---|---|
| OK | Play / pause, shows controls |
| ◀ ▶ | Seek −10 / +10 s; hold accelerates (10 → 30 → 60 s steps) |
| ▲ ▼ | Show / walk controls row |
| CH+ / CH− | Next / previous episode |
| BACK | Close controls → else stop, save position, back to library |

### 6.7 Home with featured title (Netflix-style)
- Top 340 dp: TMDB backdrop on the right, left scrim to `nightInk` for legibility. Title as a 52 dp condensed caps wordmark (or the TMDB title logo when one exists), IMDb rating chip (outlined, neutral — not IMDb's brand yellow), year · runtime · genres, quality badges (4K, 5.1, HDR), 2-line overview.
- Buttons: **Play** (focused, cream) · More info · + My List. Rotates every 10 s between recent / top-rated titles; dots bottom-right.
- Rows below: Continue watching · New in your chats · New episodes · Top rated (IMDb ≥ 7.5) · per language · per genre · Because you watched X · Collections · actor rows. Cards 200 × 112 with a quality badge and "IMDb 8.4 · 2022 · Thriller" under them.
- **Preview on focus:** after 1.5 s on a card, a muted 20 s clip plays inside the card (Settings › Previews).

### 6.8 Detail page + Versions
- Backdrop dimmed with a left scrim; title 44 dp caps, IMDb chip, year · runtime · certificate · genres, overview, buttons **Play · Auto** · Trailer · My List · Fix match.
- Cast row: 56 dp round photos (TMDB) with names. Audio and subtitle languages line (notes "`.srt` from chat" when a subtitle file was found next to the video).
- **Versions panel** (right, 340 dp, same as Options): header "Choose version" + "4 copies found in 3 chats · your speed 18 MB/s". Rows: **Auto · 1080p** (default, "Best for this TV and your connection"), then each copy with quality, size (gold condensed), audio languages and source chat. A copy this TV cannot decode is muted with an amber note "Won't play smoothly on this TV". Split uploads say "2 parts · Plays as one video".
- The same panel opens from the player's controls row (Quality) and switches at the current time.

### 6.9 Chat as a TV channel (901+)
- Opt-in per chat (Settings › Chats shown in library › *Show as channel*). The chat joins the live lineup from 901 and behaves like any channel: ▲▼ zaps to it, it shows in the channel list and guide.
- Standard channel banner (§3.2): chat avatar as logo, **901** gold number, chat name, "Telegram channel" and quality chips, NOW/NEXT from a schedule computed by chaining video durations (looping), progress + "54 min left".
- Options adds **Watch from the start** for the current video (opens it in the movie player).

---

## 7. Phone app

Same repo, same APK, same tokens (the phone app is dark too). Shared ViewModels and state; only the screens differ. Touch targets ≥ 44 dp. Bottom navigation: **Live TV · Movies & Series · Search · Settings** (Movies & Series is hidden when the feature is Off).

### 7.1 Live TV (portrait)
- 16:9 video on top with LIVE pill and full-screen button. Swipe the picture up/down to zap (same debounce as §3.3); tap shows the banner.
- Under it, banner card: gold number (26 dp) + name + favourite star, NOW row with progress, hint "Swipe the picture up or down to change channel" (first sessions only).
- Group chips (All, Favourites, News…), then channel rows 64 dp: number · 36 dp logo · name + now-title · playing bars. The playing row has a gold 10 % tint.
- Landscape = full-screen video with the TV banner layout scaled to the phone.

### 7.2 Movies & Series
- Title + search button; filter chips All / Movies / Series / Chats.
- Continue watching (220 × 124 cards with play button and progress), Series row (104 × 156 posters, "See all"), Movies 3-column grid.

### 7.2a Movie detail
- 220 dp backdrop with back and Cast to TV buttons, title 36 dp caps over a bottom fade. IMDb chip, year · runtime · certificate, quality badges.
- Gold **Play** (52 dp), then a **Version** row ("Version · 4 copies in 3 chats" / "Auto · 1080p · 2.1 GB" ›) that opens a bottom sheet with the same rows as the TV Versions panel.
- Icon actions: My List · Trailer · Download · Fix match. Overview, Cast row, More like this.

### 7.3 Series page
- 230 dp artwork header with back button and fading title (34 dp condensed). Gold full-width **Resume** button (52 dp), Watch later / Download season, Season segmented control, episode rows with 112 × 63 thumbnails, progress and "⋮" menu.

### 7.4 Connect Telegram
- Phone number first (country picker + number field, "Send code"), code boxes next, password if set. **"Show a QR code instead"** for an account on another phone. Same privacy line as the TV.

### 7.5 Player (landscape)
- Top: back, title, "S1 E4 · Downloaded 55 % · 3.1 MB/s", Cast to TV, Picture-in-picture.
- Centre: −10 · gold 76 dp Play/Pause · +10. Double-tap left/right edges seeks ±10 s; vertical swipes on the left/right halves set brightness/volume.
- Bottom: same time bar as the TV (gold watched, light downloaded), then Audio & subtitles · Speed · Lock · Next episode ›.
- Leaving the app while playing → picture-in-picture.
