# Android editor validation (#13)

This branch integrates the real private image repository, bundled preset
repository and GLES renderer. It depends on [renderer PR #19](https://github.com/HaaaappyDay/spektrafilm/pull/19),
whose physical-GPU acceptance remains pending. This report does not claim v1
completion or gallery saving: #14 owns the real PhotoExporter.

## State and ownership

The ViewModel exposes immutable StateFlow snapshots, collected with lifecycle
awareness. A successful import uses Portra 400 at 100% / 0 EV / 0 temperature.
Preset changes retain adjustments; Reset retains the preset. Original comparison
displays the untouched normalized input, independently of film strength.

The normalized 1600-cap preview is decoded once per source. At most two render
coroutines and one conflated pending request exist; the GL renderer serializes
actual GPU work. Version, source and parameter checks prevent an obsolete
noncooperative result from replacing the latest preview. A failed preview clears
its validity and disables export. Unpublished bitmaps are recycled; displayed
bitmaps are released through references. Clearing the ViewModel cancels and joins
its work before releasing its renderer off the main thread.

The private atomic JSON contains only source descriptors and edit parameters.
A single conflated writer commits snapshots in order. Old sources remain until
a successful replacement commit; hard process death can restore the last
committed edit rather than the last uncommitted slider event. Startup orphan
cleanup runs once per process/store and only targets canonical UUID source
files. Foreign files and symlinks are preserved. Export completion is never
persisted, so restoration cannot invent success or trigger another save.

## Executed validation

The implementation at `70a75a4` built successfully with 9 unit tests and lint;
the Python/mobile reference job passed 109 tests. Both API 29 and API 36 executed
45 device tests: 42 passed and 3 storage fixture tests failed because their
source descriptors used an aliased cache path rather than a canonical path.
The real repository already produces canonical descriptors. The test fixture
was corrected without relaxing the production path boundary.

The first run is [CI 43546591356](https://github.com/HaaaappyDay/spektrafilm/actions/runs/43546591356).
Its artifacts include actual portrait, landscape, failed-import screenshots and
an H.264 interaction recording on each emulator. Screenshots and decoded video
frames were inspected. Screenrecord was requested for up to 12 seconds; the
actual clips are 6.90 seconds on API 29 and 3.31 seconds on API 36 (rotation can
end recording). They are interaction evidence, not performance measurements.

Whole-branch review found no critical defects and two important recovery defects:
unchecked AtomicFile commit success and clearing a valid session after a
temporary decode failure. Regression tests were added before correcting them.
At `fd0ee50`, [CI 43547214326](https://github.com/HaaaappyDay/spektrafilm/actions/runs/43547214326)
ran 48 tests on each API: only the two new regression cases failed. The source
retirement failure test and the corrected storage fixtures passed.

The correction stages and synchronizes a sibling file, performs a checked atomic
rename, verifies recoverable bytes, and synchronizes the directory. It keeps
compatibility with interrupted legacy AtomicFile writes on load. Platform
AtomicFile's silent failure and API 29 rollback behavior were verified against
[current AOSP](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/util/AtomicFile.java)
and [Android 10 AOSP](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-10.0.0_r1/core/java/android/util/AtomicFile.java).
Read/decode failures preserve the descriptor for Retry; the no-photo recovery
UI still offers import, which can replace a missing or corrupt session.
Final regression results will be recorded after CI completes.

## UI and test scope

Compose tests exercise accessible slider actions, preset selection, Reset,
original toggle, no-photo disabled controls, export lockout, retry, output
dimensions and 2x text scaling. Actual Activity tests cover real import/render,
portrait/landscape rotation, background/foreground, failed replacement and a
new ViewModel restoring the persisted source and adjustments by decoding again.
The latter simulates process restoration; it is not an OS low-memory kill.

State tests deliberately finish B before cancelled A, discard a previous
photo's late render, issue 100 adjustments while rendering is gated, assert one
input decode, check snapshot-before-source-retirement, and exercise export
snapshot/lockout/failure/retry with a test exporter. Test doubles exist only in
test source sets. Manual TalkBack and vivo hardware verification remain pending.

Minor review coverage suggestions are deferred to #15: assert rendered pixels
for the final temperature in the rapid-update test, and gate a noncooperative
import itself (current cancellation coverage includes picker cancellation and
late renders across imports). No claim is made that those extra paths passed.

## Export handoff (#14)

`EditorViewModel` accepts an optional `PhotoExporter` after its four required
repositories/services. `MainActivity` is the production injection point. Until
#14 supplies its coordinator, `exportAvailable` is false and Save is disabled.

`export()` captures one immutable `ExportRequest(source, params)` before awaiting
the coordinator, then locks edits/import/duplicate saves. Success exposes only
the returned URI and dimensions; typed failure leaves the edit available for
retry. The Open action uses ACTION_VIEW with a read grant and handles a missing
viewer. Actual JPEG encoding, MediaStore pending-row recovery, cancellation and
gallery verification belong exclusively to #14.

Color math remains in FilmRenderer. The editor has no duplicate LUT/adjustment
implementation and introduces no Auto, network or capture features.
