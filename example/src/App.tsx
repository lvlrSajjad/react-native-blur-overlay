import { useEffect, useRef, useState, type ReactNode } from 'react';
import {
  Animated,
  Easing,
  FlatList,
  Modal,
  Platform,
  Pressable,
  StatusBar,
  StyleSheet,
  Switch,
  Text,
  View,
} from 'react-native';
import BlurOverlay, {
  BlurTarget,
  closeOverlay,
  openOverlay,
  type BlurMode,
  type BlurOverlayInstance,
  type BlurStyle,
} from 'react-native-blur-overlay';

const BLUR_STYLES: BlurStyle[] = [
  'light',
  'extraLight',
  'dark',
  'systemThinMaterial',
  'systemChromeMaterial',
];

const PALETTE = [
  '#ef476f',
  '#ffd166',
  '#06d6a0',
  '#118ab2',
  '#8338ec',
  '#fb5607',
  '#3a86ff',
  '#ff006e',
  '#02c39a',
];

// Long enough to keep a scroll going, which is the point: a live blur is only
// interesting while something is moving behind it.
const TILES = Array.from({ length: 400 }, (_, index) => `tile-${index}`);

const UPDATE_RATES = [30, 60, 0];

/**
 * Initial props, which on Android come from the launch intent's extras — see
 * `MainActivity`. They only pick the demo's starting state; every one of them is
 * still a button below.
 */
interface LaunchProps {
  blurMode?: BlurMode;
  maxUpdateFps?: number;
  downsampling?: number;
  panel?: boolean;
  /** Opens the `<Modal>` demo on launch. */
  modal?: boolean;
  /**
   * Shrink the modal's overlay so it covers only part of the modal window. A
   * window blur cannot be scoped to part of a window, so a live overlay should
   * decline it and fall back to a snapshot of its own bounds.
   */
  modalPartial?: boolean;
  /**
   * Point the panel at a `<BlurTarget>` that does not exist, to see what a live
   * overlay does when it cannot find one. It should fall back to a snapshot.
   */
  blurTargetId?: string;
}

export default function App({
  blurMode: initialBlurMode,
  maxUpdateFps: initialMaxUpdateFps,
  downsampling: initialDownsampling,
  panel,
  modal,
  modalPartial,
  blurTargetId,
}: LaunchProps) {
  const [blurStyle, setBlurStyle] = useState<BlurStyle>('dark');
  const [radius, setRadius] = useState(14);
  const [downsampling, setDownsampling] = useState(initialDownsampling ?? 2);
  const [alwaysOn, setAlwaysOn] = useState(false);
  const [glass, setGlass] = useState(panel ?? false);
  const [modalOpen, setModalOpen] = useState(modal ?? false);
  const [blurMode, setBlurMode] = useState<BlurMode>(
    initialBlurMode ?? 'snapshot'
  );
  const [maxUpdateFps, setMaxUpdateFps] = useState(initialMaxUpdateFps ?? 30);

  const menu = useRef<BlurOverlayInstance>(null);

  return (
    <View style={styles.screen}>
      <StatusBar barStyle="light-content" />

      {/* Something worth blurring — and, for `blurMode="live"`, the subtree the
          overlay captures. Note that every overlay below is a *sibling* of this,
          never a child: an overlay cannot blur a subtree it is part of. */}
      <BlurTarget style={styles.target}>
        {/* Only while the modal demo is up: something behind the modal that
            keeps moving on its own. A modal's window covers the screen, so
            there is no way to scroll the list under it — without this there is
            nothing to tell a live window blur from a frozen snapshot. Gated so
            that it never runs during a frame measurement of the list. */}
        {modalOpen ? <MovingBand /> : null}

        <FlatList
          data={TILES}
          numColumns={4}
          keyExtractor={(tile) => tile}
          contentContainerStyle={styles.content}
          columnWrapperStyle={styles.gridRow}
          renderItem={({ index }) => (
            <View
              style={[
                styles.tile,
                { backgroundColor: PALETTE[index % PALETTE.length] },
              ]}
            >
              <Text style={styles.tileLabel}>{index}</Text>
            </View>
          )}
          ListHeaderComponent={
            <View style={styles.header}>
              <Text style={styles.title}>react-native-blur-overlay</Text>
              <Text style={styles.subtitle}>
                The overlay blurs whatever is rendered behind it. Scroll, then
                open one of the overlays below.
              </Text>
            </View>
          }
          ListFooterComponent={
            <View style={styles.footer}>
        <Section title="Open it">
          <Button label="openOverlay()" onPress={() => openOverlay()} />
          <Button label="ref.open()" onPress={() => menu.current?.open()} />
          <Button
            label="Blur one corner"
            onPress={() => openOverlay('corner')}
          />
          <Button
            label={glass ? 'Hide glass panel' : 'Glass panel'}
            selected={glass}
            onPress={() => setGlass((value) => !value)}
          />
          <Button label="Modal" onPress={() => setModalOpen(true)} />
        </Section>

        <Section title="Keep it up (visible prop)">
          <View style={styles.row}>
            <Switch value={alwaysOn} onValueChange={setAlwaysOn} />
            <Text style={styles.rowLabel}>
              {alwaysOn ? 'Always blurred' : 'Off'}
            </Text>
          </View>
        </Section>

        {Platform.OS === 'ios' ? (
          <Section title="blurStyle (iOS)">
            {BLUR_STYLES.map((style) => (
              <Button
                key={style}
                label={style}
                selected={style === blurStyle}
                onPress={() => setBlurStyle(style)}
              />
            ))}
          </Section>
        ) : (
          <Section title="Blur settings (Android)">
            <Button
              label={`radius ${radius}`}
              onPress={() => setRadius((value) => (value >= 25 ? 5 : value + 5))}
            />
            <Button
              label={`downsampling ${downsampling}`}
              onPress={() =>
                setDownsampling((value) => (value >= 4 ? 1 : value + 1))
              }
            />
            <Button
              label={`blurMode ${blurMode}`}
              selected={blurMode === 'live'}
              onPress={() =>
                setBlurMode((value) => (value === 'live' ? 'snapshot' : 'live'))
              }
            />
            <Button
              label={
                maxUpdateFps === 0
                  ? 'maxUpdateFps every frame'
                  : `maxUpdateFps ${maxUpdateFps}`
              }
              onPress={() =>
                setMaxUpdateFps(
                  (value: number) =>
                    UPDATE_RATES[
                      (UPDATE_RATES.indexOf(value) + 1) % UPDATE_RATES.length
                    ] ?? 30
                )
              }
            />
          </Section>
        )}
            </View>
          }
        />
      </BlurTarget>

      {/* 1. Driven imperatively, by id or through the ref. */}
      <BlurOverlay
        ref={menu}
        blurStyle={blurStyle}
        radius={radius}
        downsampling={downsampling}
        brightness={-120}
        fadeDuration={250}
        onPress={() => closeOverlay()}
        style={styles.centered}
      >
        <View style={styles.card}>
          <Text style={styles.cardTitle}>Hello from the overlay</Text>
          <Text style={styles.cardText}>
            Pressing this card does nothing — presses only close the overlay
            when they land on the blurred backdrop around it.
          </Text>
          <Button label="Close" onPress={() => closeOverlay()} />
        </View>
      </BlurOverlay>

      {/* 2. Blurs only part of the screen: the overlay is sized, not full. */}
      <BlurOverlay
        id="corner"
        blurStyle={blurStyle}
        radius={radius}
        downsampling={downsampling}
        onPress={() => closeOverlay('corner')}
        style={styles.corner}
      >
        <View style={styles.cornerLabel}>
          <Text style={styles.cornerText}>Only this box is blurred</Text>
        </View>
      </BlurOverlay>

      {/* 3. A frosted glass panel: the overlay is the glass, and a rounded,
              overflow-hidden parent clips it to shape on both platforms. */}
      <View pointerEvents="box-none" style={styles.glassClip}>
        <BlurOverlay
          visible={glass}
          blurStyle="systemThinMaterial"
          blurMode={blurMode}
          blurTargetId={blurTargetId}
          maxUpdateFps={maxUpdateFps}
          radius={20}
          downsampling={downsampling}
          // Gives the blur real pixels to sample past the panel's edge instead
          // of clamping the last row.
          captureOutset={20}
          brightness={-16}
          fadeDuration={220}
        >
          <View style={styles.glassInner}>
            <Text style={styles.glassTitle}>Frosted glass</Text>
            <Text style={styles.glassText}>
              A rounded, overflow-hidden parent clips the blur into a glass
              panel — the same code on iOS and Android.
            </Text>
          </View>
        </BlurOverlay>
      </View>

      {/* 4. Inside a <Modal />, which on Android is a window of its own.
              Nothing can capture the app behind another window, so with
              `blurMode="live"` the overlay asks the system to blur behind the
              whole modal window instead — live, and composited for free. Where
              cross-window blur is unavailable it falls back to the snapshot,
              which still shows the app behind the modal, frozen. */}
      <Modal
        visible={modalOpen}
        transparent
        animationType="fade"
        onRequestClose={() => setModalOpen(false)}
      >
        <BlurOverlay
          visible
          blurStyle={blurStyle}
          blurMode={blurMode}
          radius={radius}
          downsampling={downsampling}
          brightness={-40}
          fadeDuration={0}
          onPress={() => setModalOpen(false)}
          style={modalPartial ? styles.modalPartial : styles.centered}
        >
          <View style={styles.card}>
            <Text style={styles.cardTitle}>Blurred inside a Modal</Text>
            <Text style={styles.cardText}>
              blurMode is {blurMode}. On Android 12+ `live` blurs the app behind
              this window as it moves; `snapshot` freezes it as it was when the
              modal opened. Scroll the list behind, reopen, and compare.
            </Text>
            <Button label="Close" onPress={() => setModalOpen(false)} />
          </View>
        </BlurOverlay>
      </Modal>

      {/* 5. Fully declarative. */}
      <BlurOverlay
        visible={alwaysOn}
        blurStyle={blurStyle}
        blurMode={blurMode}
        maxUpdateFps={maxUpdateFps}
        radius={radius}
        downsampling={downsampling}
        brightness={-60}
        style={styles.bottomSheet}
      >
        <View style={styles.sheetInner}>
          <Text style={styles.cardText}>
            This one is controlled by the `visible` prop, so it stays until you
            flip the switch back.
          </Text>
          <Button label="Turn off" onPress={() => setAlwaysOn(false)} />
        </View>
      </BlurOverlay>
    </View>
  );
}

/**
 * A band that slides across the screen forever, on the native driver so that it
 * keeps going while a modal is up and JS is idle.
 */
function MovingBand() {
  const progress = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    const loop = Animated.loop(
      Animated.timing(progress, {
        toValue: 1,
        duration: 1800,
        easing: Easing.linear,
        useNativeDriver: true,
      })
    );

    loop.start();

    return () => loop.stop();
  }, [progress]);

  return (
    <Animated.View
      pointerEvents="none"
      style={[
        styles.band,
        {
          transform: [
            {
              translateY: progress.interpolate({
                inputRange: [0, 1],
                outputRange: [-200, 900],
              }),
            },
          ],
        },
      ]}
    />
  );
}

function Section({
  title,
  children,
}: {
  title: string;
  children: ReactNode;
}) {
  return (
    <View style={styles.section}>
      <Text style={styles.sectionTitle}>{title}</Text>
      <View style={styles.sectionBody}>{children}</View>
    </View>
  );
}

function Button({
  label,
  onPress,
  selected = false,
}: {
  label: string;
  onPress: () => void;
  selected?: boolean;
}) {
  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        selected && styles.buttonSelected,
        pressed && styles.buttonPressed,
      ]}
    >
      <Text style={[styles.buttonLabel, selected && styles.buttonLabelSelected]}>
        {label}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#0b1020' },
  target: { flex: 1 },
  band: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: 120,
    backgroundColor: 'rgba(255,255,255,0.75)',
    // Above the list, so the blur behind the modal has something unmistakable
    // to smear.
    zIndex: 10,
  },
  content: {
    padding: 20,
    // The app runs edge to edge, so keep the header clear of the status bar.
    paddingTop: (StatusBar.currentHeight ?? 44) + 20,
    paddingBottom: 48,
    gap: 8,
  },
  title: { color: 'white', fontSize: 24, fontWeight: '700' },
  subtitle: { color: '#9fb3c8', fontSize: 14, lineHeight: 20 },
  header: { gap: 16, paddingBottom: 16 },
  footer: { gap: 16, paddingTop: 16 },
  gridRow: { gap: 8 },
  tile: {
    flex: 1,
    height: 64,
    borderRadius: 10,
    padding: 8,
    justifyContent: 'flex-end',
  },
  tileLabel: { color: 'white', fontSize: 11, fontWeight: '600' },
  section: { gap: 8 },
  sectionTitle: {
    color: '#9fb3c8',
    fontSize: 12,
    fontWeight: '700',
    textTransform: 'uppercase',
  },
  sectionBody: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  rowLabel: { color: 'white', fontSize: 14 },
  button: {
    backgroundColor: '#1c2540',
    borderRadius: 8,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  buttonSelected: { backgroundColor: '#3a86ff' },
  buttonPressed: { opacity: 0.7 },
  buttonLabel: { color: '#dbe7f3', fontSize: 13, fontWeight: '600' },
  buttonLabelSelected: { color: 'white' },
  centered: { alignItems: 'center', justifyContent: 'center' },
  modalPartial: {
    top: 120,
    left: 24,
    right: 24,
    bottom: 'auto',
    height: 360,
    borderRadius: 16,
    overflow: 'hidden',
    alignItems: 'center',
    justifyContent: 'center',
  },
  card: {
    backgroundColor: 'rgba(255,255,255,0.92)',
    borderRadius: 16,
    gap: 12,
    margin: 24,
    padding: 24,
  },
  cardTitle: { fontSize: 18, fontWeight: '700', color: '#0b1020' },
  cardText: { fontSize: 14, lineHeight: 20, color: '#33415c' },
  corner: {
    top: 60,
    left: 24,
    width: 220,
    height: 160,
    right: 'auto',
    bottom: 'auto',
    borderRadius: 16,
    overflow: 'hidden',
    alignItems: 'center',
    justifyContent: 'center',
  },
  cornerLabel: { paddingHorizontal: 16 },
  cornerText: { color: 'white', fontWeight: '700', textAlign: 'center' },
  glassClip: {
    position: 'absolute',
    left: 20,
    right: 20,
    bottom: 40,
    height: 190,
    borderRadius: 28,
    // Clips the blurred surface into the panel's shape.
    overflow: 'hidden',
  },
  glassInner: {
    width: '100%',
    height: '100%',
    borderRadius: 28,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: 'rgba(255,255,255,0.35)',
    justifyContent: 'center',
    padding: 22,
    gap: 8,
  },
  glassTitle: { color: 'white', fontSize: 18, fontWeight: '700' },
  glassText: { color: 'rgba(255,255,255,0.85)', fontSize: 14, lineHeight: 20 },
  bottomSheet: {
    top: 'auto',
    height: 220,
    justifyContent: 'center',
  },
  sheetInner: { gap: 12, padding: 24 },
});
