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
| `G` | same as B, plus the AGSL glass edge | the Phase 6 prototype. API 33. |

**Phase 0's numbers were measured before variant `G` existed.** The `off`/`A`/`An`/`B`/`Bc`
paths were re-checked after glass landed and are unchanged (variant A still reports
`exclDraw=1 firstExclFrame=1`, B still `exclDraw=0`, capture cost within noise), but if you
need the exact harness that produced them, take the tree at the "Phase 0 passes" commit.

### Variant G

Three RenderNodes, not one: `captureNode` holds a sharp capture with a bleed margin,
`frostNode` blurs it for the interior, `rimNode` refracts the same sharp pixels for the
edge and is transparent everywhere else. Drawing frost then rim is what gets a crisp bevel
over a frosted panel out of a single capture — refracting already-blurred pixels looks like
a smudge.

| Extra | Meaning |
| --- | --- |
| `--ef flatness` | 0..1. Derives band, bevel depth and sheen together. 1 = flat slab, 0 = deep bevel that domes the panel. ~0.85 matches iOS. |
| `--ef radius` | interior frost. Independent of the rim. |
| `--ef edgeBlur` | blur applied to the rim before refraction, separate from the interior. 0 = crisp bevel. Defaults to 35% of `radius`. Also removes the aliasing that extreme compression causes on high-frequency backdrops. |
| `--ef bleed` | how far the capture extends past the panel. Must exceed the bevel depth. |
| `--ef ior`, `--ef band`, `--ef thickness`, `--ef specular`, `--ef rim`, `--ef tint` | raw overrides, used when `flatness` is absent |
| `--ez pill true`, `--ei panelH`, `--el tintColor`, `--ef elevation` | shape and styling |

```bash
adb shell am start -n com.bluespike/.SpikeActivity --es variant G --ei durationMs 0 \
    --ez pill true --ei panelH 130 --ef corner 65 --ef flatness 0.85 --ef radius 12
```

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
