<!-- KMK -->
# EPUB loading and index performance

## Source-proven bottlenecks in pinned Readium 3.2

1. `EpubNavigatorFragment` constructs its grouped positions with `runBlocking`.
   A cold index can therefore run during Fragment creation on the UI thread.
2. The SDK's default `PositionsService.positions()` flattens the grouped index on every call.
   The navigator's internal `positionsByResource` extension then groups the whole list by href
   during location notifications (debounced by about 100 ms).
3. The default EPUB strategy creates roughly one synthetic point per 1,024 archive-entry bytes.
   A large, mostly stored-text omnibus can therefore create nearly a million locator objects.

These are source findings, not device measurements. This document does not establish end-to-end rendering speed or memory use.

## Changes in this development slice

- The EPUB-only engine now enforces IO execution for asset retrieval, parsing and position
  preparation. The session is not published as Ready until its index is prepared.
- A session-scoped `CachedEpubPositions` decorates the SDK service and caches both grouped
  and flattened lists. Concurrent warm-ups share one computation. Failed/cancelled
  preparation cannot publish a partial snapshot; close is idempotent and prevents re-publication.
- The wrapper preserves the SDK's exact locator objects. No arbitrary chapter subset or custom
  reading order is passed to the navigator to bypass indexing.
- Files smaller than **128 MiB** retain the recommended **1 KiB** archive-entry strategy.
  Files at least **128 MiB** use the SDK's native **16 KiB** strategy.
- All chapters remain readable; actual chapter href and resource scroll progression are
  still saved. Synthetic position numbering and coarse whole-book progress granularity
  change for oversized files. These synthetic points are not rendered pages.
- Cancellation at the IO dispatcher handoff closes a prepared session that cannot be delivered.
  The native SDK strategy is sealed; cancellation cannot interrupt every inner computation,
  but preparation guards prevent a cancelled result being handed to the reader.
- The existing native reader, font/layout preferences, source files and manga renderer
  are otherwise unchanged. No Readium binary patch, SDK upgrade or new dependency was added.

## Reproducible measurements

### Real fixture metadata, not rendering

Authenticated ZIP/package range inspection of `Nurseholic_Collected_Works.epub`:

| Metric | Result |
| --- | ---: |
| File bytes | 999,782,146 |
| Spine sections | 61,958 |
| Estimated positions at 1 KiB | 976,480 |
| Estimated positions at 16 KiB | 93,728 |
| Reduction in modeled synthetic point count | approximately 90.4% |

This estimate uses each referenced ZIP entry's compressed size (stored entries use original
size), matching the default unencrypted local archive-length model. It excludes missing/external
resources and is not a conformance/DRM check. Only 12,927,223 bytes across nine authenticated
range reads were needed; chapter bodies/images were not extracted or read to compute the model.

### Actual SDK method and deterministic JVM work counters

`CachedEpubPositionsTest` exercises the real default `PositionsService.positions()`:

- 61,958 input locators, 20 requests: default flattening visits **1,239,160** elements.
- Cached service: **61,958** element visits to prepare once, with the same flat list reused.
- This measures eliminated repeated flatten work only; the SDK's later grouping still runs.

`EpubPositionPolicyTest` exercises the SDK's actual archive-length strategy through its
metadata-length fallback: a synthetic 1 MiB resource produces **1,024 → 64** points.
Small/empty resources retain at least one point, and normal-book density is unchanged.

These are deterministic operation/point counts, not a “10× faster reader” claim.

```sh
./gradlew :core:epub:testDebugUnitTest --tests '*CachedEpubPositionsTest' --tests '*EpubPositionPolicyTest'
python3 scripts/epub/test_inspect_fixture.py
```

## Verification in this slice (2026-10-17)

- Ordered `spotlessApply → spotlessCheck → assembleDebug`: passed on final code.
- Kotlin tests: **43 passed** (cache 8, position policy 4, importer 8, store 11, fonts 5, layout 7).
- Python metadata inspector: **12 passed**, including stored/deflated position estimates.
- Release runtime dependency report: no Readium or `:core:epub`; manga/release integration unchanged.
- `git diff --check`: passed.
- Device rendering, real memory/latency, and old-locator resume regression: **not run**.

## Device measurement hooks

The EPUB reader logs `EPUB performance:` with:
- asset retrieval, publication parsing and index preparation durations in milliseconds;
- section count, actual synthetic position count, chosen position byte length;
- time from Fragment attachment start to the first page-loaded callback.
- TOC-link-to-locator conversion time (`locatorMs`), synchronous `go(locator)` dispatch time
  (`dispatchMs`), and time until `currentLocator` reports the target resource
  (`locatorUpdatedMs`). These milestones do not measure WebView first paint.

Logs intentionally exclude book names, source URIs, text and private paths.
A page-loaded callback can describe a preloaded adjacent resource: it is **not** proof
of the current page being fully drawn, first-paint timing or exact resume completion.
Copy time remains separate from engine time and must be measured separately.

Readium 3.2's `EpubNavigatorFragment.go(locator)` synchronously scans its page-resource list
with `withIndex().firstOrNull` before selecting the target. For a 61,958-section publication,
a jump near the end can inspect most of that list on the caller thread. This is a source-level
candidate for dispatch overhead, not a measured explanation of the user's delay; the local
`dispatchMs` and `locatorUpdatedMs` values are needed to distinguish lookup from content loading.

## Remaining work / acceptance gate

- Readium still flattens once while creating its Fragment and builds a resource list on the UI
  thread. Its internal href grouping still repeats during locator updates. Further patching or
  a compatible upstream version should be considered only with a device trace.
- OPF/navigation parsing for 61,958 sections and a 9.2 MB XHTML DOM can remain expensive.
  This change does not split or rewrite user content, cap visible text or guarantee bounded
  WebView memory.
- Profile Android UI thread and WebView renderer separately; record device RAM/Android/WebView.
  Measure copy, parse, index, current-page-ready time, scroll frame time and both process heaps.
- Test beginning/middle/end, the largest chapter, cancellation, low storage, restart and rotation,
  latest-position resume after font/layout changes, and normal manga reading.
- Explicitly test an existing saved locator created under the older 1 KiB strategy. Its href and
  resource progression must restore correctly even if synthetic position numbers changed.
- Agree latency/RAM budgets from measurements before calling the 1 GB reader production-ready.

Pinned sources: [navigator](https://github.com/readium/kotlin-toolkit/blob/3.2.0/readium/navigator/src/main/java/org/readium/r2/navigator/epub/EpubNavigatorFragment.kt),
[positions service](https://github.com/readium/kotlin-toolkit/blob/3.2.0/readium/shared/src/main/java/org/readium/r2/shared/publication/services/PositionsService.kt),
[EPUB strategy](https://github.com/readium/kotlin-toolkit/blob/3.2.0/readium/streamer/src/main/java/org/readium/r2/streamer/parser/epub/EpubPositionsService.kt),
[navigator grouping](https://github.com/readium/kotlin-toolkit/blob/3.2.0/readium/navigator/src/main/java/org/readium/r2/navigator/extensions/Publication.kt).
<!-- KMK -->
