# Live blur — measurements and decisions

Append-only. Each session adds a section; nothing here gets rewritten, because a later
phase may be built on a number recorded earlier.

Record the device, the Android version, the refresh rate, the build type and the exact
command used. A number without those is not reusable.

## Template

```
## Phase N — <what was measured> — YYYY-MM-DD

Device: Pixel 8a, Android 16, 60Hz / 120Hz, release build
Method: <command or harness, e.g. dumpsys gfxinfo framestats over a 10s fling>

| Variant | inputScale | P90 frame (ms) | Janky frames | Notes |
| --- | --- | --- | --- | --- |
| A — root capture + self-exclusion | 0.5 | | | recursive self-blur? yes/no |
| B — scoped BlurTarget | 0.5 | | | |

Decision: <what this means for the plan>
Follow-ups: <anything the next phase must know>
```

## Baseline (already known, 3.0.x)

- `StackBlur` on a 1080×1920 frame: **~43ms on a desktop JVM** (`scripts`-free harness,
  2026-09-09). Well over a frame budget on-device, which is why the shipped Android path
  is a one-time downsampled snapshot rather than anything live.
- Android snapshot capture excludes the overlay's own subtree via the `capturing` flag on
  `draw()`/`dispatchDraw()`, verified visually on an Android 16 emulator: the blurred
  backdrop contains no copy of the overlay or its children.
- Current Android behaviour is confirmed **static**: the snapshot is taken once when the
  overlay mounts and is not refreshed while content scrolls behind it.

---

<!-- New sessions append below this line. -->

## Phase 0 — feasibility spike — 2026-09-16

Harness: [`spike/`](./spike) (plain Views, no React Native, no library code). A
`RecyclerView` of 5000 tiles auto-scrolls at 24px/frame off a `Choreographer` callback; a
400dp panel re-records a target subtree into a `RenderNode` in `onPreDraw`, applies
`RenderEffect.createBlurEffect(r, r, CLAMP)` and draws it as its backdrop. Release build,
`debuggable=false`, debug-signed.

Method: `spike/sweep.sh`. 12s of auto-scroll per run; `dumpsys gfxinfo <pkg> reset` fires
3s in so process start and RecyclerView warm-up are not charged to the variant; the
baseline is re-run immediately before the variants on every repetition. 3 repetitions;
tables give the median of the three. Capture percentiles come from `System.nanoTime()`
around the record, logged under the `BLURSPIKE` tag.

### Verdict: variant A fails. Phase 1 needs a `<BlurTarget>`.

**Self-exclusion does not survive hardware capture.** Not marginally — the mechanism is
never reached. Instrumented, over 180 captures in variant A:

```
exclDraw=1  exclDispatch=0  firstExclFrame=1  captureFrames=181
```

The `draw()`/`dispatchDraw()` flag fired **once**, on the very first capture, and never
again. That is exactly what HWUI's `ViewGroup.drawChild` does: on a hardware
`RecordingCanvas` it takes `updateDisplayListIfDirty()` + `drawRenderNode(child)`, and only
falls through to `draw(Canvas)` when the child's display list is dirty. The panel is dirty
once, when its display list is first built; from then on every capture re-references the
panel's `RenderNode` and the flag is dead code.

So the flag is not merely inert — it is destructive. The one time it fires is the one time
HWUI asked the panel to record itself, and the early return leaves the panel's `RenderNode`
**empty**. Nothing ever invalidates the panel again, so the empty display list is what gets
composited forever: the overlay is invisible for the rest of its life.

| Still frame | What it shows |
| --- | --- |
| [`variantB.png`](./spike/evidence/variantB.png) | variant B, correct: sharp panel content over a blurred backdrop |
| [`variantA.png`](./spike/evidence/variantA.png) | variant A: **the panel is gone entirely** — no blur, no text, no magenta bar |
| [`variantAn.png`](./spike/evidence/variantAn.png) | variant A with the flag disabled: panel content draws, **backdrop blur is silently dropped** — HWUI refuses the recursive `RenderNode` reference |

The two A screenshots together close the question. With the flag, the overlay blanks
itself. Without it, the capture references the panel's `RenderNode`, which references the
capture node, and HWUI drops the whole backdrop rather than recursing. There is no third
option: no configuration of variant A both draws the panel and blurs behind it.

The expected failure was recursive self-blur (a mirror-tunnel). It is worth recording that
the real failure is quieter and worse — a blank or un-blurred overlay with nothing in
logcat. Phase 1 must not rely on a visual check alone to catch a mis-targeted capture.

### Performance: the capture is cheap; the budget question is still open

**A per-frame subtree re-record costs ~0.2–0.4ms on 2019 mid-range silicon.** This was the
number with no public precedent, and it is comfortably affordable. Re-recording is cheap
for the reason the plan assumed: the target's children are re-referenced by `RenderNode`,
not re-drawn.

Samsung Galaxy A70 (SM-A705FN), Android 11 / API 30, Snapdragon 675, 60Hz, release build:

| Variant | inputScale | P90 frame (ms) | Janky frames | Re-record cost, median / P90 |
| --- | --- | --- | --- | --- |
| off — baseline | — | 10 | ≤0.16% | — |
| Bc — capture only | 1.0 | 11 | ≤0.33% | 0.17 / 0.35 ms |
| Bc — capture only | 0.5 | 11 | ≤0.16% | 0.18 / 0.37 ms |
| Bc — capture only | 0.25 | 10 | ≤0.16% | 0.16 / 0.37 ms |

Scroll held 59.8–59.9fps in every run. **API 30 cannot run `RenderEffect`**, so this device
measures the re-record only — variant `Bc` exists for exactly that reason. The blur cost on
real hardware is not in this table.

Android 17 / API 37 emulator (`sdk_gphone16k_arm64`, 1280×2856, 60Hz, host GPU):

| Variant | inputScale | P90 frame (ms) | Janky frames | Re-record cost, median / P90 |
| --- | --- | --- | --- | --- |
| off — baseline | — | 23–24 | ≤0.49% | — |
| B — capture + blur | 1.0 | 23 | ≤0.66% | 0.04 / 0.10 ms |
| B — capture + blur | 0.5 | 19–24 | ≤0.82% | 0.05 / 0.12 ms |
| B — capture + blur | 0.25 | 23 | ≤0.32% | 0.05 / 0.12 ms |
| Bc — capture only | 0.5 | 22 | ≤0.65% | 0.05 / 0.11 ms |

No variant separated from the baseline on the emulator — including `B` vs `Bc`, which
should differ by exactly the blur. **That is a statement about the instrument, not about
the blur.** The emulator's baseline sits at 20ms p50 and 23ms P90 while doing nothing at
60Hz, and repeated baselines drifted between 18ms and 31ms P90 depending on host load
(an early non-interleaved sweep produced a 66% "janky frames (legacy)" reading at
inputScale 0.25 that later interleaved runs did not reproduce — host noise, not a real
effect). The noise floor is several milliseconds; the effect being measured is a fraction
of one. Emulator frame numbers from this phase should not be quoted anywhere.

### What this does not answer

- **The exit criterion's actual number.** "Variant B holds ≤16.6ms P90 at 60Hz with
  downscale 0.5" has not been demonstrated on hardware that can run the feature. The A70
  is API 30 and the emulator has no resolution. No API 31+ physical device was available
  this session.
- **120Hz.** Neither device offers it — the A70 and the emulator are both 60Hz-only.
- **What an RN Fabric hierarchy adds.** The harness is plain Views, per the phase brief.
  Phase 1 measures this for real in the example app.

### Decision

- **Phase 1 ships a `<BlurTarget>` component.** Variant A is not a matter of cost, it is
  structurally impossible on a hardware canvas, so there is nothing to revisit and no
  downscale that rescues it. The capture target must be a subtree that does not contain the
  overlay.
- **Phase 0 passes on structure, provisionally on performance.** The re-record is cheap on
  real mid-range hardware and the blur is `RenderEffect` on the RenderThread. Nothing
  suggests a wall. But per the ground rule that nothing ships unmeasured, Phase 1's exit
  criteria must include the P90 number on an API 31+ device, and Phase 1 should not be
  called done without it.
- **Keep the snapshot default.** Nothing here changes that.

### Follow-ups Phase 1 must know

- Capture with `RenderNode.beginRecording()` + `canvas.scale(inputScale)` + `target.draw()`,
  then `setRenderEffect` with radius scaled by `inputScale`, then draw via
  `RecordingCanvas.drawRenderNode` with the node's own `scaleX/scaleY = 1/inputScale` and
  pivot at 0,0. Canvas-side scaling of a `drawRenderNode` was avoided; the node's own
  transform properties are the reliable path.
- Blur updates propagate **without re-recording the panel**. The panel's display list holds
  a reference to the capture node, so re-recording the capture node's content is enough —
  do not `invalidate()` the overlay every frame.
- Delete the `capturing` flag from the live path. On a hardware canvas it can only blank
  the overlay. It must stay on the snapshot path, where software capture still honours it.
- `SDK_INT_FULL` on the Android 17 emulator is `3700000` — i.e. 37.0, below the `3_700_002`
  that Phase 4's `RenderNode.setBackdropRenderEffect` needs. No emulator image can exercise
  that path yet.
- `Build.VERSION.SDK_INT_FULL` is not resolvable by name on API 30 (the harness reads it
  reflectively and prints `n/a`). Phase 4's gate needs an API-level guard around the field
  access, not just a value comparison.

## Phase 0 (continued) — the device measurement Phase 0 owed — 2026-09-16

Same harness and same method as the section above. This closes the gap that section left
open: an API 31+ physical device, at two refresh rates.

Device: Samsung Galaxy A22 (SM-A225F), Android 13 / API 33, MediaTek Helio G80
(MT6769V/CT) with a Mali-G52 MC2, 720×1600, 60Hz and 90Hz, release build. This is
low-end 2021 hardware, which makes it a harder test than a flagship and a fairer one for
the "mid-range" wording in the exit criteria. Refresh rate pinned per run with
`settings put system min_refresh_rate` / `peak_refresh_rate`, confirmed against
SurfaceFlinger's active mode, and restored afterwards. Still frame from this device:
[`variantB-device.png`](./spike/evidence/variantB-device.png) — sharp panel content over a
blurred backdrop, same as the emulator.

### 60Hz — the exit criterion, met

Budget 16.6ms. P90 and jank are medians of 3 interleaved repetitions.

| Variant | inputScale | P90 frame (ms) | Janky frames | Re-record cost, median / P90 |
| --- | --- | --- | --- | --- |
| off — baseline | — | 11 | ≤0.16% | — |
| Bc — capture only | 0.5 | 14 | ≤0.16% | 0.19 / 0.43 ms |
| B — capture + blur | 1.0 | 15 | **0.00%** | 0.19 / 0.40 ms |
| B — capture + blur | 0.5 | 15 | **0.00%** | 0.20 / 0.43 ms |
| B — capture + blur | 0.25 | 16 | ≤0.16% | 0.21 / 0.44 ms |

**Variant B at inputScale 0.5 holds 15ms P90 with zero janky frames at 60Hz.** That is the
Phase 0 exit criterion, met on hardware that can run the feature. Scroll held 60.0–60.1fps
throughout. Every inputScale fits the budget at 60Hz, including 1.0.

### 90Hz — passes, and downscale starts to matter

Budget 11.1ms. Medians of 3 interleaved repetitions, excluding the environmental outliers
discussed below.

| Variant | inputScale | P90 frame (ms) | Janky frames | Re-record cost, median / P90 |
| --- | --- | --- | --- | --- |
| off — baseline | — | 13 | 0.00% | — |
| Bc — capture only | 0.5 | 13 | ≤0.22% | 0.18 / 0.43 ms |
| B — capture + blur | 1.0 | 15 | ≤0.22% | 0.19 / 0.40 ms |
| B — capture + blur | 0.5 | 13 | ≤0.22% | 0.18 / 0.39 ms |
| B — capture + blur | 0.25 | 13 | ≤0.11% | 0.18 / 0.38 ms |

Scroll held 90.0–90.5fps throughout. Note the baseline itself reports 13ms P90 at 90Hz
while janking 0.00%, so P90 total frame duration overshoots the 11.1ms vsync interval even
with the app doing nothing; at this refresh rate the jank percentage is the meaningful
signal and it stays at or below 0.22% for every variant.

### The blur's cost, finally separated

The emulator could not resolve `B` from `Bc`. This device can:

- **inputScale 1.0 costs about 2ms** — B@1.0 sits at 15ms P90 against 13ms for capture-only
  at both refresh rates. Affordable at 60Hz, and the largest single item in the budget at
  90Hz.
- **inputScale 0.5 and 0.25 cost under 1ms** — indistinguishable from capture-only at
  90Hz, and within a millisecond of it at 60Hz.

So downscaling is worth roughly 2ms of `RenderEffect` time on a Mali-G52 at 720p, and
buys it back almost entirely at 0.5. **This confirms the plan's intent to default live blur
to `downsampling` 2.** Going further to 0.25 bought nothing on this device and was
marginally *worse* at 60Hz (16ms vs 15ms), so 0.25 should not be the default.

The re-record cost is 0.17–0.21ms median / 0.37–0.46ms P90 — statistically the same as the
A70's, across a different SoC, a different GPU vendor, a different Android version and a
different screen resolution. The per-frame subtree re-record looks genuinely cheap and
genuinely portable.

### About the jank outliers

Three runs out of 40 on this device reported 69–99% janky frames with p50 inflated to
14–23ms. They are environmental, not a property of the blur, and the evidence is direct:
**one of them was the `off` baseline** — no capture, no blur, no `RenderNode` — at 99.23%
jank and 23ms p50. Counting every 90Hz run, the outlier rate was 2 of 13 for B@0.5 and 1 of
6 for the capture-free baseline. Indistinguishable.

This is a real person's daily phone with real apps installed and notifications arriving, so
occasional interference is expected. Recorded here so a future session does not mistake a
repeat for a regression. It is also a reminder that a single 12s run on a shared device is
not evidence; the interleaved baseline is what made these dismissible.

### Phase 0 status after this section

The performance half is no longer provisional. Both the structural verdict and the frame
budget are now measured:

- **Variant B at inputScale 0.5 meets the ≤16.6ms P90 / low-jank bar at 60Hz on low-end
  2021 hardware**, and holds at 90Hz too.
- **`<BlurTarget>` is confirmed as the Phase 1 API** — unchanged, and unchangeable, from the
  structural finding above.
- Still untested: **120Hz** (no 120Hz panel was available; 90Hz is the highest measured)
  and **what an RN Fabric hierarchy adds** over plain Views. Phase 1 still owes the Fabric
  number, measured in the example app rather than this harness.

## Glass edge refraction — feasibility prototype — 2026-09-16

**No performance numbers in this section.** Everything below is visual, from the Android 17
emulator, whose frame timings this document has already established are unusable. The cost
of this technique is entirely unmeasured. Nothing here may be quoted as a budget.

Prototype: variant `G` in [`spike/`](./spike). API 33 (`RuntimeShader` +
`RenderEffect.createRuntimeShaderEffect`), chained onto the Phase 0 capture. Public SDK
only, so it stays inside constraint 1.

### It works, and four things made the difference

Side by side with iOS 26 Liquid Glass: [`glass-vs-ios.png`](./spike/evidence/glass-vs-ios.png).
Same phenomenon, recognisably.

1. **Refract outward, not inward.** A convex glass edge pulls the backdrop in from *outside*
   the shape and squeezes a wide band into a thin rim. Sampling toward the centre produces
   a smear with no lens character. This was the single biggest error.
2. **The capture must bleed past the panel.** Direct consequence of (1): there is nothing
   outside the border to pull in if the capture stops at the border. **This is the one
   finding that constrains Phase 1** — see below.
3. **Three RenderNodes, not one.** `captureNode` stays sharp; `frostNode` blurs it for the
   interior; `rimNode` refracts the *same sharp pixels* for the edge and is transparent
   elsewhere. Drawing frost then rim gives a crisp bevel over a frosted panel out of a
   single capture. Refracting already-blurred pixels — one node for both — is what made the
   first attempt look like a smudge, and had been flagged as the likely quality ceiling.
   It is not a ceiling; it is an architecture choice.
4. **Snell's law over a circular bevel**, `thickness * tan(θi - θr)`, rather than an invented
   falloff. It diverges near the border, and that divergence is the hard squeeze that reads
   as thick glass.

Two smaller ones worth keeping: `flat` is a reserved word in AGSL and the compile error is a
runtime `IllegalArgumentException`, not a build failure; and screen-space derivatives
(`dFdx`/`dFdy`) are not dependable in AGSL, so edge normals come from a numerical SDF
gradient. The numerical gradient is the better code but it was *not* the cause of the corner
artefacts it was written to fix — those turned out to be the displacement sweeping across a
hard colour boundary in the source, which is what a real lens does.

### Parameters that matter, and the API they imply

| Knob | Finding | Evidence |
| --- | --- | --- |
| `flatness` 0..1 | Band width and bevel depth **must move together**. Independently they give either nothing visible or a domed panel. One derived knob; ~0.85 matches iOS. | [`glass-flatness.png`](./spike/evidence/glass-flatness.png) |
| interior blur | Works orthogonally — clear glass through to opaque frost with the bevel pixel-identical throughout. The existing `downsampling`/radius prop carries over unchanged. | [`glass-frost.png`](./spike/evidence/glass-frost.png) |
| `edgeBlur` | The rim needs its *own* blur, independent of the interior. Also removes the aliasing that extreme compression causes on high-frequency backdrops — visible as striped ghost text at 0, gone by 4dp. | [`glass-edgeblur.png`](./spike/evidence/glass-edgeblur.png) |

The bevel is **absolute dp, not a fraction of panel size**: a real sheet of glass has a
physical edge thickness and a larger pane does not get a larger bevel.

### What is still missing

- **Cost.** Unmeasured, and not cheap-looking: 3 RenderNodes, an enlarged capture, and 5
  dependent texture reads per rim pixel. Needs the A22 before any claim.
- **Motion.** iOS ties the sheen to device tilt and morphs shape on interaction. This is a
  static light direction.
- **iOS parity is not the goal on iOS.** iOS 26 has a native glass effect in UIKit
  (`UIGlassEffect`, a `UIVisualEffect` subclass) and our iOS side is already a
  `UIVisualEffectView`. Adopt the system one there rather than porting this shader; verify
  against current docs first.

### Decision

Not in 3.1.0. Recorded as a sketched **Phase 6** in PLAN.md, after the `setBackground()` fix
it depends on.

**The one thing Phase 1 must not get wrong:** the capture rect needs to take an
inset/outset rather than being hard-wired to the overlay's bounds. Glass needs a bleed
margin; live blur does not. Parameterising it now costs nothing, and retrofitting it after
`<BlurTarget>` is public is the expensive kind of change.

## Glass edge — measured on device — 2026-09-16

The cost the section above said was unmeasured. Same device and method as the Phase 0
device section: Galaxy A22 (SM-A225F), Android 13 / API 33, Helio G80 / Mali-G52 MC2,
720×1600, release build, refresh pinned and restored, baseline interleaved, 3 repetitions,
medians below.

The shader renders correctly on Mali-G52 — no vendor divergence from the emulator, which
was a real risk with AGSL.

### Glass requires downscaling. It is not optional.

60Hz (budget 16.6ms):

| Config | P90 frame | Janky | Scroll |
| --- | --- | --- | --- |
| off — baseline | 11ms | ≤0.16% | 60fps |
| live blur, inputScale 0.5 | 14–15ms | ≤0.16% | 60fps |
| **glass, inputScale 1.0** | **24ms** | ≤0.32% | 60fps |
| **glass, inputScale 0.5** | **20ms** | ≤0.16% | 60fps |

90Hz (budget 11.1ms):

| Config | P90 frame | Janky | Scroll |
| --- | --- | --- | --- |
| off — baseline | 13ms | ≤0.11% | 90fps |
| live blur, inputScale 0.5 | 13ms | ≤0.33% | 90fps |
| **glass, inputScale 1.0** | **42ms** | **100.00%** | **70fps** |
| glass, inputScale 0.5 | 19ms | ≤1.09% | 90fps |

**At 90Hz and full resolution the effect collapses**: every frame janky, frame duration
nearly 4x the budget, and the scroll drops from 90fps to 70. All three repetitions were
identical (42/42/42ms, 70.1/70.0/70.1fps), so this is a hard GPU limit, not the
environmental noise documented earlier.

At inputScale 0.5 it holds 90fps with about 1% jank, costing ~6ms P90 over baseline. So the
feature is viable on low-end 2021 hardware **only** with downscaling, and any implementation
should clamp rather than trust a caller-supplied 1.0.

Worth noting at 60Hz: frame *duration* rises to 20–24ms while janky frames stay near zero
and the scroll holds 60fps. Glass buys latency there, not dropped frames. The two metrics
disagree and the jank percentage is the one that matches what the scroll actually did.

### A 4ms self-inflicted wound, for the record

The first 60Hz run measured glass capture at **4.2ms median, 5.3ms P90** against live blur's
0.20ms — a 20x difference that had nothing to do with the effect. `RenderEffect.rimEffect()`
called `new RuntimeShader(AGSL)` every frame, and constructing a RuntimeShader **compiles the
AGSL**. Caching the compiled shader and rebuilding the immutable RenderEffect only when a
uniform actually changes put capture back to 0.20ms, identical to live blur.

All the numbers in the tables above are post-fix. Anyone extending this: `RuntimeShader`
construction is compilation, and it must be hoisted out of the frame loop.

### Three corrections to the section above

1. **Band width and bevel depth move in OPPOSITE directions, not together.** The earlier
   `flatness` mapping shrank both, which also shrinks the compression ratio — so a high
   flatness produced a thin rim with almost no distortion left in it. What reads as glass is
   a *thin* band sampling from *far* out; iOS looks like roughly 15:1. Mapping is now
   band 20→4dp against depth 150→70dp. [`glass-rim-ratio.png`](./spike/evidence/glass-rim-ratio.png)
2. **The sheen belongs on the border stroke, not spread across the band.** Liquid Glass draws
   a hairline whose brightness varies around the perimeter with the light; modulating the
   whole rim band instead reads as a soft glow, which is visibly wrong next to a real one.
   The directional lobes now multiply the stroke.
3. **Horizontal bands cannot test rim distortion.** They run parallel to a horizontal rim and
   perpendicular to a vertical one, so in both cases the displacement slides *along* the
   boundary and nothing appears to bend. The harness now has `--ez diag true`. Every rim
   comparison before this was reading a backdrop that could not show the effect.

### Status

Feasible and affordable at inputScale 0.5, on the weakest hardware available. Still a
Phase 6 sketch, still not in 3.1.0, and the remaining gap to iOS is the compression ratio
and the aliasing under extreme squeeze — UI iteration, not a technical unknown.

## Phase 1 — live blur inside a React Native hierarchy — 2026-09-17

The number Phase 0 owed and could not produce: **what Fabric adds over plain Views.**
Everything below is the shipped library in the example app, not a harness.

Device: Samsung Galaxy A22 (SM-A225F), Android 13 / API 33, Helio G80 / Mali-G52 MC2,
720×1600 @ 300dpi, release build (`assembleRelease`, Hermes, New Architecture / Fabric,
debug-signed). Refresh pinned per run with `settings put system min_refresh_rate` /
`peak_refresh_rate`, restored afterwards.

Method: [`example-sweep.sh`](./example-sweep.sh). The example app's tile list is a
`FlatList` of 400 cells inside `<BlurTarget>`; the frosted panel is a sibling overlay
across the bottom quarter, 720×380px. Each run force-stops the app, relaunches it with
the variant chosen by launch-intent extras, settles 5s, resets `gfxinfo`, then drives ten
alternating 900ms drags of 800px clear of the panel — about 10s of continuous scrolling —
and reads `dumpsys gfxinfo`. The baseline re-runs immediately before every variant on
every repetition. 3 repetitions; tables give the median with the range across
repetitions. Raw rows: [`evidence/phase1-60hz.tsv`](./evidence/phase1-60hz.tsv),
[`evidence/phase1-90hz.tsv`](./evidence/phase1-90hz.tsv).

### It works, and the check is numeric rather than visual

The panel tracks the list as it scrolls:
[`evidence/live-panel.png`](./evidence/live-panel.png). Phase 0 established that this
failure mode is invisible, so it was checked by measurement instead of by eye — two
screenshots at two scroll positions differ by a mean of **93/255 per channel** across the
panel, so the backdrop really is being re-blurred rather than held.

The library's own counter (`adb shell setprop log.tag.BlurOverlay DEBUG`) reported
**109 captures over 120 drawn frames** with the cadence cap off. So in a Fabric hierarchy
the `<BlurTarget>`'s own display list is rebuilt on about 91% of scrolled frames: the
change gate is nearly a no-op during a scroll, and total on a still screen, where no frame
is drawn and the pre-draw listener never runs at all.

### 60Hz — the default configuration costs about one millisecond

Budget 16.6ms. Medians of 3; the baseline row is 18 interleaved runs.

| Variant | downsampling | maxUpdateFps | p50 | P90 | Janky frames |
| --- | --- | --- | --- | --- | --- |
| off — baseline | — | — | 9ms | 13ms | 0.17% |
| snapshot — today's default | 2 | — | 11ms | 14ms | 0.17% |
| **live — the shipped default** | **2** | **30** | **12ms** | **14ms** | **0.52%** |
| live | 2 | uncapped | 12ms | 15ms | 0.17% |
| live | 1 | 30 | 22ms | 25ms | 6.53% |
| live | 1 | uncapped | 22ms | 25ms | 4.15% |

**Live blur at its defaults is indistinguishable from the snapshot it replaces** — 14ms
P90 for both, about 1ms over a no-overlay baseline, with jank at half a percent. That is
the Phase 1 exit criterion met inside Fabric.

**What Fabric costs, against Phase 0's plain-View spike on this same phone:** the baseline
rises from 11ms to 13ms P90 and live blur from 15ms to 14ms — i.e. the hierarchy costs a
couple of milliseconds of baseline, and the blur's *marginal* cost does not change. The
per-frame capture is as cheap in React Native as it was in plain Views.

### 90Hz — the same defaults hold, and the cadence cap earns its keep

Budget 11.1ms. As Phase 0 recorded, the baseline itself overshoots that at this refresh
rate while janking almost nothing, so the jank percentage is the signal that matches what
the scroll actually did.

| Variant | downsampling | maxUpdateFps | p50 | P90 | Janky frames |
| --- | --- | --- | --- | --- | --- |
| off — baseline | — | — | 8ms | 11ms | 0.23% |
| snapshot | 2 | — | 10ms | 12ms | 0.34% |
| **live — the shipped default** | **2** | **30** | **11ms** | **14ms** | **1.50%** |
| live | 1 | 30 | 12ms | 15ms | 1.50% |
| live | 2 | uncapped | 15ms | 17ms | **31.29%** (1.16 / 31.29 / 56.24) |
| live | 1 | uncapped | 12ms | 15ms | 0.81% |

**Uncapped capture is the one configuration that fails.** At half resolution and 90Hz it
janked 31% and 56% of frames in two of three repetitions, against 1.50% for the same
configuration capped at 30fps. At 60Hz the same uncapped variant was fine (0.17%). So the
cap costs nothing where it is not needed and prevents a collapse where it is — it stays on
by default at 30.

### Two anomalies, recorded rather than explained

Both are consistent enough not to be dismissed and neither has an explanation that this
session could test. They do not change the decision — the shipped configuration is the
best one at both refresh rates — but a later phase should not be surprised by them.

1. **Full resolution is ruinous at 60Hz and fine at 90Hz.** `downsampling` 1 measured
   25ms P90 / ~6% jank at 60Hz, in three repetitions out of three, p50 22/22/23 — and
   15ms P90 / ~1.5% at 90Hz, also three out of three, p50 12/12/12. The same work cannot
   genuinely cost 10ms more on a slower-refreshing display. The likeliest cause is GPU
   DVFS: this Mali-G52 probably clocks lower in the 60Hz display mode, so the expensive
   full-resolution blur is absorbed at 90Hz and not at 60Hz. Untested.
2. **The uncapped/half-resolution failure at 90Hz does not reproduce at full
   resolution**, which is backwards if the cost were simply the blur.

Both variants sit at fixed positions in the run order, which is the obvious confound and
the cheapest thing to rule out: a future sweep should randomise variant order within a
repetition. Note that the interleaved baselines next to both anomalies were clean
(13ms/0.17% at 60Hz, 11ms/0.23% at 90Hz), so this is not the environmental jank Phase 0
documented.

### Decisions

- **`downsampling` defaults to 2 for live blur and 1 for snapshot.** Phase 0 measured the
  downscale as worth ~2ms on a 400dp panel; on a full-width panel in an RN app it is worth
  11ms at 60Hz. The plan's intent is confirmed, and more strongly than the spike implied.
- **`maxUpdateFps` defaults to 30.** It is free at 60Hz and prevents a 31–56% jank
  collapse at 90Hz.
- **The change gate stays**, but for what it does on a still screen rather than during a
  scroll: a Fabric `<BlurTarget>` redraws on ~91% of scrolled frames, so it saves almost
  nothing there. The real saving is that a pre-draw listener never fires when nothing draws.
- **Phase 1's exit criteria are met on device**, at both available refresh rates.

### Two things found by reading the code, not by measuring it

Recorded because both are the silent-failure class Phase 0 warned about, and neither would
have shown up in the sweep — the example app always has a valid target.

- **The fallback did not fall back.** `scheduleBlur()` and `captureAndBlur()` both bailed
  out while live mode was running, so an overlay with `blurMode="live"` and no
  `<BlurTarget>` in its window suppressed the snapshot *and* had nothing live to draw: a
  transparent overlay. Fixed by gating the snapshot on whether live blur actually owns the
  backdrop rather than on whether it is enabled, and verified on device with a deliberately
  wrong `blurTargetId`:
  [`evidence/live-no-target-fallback.png`](./evidence/live-no-target-fallback.png) shows
  the snapshot blur, and exactly one warning in logcat.
- **An ancestor target is refused in code.** Phase 0 proved a capture containing the
  overlay cannot work; `resolveTarget()` now walks the overlay's parents and rejects a
  target it finds among them, with a warning, rather than leaving HWUI to blank the panel
  with nothing in the logs.

### Still open after Phase 1

- **120Hz.** Unchanged from Phase 0: no 120Hz panel has run any of this.
- **The split between re-record and blur inside Fabric.** Phase 0 separated them with a
  capture-only variant in the spike; the library has no such mode, so the sweep measures
  them together. Phase 4 needs the split to judge `setBackdropRenderEffect` honestly.
- **`borderRadius` on the Android overlay.** Live blur no longer goes through
  `setBackground()`, so half the obstacle is gone, but the blur is drawn in `onDraw()`
  without clipping to RN's outline, so corners are still square. Left to Phase 3 as the
  plan allows.
- **Whether a 30fps cap is visually acceptable** — measured as cheap, not judged by eye.
  The bound is one cap interval, so up to 33ms of staleness in the backdrop.

## Phase 2 — window blur for Modal-hosted overlays — 2026-09-17

**No frame numbers in this section, by design.** The blur is composited by
SurfaceFlinger, not by us; there is no per-frame work on our side to measure. What
is recorded instead is that the mechanism engages, that it is genuinely live, and
that every way it can decline degrades to the snapshot rather than to nothing.

### The API is not the one the plan named, and here is why

PLAN.md says `Window.setBackgroundBlurRadius`. The shipped code uses
`WindowManager.LayoutParams.FLAG_BLUR_BEHIND` + `setBlurBehindRadius(int)`,
reached through `getRootView().getLayoutParams()` and pushed with
`WindowManager.updateViewLayout`.

The two are halves of the same Android 12 feature and are gated by the same
`isCrossWindowBlurEnabled()`. The difference that decided it: `setBackgroundBlurRadius`
is a method on `android.view.Window`, and **there is no public route from a `View`
to the `Window` hosting it**. Checked against `android.jar`:

```
Window:                    public void setBackgroundBlurRadius(int);
WindowManager.LayoutParams: public void setBlurBehindRadius(int);
                            public static final int FLAG_BLUR_BEHIND;
```

React Native does have a route — `ExtraWindowEventListener`, which hands a library
every Dialog `Window` a `<Modal>` creates, and is documented for exactly this — but it
landed after the `react-native >= 0.80` this package supports, so compiling against
it would break the floor. Reflection is out under constraint 1. The layout-params
route needs neither and is public on every supported version.

What that costs: background blur can be masked by the window's background drawable
(so, shaped and rounded), blur-behind cannot — it blurs everything behind the window.
That is why the overlay refuses the window path when it does not cover the window;
see below. If the RN floor ever moves past `ExtraWindowEventListener`, switching to
`setBackgroundBlurRadius` would buy a shaped blur and nothing else.

### Tied to `blurMode="live"`, not automatic

An overlay already inside a `<Modal>` in 3.0 gets a snapshot of the activity content
behind it, which is a correct and frozen blur. Turning that into a live system blur
without being asked would be a behaviour change for existing users, which the ground
rules forbid. So window blur is what `live` *means* in a Dialog window, and `snapshot`
in a modal is byte-for-byte what it was.

Also inert on this path, because nothing is being captured: `downsampling`,
`maxUpdateFps`, `captureOutset`, `blurTargetId`. `radius` applies unchanged (it is
already in screen pixels). `brightness` becomes a scrim rather than a `ColorMatrix`
offset — the compositor owns the pixels, so they cannot be shifted, only covered.

### Happy path — Android 17 emulator

Device: `sdk_gphone16k_arm64`, Android 17 / API 37, 1280×2856, release build.
(HANDOFF said API 36; the image actually attached is API 37. Both have
`ro.surface_flinger.supports_background_blur=1` and report `KawaseDualFilterV2`.)

Method: `am start ... --es blurMode live --ez modal true`, settle 10s, then three
screenshots 450ms apart. The example app's Modal demo now animates a band across the
app *behind* the modal — a modal window covers the screen, so there is no way to
scroll the list under it, and without moving content there is nothing to tell a live
window blur from a frozen snapshot. It runs only while that demo is open, so the
Phase 1 sweep is unaffected.

| | `dumpsys SurfaceFlinger` | mean per-channel Δ between screenshots |
| --- | --- | --- |
| `blurMode="live"` | `backgroundBlurRadius=14` on the dialog layer | **47.6** then 10.0 |
| `blurMode="snapshot"` | no layer with a non-zero radius | **0.00**, byte-identical |

The radius on the layer is the `radius={14}` the demo passes, so it is ours. And the
live backdrop changes between consecutive screenshots while the snapshot one is
identical to the byte, with the same app doing the same thing — which is the whole
claim: [`evidence/modal-window-blur.png`](./evidence/modal-window-blur.png).

Worth knowing as a tell for future sessions: **window blur reaches under the status
and navigation bars; the snapshot does not**, because the snapshot captures
`android.R.id.content`. The two are distinguishable in a screenshot by that strip
alone.

### The fallback, on hardware that really lacks the feature — Galaxy A22

Device: SM-A225F, Android 13 / API 33, Helio G80 / Mali-G52 MC2, 720×1600, release
build. `ro.surface_flinger.supports_background_blur` unset, SurfaceFlinger names no
blur algorithm, so `isCrossWindowBlurEnabled()` is false. This is the degradation
path tested against a device that genuinely cannot do it rather than a simulated
switch.

Result: one warning naming cross-window blur as the reason, one naming the missing
in-modal `<BlurTarget>`, and a working snapshot blur of the app behind the modal —
not a transparent overlay, which is the failure Phase 1 found in the other fallback.
Frozen across all three screenshots (Δ 0.00), which is what `snapshot` means.
[`evidence/modal-fallback-a22.png`](./evidence/modal-fallback-a22.png)

### Two more ways it can decline, both checked

**Cross-window blur turned off at runtime.** `settings put global disable_window_blurs 1`
is what battery saver does. With the modal already up and blurred, flipping it took
the dialog layer's radius to 0, fired `addCrossWindowBlurEnabledListener`, and the
overlay put a snapshot up in its place; flipping it back brought the blur layer back.
[`evidence/modal-window-blur-disabled.png`](./evidence/modal-window-blur-disabled.png)

**An overlay that covers only part of the modal.** A window blur cannot be scoped to
part of a window, so blurring on its behalf would blur pixels the caller never asked
for. The overlay measures its own bounds against the window's, minus system-bar
insets, and declines: no blur layer, one warning, and a snapshot scoped to the
overlay's own bounds while the rest of the screen behind the modal stays sharp.
`--ez modalPartial true` in the example app reproduces it.
[`evidence/modal-partial-declined.png`](./evidence/modal-partial-declined.png)

### The warnings had to be deferred, and that is a finding

The first build warned twice on every modal, on both devices, and both warnings were
wrong by the time anyone read them: a modal reaches its final size and insets over
several frames, and on the way there the overlay really does not cover its window and
really has no target. Warning the moment a reason is true produces logcat lines that
describe a state that lasted two frames.

The reasons are now collected and printed one second later, and only the ones still
true get printed. After that change the emulator's happy path logs **nothing at all**,
and the A22 logs exactly the two lines that are permanently true of it. This is the
same class of problem as Phase 1's "the fallback did not fall back": a live blur has
several legitimate reasons to be a snapshot instead, and each one needs to be
distinguishable in logs from the others.

### Decisions

- **`FLAG_BLUR_BEHIND` via layout params, not `Window.setBackgroundBlurRadius`** —
  forced by the RN version floor, and revisitable if that floor moves.
- **Window blur is what `live` means in a Dialog window**, and nothing changes for
  `snapshot`.
- **The overlay must cover the modal.** A window-wide effect offered for a
  part-window request would be wrong pixels, silently. Declining is the safe half.
- **Phase 2's exit criteria are met**: live blur behind a `<Modal>` on a device that
  supports it, and graceful degradation on one that does not — plus the runtime
  toggle and the partial-coverage case, neither of which the brief asked for.

### Still open after Phase 2

- **A physical device that supports cross-window blur.** Neither phone here does, so
  the happy path has only ever run on an emulator. That is acceptable for a
  system-side effect with no frame cost of ours, but nobody has looked at this on real
  glass.
- **`Window.setBackgroundBlurRadius` and a shaped blur.** Would let a rounded or
  partial overlay have a window blur instead of being refused. Needs the RN floor at
  or past `ExtraWindowEventListener`.
- **iOS.** Untouched by this phase. An overlay inside an RN `<Modal>` on iOS is a
  `UIVisualEffectView` in the modal's own view hierarchy and is already live.
- **120Hz.** Unchanged from Phases 0 and 1: still nothing.
