# Changelog

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
