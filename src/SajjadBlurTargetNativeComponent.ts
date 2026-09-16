import { codegenNativeComponent } from 'react-native';
import type { CodegenTypes, HostComponent, ViewProps } from 'react-native';

export interface NativeProps extends ViewProps {
  /**
   * Id a live `<BlurOverlay />` captures this subtree by. Android only.
   */
  blurTargetId?: CodegenTypes.WithDefault<string, 'default'>;
}

// The explicit annotation keeps `tsc` from inlining a path into React
// Native's internals when it emits the declaration file.
export default codegenNativeComponent<NativeProps>(
  'SajjadBlurTarget'
) as HostComponent<NativeProps>;
