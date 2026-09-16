# Phase 0 spike harness

Throwaway, but kept: this harness produced Phase 0's verdict, and Phase 1 still owes a
120Hz run and a comparison against React Native's own hierarchy. Deleting it would make
those impossible to do on the same footing.

No React Native and no library code — plain Views, so nothing here can be confused for a
change to the shipped library. It is under `docs/`, which `package.json`'s `files`
whitelist already excludes from the npm package.

## What it does

A `RecyclerView` of 5000 high-contrast tiles fills the screen. A 400dp `BlurPanel` sits on
top and, in `onPreDraw`, re-records a target subtree into a `RenderNode`, applies
`RenderEffect.createBlurEffect`, and draws that node as its own backdrop. The list
auto-scrolls at a fixed px/frame off a `Choreographer` callback, so a run is deterministic
and needs no input injection.

## Variants

| `--es variant` | Target | Point |
| --- | --- | --- |
| `off` | — | baseline: same tree, same scroll, no capture |
| `A` | `android.R.id.content` (contains the panel) | self-exclusion via the `draw()`/`dispatchDraw()` flag the library ships |
| `An` | same as A, flag disabled | control: shows what the flag is actually doing |
| `B` | a subtree that excludes the panel | the `<BlurTarget>` shape |
| `Bc` | same as B, `RenderEffect` skipped | isolates re-record cost from blur cost; runs on API 29–30 |

The panel draws a magenta bar and the word PANEL. If the panel ever lands inside its own
capture, a blurred magenta smear appears in the backdrop — the numbers alone will not show
that, so check a still frame.

## Running

```bash
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk

# one run
adb shell am start -n com.bluespike/.SpikeActivity \
    --es variant B --ef scale 0.5 --ef radius 25 --ei speed 24 --ei durationMs 12000

# the sweep used for RESULTS.md: <device> <output file> then variant:scale pairs
./sweep.sh emulator-5554 out.txt off:1.0 B:1.0 B:0.5 B:0.25
```

To pin the refresh rate (the Phase 0 device runs did this, and restored it afterwards):

```bash
adb shell settings put system min_refresh_rate 60.0
adb shell settings put system peak_refresh_rate 60.0
# ... run ...
adb shell settings delete system min_refresh_rate
adb shell settings delete system peak_refresh_rate
```

`sweep.sh` interleaves the baseline with the variants on every repetition, because host-side
drift on an emulator is large enough to masquerade as a variant cost otherwise. It resets
`gfxinfo` three seconds into each run so process start and RecyclerView warm-up are not
charged to the variant.

Per-run capture percentiles are logged to logcat under the `BLURSPIKE` tag.

Release builds are signed with the debug key so the spike installs without a keystore;
`debuggable` is off so HWUI and the JIT behave the way they would for a real user.
