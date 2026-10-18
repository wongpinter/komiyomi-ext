<!-- KMK -->
# EPUB reading fonts

The EPUB reader bundles six optional font families from the official
[Google Fonts repository](https://github.com/google/fonts). Their reader roles follow
the requested Kindle- and Kobo-inspired slots; the familiar names are comparison points,
not claims that these typefaces are equivalent to proprietary fonts. These are reading
options, not a ranking of universally best fonts.

| Family | Reader role |
| --- | --- |
| [Literata](https://github.com/googlefonts/literata) variable | Book serif; Bookerly-inspired slot |
| [Crimson Pro](https://github.com/google/fonts/tree/main/ofl/crimsonpro) variable | Classic serif; Baskerville/Palatino-inspired slot |
| [Bitter](https://github.com/google/fonts/tree/main/ofl/bitter) variable | Slab serif; Caecilia/Amasis-inspired slot |
| [Merriweather](https://github.com/google/fonts/tree/main/ofl/merriweather) variable | Sturdy serif; Georgia-inspired slot |
| [Inter](https://github.com/google/fonts/tree/main/ofl/inter) variable | Sans serif; Ember/Avenir Next-inspired slot |
| [Atkinson Hyperlegible Next](https://github.com/googlefonts/atkinson-hyperlegible-next) variable | Distinctive accessibility option; not a medical accessibility guarantee |

Personal preference, font size, weight, spacing and display quality affect readability.
This is not a universal CJK, Arabic or Indic font collection.

## Files and provenance

- Font assets are stored under `app/src/debug/assets/epub-fonts/<family>/` and included in all app variants through the main asset source set.
- Each family has upright and italic TrueType variable fonts and its original `OFL.txt`.
  Weight ranges are declared from the families' published metadata. Original filenames
  and font internals are preserved.
- The fonts and licenses total **14,270,986 bytes** (about 13.6 MiB), excluding the manifest.
  Merriweather contributes 9,226,348 bytes across its two full variable TTF files.
- `manifest.json` records each official raw source URL, Git blob SHA-1, SHA-256 and byte size.
- Licenses are the SIL Open Font License 1.1 and remain beside their respective font files.

## Reader behavior

The font picker appears for reflowable EPUBs under **EPUB reading settings → Reading font**.
It offers the six bundled families plus **Publisher / embedded fonts**. The selection is saved
with the current book and applies immediately. Cancel leaves the current selection unchanged.
Fixed-layout books do not expose the picker because their pages cannot reflow.

A book starts with its publisher/default styles. Saved selections from the replaced Source Serif 4,
Lora or Source Sans 3 options migrate to **Publisher / embedded fonts**. Choosing that option clears
the app font override and restores publisher styles, allowing the EPUB's CSS and embedded
`@font-face` fonts to take effect. Choosing a bundled family applies its font override while
preserving the book's other preferences. The current locator is checkpointed before the change;
Readium reflows in the existing navigator without reopening the book.

`EpubFonts.kt` registers upright and italic variable faces with these weight ranges:
Literata **200–900**, Crimson Pro **200–900**, Bitter **100–900**, Merriweather **300–900**,
Inter **100–900**, and Atkinson Hyperlegible Next **200–800**. Merriweather also has optical-size
and width axes; Literata has optical-size; Inter has optical-size. Assets load on demand, not
all at once. Serif or sans-serif fallbacks are declared for missing glyphs.

Readium 3.2 uses Android `PATTERN_SIMPLE_GLOB` to control served assets. Only the twelve literal
TTF paths are allowed, with periods escaped; brackets and commas remain literal. `addSource(String)`
encodes decoded paths itself, so pre-encoding would double-encode them. See the
[Readium 3.2 EPUB font guide](https://github.com/readium/kotlin-toolkit/blob/3.2.0/docs/guides/navigator/epub-fonts.md).

Build and device verification for this six-font revision are tracked with the feature work.
<!-- KMK -->
