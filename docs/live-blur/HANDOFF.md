# Live blur — session handoff

One phase per session. Start a new session, paste that phase's prompt, work, then update
[RESULTS.md](./RESULTS.md) and the status line in [PLAN.md](./PLAN.md) before finishing.

## Status

| Phase | State | Session notes |
| --- | --- | --- |
| 0 — Feasibility spike | **passed** (2026-09-16) | variant A fails structurally → we need a `<BlurTarget>`. Budget met on device: 15ms P90 / 0% jank at 60Hz, inputScale 0.5, Galaxy A22 (API 33, Helio G80). Re-record 0.2–0.4ms across two SoCs. 120Hz and the Fabric overhead remain untested. |
| 1 — Live blur core (API 31+) | ready | ships `<BlurTarget>`, `downsampling` 2 by default; owes the Fabric-hierarchy measurement in `example/` |
| 2 — Modal / window blur | blocked on 1 | |
| 3 — Fallbacks, props, docs | blocked on 1 | |
| 4 — SDK 37.2 fast path | optional, any time after 1 | |
| 5 — Release 3.1.0 | blocked on 1–3 | |

## Still owed after Phase 0

Phase 0's own exit criteria are met. Two things it could not reach, both inherited by
Phase 1:

- **120Hz.** The best panel available was the Galaxy A22's 90Hz, where everything passed.
  No 120Hz device has run this.
- **What React Native's Fabric hierarchy adds.** The spike is plain Views, per the phase
  brief. This is only answerable in `example/`, so Phase 1 must measure it there rather
  than inheriting the spike's numbers.

Hardware note for whoever picks this up: a Galaxy A70 is attached to this machine but is
**API 30**, below the API 31 floor `RenderEffect` needs — it can only exercise the snapshot
and capture-only paths. Emulator frame timings on this machine are not usable at all; the
baseline alone drifts 18–31ms P90 with host load.

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
Android; and the self-exclusion trick is only known to work for *software* capture.

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

Use Macrobenchmark instead when a phase needs numbers precise enough to publish.

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

Verify by running the example app on a device with the tile list scrolling behind the
glass panel, and record frame numbers in RESULTS.md. Phase 0 measured the spike's plain-View
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
off. Add a Modal demo to the example app and verify live blur behind it on a device.
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
