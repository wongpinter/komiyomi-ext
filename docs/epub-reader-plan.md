<!-- KMK -->
# EPUB reader and manager implementation plan

Status: The EPUB reader is integrated into the production app source set, with an Ebook bottom navigation destination and Updates under More.
See [reader verification and known limits](epub-reader-verification.md). The architecture notes below include original proposals that are not part of the current implementation.
Benchmarks: Moon+ Reader for customization; Readest for visual clarity.
Primary acceptance fixture: the user's approximately 1 GB EPUB on a named Android device.
Scope: integrate into Komiyomi Ext without replacing or regressing its comic reader.

## 1. Feasibility and existing behavior

The app now has a dedicated EPUB shelf and reflowable reader, separate from its comic catalog and manga reader.

Verified integration points:

| Existing file/module | Current behavior | Planned use |
| --- | --- | --- |
| `app/.../ui/home/HomeScreen.kt` and `ui/ebook/EbookTab.kt` | Compose/Voyager main navigation | Ebook opens the dedicated EPUB shelf; Updates opens from More |
| `app/.../ui/home/LocalArchiveScreenModel.kt` | Comic scanning, metadata, Manga/Chapter progress | Reuse UI patterns, not its Manga/Chapter conversion flow |
| `source-local/.../io/LocalArchiveCatalog.kt` | Discovers CBZ/CBR archives | Leave comic discovery intact; add a separate EPUB catalog |
| `core/archive/.../EpubReader.kt` | Parses EPUB spine documents to extract images | Preserve existing illustrated/image EPUB compatibility |
| `app/.../ui/reader/loader/EpubPageLoader.kt` | Converts extracted images into ReaderPage objects | Do not stretch this into a text layout engine |
| `domain/.../storage/service/StorageManager.kt` | SAF/UniFile storage roots and changes | Reuse permission/storage conventions; add explicit ebook storage settings |
| `data/src/main/sqldelight/tachiyomi/data/` | Existing SQLDelight database | Add book tables and migrations, without changing manga identity |
| `app/.../data/backup/models/Backup.kt` | Protobuf app backup | Add optional book metadata/progress/preferences with unused field numbers |

Paths containing `...` abbreviate existing package roots, not proposed literal directories.

The comic reader uses image/page-index progress. The ebook reader persists publication
locations that remain meaningful after changing fonts, margins, orientation or screen size.

## 2. Original architecture proposal (not the current implementation)

Start with one isolated engine module, not a new application or a generic reader framework:

- `:core:epub`: publication opening, resource access, engine lifecycle and location mapping.
- `domain/.../ebook/`: Book, BookFile, ReadingLocation, BookPreferences and repository contracts.
- `data/.../ebook/`: repositories and database mappers.
- `app/.../ui/ebook/library/`: book catalog, details, import and management.
- `app/.../ui/ebook/reader/`: dedicated EpubReaderActivity, state holder and Compose controls.
- `app/.../ui/ebook/settings/`: font library, reading presets and storage settings.

The engine module should hide publication resource/lifecycle details behind a small
interface for opening a session, observing state/location, navigating, applying preferences
and closing. Do not expose engine objects throughout library/database code or build
an elaborate multi-engine abstraction before the spike identifies an actual need.

Reuse Compose/Material styling, Voyager for library screens, Injekt registrations,
SQLDelight, coroutines/Flow, Coil for covers and the existing preference infrastructure.
Use a dedicated activity for reading, following the existing app pattern.
A Fragment-based renderer can be hosted there while its controls remain Compose.

Keep Readium-specific dependencies in the EPUB module where practical.
Resolve actual Android dependency visibility during the spike; do not create dependency
cycles between app, domain, data and the new module.

## 3. Engine selection gate

Preferred first candidate: Readium Kotlin from Maven Central.

- The official current requirements list Readium 3.4.0 with compile SDK 37.
- This repository currently uses min SDK 26, compile/target SDK 36,
  Kotlin 2.4.20, JVM 17 and Gradle 9.3.x.
- Readium 3.2.0 is a candidate for a compile SDK 36 integration, NOT an approved version.
- Check actual published artifacts, transitive dependency conflicts, desugaring,
  release packaging, license/NOTICE obligations and security fixes before pinning.
- The newest Compose navigators are documented as experimental/alpha.
  Compare the compatible established navigator against alternatives; do not adopt alpha
  rendering solely because the outer application uses Compose.
- If the acceptable engine needs SDK 37, propose that toolchain upgrade separately.
- Do not embed the entire Readest application or build EPUB layout/pagination from scratch.

Exit: a pinned, compatible engine opens representative books and passes the large-book spike.
If it fails, report measured bottlenecks and evaluate another engine before building the UI.
No engine is approved or claimed to support the user's 1 GB file yet.

## 4. Large-book storage and resource rules

- Never read the entire archive into a ByteArray/String or eagerly build a whole-book DOM.
- Open individual archive resources on demand and keep image/resource caches bounded.
- Do not make full-book pagination, text extraction, hashing or search indexing prerequisites
  for displaying the first readable section.
- Bound adjacent-section preloading; a giant single XHTML section can still exceed a
  WebView's practical limits. Diagnose this explicitly rather than promising automatic repair.
- For seekable local URIs, validate direct engine access.
- For nonseekable or unstable providers, offer a managed local copy with progress,
  cancellation, free-space checks and atomic finalization.
- A managed copy of a 1 GB book requires additional disk space; surface this before copying.
  Do not duplicate a book silently on every open.
- Persist SAF access where permitted; report revoked permissions, moved files and
  missing storage with a relink action, preserving book metadata and reading history.
- Preserve source files unchanged. Cancelled imports remove only their own staging files.
- Validate ZIP paths, resource/decompression limits, malformed XML, hostile HTML and
  unsafe font files; support legitimate large resources without blanket tiny file limits.
- Default to local content: restrict renderer access to approved publication/font resources,
  disallow arbitrary filesystem access and do not allow book scripts or remote resources
  to obtain app privileges. Verify the chosen engine's actual security controls.

## 5. Data and management

Proposed logical records (final schema follows the spike):

- Book: stable app ID, title, authors, language, description, cover reference and timestamps.
- BookFile: book ID, source URI, managed/reference ownership, file availability and fingerprint.
- ReadingState: book ID, versioned locator, section progression, overall progression,
  last-read time and reading status.
- BookPreferences: per-book overrides merged over global reader defaults.
- Collections and membership: independent of manga categories initially.
- Bookmarks/annotations: stable IDs and locator/text anchors; highlights/notes can follow MVP.

Do not identify a book solely by EPUB metadata identifiers or filename.
Use quick file identity for discovery; perform stronger duplicate checks in the background.
A moved file must not silently become an unrelated book, and relinking should validate identity.

Store a versioned engine locator plus practical section/progression fallbacks.
Never persist only a displayed page number. Avoid building millions of synthetic Chapter rows.
Save progress during reading and lifecycle transitions, not only when the activity closes.

Book-manager MVP:

- Import individual files and scan an explicitly selected folder recursively.
- Support Android “Open with” for application/epub+zip through a validated import/open
  gateway in AndroidManifest.xml; handle temporary URI grants immediately, keep the
  reader activity unexported, and never trust a MIME type or filename alone.
- Read metadata and covers in the background; cache results and show partial progress.
- Grid/list views, search, author/title/recent sorting, unread/reading/finished filters.
- Book details, continue reading, collections and relink.
- Distinguish “remove from library” from “delete managed copy.”
  Never delete a referenced external original as a side effect of removing a catalog entry.
- Add rename/move/source deletion only with explicit ownership and user confirmation rules.

Backups must distinguish metadata/preferences/annotations from EPUB binaries.
Default app backups should not silently package gigabytes of books.
Restores must tolerate missing original URIs and offer relinking.
Preserve backward compatibility using optional protobuf additions with unused field numbers.
Declare backup coverage in the UI before release; do not imply unsupported data is protected.

## 6. Reader and design scope

Reflowable EPUB MVP:

- Text rendering, table of contents, internal/external links and footnotes.
- Paginated and vertical scrolling modes, accessible tap targets and adjustable tap zones.
- Imported TTF/OTF fonts: managed font copies, validation, previews, normal/bold/italic mapping,
  fallback fonts and safe behavior when a chosen font is removed.
- Font size, supported font weight, line height, letter/word spacing.
- Left/right/center/justified alignment, horizontal/vertical margins,
  paragraph spacing and indentation.
- Publisher-style mode versus user-customized mode; explain inactive controls.
- Light/dark/sepia/custom color presets, brightness and keep-screen-on.
- Global defaults and per-book overrides, live preview and reset-to-default.
- Saved position and bookmarks; full-text search and annotations are a subsequent slice.

Fixed-layout EPUBs require a separate presentation mode: preserve layout and zoom rather
than promising arbitrary font/margin reflow. Clearly disable inapplicable settings.
RTL/CJK behavior and language-specific limitations need dedicated fixtures.

Visual direction: calm bookshelf, strong cover hierarchy, generous spacing, restrained
reader chrome, and a live-preview settings sheet grouped into Typography/Layout/Appearance.
Use benchmark apps as references, not a copied interface.
Check TalkBack, font scaling, contrast, landscape, tablets and system insets.

## 7. Delivery slices and acceptance gates

### Phase 0 — Compatibility and 1 GB proof of concept

- Obtain actual EPUB, target device/RAM, Android/WebView versions and DRM status.
- Inspect archive layout without eager resource extraction.
- Integrate a pinned engine in an isolated test screen.
- Measure first import separately from first open and warm reopen.
- Record app and renderer-process memory, disk usage, main-thread stalls, section-navigation
  latency and resume accuracy. Do not measure only the JVM heap.
- Exercise beginning/middle/end, large images, rapid navigation, font reflow,
  rotation, background/resume and process death.
- Compare the same book/device in Moon+ Reader and Readest where available.
- Agree device-specific latency/memory budgets from the results.
- Pass: actual file opens and remains usable under agreed budgets, no OOM/ANR,
  no whole-archive memory loading, and location survives reflow/restart.
- Failure: investigate or change engine; do not proceed to feature polish.

### Phase 1 — Persistent book catalog

Add schema/migration, repositories, storage/import, metadata/cover cache, Books view,
details, reading-state persistence and explicit error/relink states.
Pass: restart-safe import/catalog/progress, cancellation-safe staging and comic-library regression tests.

### Phase 2 — Complete core reader

Add reader activity, TOC, links/footnotes, pagination/scroll, imported fonts, layout settings,
themes/presets, per-book overrides and bookmarks.
Pass: settings do not lose reading location; unsupported fixed-layout controls are explicit.

### Phase 3 — Manager and design release readiness

Finish collections, filters, management ownership rules, backup/restore coverage,
accessibility/responsive polish and large-library performance.
Pass: approved screenshots/interactions, no destructive file-management ambiguity,
successful backup/relink tests and benchmark comparison against Phase 0.

### Phase 4 — Advanced features

Incremental/cancelable full-text search, highlights/notes, annotation export, dictionary,
TTS and optional synchronization. Evaluate each separately; none blocks the initial reader.
DRM, PDF, MOBI/AZW, OPDS and cloud sync are not initial-scope promises.

## 8. Test and repository requirements

- Unit/integration: identity/duplicates, preference merging, locator persistence,
  missing/revoked URIs, interrupted copy, low disk, malformed/hostile archives,
  cache invalidation, file ownership, migration and backup round trips.
- Android instrumentation: actual renderer, URI grants, process death/resume,
  font imports, link restrictions and background cancellation.
- Benchmark fixtures: small text EPUB, image-heavy 1 GB EPUB, many short sections,
  very large single XHTML, large image, RTL/CJK and fixed-layout EPUB.
- Keep the large private book outside git; use documented local fixture configuration.
- Retain CBZ/CBR reading, legacy image EPUB behavior and existing database/backup compatibility.
- Mark fork code with KMK; new strings only in i18n-kmk base resources via KMR.
- Register DI through existing AppModule/KMKDomainModule conventions.
- Add the next migration number at implementation time; do not reserve a stale number now.
- After implementation edits: spotlessApply -> spotlessCheck -> assembleDebug,
  plus focused tests and release build/shrinking verification.

## 9. Decisions still needed

1. Target device specifications. Fixture identified: `Nurseholic_Collected_Works.epub` on `gdrive-hasan`; not yet benchmarked.
2. Whether it is DRM-free, reflowable, fixed-layout or a mixed/illustrated publication.
3. Default storage policy: reference files versus managed copies.
4. Whether Books and Comics share the same selected root or use independent roots
   (proposal: independently configurable roots within the same local-library destination).
5. Engine/version selection and any separately approved toolchain upgrade after Phase 0.

## Sources

Checked 2026-10-17; recheck before dependency selection:

- [Readium toolkit requirements](https://readium.org/kotlin-toolkit/latest/)
- [Navigator integration](https://readium.org/kotlin-toolkit/latest/guides/navigator/navigator/)
- [Experimental Compose navigators](https://readium.org/kotlin-toolkit/latest/guides/navigator/web-navigators/)
- [Custom font support and fixed-layout limitations](https://readium.org/kotlin-toolkit/latest/guides/navigator/epub-fonts/)
- [Reader preference constraints](https://readium.org/kotlin-toolkit/latest/guides/navigator/preferences/)
- [Readest architecture](https://github.com/readest/readest/blob/main/apps/readest-app/docs/architecture.md)

No performance claims in this plan are measured results.
<!-- KMK -->
