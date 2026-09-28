# Changelog

## Unreleased (3.2.1)

### Fixed

- **iOS: the blur follows the overlay's own `borderRadius`.** Only glass did
  before: a rounded overlay drew a rounded border around a square blur,
  unless a parent with `overflow: 'hidden'` clipped it. This included glass's
  fallback below iOS 26. A capsule radius stays a capsule.
- Android: below API 31 the fallback log line now names the mode that was
  set (it said `"live"` even for `"glass"`).

### Changed

- The example app's Metro is 0.87.1, which drops `image-size` and the two
  denial-of-service advisories against it. Development tooling only: nothing
  in the published package changed.
- The README shows `interactive` glass in motion on both platforms.

## 3.2.0

Glass options that exist on both platforms: each maps to a `UIGlassEffect`
property on iOS 26 and is built to match on Android 13.

### Added

- **`glassVariant`**: `'regular'` (default) or `'clear'`. iOS's two glass
  styles. On Android, regular is a 10dp blur lifted toward white and clear a
  5dp blur that is not, matched side by side against iOS 26.
- **`glassTint`**: the glass body's colour; its alpha is the strength. iOS's
  `tintColor`.
- **`interactive`**: glass that responds to touch. iOS's `interactive`; on
  Android a light blooms under the finger and the rim brightens, without taking
  the touch from the children.
- **`blurRadius`**: a blur radius in dp, so it blurs the same on every screen.
  Android only; it wins over `radius`.

### Changed

- With `blurMode="glass"` and no radius set, the Android blur now follows
  `glassVariant` (10dp regular, 5dp clear) instead of 20 physical pixels.
- On iOS, glass now hosts the children inside its content view, as Apple
  intends, and is assigned to the effect view once it is on screen. Both are
  needed for `interactive` to respond.

### Deprecated

- `radius`, which is in physical pixels and so blurs differently on every
  Android screen. Use `blurRadius`. `radius` is still honoured.

## 3.1.0

Live blur on Android, and Liquid Glass on both platforms. Everything here is
opt-in: `snapshot` stays the default, so an app upgrading from 3.0 sees no
change until it asks for one.

### Added

- **`blurMode="glass"`: Liquid Glass.** On iOS 26+ the overlay becomes the
  system's own `UIGlassEffect`, shaped to the overlay's `borderRadius` (a
  capsule when the radius is half the height). On Android 13+ it is the live
  blur plus an AGSL lens: the backdrop bends and folds back on itself toward
  the rim, with a thin, directionally lit hairline and a faint colour fringe.
  It falls back to `live` on Android 12 and to the `blurStyle` blur below
  iOS 26. The lens ports ideas from two open-source Android implementations:
  Kyant0/AndroidLiquidGlass (Apache-2.0) and QWEA0/Liquid-Glass-Android (MIT).
  Both are credited in `Glass.java`.
- **`blurMode="live"`** on Android 12+: a `<BlurTarget>` around the content to
  blur, re-recorded into a `RenderNode` and blurred on the RenderThread as it
  draws, so content scrolling behind the overlay stays blurred, as on iOS.
  `maxUpdateFps` (default 30) caps the re-blurs; `blurTargetId` picks a target;
  `captureOutset` reaches past the overlay's edges. `downsampling` defaults to
  2 in live mode.
- **Blur behind a `<Modal>`**: `blurMode="live"` inside a modal asks the system
  to blur behind the modal's window, where cross-window blur is available, and
  falls back to a snapshot where it is not.
- **`snapshotUpdateFps`**: retakes the snapshot on a timer, the only way to
  get a moving backdrop below Android 12. Off by default.
- **`saturation`** (default 1): colour saturation of the blur on Android. A
  blur averages, so it washes colour out; iOS materials put it back at about
  1.8, and so can you now.
- Documented frosted-glass panels: an overlay sized to a rounded box gives an
  iOS-material-style glass surface on both platforms, with a demo in the
  example app and screenshots from both.
- The example app has a floating capsule tab bar, and on both platforms it
  takes its starting state from launch arguments (intent extras on Android,
  `-key value` arguments on iOS).

### Fixed

- **`borderRadius`, `borderWidth` and `backgroundColor` now work on the Android
  overlay itself.** The blur used to be the view's background drawable, so
  setting any of the three replaced it. It is now drawn under what React Native
  draws, so the radius shapes it, a translucent colour tints it and the border
  frames it.
- **A live blur clips to a capsule.** React Native publishes a rounded outline
  as a path, which has no radius to read back, so a pill-shaped live overlay
  used to stay rectangular. A uniform `borderRadius` is now read from the style.
- Snapshot blurs no longer use RenderScript anywhere.

### Performance

Measured on a Galaxy A22 (Android 13, Helio G80), the weakest device we have,
in the example app's release build:

- `snapshot` and `live` cost the same as each other: live holds 60Hz with
  under 1.3% jank, and 90Hz at 1.4-2.2% jank, level with Phase 1.
- `glass`: the lens adds no measurable GPU time and about 0.4ms of RenderThread
  time per frame over `live`. At 60Hz it holds the frame rate (P90 15-25ms, at
  most 3.6% janky frames). At 90Hz that phone runs `live` and `glass` at the
  edge of its budget, and glass tips over more often: 2-14% janky frames
  depending on the run, a known limit on low-end 90Hz phones. Glass never
  captures above half resolution.

Per-corner radii shape a snapshot but not a live or glass blur, which can only
be clipped to a uniform radius.

## 3.0.1

Two iOS bugs that 3.0.0 shipped with, both found by building and running the
new example app on a simulator. Android was unaffected.

### Fixed

- **iOS builds on the New Architecture failed**: `SajjadBlurOverlayManager.m`
  is compiled as Objective-C but imported the view header, which derives from
  `RCTViewComponentView` and pulls in C++ (`'atomic' file not found`). The
  import now lives inside the legacy-architecture guard, where it belongs.
- **Nothing inside the overlay responded to presses on iOS**: the view handed
  its children container to React Native as `contentView`, but React Native
  clears that when it recycles a component view — leaving an empty container
  that `betterHitTest:` searched (and that `applyEffect` kept re-adding on
  top), so every touch was attributed to the overlay itself. The container is
  now owned by the view, with children routed into it by
  `mountChildComponentView:index:`.

### Added

- An example app under `example/`, wired up as an npm workspace, that exercises
  every prop on both platforms. CI typechecks it, bundles it for both platforms
  and builds the Android app.
- `scripts/check-ios.sh` now compiles `.m` as Objective-C and `.mm` as
  Objective-C++, the way Xcode does — compiling everything as C++ is what let
  the bug above through.

## 3.0.0

A maintenance release: both native implementations were rewritten, the JS API
is now typed and hook-based, and the package works on the New Architecture.

### Breaking

- Requires React Native >= 0.80, iOS 15.1+ and Android minSdk 24. Use `2.x` for older versions.
- The library is autolinked: remove the manual `pod 'SajjadBlurOverlay', :path => ...` entry from your `Podfile` and the manual `SajjadBlurOverlayPackage` registration from `MainApplication`.
- `onPress` is no longer called for presses that land on the overlay's children. Pass `closeOnChildPress` for the old behaviour ([#21](https://github.com/lvlrSajjad/react-native-blur-overlay/issues/21)).
- `idBlur`, `customStyles` and `animationDuration` are deprecated in favour of `id`, `style` and `fadeDuration`. The old names still work.
- The Android component is now registered as `SajjadBlurOverlay` (was `RCTSajjadBlurOverlay`) so that one Codegen spec covers both platforms.

### Added

- New Architecture (Fabric) support through a Codegen spec, with the legacy architecture still supported.
- `visible` prop for declarative control, which is also how to keep the overlay up permanently ([#22](https://github.com/lvlrSajjad/react-native-blur-overlay/issues/22)).
- Imperative `ref` API: `open()` and `close()`.
- `fadeDuration` prop to configure the fade in/out animation, from [#13](https://github.com/lvlrSajjad/react-native-blur-overlay/pull/13), [#15](https://github.com/lvlrSajjad/react-native-blur-overlay/pull/15) and [#16](https://github.com/lvlrSajjad/react-native-blur-overlay/pull/16) (thanks @petefox and @hsjoberg).
- `onShow` / `onHide` callbacks.
- `closeOnChildPress` prop.
- iOS: `blurStyle` accepts the system materials (`systemMaterial`, `systemThinMaterial`, `systemUltraThinMaterial`, `systemThickMaterial`, `systemChromeMaterial`, `regular`, `prominent`), which follow the device appearance.
- iOS: `blurStyle` and `vibrant` are actually applied — neither prop was exported by the view manager before.
- `openOverlay()` / `closeOverlay()` warn in development when no mounted overlay uses the given id ([#8](https://github.com/lvlrSajjad/react-native-blur-overlay/issues/8)).
- TypeScript declarations, published from source built with `react-native-builder-bob`.
- Unit tests and a CI workflow; releases publish from CI through npm Trusted Publishing.

### Fixed

- Android: the snapshot is cropped to the overlay's position on screen instead of stretching a full-screen snapshot, so an overlay that covers part of the screen blurs just that part ([#20](https://github.com/lvlrSajjad/react-native-blur-overlay/issues/20), [#2](https://github.com/lvlrSajjad/react-native-blur-overlay/issues/2)).
- Android: the blur no longer includes the overlay itself or its children, and is taken fresh every time the overlay is shown ([#14](https://github.com/lvlrSajjad/react-native-blur-overlay/issues/14)).
- Android: blurring no longer depends on the `brightness` prop being set — nothing was blurred at all without it.
- Android: replaced RenderScript (deprecated in Android 12, unavailable to new builds) with a Stack Blur implementation, and `AsyncTask` with an executor.
- Android: `compile` is gone from `build.gradle`, along with the `package` attribute in the manifest that AGP 8 rejects ([#23](https://github.com/lvlrSajjad/react-native-blur-overlay/issues/23)).
- `openOverlay()` with no id now matches an overlay rendered without an id, which is what the README always showed ([#8](https://github.com/lvlrSajjad/react-native-blur-overlay/issues/8)).
- `onPress` is passed through to the overlay instead of being hardcoded ([#5](https://github.com/lvlrSajjad/react-native-blur-overlay/issues/5)).
- 2.x shipped `index.tsx` as the package entry point and imported Node's `events` module, which Metro cannot resolve; the package now ships a compiled build.
- iOS: children are mounted into a container view above the blur, instead of relying on subview insertion order.
- iOS: the tap gesture recogniser that swallowed touches meant for the children is gone; presses are handled in JS on both platforms.

## 2.0.1

See the git history.
