import { codegenNativeComponent } from 'react-native';
import type { CodegenTypes, HostComponent, ViewProps } from 'react-native';

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
