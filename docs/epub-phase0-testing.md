<!-- KMK -->
# EPUB Phase 0: isolated Android prototype

This is an engine evaluation harness, not the final Books manager/design.
Branch: `feat/epub-reader`.

## Implementation

- `:core:epub` pins Readium 3.2.0 without upgrading compile SDK 36.
  The engine uses the EPUB-only parser; PDF support is not enabled.
  [Index/load optimizations](epub-performance.md) prepare cached positions on IO and
  use coarser synthetic position density for oversized files, without dropping chapters.
- The renderer, state holder, locator/preferences store and unexported Activity live in
  `app/src/debug/`; `debugImplementation` keeps Readium out of the app's release graph.
- Readium embeds PhotoView 2.3.0, already used by manga. Debug runtime removes the duplicate
  external artifact only after `verifyEpubPhotoView` proves all 22 bundled class files are
  byte-identical to the original dependency. A mismatch blocks the build; release is unchanged.
- Debug builds expose **More -> EPUB reader (experimental)**.
  Main navigation, Local comic catalog and manga reader are not replaced.
- Import uses the Android document picker, makes an explicit managed local copy,
  buffers 64 KiB, reports transferred bytes, checks space and cleans up ordinary
  cancellation/failure staging files.
- Copies are under app-private `noBackupFilesDir/epub-prototype`.
  Original selected files are not modified. Cloud-backed selections require a full copy.
- Reopening the prototype automatically resumes the last successfully opened copy.
- Vertical scrolling is the default. Center taps reveal controls; system bars are hidden
  while reading. Basic text-size and optional left-to-right pagination controls are present.
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
- Close book returns to import controls. Remove imported copies requires confirmation,
  frees only prototype-owned copies/staging files and resets only prototype state.

## Current limits

- The bookshelf remains an experimental debug prototype; no production book catalog, annotations,
  app-backup integration or user font-file import yet. Per-book margin/line/paragraph/alignment controls
  are available in the debug reflowable reader; see [layout settings](epub-layout-settings.md).
  Five bundled Google font families
  are selectable per reflowable book in the debug reader; see [font details](epub-fonts.md).
- Only trusted DRM-free EPUBs should be used. Publication HTML/script/remote-resource
  isolation needs a separate security review before enabling this in production.
  External hyperlink activation is blocked by the harness; this is not a claim that
  every remote subresource is blocked by the renderer.
- Each explicit import creates a separate managed copy. There is no duplicate manager yet.
- Progress/reader lifecycle and navigation interactions need real Android testing.
  Source compilation/unit tests do not prove rendered behavior.
- Process termination during import can leave a staging file. Remove imported copies
  also removes such abandoned files; an interrupted import is never published for resume.
- Rendering/gesture performance for very large single XHTML resources is unproven.
  Do not describe 1 GB support as validated before the named fixture/device run.

## Verified in this development slice (2026-10-17)

- Ordered spotlessApply -> spotlessCheck -> assembleDebug: passed.
- EpubImporterTest: 8 passed; bounded reads, exact copy, truncated/empty inputs,
  low disk, provider errors, cancellation and staging cleanup.
- Multi-book baseline EpubPrototypeStoreTest: 9 passed; isolated per-book preferences, latest-position
  overwrite policy, managed-path checks, multi-book metadata/order, legacy copy discovery,
  selective deletion and cleanup preserving external originals.
- Metadata inspector: 10 synthetic Python tests; bounded reads/decompression, path resolution,
  missing resources and DTD/entity rejection including UTF-16. No private book content in tests.
- verifyEpubPhotoView: all 22 bundled classes byte-identical; duplicate-class gate passed.
- App releaseRuntimeClasspath: no Readium artifacts.
- git diff --check: passed.
- EPUB shelf UI smoke on Oppo CPH2529 / Android 15: selected app theme, imported covers, small/medium/large grid sizes, list view and Finished filter rendered. The 1 GB title was visible in the shelf but was not opened during this UI pass.
- Full 1 GB reader traversal/performance validation was not part of this UI pass.

These checks establish build/storage behavior, not production reader readiness.

## Device acceptance run

Fixture: `Nurseholic_Collected_Works.epub` on `gdrive-hasan`.
Keep the private book outside git. Target device/RAM/Android/WebView version and
DRM/layout characteristics still need to be recorded.
No full fixture download or device benchmark has been performed. Authenticated, bounded
remote ZIP/package reads are a separate metadata-only check (see below); they do not test rendering.

1. Build/install debug APK; confirm normal manga reading/settings still behave as before.
2. Open the experimental More entry. Import the fixture after reading the copy warning.
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
12. Agree pass/fail budgets from measurements before moving to the production catalog.

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
invoke the Android renderer. Five Google font families were subsequently downloaded and
registered for per-book selection in the debug reader; user-provided font imports remain future work.

## Checks

```sh
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew assembleDebug
./gradlew :core:epub:testDebugUnitTest --tests '*EpubImporterTest'
./gradlew :app:testDebugUnitTest --tests '*EpubPrototypeStoreTest'
./gradlew :app:dependencies --configuration releaseRuntimeClasspath
python3 scripts/epub/test_inspect_fixture.py
```

The release dependency report must not contain Readium artifacts. A release app build and
manga regression/device smoke checks remain required before production rollout.
<!-- KMK -->
