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
