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
