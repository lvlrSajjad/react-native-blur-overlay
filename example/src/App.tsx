import { useRef, useState, type ReactNode } from 'react';
import {
  Platform,
  Pressable,
  ScrollView,
  StatusBar,
  StyleSheet,
  Switch,
  Text,
  View,
} from 'react-native';
import BlurOverlay, {
  closeOverlay,
  openOverlay,
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

const TILES = [
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

export default function App() {
  const [blurStyle, setBlurStyle] = useState<BlurStyle>('dark');
  const [radius, setRadius] = useState(14);
  const [downsampling, setDownsampling] = useState(2);
  const [alwaysOn, setAlwaysOn] = useState(false);

  const menu = useRef<BlurOverlayInstance>(null);

  return (
    <View style={styles.screen}>
      <StatusBar barStyle="light-content" />

      {/* Something worth blurring. */}
      <ScrollView contentContainerStyle={styles.content}>
        <Text style={styles.title}>react-native-blur-overlay</Text>
        <Text style={styles.subtitle}>
          The overlay blurs whatever is rendered behind it. Scroll, then open
          one of the overlays below.
        </Text>

        <View style={styles.grid}>
          {TILES.map((color) => (
            <View key={color} style={[styles.tile, { backgroundColor: color }]}>
              <Text style={styles.tileLabel}>{color}</Text>
            </View>
          ))}
        </View>

        <Section title="Open it">
          <Button label="openOverlay()" onPress={() => openOverlay()} />
          <Button label="ref.open()" onPress={() => menu.current?.open()} />
          <Button
            label="Blur one corner"
            onPress={() => openOverlay('corner')}
          />
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
          </Section>
        )}
      </ScrollView>

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

      {/* 3. Fully declarative. */}
      <BlurOverlay
        visible={alwaysOn}
        blurStyle={blurStyle}
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
  content: {
    padding: 20,
    // The app runs edge to edge, so keep the header clear of the status bar.
    paddingTop: (StatusBar.currentHeight ?? 44) + 20,
    paddingBottom: 48,
    gap: 16,
  },
  title: { color: 'white', fontSize: 24, fontWeight: '700' },
  subtitle: { color: '#9fb3c8', fontSize: 14, lineHeight: 20 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  tile: {
    width: 96,
    height: 72,
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
  bottomSheet: {
    top: 'auto',
    height: 220,
    justifyContent: 'center',
  },
  sheetInner: { gap: 12, padding: 24 },
});
