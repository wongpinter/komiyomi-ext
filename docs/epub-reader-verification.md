<!-- KMK -->
# Ebook reader verification and known limits

This document records the EPUB reader implementation, validation evidence, and known limits. Its shelf, reading positions and preferences remain separate from manga data.

## Implementation

- `:core:epub` pins Readium 3.2.0 without upgrading compile SDK 36.
  The engine uses the EPUB parser; local PDF files use the separate PDFium reader.
  [Index/load optimizations](epub-performance.md) prepare cached positions on IO and
  use coarser synthetic position density for oversized files, without dropping chapters.
- The renderer, state holder, locator/preferences store and unexported Activity live in
  `app/src/main/java/eu/kanade/tachiyomi/ui/ebook/`; `implementation(projects.core.epub)` includes them
  in every app build.
- Readium embeds PhotoView 2.3.0, already used by manga. Runtime dependency graphs exclude the duplicate
  external artifact only after `verifyEpubPhotoView` proves all 22 bundled class files are byte-identical
  to the original dependency. A mismatch blocks the build.
- **Ebook** is a bottom navigation destination. **Updates** opens from More.
  The EPUB library, comic catalog and manga reader remain separate.
- Import uses the Android document picker, makes an explicit managed local copy,
  buffers 64 KiB, reports transferred bytes, checks space and cleans up ordinary
  cancellation/failure staging files.
- Copies are kept in app-private no-backup storage, preserving imported books and reading state across app upgrades.
  Original selected files are not modified. Cloud-backed selections require a full copy.
- Reopening Ebook automatically resumes the last successfully opened copy.
- Vertical scrolling is the default. Center taps reveal controls; system bars are hidden
  while reading. Basic text-size and optional left-to-right pagination controls are present.
- Local PDF books support saved page position, continuous/paginated layout, page/width fit,
  margin trimming, color filters and per-book page bookmarks. PDF device acceptance has not
  been recorded for this release.
- Book sections use recycled list rows instead of eagerly generating every spine label.
  Direct middle/last-section jumps support checks on spines with tens of thousands of entries.
- Latest Readium locator and basic preferences are saved per imported copy, isolated from
  manga data. The latest position can be earlier than a previous position.
- **Imported books** lists existing copies with publication cover, title/author and size, newest opened first.
  The cover grid starts compact and offers adjustable small/medium/large sizes, plus list view, title/author
  search, reading-status filters, sorting and a continue-reading card. The shelf, reader controls and menus
  use the selected app `TachiyomiTheme`; only the reading page keeps its separate light/sepia/dark setting.
  Reopen any copy with its own locator/preferences, or confirm removal of just that copy. Failed opening of an
  older copy never deletes it. No book is parsed just to display the list.
- Close book returns to the shelf. Remove imported copies requires confirmation,
  frees only app-managed copies/staging files and clears only that ebook copy's reader state.

## Current limits

- The shelf manages imported local EPUB and PDF books. Annotations, app-backup integration and user font-file imports are not supported.
  Per-book margin/line/paragraph/alignment controls and six bundled font families are available for reflowable EPUBs; see [layout settings](epub-layout-settings.md) and [font details](epub-fonts.md).
- Use trusted DRM-free EPUBs and local PDFs. EPUB external hyperlink activation is blocked, and Readium's HTTP
  client rejects remote publication-resource requests; local packaged resources remain available.
- Each explicit import creates a separate managed copy. There is no duplicate manager yet.
- User-reported device smoke (2026-10-19): an approximately 1 GB EPUB opened without crashing and was easy to navigate. Device details, import/open timings and app/renderer memory were not recorded; treat this as beta stability and usability evidence, not a quantified performance benchmark.
- Process termination during import can leave a staging file. Remove imported copies
  also removes such abandoned files; an interrupted import is never published for resume.
- Rendering/gesture performance for a very large single XHTML resource, exact resume behavior, and measured renderer memory remain unverified. Treat the 1 GB result as a device observation, not a quantified benchmark.

## Verified in this development slice (2026-10-17)

- Ordered spotlessApply -> spotlessCheck -> assembleDebug: passed.
- EpubImporterTest: 8 passed; bounded reads, exact copy, truncated/empty inputs,
  low disk, provider errors, cancellation and staging cleanup.
- Multi-book baseline EpubStoreTest: 9 passed; isolated per-book preferences, latest-position
  overwrite policy, managed-path checks, multi-book metadata/order, legacy copy discovery,
  selective deletion and cleanup preserving external originals.
- Metadata inspector: 10 synthetic Python tests; bounded reads/decompression, path resolution,
  missing resources and DTD/entity rejection including UTF-16. No private book content in tests.
- verifyEpubPhotoView: all 22 bundled classes byte-identical; duplicate-class gate passed.
- At that time, App releaseRuntimeClasspath had no Readium artifacts; the production
  promotion verified below now includes the EPUB engine in app variants.
- git diff --check: passed.
- EPUB shelf UI smoke on Oppo CPH2529 / Android 15: selected app theme, imported covers, small/medium/large grid sizes, list view and Finished filter rendered. The 1 GB title was visible in the shelf but was not opened during this UI pass.
- Full 1 GB reader traversal/performance validation was not part of this UI pass.

These earlier checks establish build/storage behavior, not full large-book readiness.

## Production promotion verification (2026-10-18)

- `spotlessApply`, `spotlessCheck`, `assembleDebug` and `assemblePreview` passed.
  The Android build emitted D8 Kotlin-metadata compatibility warnings, but no task failed.
- Installed the debug APK on Oppo CPH2529 / Android 15. The Ebook tab opened the
  existing shelf with all three books, including `Nurseholic - Collected Works`;
  this confirms the old preference and app-private storage identifiers still find
  imported data after the source-set promotion. The large book was visible but not opened.
- Back from the shelf returned to Library. More displayed Updates, and selecting it
  opened the Updates screen. The manga and ebook shelves remained distinct.
- `git diff --check` passed.

## Device acceptance run

Fixture: `Nurseholic_Collected_Works.epub` on `gdrive-hasan`.
Keep the private book outside git. Target device/RAM/Android/WebView version and
DRM/layout characteristics still need to be recorded.
No formal device benchmark or renderer-process memory profile has been recorded. Authenticated, bounded
remote ZIP/package reads are a separate metadata-only check (see below); they do not test rendering.

1. Build/install debug APK; confirm normal manga reading/settings still behave as before.
2. Open the Ebook tab. Import the fixture after reading the copy notice.
3. Record copy duration/free space and publication-open duration separately.
4. Profile app AND WebView renderer memory, not only the Kotlin/JVM heap.
5. Read/scroll at the beginning, jump to a middle/last section and exercise large images.
6. Move backward, leave/reopen: verify the latest location, not furthest progress.
7. Change text size/mode; rotate; background/resume; kill/relaunch the app.
   Verify location, picker recovery and absence of crashes/leaked sessions.
8. Cancel a copy and simulate low storage/provider failure; original book must remain intact.
9. Close book and import another EPUB. Use Imported books to alternate between copies;
   check titles/order and independent preferences/latest positions after restart.
10. Confirm removing one copy preserves the other copy/progress, and Remove imported copies
    does not touch source books or manga data. Try a damaged older copy: failure must not delete it.
11. Compare latency/memory on the same device/book with Moon+ Reader/Readest if available.
12. Record latency and memory measurements against the agreed acceptance budgets.

## Remote fixture metadata (2026-10-17)

The authenticated `gdrive-hasan` inventory contained **7,872 EPUB files** at inspection time.
Three files were inspected through ZIP ranges; no complete book was downloaded, no
story text/images were extracted, and private fixture paths/content are not committed.

| Fixture class | File bytes | Spine sections | Package XML bytes | Largest XHTML bytes | Images |
| --- | ---: | ---: | ---: | ---: | ---: |
| Nurseholic collected works | 999,782,146 | 61,958 | 8,533,103 | 9,229,300 | 1 |
| Small prose sample | 595,105 | 72 | 14,119 | 34,872 | 3 |
| Image-heavy cookbook sample | 17,432,290 | 39 | 8,581 | 4,014 | 36 |

The large fixture has 61,970 ZIP entries and two embedded fonts. The initial default
8 MiB XML cap stopped inspection safely; an explicit 32 MiB cap completed the metadata
check using **12,927,223 bytes across nine range requests**, not a full 1 GB transfer.
All three had zero missing manifest files/spine IDs, zero unsafe ZIP paths, no declared
external manifest resources, and no encryption metadata/ZIP encrypted entries.
These observations do **not** prove EPUB conformance, safe HTML/CSS, absence of all remote
references, DRM usability, or successful Readium rendering.

Large-file risks now include the huge OPF/spine and a 9.2 MB individual XHTML resource,
not merely archive size. Android publication-open latency, Readium position generation,
WebView memory and exact resume remain **unverified**.

Inspector usage (obtain the exact remote path/size from your own authenticated inventory):

```sh
python3 scripts/epub/inspect_fixture.py /path/to/book.epub
python3 scripts/epub/inspect_fixture.py --remote 'gdrive-hasan:observed/path.epub' --size 12345
# For a known large OPF only; maximum accepted XML cap is 64 MiB:
python3 scripts/epub/inspect_fixture.py /path/to/book.epub --max-xml-bytes 33554432
```

The inspector caps each remote range at 16 MiB and metadata XML at 8 MiB by default;
it rejects DTD/entities before expansion, including UTF-16 declarations. It does not
invoke the Android renderer. Six Google font families were subsequently registered for per-book
selection in the EPUB reader; user-provided font imports remain unsupported.

## Checks

```sh
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew assembleDebug
./gradlew :core:epub:testDebugUnitTest --tests '*EpubImporterTest'
./gradlew :app:testDebugUnitTest --tests '*EpubStoreTest'
./gradlew :app:dependencies --configuration releaseRuntimeClasspath
python3 scripts/epub/test_inspect_fixture.py
```

The release dependency report should include `:core:epub` and Readium. A release-like app build and
manga regression/device smoke checks remain useful before publishing.
<!-- KMK -->
