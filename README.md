# react-native-blur-overlay

[![npm version](https://img.shields.io/npm/v/react-native-blur-overlay.svg)](https://www.npmjs.com/package/react-native-blur-overlay)
[![npm downloads](https://img.shields.io/npm/dm/react-native-blur-overlay.svg)](https://www.npmjs.com/package/react-native-blur-overlay)
[![license](https://img.shields.io/npm/l/react-native-blur-overlay.svg)](./LICENSE)

A native blur overlay for React Native: it blurs whatever is rendered behind it and lets you put your own content on top — full-screen, or just one rounded panel, which is the closest you can get to iOS's frosted-glass materials on Android.

<p>
  <img src="https://raw.githubusercontent.com/lvlrSajjad/react-native-blur-overlay/master/docs/ios-glass.png" width="220" alt="iOS: a rounded frosted-glass panel">
  <img src="https://raw.githubusercontent.com/lvlrSajjad/react-native-blur-overlay/master/docs/android-glass.png" width="220" alt="Android: the same rounded frosted-glass panel">
  <img src="https://raw.githubusercontent.com/lvlrSajjad/react-native-blur-overlay/master/docs/ios-overlay.png" width="220" alt="iOS: a full-screen dark blur behind a card">
  <img src="https://raw.githubusercontent.com/lvlrSajjad/react-native-blur-overlay/master/docs/android-overlay.png" width="220" alt="Android: a full-screen blurred, darkened overlay behind a card">
</p>

<sub>The <a href="./example">example app</a> on the New Architecture. The first two are <strong>the same glass panel, same code</strong>, on iOS and Android; then a full-screen overlay on each.</sub>

- **Frosted-glass panels on both platforms** — the same code that gives you a `UIVisualEffectView` material on iOS gives you a matching glass panel on Android
- Works on the **New Architecture** (Fabric, via Codegen) and on the legacy architecture
- TypeScript types included
- Autolinked — no Podfile or `MainApplication` edits
- No third-party dependencies

> **3.0 is a maintenance release** that rewrites both native sides and the JS API. See [Migrating from 2.x](#migrating-from-2x).

## Requirements

| | |
| --- | --- |
| React Native | >= 0.80 (New Architecture and legacy both supported) |
| iOS | 15.1+ |
| Android | minSdk 24+ |

For older React Native versions use `react-native-blur-overlay@2`.

## Installation

```bash
npm install react-native-blur-overlay
```

```bash
cd ios && pod install
```

That's it — the library is autolinked. If you are upgrading from 2.x, **remove** the manual `pod 'SajjadBlurOverlay', :path => ...` line from your `Podfile` and the manual `add(SajjadBlurOverlayPackage())` call from `MainApplication`.

## Usage

```tsx
import { useRef } from 'react';
import { Button, StyleSheet, Text, View } from 'react-native';
import BlurOverlay, { closeOverlay, openOverlay } from 'react-native-blur-overlay';

export default function App() {
  return (
    <View style={styles.container}>
      <Button title="Blur it" onPress={() => openOverlay()} />

      <BlurOverlay
        blurStyle="dark"
        radius={14}
        downsampling={2}
        brightness={-200}
        onPress={() => closeOverlay()}
        style={styles.center}
      >
        <View style={styles.card}>
          <Text>Anything you render here sits on top of the blur.</Text>
          <Button title="Close" onPress={() => closeOverlay()} />
        </View>
      </BlurOverlay>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  center: { alignItems: 'center', justifyContent: 'center' },
  card: { backgroundColor: 'white', borderRadius: 12, padding: 24 },
});
```

### Three ways to drive it

**1. The `openOverlay` / `closeOverlay` helpers** — handy when the overlay lives far away from the code that opens it:

```tsx
import BlurOverlay, { closeOverlay, openOverlay } from 'react-native-blur-overlay';

<BlurOverlay onPress={() => closeOverlay()}>{/* ... */}</BlurOverlay>;

openOverlay();
```

Pass an `id` when more than one overlay can be mounted at the same time, and use the same id on both sides:

```tsx
<BlurOverlay id="settings" />;

openOverlay('settings');
closeOverlay('settings');
```

**2. A ref**, if you would rather not use module-level functions:

```tsx
const overlay = useRef<BlurOverlayInstance>(null);

<BlurOverlay ref={overlay} onPress={() => overlay.current?.close()}>{/* ... */}</BlurOverlay>;

overlay.current?.open();
```

**3. The `visible` prop**, for fully declarative control (also the way to keep the overlay up permanently):

```tsx
const [visible, setVisible] = useState(false);

<BlurOverlay visible={visible} onPress={() => setVisible(false)} />;

// Always blurred:
<BlurOverlay visible />
```

When `visible` is set, the imperative API is ignored for that overlay.

## Props

| Prop | Type | Default | Platform | Description |
| --- | --- | --- | --- | --- |
| `visible` | `boolean` | — | both | Controls the overlay declaratively. When set, the imperative API is ignored. |
| `id` | `string` | `'default'` | both | Id used by `openOverlay(id)` / `closeOverlay(id)`. |
| `radius` | `number` | `20` | Android | Blur radius. Effective radius is `radius / downsampling`, capped at 25. |
| `downsampling` | `number` | `1` | Android | How much the snapshot is scaled down before blurring. Higher is faster and coarser. |
| `brightness` | `number` | `0` | Android | Brightness offset, `-255`..`255`. Negative darkens. |
| `blurStyle` | `BlurStyle` | `'light'` | iOS | Which `UIBlurEffectStyle` to use, see below. |
| `vibrant` | `boolean` | `false` | iOS | Renders the children inside a `UIVibrancyEffect` view. |
| `fadeDuration` | `number` | `500` | both | Fade in/out duration in ms. `0` disables the animation. |
| `onPress` | `() => void` | — | both | Called when the blurred backdrop is pressed. |
| `closeOnChildPress` | `boolean` | `false` | both | Also report presses that land on the children through `onPress`. |
| `backdropAccessibilityLabel` | `string` | `'Close overlay'` | both | Accessibility label of the pressable backdrop. |
| `onShow` | `() => void` | — | both | Called once the overlay is fully faded in. |
| `onHide` | `() => void` | — | both | Called once the overlay is fully faded out. |
| `style` | `StyleProp<ViewStyle>` | fills the parent | both | Style of the blurred surface. |
| `children` | `ReactNode` | — | both | Content rendered on top of the blur. |

`BlurStyle` is one of `'light'`, `'extraLight'`, `'dark'`, `'regular'`, `'prominent'`, `'systemUltraThinMaterial'`, `'systemThinMaterial'`, `'systemMaterial'`, `'systemThickMaterial'`, `'systemChromeMaterial'`. The `system*` styles follow the device's light/dark appearance.

Deprecated aliases, still honoured: `idBlur` (→ `id`), `customStyles` (→ `style`), `animationDuration` (→ `fadeDuration`).

## API

```ts
import BlurOverlay, {
  openOverlay,
  closeOverlay,
  DEFAULT_ID,
  type BlurOverlayProps,
  type BlurOverlayInstance,
  type BlurStyle,
} from 'react-native-blur-overlay';
```

| Export | Description |
| --- | --- |
| `openOverlay(id?)` | Fades in the overlay mounted with that id. |
| `closeOverlay(id?)` | Fades it out. |
| `DEFAULT_ID` | The id used when none is given. |
| `ref.open()` / `ref.close()` | The same, per instance. |

## How it works, per platform

**iOS** uses a `UIVisualEffectView`, so the blur is live: whatever moves behind the overlay stays blurred while it moves.

**Android** has no equivalent system view, so the library takes a snapshot of the screen behind the overlay when the overlay appears, blurs it off the main thread and draws it as the overlay's background. Two things follow from that:

- The blur is a **still image** — content that animates behind the overlay is not re-blurred. Close and re-open (or remount) the overlay to refresh it.
- The snapshot is cropped to the overlay's position on screen, so an overlay that covers part of the screen blurs exactly that part.
- It is drawn as the view's background, which means `borderRadius`, `borderWidth` and `backgroundColor` on the overlay itself have no effect on Android — wrap the overlay in a rounded, `overflow: 'hidden'` parent instead.

Blurring used to be done with RenderScript, which Android deprecated in Android 12 and no longer ships to new builds. 3.0 replaces it with a Stack Blur implementation that works on every supported API level.

## Recipes

### Frosted-glass panels (iOS materials, on Android too)

A blur overlay does not have to cover the screen. Size it to a box and you get
a frosted-glass surface — a sheet, a card, a tab bar — on **both** platforms:

```tsx
// The parent's rounded corners are what shape the glass, on both platforms.
const styles = StyleSheet.create({
  clip: {
    position: 'absolute',
    left: 20,
    right: 20,
    bottom: 40,
    height: 190,
    borderRadius: 28,
    overflow: 'hidden',
  },
  panel: {
    width: '100%',
    height: '100%',
    borderRadius: 28,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.35)',
    justifyContent: 'center',
    padding: 22,
    gap: 8,
  },
});

<View pointerEvents="box-none" style={styles.clip}>
  <BlurOverlay
    visible={showPanel}
    blurStyle="systemThinMaterial" // iOS
    radius={20} // Android
    downsampling={2}
    brightness={-16}
  >
    <View style={styles.panel}>
      <Text style={styles.title}>Frosted glass</Text>
      <Text style={styles.body}>Same code on iOS and Android.</Text>
    </View>
  </BlurOverlay>
</View>
```

Two things to know:

- **Put the rounding on the parent.** On Android the blurred snapshot is drawn
  as the overlay's background, so `borderRadius`, `borderWidth` and
  `backgroundColor` set on the overlay itself are not applied there — a
  rounded, `overflow: 'hidden'` parent clips it on both platforms instead.
- **On Android the glass is a still image** of what was behind the panel when
  it appeared (see [How it works](#how-it-works-per-platform)), so it suits
  panels that appear over settled content — sheets, dialogs, menus. Content
  scrolling behind an already-visible panel will not re-blur. On iOS the same
  panel is live.

### Blur only part of the screen

Works on both platforms — iOS blurs whatever is behind the overlay's own frame,
and Android crops its snapshot to the overlay's position on screen. Give the
overlay an explicit position and size, or mount it inside the view you want
blurred:

```tsx
// Inside the view you want blurred — the overlay fills its parent:
<View style={{ height: 240 }}>
  <BlurOverlay visible />
</View>

// Or with an explicit box (the defaults pin all four edges, so release the
// two you replace):
<BlurOverlay
  visible
  style={{ top: 80, left: 24, width: 300, height: 200, right: 'auto', bottom: 'auto' }}
/>
```

### Keep presses inside your content from closing the overlay

This is the default: presses that land on the children are left to the children, and only presses on the backdrop call `onPress`. Note that a plain `View` does not capture touches in React Native — the library wraps your children in a touch-claiming view sized to them. Pass `closeOnChildPress` if you want every press to call `onPress`.

### Make it faster

Blurring a full-screen snapshot costs the most on Android. Raise `downsampling` (2–4 is a good range) — the snapshot is scaled down before blurring, and the effective radius is adjusted so the result looks the same.

## Troubleshooting

**`openOverlay()` does nothing.** The id has to match on both sides: an overlay rendered with `id="x"` only listens to `openOverlay('x')`. In development the library warns when a call finds no matching overlay. Overlays driven by the `visible` prop deliberately ignore these calls.

**The Android blur shows a stale screen.** 3.0 takes the snapshot when the overlay is mounted, so opening it right after a navigation transition can still catch the tail of the animation. Delay the `openOverlay()` call until the transition has settled.

**The blur is transparent/black on Android.** `view.draw()` cannot capture hardware surfaces — video players, camera previews, maps and other `SurfaceView`-based content come out blank. That is a platform limitation of snapshot-based blurring.

## Migrating from 2.x

- **Autolinking**: remove the manual `Podfile` entry and the manual `SajjadBlurOverlayPackage` registration.
- **React Native >= 0.80** is required, and the package now works on the New Architecture.
- **`idBlur` → `id`**, **`customStyles` → `style`**, **`animationDuration` → `fadeDuration`**. The old names still work.
- **`onPress` no longer fires for presses on your children.** Pass `closeOnChildPress` to get the old behaviour.
- **Android props behave the same**, but `radius` is now capped at 25 after downsampling.
- 2.x published `index.tsx` as the package entry point and imported Node's `events` module, which Metro cannot resolve; 3.0 ships a compiled build with type definitions.

## Example app

The repo ships a small app that exercises every prop — imperative and
declarative opening, the iOS blur styles, the Android radius/downsampling, a
partial-screen overlay and press handling:

```bash
npm install
npm run example:start     # in one terminal
npm run example:android   # or: npm run example:ios
```

It is an npm workspace of the library and Metro resolves the library to its
TypeScript sources, so editing `src/` refreshes the app without a rebuild.

## Roadmap

Android's blur is a snapshot; iOS's is live. Making Android live is planned for 3.1.0 —
the constraints, the research behind it and the phased plan are in
[docs/live-blur/PLAN.md](docs/live-blur/PLAN.md).

## Contributing

```bash
npm install            # installs deps and builds the library
npm run typecheck
npm test
npm run check:android  # compiles the Java against React Native's classes
npm run check:ios      # type-checks the Obj-C(++) on both architectures (needs Xcode)
```

The two native checks need neither an example app nor a CocoaPods/Gradle
install: they fetch React Native's own headers and classes for the version in
`node_modules` and run `clang`/`javac` against the Codegen output. CI runs all
of them.

The Codegen spec lives in [`src/SajjadBlurOverlayNativeComponent.ts`](src/SajjadBlurOverlayNativeComponent.ts); the native implementations are in [`android/src/main/java/com/bluroverly`](android/src/main/java/com/bluroverly) and [`ios`](ios).

Releases are published from CI with npm [Trusted Publishing](.github/workflows/release.yml): bump the version, tag it `vX.Y.Z` and push the tag.

## License

MIT © [Sajjad Asadi](https://github.com/lvlrSajjad)
