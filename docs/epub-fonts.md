<!-- KMK -->
# EPUB reading font candidates

Downloaded 2026-10-17 from the official [Google Fonts repository](https://github.com/google/fonts).
These are five reading candidates, not an evidence-based ranking of universally best fonts.
Personal preference, font size, weight, spacing and display quality also affect readability.

| Family | Role in the reader | Selection rationale |
| --- | --- | --- |
| [Literata](https://github.com/googlefonts/literata) | Suggested initial serif candidate | Designed for screen reading and originally Google Play Books; try this first for novels. |
| [Source Serif 4](https://github.com/adobe-fonts/source-serif) | Alternative book serif | A restrained editorial alternative with optical-size and weight axes. |
| [Lora](https://github.com/google/fonts/tree/main/ofl/lora) | Softer serif alternative | A different, more calligraphic texture for readers who prefer it over Literata/Source Serif. |
| [Atkinson Hyperlegible Next](https://github.com/googlefonts/atkinson-hyperlegible-next) | Character-distinction sans | Deliberately distinctive letterforms; worth offering for readers who find similar characters hard to distinguish. Not a medical accessibility guarantee. |
| [Source Sans 3](https://github.com/adobe-fonts/source-sans) | Neutral sans alternative | A less stylized sans option for readers who prefer sans-serif body text. |

## Files and provenance

- Staged under `app/src/debug/assets/epub-fonts/<family>/`; **debug builds only**.
- Each family has original, unmodified upright and italic variable TrueType fonts,
  plus its original `OFL.txt`. Weight axes provide regular/bold variants without
  additional static font files. Original upstream filenames are retained.
- Total font/license bytes: **5,658,630** (about 5.4 MiB), excluding the manifest.
- `manifest.json` records the source URL, Google repository Git blob SHA-1,
  SHA-256 and byte size for every font and license.
- Downloads matched the Google repository blob hashes and sizes. TrueType signatures,
  table bounds and required outline/character-map/name/variation tables were checked.
- Original license files were checked for the SIL Open Font License 1.1 header and
  retained alongside each family. Do not strip licenses or rename font internals.

## Integration status

**Registered in the debug EPUB navigator and selectable per book.**
Open a reflowable book, reveal controls, then choose **EPUB reading settings → Reading font**.
Choose one of the five families or **Publisher / default font**. Choices apply immediately
and are serialized in the existing per-book preferences. Cancel does not change the font.
The picker is omitted for fixed-layout books.

No manga/app typography or initial font preference has been changed: publisher/default
remains the starting choice. Returning to it clears only the font override, preserving
text size, reading mode, margins and other settings. The current locator is checkpointed
before a font change; the existing navigator performs reflow without reopening the book.

`EpubPrototypeFonts.kt` registers upright/italic faces and weight ranges measured from
the actual TTF `fvar` tables: Literata/Source Serif 4/Source Sans 3 **200–900**,
Lora **400–700**, Atkinson Hyperlegible Next **200–800**. Font files are loaded on demand,
not all preloaded. Serif/sans-serif fallbacks are declared for missing glyphs.

Readium 3.2 uses Android `PATTERN_SIMPLE_GLOB` for served assets, not regex: only
the ten literal TTF paths are allowed, with escaped periods. `addSource(String)`
encodes decoded paths itself, including brackets/commas; passing pre-encoded paths
would double-encode them. The pinned guide contains outdated API names; implementation
uses `EpubNavigatorFragment.Configuration` and `setFontWeight(range)` from actual 3.2 sources.

Verification: ordered `spotlessApply → spotlessCheck → assembleDebug` passed; five font
catalogue/selection/asset-range tests, ten store tests and eight importer tests passed.
All fifteen original font/license files plus the manifest were verified byte-for-byte
inside the arm64 debug APK. Font declarations/URL serving are Android-dependent and
are not exercised by the JVM-only tests.

**Rendered UI and font/reflow behavior are not verified without an Android device.**
Verify real font loading, publisher bold/italic emphasis, weight/optical sizing, large-text
wrapping, TalkBack/focus, and latest-location restoration after changing fonts and restarting.
This set is not a universal CJK/Arabic/Indic font collection.

Primary integration reference: [Readium 3.2 font guide](https://github.com/readium/kotlin-toolkit/blob/3.2.0/docs/guides/navigator/epub-fonts.md)
and the pinned navigator/font-declaration/WebViewServer sources.
<!-- KMK -->
