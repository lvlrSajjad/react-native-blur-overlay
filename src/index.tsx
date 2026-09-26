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
  PixelRatio,
  Platform,
  Pressable,
  StyleSheet,
  View,
  type ColorValue,
  type StyleProp,
  type ViewProps,
  type ViewStyle,
} from 'react-native';

import NativeBlurOverlay from './SajjadBlurOverlayNativeComponent';
import NativeBlurTarget from './SajjadBlurTargetNativeComponent';
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

/**
 * How the Android blur is produced. iOS is always live, whatever this says.
 *
 * - `snapshot` blurs the screen once, when the overlay appears, and leaves it
 *   frozen. Costs nothing per frame. The default, and all 3.0 could do.
 *   `snapshotUpdateFps` can have it retaken on a timer instead, which is the
 *   only way to get a moving backdrop below Android 12.
 * - `live` re-blurs a `<BlurTarget>` as it draws, so content moving behind the
 *   overlay stays blurred. Needs Android 12 (API 31) and a `<BlurTarget>`;
 *   without either it falls back to `snapshot`.
 *
 * Inside a `<Modal>`, `live` means something different, because a modal is its
 * own Android window and no capture can reach across one: the overlay asks the
 * system to blur behind the whole modal window. That needs no `<BlurTarget>`
 * and costs nothing per frame, but it is the system's to give — where
 * cross-window blur is off (some GPUs never have it, battery saver turns it off
 * everywhere) it falls back to `snapshot`, which still shows the app behind the
 * modal, frozen.
 */
export type BlurMode = 'snapshot' | 'live' | 'glass';

/**
 * Liquid Glass comes in two variants on iOS 26, and this library keeps the
 * same two on Android: `regular` is frosted (what system bars use), `clear` is
 * highly translucent, for glass over photos and video.
 */
export type GlassVariant = 'regular' | 'clear';

// The blur each glass variant gets on Android when neither `blurRadius` nor
// `radius` is set, in dp, matched by eye against iOS 26's two styles.
const GLASS_BLUR_DP: Record<GlassVariant, number> = { regular: 10, clear: 5 };

export interface BlurTargetProps extends ViewProps {
  /**
   * Id a live `<BlurOverlay />` finds this subtree by. Only needed when more
   * than one target is mounted at a time.
   *
   * @default DEFAULT_ID
   */
  id?: string;
  children?: ReactNode;
}

/**
 * The subtree a live overlay blurs.
 *
 * Wrap the content that should show through the glass — a list, a screen, the
 * whole app — and keep the overlay *outside* it:
 *
 * ```tsx
 * <BlurTarget style={{ flex: 1 }}>
 *   <ScrollView>{...}</ScrollView>
 * </BlurTarget>
 * <BlurOverlay blurMode="live" visible />
 * ```
 *
 * An overlay cannot blur a subtree it is part of: on a hardware canvas that
 * either blanks the overlay or drops the backdrop, with nothing in the logs.
 * The overlay checks for it and falls back to `snapshot` rather than showing
 * you the failure.
 *
 * Otherwise this is a plain `<View />` — it lays out, draws and takes touches
 * like one, and on iOS it *is* one, since the iOS overlay is already live.
 */
export function BlurTarget({ id = DEFAULT_ID, ...rest }: BlurTargetProps) {
  if (Platform.OS !== 'android') {
    return <View {...rest} />;
  }

  return <NativeBlurTarget blurTargetId={id} {...rest} />;
}

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
   * Blur radius, in **physical pixels**. Android only (on iOS the radius is
   * fixed by `blurStyle`).
   *
   * Pixels, not dp, so the same number is a different amount of blur on every
   * screen — wrap it in `PixelRatio.get() * n` for a density-independent one.
   * The default is low on a modern phone and stays where it is only because
   * changing it would restyle every app already using the library.
   *
   * A `snapshot` saturates at `25 * downsampling`, because Stack Blur clamps
   * there; a `live` blur has no ceiling. Above that the two modes stop
   * matching.
   *
   * @default 20
   * @deprecated Use `blurRadius`, which is in dp. Still honoured when
   * `blurRadius` is not set.
   */
  radius?: number;
  /**
   * Blur radius in **dp**, so it blurs the same amount on every screen.
   * Android only: iOS offers no blur amount on its materials or its glass.
   * Takes precedence over `radius`.
   *
   * With `blurMode="glass"` and neither set, the blur follows `glassVariant`.
   */
  blurRadius?: number;
  /**
   * Which Liquid Glass, when `blurMode` is `"glass"`: `regular` (frosted, the
   * default) or `clear` (highly translucent, for glass over rich content like
   * photos). iOS 26's `UIGlassEffectStyle`; on Android, `regular` is milky and
   * `clear` blurs less and is not lifted toward white, as on iOS.
   *
   * @default 'regular'
   */
  glassVariant?: GlassVariant;
  /**
   * Colour of the glass body, when `blurMode` is `"glass"`. Its alpha sets how
   * strongly it tints: `'rgba(0,122,255,0.3)'` is a light blue glass. iOS
   * 26's `UIGlassEffect.tintColor`, mixed the same way on Android.
   */
  glassTint?: ColorValue;
  /**
   * Glass that responds to touch, when `blurMode` is `"glass"`: on iOS 26 the
   * system's own interactive glass; on Android the glass swells and its rim
   * brightens under the finger.
   *
   * @default false
   */
  interactive?: boolean;
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
   * Colour saturation of the blur: 1 leaves it as it is, 0 is greyscale, and
   * above 1 is more vivid. A blur is an average, so it washes colour out;
   * iOS's materials put it back with roughly 1.8, which is why an Android
   * overlay at 1 reads flatter beside an iOS one. Applies to the snapshot and
   * the live blur; a window blur inside a `<Modal>` is the system's and cannot
   * take it. Android only.
   *
   * @default 1
   */
  saturation?: number;
  /**
   * Whether the Android blur is taken once or kept up to date. On iOS the
   * overlay is a `UIVisualEffectView` and always live, so only `glass` means
   * anything there: it uses the system's own Liquid Glass (`UIGlassEffect`) on
   * iOS 26 and later, and the `blurStyle` blur below that. `vibrant` does not
   * apply to glass.
   *
   * `live` needs Android 12 (API 31) and a `<BlurTarget>` around the content
   * to blur; without either it falls back to `snapshot`.
   *
   * Inside a `<Modal>` it needs neither: a modal is its own window, so `live`
   * there asks the system to blur behind that window. `radius` still applies;
   * `downsampling`, `blurTargetId`, `maxUpdateFps` and `captureOutset` do not,
   * because nothing is being captured. The overlay has to cover the modal —
   * a window blur has no way to be scoped to part of one.
   *
   * `glass` is `live` plus a lens along the edge: a band just inside the
   * border mirrors the blurred content next to it, so the backdrop bends as it
   * reaches the rim, lit by a hairline whose brightness varies around the
   * shape — the Liquid Glass look. It needs Android 13 (API 33) and falls back
   * to `live` below that. It follows the overlay's `borderRadius` (uniform
   * radii only, so a capsule works) and never captures above half resolution,
   * whatever `downsampling` says: at full resolution it measured 100% jank at
   * 90Hz on a Galaxy A22. Inside a `<Modal>` it behaves as `live`. Pair it
   * with `saturation` around 1.8 for an iOS-like material.
   *
   * @default 'snapshot'
   */
  blurMode?: BlurMode;
  /**
   * Which `<BlurTarget>` a live overlay captures. Only needed when more than
   * one target is mounted at a time. Android only.
   *
   * @default DEFAULT_ID
   */
  blurTargetId?: string;
  /**
   * Upper bound on how often a live blur is refreshed, per second. `0` refreshes
   * on every frame the screen draws.
   *
   * A blur one frame behind the content is much harder to notice than a scroll
   * that stutters, so this trades the former for the latter. Android only.
   *
   * @default 30
   */
  maxUpdateFps?: number;
  /**
   * How far past the overlay's own edges a live capture reaches, in the same
   * (pre-downsampling) pixels as `radius`. Negative values pull it in.
   *
   * A blur clamps at the edge of what it can see, which darkens or smears the
   * first few pixels inside the border. Giving the capture roughly a radius of
   * bleed replaces that with real content. Costs the blur a slightly larger
   * surface and nothing else. Android only.
   *
   * @default 0
   */
  captureOutset?: number;
  /**
   * How often the `snapshot` blur is retaken, per second. `0` takes it once,
   * when the overlay appears, and leaves it frozen.
   *
   * This is the coarse fallback for Android 11 and older, where `blurMode`
   * `live` is unavailable.
   *
   * **Use 30 if you use it at all.** Lower rates do not read as a slower blur,
   * they read as a slideshow — frozen, then a jump. Measured at about 2ms of
   * P90 over baseline at `downsampling` 4 on a Galaxy A22, so the rate is not
   * the thing to economise on; `downsampling` is.
   *
   * Ignored while a live blur or a window blur owns the backdrop, since neither
   * is a snapshot. Android only.
   *
   * @default 0
   */
  snapshotUpdateFps?: number;
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
      radius,
      blurRadius,
      glassVariant = 'regular',
      glassTint,
      interactive = false,
      downsampling,
      brightness = 0,
      saturation = 1,
      blurMode = 'snapshot',
      blurTargetId = DEFAULT_ID,
      maxUpdateFps = 30,
      captureOutset = 0,
      snapshotUpdateFps = 0,
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
    // A live blur re-runs every frame, so it starts from the downscale Phase 0
    // measured as free on low-end hardware; a snapshot runs once and can afford
    // full resolution.
    const scale = downsampling ?? (blurMode === 'snapshot' ? 1 : 2);
    // Native takes physical pixels. `blurRadius` is dp and wins; then the
    // deprecated `radius`; then, for glass, the variant's own blur.
    const pixels =
      blurRadius !== undefined
        ? Math.round(blurRadius * PixelRatio.get())
        : (radius ??
          (blurMode === 'glass'
            ? Math.round(GLASS_BLUR_DP[glassVariant] * PixelRatio.get())
            : 20));
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

    useEffect(() => {
      if (__DEV__ && snapshotUpdateFps > 0 && snapshotUpdateFps < 20) {
        console.warn(
          `[react-native-blur-overlay] snapshotUpdateFps={${snapshotUpdateFps}} ` +
            'will look like a slideshow rather than a blur: the backdrop stays ' +
            `frozen for ${Math.round(1000 / snapshotUpdateFps)}ms at a time and ` +
            'then jumps. 30 is the lowest rate that reads as following the ' +
            'content, and it measured no more expensive than a still screen on ' +
            'low-end hardware — raise `downsampling` rather than lowering this.'
        );
      }
    }, [snapshotUpdateFps]);

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
          radius={pixels}
          downsampling={scale}
          brightness={brightness}
          saturation={saturation}
          blurMode={blurMode}
          blurTargetId={blurTargetId}
          maxUpdateFps={maxUpdateFps}
          captureOutset={captureOutset}
          snapshotUpdateFps={snapshotUpdateFps}
          blurStyle={blurStyle}
          vibrant={vibrant}
          glassVariant={glassVariant}
          glassTint={glassTint}
          interactive={interactive}
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
