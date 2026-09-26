import { codegenNativeComponent } from 'react-native';
import type {
  CodegenTypes,
  ColorValue,
  HostComponent,
  ViewProps,
} from 'react-native';

export interface NativeProps extends ViewProps {
  /**
   * Blur radius, in (pre-downsampling) pixels. Android only.
   */
  radius?: CodegenTypes.WithDefault<CodegenTypes.Int32, 20>;
  /**
   * How much the snapshot is scaled down before it is blurred. Higher is
   * cheaper and blurrier. Android only.
   */
  downsampling?: CodegenTypes.WithDefault<CodegenTypes.Float, 1>;
  /**
   * -255..255, 0 leaves the snapshot untouched. Android only.
   */
  brightness?: CodegenTypes.WithDefault<CodegenTypes.Float, 0>;
  /**
   * Colour saturation of the blur, 1 leaves it untouched. Android only.
   */
  saturation?: CodegenTypes.WithDefault<CodegenTypes.Float, 1>;
  /**
   * `"snapshot"` blurs once, when the overlay appears. `"live"` re-blurs a
   * `<BlurTarget>` every frame, and needs API 31+ — below that, and when no
   * target is found, it falls back to `"snapshot"`. In a Dialog window (an RN
   * `<Modal>`) `"live"` blurs behind the window instead, since no capture can
   * cross a window boundary. Android only.
   */
  blurMode?: CodegenTypes.WithDefault<string, 'snapshot'>;
  /**
   * Which `<BlurTarget>` a live overlay captures. Android only.
   */
  blurTargetId?: CodegenTypes.WithDefault<string, 'default'>;
  /**
   * Upper bound on live re-blurs per second. `0` re-blurs on every frame the
   * screen draws. Android only.
   */
  maxUpdateFps?: CodegenTypes.WithDefault<CodegenTypes.Int32, 30>;
  /**
   * How far past the overlay's own bounds the capture reaches, in
   * (pre-downsampling) pixels. Negative values inset it. Android only.
   */
  captureOutset?: CodegenTypes.WithDefault<CodegenTypes.Float, 0>;
  /**
   * How often a `"snapshot"` blur is retaken, per second. `0` takes it once and
   * leaves it frozen, which is what every 3.0 app gets. Android only.
   */
  snapshotUpdateFps?: CodegenTypes.WithDefault<CodegenTypes.Int32, 0>;
  /**
   * `"regular"` or `"clear"` glass, when `blurMode` is `"glass"`. iOS's
   * `UIGlassEffectStyle`; on Android, `"regular"` is lifted toward white and
   * `"clear"` blurs less and is not.
   */
  glassVariant?: CodegenTypes.WithDefault<string, 'regular'>;
  /**
   * Colour of the glass body, when `blurMode` is `"glass"`. Its alpha is the
   * strength. iOS's `UIGlassEffect.tintColor`.
   */
  glassTint?: ColorValue;
  /**
   * Glass that reacts to touch, when `blurMode` is `"glass"`. iOS's
   * `UIGlassEffect.interactive`.
   */
  interactive?: CodegenTypes.WithDefault<boolean, false>;
  /**
   * `UIBlurEffectStyle` to use. iOS only.
   */
  blurStyle?: CodegenTypes.WithDefault<string, 'light'>;
  /**
   * Renders children inside a `UIVibrancyEffect` view. iOS only.
   */
  vibrant?: CodegenTypes.WithDefault<boolean, false>;
}

// The explicit annotation keeps `tsc` from inlining a path into React
// Native's internals when it emits the declaration file.
export default codegenNativeComponent<NativeProps>(
  'SajjadBlurOverlay'
) as HostComponent<NativeProps>;
