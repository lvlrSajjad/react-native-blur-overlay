# Live backdrop blur on Android — plan

**Status:** Phase 0 not started · **Target release:** 3.1.0 (additive, opt-in) · **Last updated:** 2026-09-16

## The problem

On iOS the overlay is a `UIVisualEffectView`, so the blur is **live**: content moving
behind it is re-blurred by the system every frame, for free.

On Android (3.x) the overlay takes **one snapshot** when it mounts — it draws the
activity's content view into a software `Bitmap`, blurs it on a background thread with
`StackBlur`, and sets the result as the view's background. Scroll behind it and the blur
does not update.

The goal is live blur on Android: a glass panel stays blurred while a list scrolls
behind it, at 60fps (120Hz where applicable), on mid-range hardware.

## Hard constraints

These decide whether a technique is shippable at all. A finding that violates one is out,
however clever.

1. **Public SDK APIs only.** No reflection into `@hide`/non-SDK interfaces — this library
   is a dependency inside other people's apps.
2. **No runtime permissions, consent dialogs or persistent notifications** (rules out
   `MediaProjection`).
3. Must work for a view inside React Native's **Fabric hierarchy, in the same window** as
   the content being blurred.
4. **minSdk 24**, degrading gracefully to today's snapshot below whatever API a technique
   needs.
5. **No new Maven repositories** (JitPack is friction for an autolinked RN library).
   Borrowed code must be MIT/Apache-2.0 and is vendored, not depended on.
6. `SurfaceView`-backed content (video, camera, maps) cannot be captured. Accepted.

## What is already settled

From [RESEARCH-2026-09-16.md](./RESEARCH-2026-09-16.md) (third-party research, sourced),
cross-checked against the Android docs:

| Claim | Status | Consequence |
| --- | --- | --- |
| `RenderEffect.createBlurEffect` (API 31+) blurs a view's **own** content | verified | Not a backdrop primitive on its own |
| `SurfaceControl.Transaction#setBackgroundBlurRadius` is `@hide` | verified in AOSP | Dead end — violates constraint 1 |
| `Window.setBackgroundBlurRadius` (API 31+) blurs behind a **window**, live, system-side, gated by `isCrossWindowBlurEnabled()` | verified | Usable only for Dialog/Modal-hosted overlays — see Phase 2 |
| `RenderNode.setBackdropRenderEffect` is the true primitive, public from **SDK 37.2** (Android 17 QPR2, ~Dec 2026) | verified | ~0% install base today — Phase 4, not a plan |
| Haze measured the native backdrop path **23–46% higher CPU P90** than its re-record path | verified, but CPU-time only | The future primitive is not automatically faster — measure, don't assume |
| No public path to backdrop pixels below 37.2 without re-recording a subtree | verified | Capture-based approach is the only shippable one |
| Skia `BackdropFilter` cannot blur native views outside its canvas (maintainer statement) | verified | Rules out an RN Skia route |
| Flutter's `BackdropFilter` works because the engine owns one surface | verified | Not reachable from a View-based renderer |
| Dual-Kawase is near-constant-cost vs radius, unlike Gaussian | verified | Only relevant if we own the texture; `RenderEffect` + downscale is cheaper to reach |
| Dimezis BlurView is Apache-2.0 but JitPack-distributed; the Maven Central republish is unofficial and lagging | verified | Vendor our own implementation, study theirs |
| `inputScale = 0.5` measured a 5–20% win in Haze | verified | Downscale is a real knob, default it |

**Unmeasured anywhere public:** the per-frame cost of this approach inside a React
Native Fabric hierarchy. Every number above comes from Compose (Haze) or the Flutter
engine. That gap is what Phase 0 exists to close.

## The open question that decides our API

The research concludes the blurred content must live in a **BlurTarget** subtree that
excludes the overlay — which forces users to restructure their app tree, exactly as
`expo-blur` and `@react-native-community/blur` do.

We may not need that. Our Android view **already excludes itself** from an ancestor's
capture: `SajjadBlurOverlayView` sets a `capturing` flag and early-returns from both
`draw()` and `dispatchDraw()`, so `contentView.draw(canvas)` records everything except
our own subtree. That is the mechanism BlurTarget exists to provide.

**The known failure mode:** that trick works today because we capture into a *software*
canvas. When HWUI re-records an ancestor into a hardware display list, children with
valid `RenderNode`s are re-referenced (`drawRenderNode`) rather than re-drawn, so our
`draw()` override may never be called — and the overlay would blur **itself**,
recursively. This is very likely why BlurView v3 introduced explicit targets.

Phase 0 tests exactly this. The answer sets our public API:

- **If self-exclusion survives hardware capture** → live blur with no app-tree
  restructure, which no other RN blur library offers.
- **If it does not** → we adopt a `<BlurTarget>` component like everyone else, and
  Phase 2 becomes the reason to choose this library.

## Phases

Each phase is a session's worth of work. Do not start a phase before its predecessor's
exit criteria are recorded in [RESULTS.md](./RESULTS.md).

### Phase 0 — Feasibility spike (decides the API)

**Objective:** measure whether a per-frame hardware capture is affordable in an RN app,
and whether self-exclusion survives it.

**Build:** a throwaway harness — no RN, no library changes. A plain Android project (or
an added activity in `example/android`) with a `RecyclerView` filling the screen and a
~400dp panel on top that, every frame:

1. records a target into a `RenderNode` (variant **A**: the whole `android.R.id.content`
   with self-exclusion; variant **B**: a scoped target subtree that excludes the panel),
2. applies `RenderEffect.createBlurEffect(r, r, CLAMP)` at `inputScale` 1.0 / 0.5 / 0.25,
3. draws it as the panel's backdrop.

**Measure:** P90 frame duration and jank during a sustained fling, release build, at 60Hz
and 120Hz, on the emulator *and* at least one physical device.

**Exit criteria:**

- **Pass** — variant A holds ≤16.6ms P90 at 60Hz with downscale 0.5 and shows no
  recursive self-blur → Phase 1 ships without a `BlurTarget`.
- **Partial** — only variant B holds → Phase 1 ships with a `<BlurTarget>` component.
- **Fail** — neither holds on mid-range hardware even at 0.25 downscale → stop; live
  blur is not viable for this library. Write that up in RESULTS.md and close the
  initiative honestly. Temporal decoupling (below) is then the only offer.

**Risks:** emulator frame times are not representative — a physical device is required
for the verdict. Recursive self-blur may be visually subtle; check a still frame, not
just the numbers.

### Phase 1 — Live blur core (API 31+)

**Objective:** ship `blurMode="live"` for API 31+, defaulting to `"snapshot"`.

**Scope:**

- New prop on the Codegen spec (`src/SajjadBlurOverlayNativeComponent.ts`) plus the
  Android manager; iOS ignores it (already live).
- Capture path chosen by Phase 0. `RenderEffect` Gaussian on the RenderThread.
- **Change gating:** recapture only when the target subtree actually draws.
- **Cadence cap:** a `maxUpdateFps`-style knob (default ~30) so a scrolling backdrop
  costs a bounded amount. The eye tolerates a slightly stale blur far better than a
  stuttering one.
- **Downscale:** reuse the existing `downsampling` prop; default 2 for live.
- Automatic fallback to snapshot below API 31, and whenever capture fails.

**Opportunity while here:** drawing the blur ourselves instead of via `setBackground()`
would let `borderRadius` / `borderWidth` / `backgroundColor` work on the overlay itself
on Android — currently they are ignored (see README "How it works"). Only take this on if
it falls out of the implementation naturally; otherwise leave it for Phase 3.

**Exit criteria:** the example app's glass panel stays blurred while the tile list
scrolls behind it, on device, with P90 within budget; `npm run check:android` clean;
snapshot mode unchanged for existing users.

### Phase 2 — Modal / window blur (the differentiator)

**Objective:** make the overlay work where every capture-based library fails.

An RN `<Modal>` renders in a **separate Android window**, so no capture technique can
reach the content behind it — this is a hard wall for `expo-blur`
([expo#44165](https://github.com/expo/expo/issues/44165)) and for us. But
`Window.setBackgroundBlurRadius` blurs behind a *window*, live and system-side, at no
cost to us.

**Scope:** detect that the overlay is hosted in a Dialog window; if so, apply window blur
gated on `WindowManager.isCrossWindowBlurEnabled()`, with a translucent window
background, falling back to the current behaviour when cross-window blur is disabled
(battery saver, unsupported GPUs, multimedia tunnelling).

**Exit criteria:** a blur inside an RN `<Modal>` in the example app shows live blur of the
app behind it on device; disabling cross-window blur degrades without breaking.

### Phase 3 — Fallbacks, props and docs

**Scope:**

- API 24–30: optional periodic re-blur (15–20fps) of the snapshot so a scrolling backdrop
  at least updates coarsely; off by default.
- Finalise the prop surface and document the cost model honestly (what is live, what is
  not, what cannot be captured).
- If not already done in Phase 1, fix the Android background/border limitation.
- Example app: a demo that makes the difference obvious (list scrolling behind glass).

### Phase 4 — SDK 37.2 fast path

**Scope:** use `RenderNode.setBackdropRenderEffect` when available — it needs no
restructure and no capture. Gate on `Build.VERSION.SDK_INT_FULL >= 3_700_002` (note the
Robolectric inconsistency flagged in the research).

**Important:** Haze measured this path as *slower* on CPU than re-recording. Put it
behind a flag, measure both on the same device, and only make it the default if it wins.

### Phase 5 — Release 3.1.0

Changelog, README, screenshots/GIF of live blur on both platforms, version bump, tag.
**Do not publish without running the example app on a device** — 3.0.0 shipped two
iOS-breaking bugs that compile checks did not catch.

## Decision record

- **Capture-based, not compositor-based** — the compositor API is `@hide` and the
  platform primitive needs SDK 37.2.
- **Vendored implementation, not a dependency** — JitPack breaks constraint 5 and the
  Maven Central republish of BlurView is unofficial and lagging.
- **Opt-in, snapshot stays the default** — live blur has real costs and real limits;
  existing users should see no change until they ask for it.
- **`RenderEffect` Gaussian, not a custom dual-Kawase shader** — the custom shader only
  pays off once we own the texture, and downscaling buys the same headroom far more
  cheaply.

## Open questions

- Does self-exclusion survive hardware display-list capture? (Phase 0)
- What does an RN Fabric hierarchy add to the per-frame cost over plain Views? (Phase 0)
- Is the cadence cap visually acceptable at 30fps? At 20? (Phase 1)
- Can the blur be drawn without `setBackground()`, restoring `borderRadius` support on
  Android? (Phase 1 or 3)
