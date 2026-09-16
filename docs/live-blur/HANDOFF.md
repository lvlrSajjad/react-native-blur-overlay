# Live blur — session handoff

One phase per session. Start a new session, paste that phase's prompt, work, then update
[RESULTS.md](./RESULTS.md) and the status line in [PLAN.md](./PLAN.md) before finishing.

## Status

| Phase | State | Session notes |
| --- | --- | --- |
| 0 — Feasibility spike | **passed** (2026-09-16) | variant A fails structurally → we need a `<BlurTarget>`. Budget met on device: 15ms P90 / 0% jank at 60Hz, inputScale 0.5, Galaxy A22 (API 33, Helio G80). Re-record 0.2–0.4ms across two SoCs. 120Hz and the Fabric overhead remain untested. |
| 1 — Live blur core (API 31+) | **passed** (2026-09-17) | `<BlurTarget>` + `blurMode="live"` shipped. Measured in `example/` on the A22: 14ms P90 / 0.5% jank at 60Hz, 1.5% at 90Hz — level with the snapshot. `downsampling` 2 and `maxUpdateFps` 30 are both load-bearing defaults. 120Hz still untested. |
| 2 — Modal / window blur | ready | |
| 3 — Fallbacks, props, docs | ready | still owes the `setBackground()`/`borderRadius` fix — Phase 1 got half of it |
| 4 — SDK 37.2 fast path | optional, any time | needs a capture-only mode to split re-record from blur; the library has none |
| 5 — Release 3.1.0 | blocked on 2–3 | |
| 6 — Glass edge refraction | sketched, not scheduled | prototyped **and measured** 2026-09-16: works, and affordable at inputScale 0.5 (collapses at 1.0 — 100% jank at 90Hz). Not in 3.1.0. **Phase 1 delivered the outset-capable capture rect it asked for** (`captureOutset`). |

## Still owed after Phase 1

- **120Hz.** Still nothing. The best panel available remains the Galaxy A22's 90Hz, where
  everything passed. Inherited by whichever phase gets a 120Hz device.
- ~~**What React Native's Fabric hierarchy adds.**~~ Answered in Phase 1: it costs a couple
  of milliseconds of baseline and nothing on the blur's marginal cost.
- **The split between re-record and blur inside Fabric.** The spike had a capture-only
  variant; the library does not, so Phase 1 measured the two together. Phase 4 needs the
  split to judge `setBackdropRenderEffect` honestly.
- **Two unexplained anomalies** in the Phase 1 sweep — full resolution is ruinous at 60Hz
  and fine at 90Hz, and the uncapped failure at 90Hz does not reproduce at full resolution.
  Variant order is fixed in the sweep script and is the obvious confound; randomise it
  before chasing anything else. See RESULTS.md.

Hardware available to this project, none of it permanently attached — ask before assuming:

| Device | API | Refresh | Use |
| --- | --- | --- | --- |
| Galaxy A70 (SM-A705FN), the owner's | 30 | 60Hz | **cannot run live blur** — below the API 31 floor `RenderEffect` needs. Snapshot and capture-only paths only. |
| Galaxy A22 (SM-A225F), **borrowed from a family member** | 33 | 60 / 90Hz | the device Phases 0 and 1 measured on. Ask first, and leave it as found: uninstall test APKs, restore refresh-rate settings, offer to turn developer options back off. |
| Small_Phone / Mo_Device / Medium_Tablet AVDs | 36 | 60Hz | functional checks only |

**Cross-window blur is off on both physical phones and on only the emulator.** Checked
2026-09-17: the A22 has `ro.surface_flinger.supports_background_blur` unset and
SurfaceFlinger names no blur algorithm, so `isCrossWindowBlurEnabled()` returns false
there; the API 36 emulator has the property set to 1 and reports `KawaseDualFilterV2`.
Phase 2 therefore sees its happy path only on the emulator — acceptable, because window
blur is system-side and Phase 2 needs no frame numbers — and gets to test its degradation
path on hardware that genuinely lacks the feature rather than on a simulated switch.

**Emulator frame timings on this machine are not usable as measurements.** The baseline
alone drifts 18–31ms P90 with host load, which swamps the sub-millisecond effects this work
is trying to see. Phase 0 tried and threw the numbers away.

## Read these first, in this order

1. [PLAN.md](./PLAN.md) — constraints, what is settled, the phases.
2. [RESULTS.md](./RESULTS.md) — measurements so far; do not re-measure what is recorded.
3. [RESEARCH-2026-09-16.md](./RESEARCH-2026-09-16.md) — the underlying research, with sources.

## Ground rules that outlive any phase

- **Public SDK APIs only.** No reflection into `@hide`. If a solution needs it, it is not
  a solution.
- **Snapshot stays the default.** Live blur is opt-in until measurements justify
  otherwise; existing users must see no behaviour change.
- **Nothing ships without running on a device.** 3.0.0 passed every compile check and
  still shipped two iOS-breaking bugs. `npm run check:android` / `check:ios` prove the
  code compiles and nothing more.
- **Measure before claiming.** No "should be fast" in commit messages or docs — numbers
  or nothing. Record them in RESULTS.md.
- **Both architectures.** Native changes must build under Fabric *and* the legacy
  architecture (the iOS sources are `#ifdef RCT_NEW_ARCH_ENABLED`; Android's manager
  implements the Codegen interface and keeps `@ReactProp` annotations).

## Repo map

| Path | What it is |
| --- | --- |
| `src/index.tsx` | the JS component, props, emitter API |
| `src/SajjadBlurOverlayNativeComponent.ts` | Codegen spec — **new props start here** |
| `android/src/main/java/com/bluroverly/SajjadBlurOverlayView.java` | capture + blur + background drawable |
| `android/src/main/java/com/bluroverly/SajjadBlurOverlayManager.java` | view manager, implements the generated interface |
| `android/src/main/java/com/bluroverly/StackBlur.java` | CPU blur, the ≤API 30 path |
| `ios/SajjadBlurOverlay.mm` | `UIVisualEffectView` + child mounting (already live; untouched by this work) |
| `example/` | RN 0.87 app, npm workspace, Metro resolves the library to `src/` |
| `scripts/check-android.sh`, `scripts/check-ios.sh` | compile checks without Gradle/CocoaPods |
| `android/.../SajjadBlurTargetView.java` | `<BlurTarget>` — the subtree a live overlay captures, plus its registry |
| `android/.../LiveBlur.java` | the API 31+ capture: `RenderNode` re-record + `RenderEffect`, reached only through out-of-line `@RequiresApi` helpers |
| `docs/live-blur/spike/` | Phase 0's standalone harness — plain Views, no RN. Re-run it for the 120Hz number, or as the plain-View control when measuring what Fabric costs |
| `docs/live-blur/example-sweep.sh` | Phase 1's sweep of the example app. Variants come from launch-intent extras, so nothing depends on tapping buttons |

### How the Android capture works today

`SajjadBlurOverlayView` finds the activity's `android.R.id.content`, sets a `capturing`
flag, and calls `root.draw(canvas)` on a **software** `Bitmap` canvas. Both `draw()` and
`dispatchDraw()` early-return while the flag is set, which is how the overlay excludes
itself and its children from its own snapshot. The bitmap is blurred on a single-thread
executor (`StackBlur`, radius clamped to 25 after downsampling), brightness is applied as
a `ColorMatrixColorFilter` on the drawable, and the result is set with `setBackground()`.
A `generation` counter drops stale async results.

Two consequences worth remembering: `setBackground()` replaces RN's own background
drawable, so `borderRadius`/`borderWidth`/`backgroundColor` on the overlay do nothing on
Android; and **the self-exclusion trick works only for software capture — Phase 0 proved it
fails on a hardware canvas**, where it fires once and permanently blanks the overlay. Keep
it on the snapshot path; it must not be carried into the live path. See RESULTS.md.

## Commands

```bash
npm install                 # installs deps, builds the library, links the example workspace
npm run typecheck && npm test
npm run check:android       # javac against real RN classes + Codegen output
npm run check:ios           # clang against real RN headers, both architectures (needs Xcode)
npx bob build
```

Running the example:

```bash
npm run example:start       # Metro, in its own terminal
npm run example:android
npm run example:ios         # first run does pod install
```

Android device/emulator by hand (the emulator boots from a clean snapshot, so reinstall):

```bash
$ANDROID_HOME/emulator/emulator -avd Small_Phone_API_36 -no-window -no-audio -no-boot-anim &
adb wait-for-device
adb install -r example/android/app/build/outputs/apk/debug/app-debug.apk
adb reverse tcp:8081 tcp:8081
adb shell am start -n com.bluroverlayexample/.MainActivity
```

Frame measurement without a benchmark harness:

```bash
adb shell dumpsys gfxinfo com.bluroverlayexample reset
adb shell input swipe 360 900 360 200 120     # repeat to sustain a fling
adb shell dumpsys gfxinfo com.bluroverlayexample | sed -n '/Janky frames/,/99th/p'
```

Or, for the example app, `HZ=60 REPS=3 SERIAL=<serial> ./docs/live-blur/example-sweep.sh`,
which does all of the above per variant. `ONLY=<variant>` restricts it to one.

Use Macrobenchmark instead when a phase needs numbers precise enough to publish.

**Three things Phase 0 learned the hard way about measuring this** — ignore them and you
will record noise as a result:

- **Interleave the baseline.** Run the no-blur baseline immediately before each variant, on
  every repetition. Phase 0's test phone threw 69–99% jank outliers about one run in six,
  on the *baseline* as often as on the variant; without an adjacent baseline those look
  exactly like a regression.
- **Drive the scroll from a `Choreographer` callback, not `input swipe`.** A fixed px/frame
  auto-scroll is deterministic and removes input-injection variance. `spike/SpikeActivity`
  does this behind `--ei durationMs`.
- **Reset `gfxinfo` a few seconds in,** so process start and list warm-up are not charged to
  the variant, and pin the refresh rate (`settings put system min_refresh_rate` /
  `peak_refresh_rate`) rather than trusting the phone to stay where you left it.

## Per-phase prompts

Paste one of these into a fresh session in this repo.

### Phase 0

```
Work Phase 0 of docs/live-blur/PLAN.md in this repo. Read PLAN.md and HANDOFF.md first.

Build a throwaway harness (no React Native, no library changes) that measures whether a
per-frame hardware capture is affordable and whether our self-exclusion trick survives
it: a RecyclerView filling the screen, a ~400dp panel on top that each frame records a
target into a RenderNode, applies RenderEffect.createBlurEffect, and draws it as its
backdrop.

Compare variant A (capture the whole android.R.id.content, excluding the panel via the
draw()/dispatchDraw() flag the library already uses) against variant B (a scoped target
subtree that does not contain the panel), at inputScale 1.0 / 0.5 / 0.25.

Report P90 frame duration and jank during a sustained fling, release build, 60Hz and
120Hz, on the emulator and on a physical device if one is attached. Check a still frame
for recursive self-blur in variant A — the numbers alone will not show it.

Record everything in docs/live-blur/RESULTS.md and update the status table in
HANDOFF.md. Do not start Phase 1. If variant A fails, say so plainly: it means the
public API needs a <BlurTarget> component.
```

### Phase 1

```
Work Phase 1 of docs/live-blur/PLAN.md. Read PLAN.md, RESULTS.md and HANDOFF.md first.
Phase 0 settled the capture strategy: a scoped <BlurTarget> subtree that does not contain
the overlay. Do not revisit that — variant A is structurally impossible, not merely slow.

Implement blurMode="live" for API 31+, with "snapshot" still the default: a <BlurTarget>
component, new props in the Codegen spec and the Android manager, RenderEffect on the
RenderThread, recapture only when the target actually draws, a capped update cadence,
downscale via the existing downsampling prop, and automatic fallback to snapshot below
API 31 or when capture fails. Read the "Follow-ups Phase 1 must know" list in RESULTS.md
before writing the capture code — it records the exact RenderNode calls that worked and
the one that did not.

Let the capture rect take an inset/outset instead of hard-wiring it to the overlay bounds.
Live blur passes 0. The Phase 6 glass prototype needs a bleed margin, and retrofitting that
after <BlurTarget> is public is the expensive kind of change. That is the only thing Phase 6
asks of Phase 1 — do not build any of the rest of it.

Verify by running the example app on a device with the tile list scrolling behind the
frosted panel, and record frame numbers in RESULTS.md. ("Glass" now means the Phase 6
refraction effect specifically; Phase 1 is plain live blur.) Phase 0 measured the spike's plain-View
numbers on a Galaxy A22 (15ms P90, 0% jank at 60Hz, inputScale 0.5); what Fabric adds on top
is still unknown, so measure in example/ rather than assuming the spike's numbers carry
over. Do not call Phase 1 done without that. `npm run check:android`, `npm run typecheck`,
`npm test` must all pass. Do not release.
```

### Phase 2

```
Work Phase 2 of docs/live-blur/PLAN.md — the Modal/window blur path. Read PLAN.md,
RESULTS.md and HANDOFF.md first.

Detect that the overlay is hosted in a Dialog window (as RN <Modal> does) and use
Window.setBackgroundBlurRadius there, gated on WindowManager.isCrossWindowBlurEnabled(),
with a translucent window background and a graceful fallback when cross-window blur is
off. Add a Modal demo to the example app.

Verify both halves, and note where: the Galaxy A22 does NOT support cross-window blur, so
it tests the fallback for real, and the happy path is only visible on the API 36 emulator.
That is fine here — window blur is system-side, so this phase needs no frame numbers — but
do not let the emulator tempt you into quoting timings from it.
```

### Phase 3

```
Work Phase 3 of docs/live-blur/PLAN.md — fallbacks, prop surface and docs. Read PLAN.md,
RESULTS.md and HANDOFF.md first.

Add the optional periodic re-blur for API 24–30 (off by default), finalise and document
the prop surface with an honest cost model, and if it was not done in Phase 1, replace
setBackground() so that borderRadius/borderWidth/backgroundColor work on the overlay
itself on Android. Update README and the example app.
```

### Phase 4

```
Work Phase 4 of docs/live-blur/PLAN.md — the SDK 37.2 fast path. Read PLAN.md and
RESULTS.md first.

Add RenderNode.setBackdropRenderEffect behind a flag, gated on
Build.VERSION.SDK_INT_FULL >= 3_700_002. Haze measured this path as 23–46% slower on CPU
than re-recording, so measure both on the same device and only make it the default if it
actually wins. Record the comparison in RESULTS.md.
```

### Phase 6

```
Work Phase 6 of docs/live-blur/PLAN.md — the glass edge. Read PLAN.md and the glass section
of RESULTS.md first; a working prototype already exists as variant G in
docs/live-blur/spike, so start by running it rather than from scratch.

Do not start this before Phase 3: it needs the setBackground() fix for the corner radius.

The API 33 measurement is already done and in RESULTS.md — glass holds 90fps at inputScale
0.5 and collapses completely at 1.0, so clamp the scale rather than trusting a caller. Read
the three corrections at the end of that section before touching the shader; two of them are
bugs that looked like aesthetic choices, and the third is why the test backdrop needs
--ez diag true.
```

### Phase 5

```
Work Phase 5 of docs/live-blur/PLAN.md — release 3.1.0. Read PLAN.md and RESULTS.md.

Changelog, README with an honest description of what is live and what is not, example
screenshots or a GIF showing live blur, version bump, tag. Run the example app on both a
device and a simulator before releasing — 3.0.0 shipped two iOS-breaking bugs that passed
every compile check. Publishing happens from CI via the tag; do not npm publish by hand.
```

## Finishing a session

1. Append measurements and decisions to RESULTS.md (append-only — do not rewrite history).
2. Update the status table above and the status line at the top of PLAN.md.
3. Commit with a message that says what was decided, not just what changed.
