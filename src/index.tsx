import {
  forwardRef,
  useCallback,
  useEffect,
  useImperativeHandle,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import {
  Animated,
  Pressable,
  StyleSheet,
  View,
  type StyleProp,
  type ViewStyle,
} from 'react-native';

import NativeBlurOverlay from './SajjadBlurOverlayNativeComponent';
import { emit, off, on } from './emitter';

/**
 * Id used by `<BlurOverlay />` and by `openOverlay()` / `closeOverlay()` when
 * no id is given, so that the id can be left out entirely on both sides.
 */
export const DEFAULT_ID = 'default';

/**
 * `UIBlurEffectStyle` values. The `system*` styles adapt to the device's
 * light/dark appearance on their own. iOS only.
 */
export type BlurStyle =
  | 'light'
  | 'extraLight'
  | 'dark'
  | 'regular'
  | 'prominent'
  | 'systemUltraThinMaterial'
  | 'systemThinMaterial'
  | 'systemMaterial'
  | 'systemThickMaterial'
  | 'systemChromeMaterial';

export interface BlurOverlayProps {
  /**
   * Controls the overlay declaratively. When set, `openOverlay()`,
   * `closeOverlay()` and the imperative ref are ignored, and the overlay
   * follows this prop — pass `true` to keep the overlay up permanently.
   */
  visible?: boolean;
  /**
   * Id this overlay listens on, for `openOverlay(id)` / `closeOverlay(id)`.
   * Only needed when more than one overlay is mounted at a time.
   *
   * @default DEFAULT_ID
   */
  id?: string;
  /**
   * @deprecated Renamed to `id`, still honoured.
   */
  idBlur?: string;
  /**
   * Blur radius. Android only (on iOS the radius is fixed by `blurStyle`).
   *
   * @default 20
   */
  radius?: number;
  /**
   * How much the snapshot is scaled down before being blurred: higher values
   * blur faster and coarser. Android only.
   *
   * @default 1
   */
  downsampling?: number;
  /**
   * Brightness offset applied to the blurred snapshot, -255..255. Negative
   * values darken the overlay. Android only.
   *
   * @default 0
   */
  brightness?: number;
  /**
   * Which `UIBlurEffectStyle` to use. iOS only.
   *
   * @default 'light'
   */
  blurStyle?: BlurStyle;
  /**
   * Renders the children inside a `UIVibrancyEffect` view, which makes them
   * blend with the blurred content behind. iOS only.
   *
   * @default false
   */
  vibrant?: boolean;
  /**
   * Fade in/out duration in ms. `0` disables the animation.
   *
   * @default 500
   */
  fadeDuration?: number;
  /**
   * @deprecated Alias of `fadeDuration`, still honoured.
   */
  animationDuration?: number;
  /**
   * Called when the blurred backdrop is pressed — presses on the children are
   * not reported unless `closeOnChildPress` is set.
   */
  onPress?: () => void;
  /**
   * Accessibility label for the pressable backdrop.
   *
   * @default 'Close overlay'
   */
  backdropAccessibilityLabel?: string;
  /**
   * By default a press that lands on the children is left to the children.
   * Set this to also report those presses through `onPress`.
   *
   * @default false
   */
  closeOnChildPress?: boolean;
  /** Called once the overlay is fully faded in. */
  onShow?: () => void;
  /** Called once the overlay is fully faded out. */
  onHide?: () => void;
  /**
   * Style for the blurred surface. Defaults to filling the parent; give it
   * `alignItems` / `justifyContent` to place the children, or an explicit
   * position and size to blur just part of the screen.
   */
  style?: StyleProp<ViewStyle>;
  /**
   * @deprecated Renamed to `style`, still honoured.
   */
  customStyles?: StyleProp<ViewStyle>;
  children?: ReactNode;
}

export interface BlurOverlayInstance {
  /** Fades the overlay in. */
  open: () => void;
  /** Fades the overlay out. */
  close: () => void;
}

const BlurOverlay = forwardRef<BlurOverlayInstance, BlurOverlayProps>(
  function BlurOverlay(props, ref) {
    const {
      visible,
      id,
      idBlur,
      radius = 20,
      downsampling = 1,
      brightness = 0,
      blurStyle = 'light',
      vibrant = false,
      fadeDuration,
      animationDuration,
      onPress,
      backdropAccessibilityLabel = 'Close overlay',
      closeOnChildPress = false,
      onShow,
      onHide,
      style,
      customStyles,
      children,
    } = props;

    const overlayId = id ?? idBlur ?? DEFAULT_ID;
    const duration = fadeDuration ?? animationDuration ?? 500;
    const isControlled = visible !== undefined;

    const [isMounted, setMounted] = useState(visible === true);
    const opacity = useRef(new Animated.Value(visible === true ? 1 : 0)).current;
    const animation = useRef<Animated.CompositeAnimation | null>(null);

    // Read through refs so that changing these props never re-subscribes the
    // listeners below (and never restarts a running animation).
    const latest = useRef({ duration, onShow, onHide });
    latest.current = { duration, onShow, onHide };

    const open = useCallback(() => {
      animation.current?.stop();
      setMounted(true);

      if (latest.current.duration <= 0) {
        opacity.setValue(1);
        latest.current.onShow?.();
        return;
      }

      const next = Animated.timing(opacity, {
        toValue: 1,
        duration: latest.current.duration,
        useNativeDriver: true,
      });

      animation.current = next;
      next.start(({ finished }) => {
        if (finished) {
          latest.current.onShow?.();
        }
      });
    }, [opacity]);

    const close = useCallback(() => {
      animation.current?.stop();

      if (latest.current.duration <= 0) {
        opacity.setValue(0);
        setMounted(false);
        latest.current.onHide?.();
        return;
      }

      const next = Animated.timing(opacity, {
        toValue: 0,
        duration: latest.current.duration,
        useNativeDriver: true,
      });

      animation.current = next;
      next.start(({ finished }) => {
        if (finished) {
          setMounted(false);
          latest.current.onHide?.();
        }
      });
    }, [opacity]);

    // Imperative API: openOverlay(id) / closeOverlay(id).
    useEffect(() => {
      if (isControlled) {
        return;
      }

      const openEvent = `open:${overlayId}`;
      const closeEvent = `close:${overlayId}`;

      on(openEvent, open);
      on(closeEvent, close);

      return () => {
        off(openEvent, open);
        off(closeEvent, close);
      };
    }, [close, isControlled, open, overlayId]);

    // Declarative API: the `visible` prop.
    useEffect(() => {
      if (!isControlled) {
        return;
      }

      if (visible) {
        open();
      } else {
        close();
      }
    }, [close, isControlled, open, visible]);

    useEffect(() => {
      return () => {
        animation.current?.stop();
      };
    }, []);

    const openFromRef = useCallback(() => {
      if (isControlled) {
        warnIgnored('open');
        return;
      }

      open();
    }, [isControlled, open]);

    const closeFromRef = useCallback(() => {
      if (isControlled) {
        warnIgnored('close');
        return;
      }

      close();
    }, [close, isControlled]);

    useImperativeHandle(
      ref,
      () => ({ open: openFromRef, close: closeFromRef }),
      [closeFromRef, openFromRef]
    );

    if (!isMounted) {
      return null;
    }

    return (
      <Animated.View
        pointerEvents="box-none"
        style={[styles.fill, styles.stack, { opacity }]}
      >
        <NativeBlurOverlay
          radius={radius}
          downsampling={downsampling}
          brightness={brightness}
          blurStyle={blurStyle}
          vibrant={vibrant}
          style={[styles.fill, customStyles, style]}
        >
          {onPress ? (
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={backdropAccessibilityLabel}
              onPress={onPress}
              style={styles.fill}
            />
          ) : null}
          <View
            // Sized to the children, so that a press outside of them still
            // reaches the backdrop above. Claiming the responder keeps
            // presses on the children from closing the overlay.
            onStartShouldSetResponder={closeOnChildPress ? undefined : yes}
            style={styles.content}
          >
            {children}
          </View>
        </NativeBlurOverlay>
      </Animated.View>
    );
  }
);

const yes = () => true;

function warnIgnored(method: 'open' | 'close') {
  if (__DEV__) {
    console.warn(
      `[react-native-blur-overlay] ${method}() was ignored: this overlay is ` +
        'driven by its `visible` prop. Change that prop instead.'
    );
  }
}

const styles = StyleSheet.create({
  fill: {
    position: 'absolute',
    left: 0,
    top: 0,
    right: 0,
    bottom: 0,
  },
  stack: {
    zIndex: 999,
  },
  content: {
    maxWidth: '100%',
    maxHeight: '100%',
  },
});

/**
 * Fades in the `<BlurOverlay />` mounted with this id.
 *
 * @param id Defaults to the id of an overlay rendered without one.
 */
export function openOverlay(id: string = DEFAULT_ID): void {
  if (!emit(`open:${id}`) && __DEV__) {
    console.warn(
      `[react-native-blur-overlay] openOverlay(${JSON.stringify(id)}) did ` +
        'nothing: no mounted <BlurOverlay /> uses that id. Pass the same ' +
        '`id` to the component, or leave it out on both sides. Overlays ' +
        'driven by the `visible` prop do not listen for this call.'
    );
  }
}

/**
 * Fades out the `<BlurOverlay />` mounted with this id.
 *
 * @param id Defaults to the id of an overlay rendered without one.
 */
export function closeOverlay(id: string = DEFAULT_ID): void {
  if (!emit(`close:${id}`) && __DEV__) {
    console.warn(
      `[react-native-blur-overlay] closeOverlay(${JSON.stringify(id)}) did ` +
        'nothing: no mounted <BlurOverlay /> uses that id.'
    );
  }
}

export default BlurOverlay;
