import React, { useRef, useEffect, useState, useCallback } from 'react';
import {
  View,
  Platform,
  NativeModules,
  requireNativeComponent,
  StyleSheet,
  TouchableWithoutFeedback,
  Animated,
  StyleProp,
  ViewStyle,
} from 'react-native';
import { EventEmitter } from 'events';

const emitter = new EventEmitter();

type BlurOverlayProps = {
  brightness?: number;
  radius?: number;
  downsampling?: number;
  blurStyle?: 'light' | 'dark' | 'extraLight'; // Define possible values if known
  vibrant?: boolean;
  idBlur: string;
  onPress?: () => void;
  customStyles?: StyleProp<ViewStyle>;
  children?: React.ReactNode;
};

console.log('Before.RCTSajjadBlurOverlay')

const RCTSajjadBlurOverlay = Platform.OS === 'ios'
    ? requireNativeComponent('SajjadBlurOverlay')
    : requireNativeComponent('RCTSajjadBlurOverlay');

// const RCTSajjadBlurOverlay = requireNativeComponent('SajjadBlurOverlay');

console.log("RCTSajjadBlurOverlay:", RCTSajjadBlurOverlay);

const BlurOverlay: React.FC<BlurOverlayProps> = ({
                                                   idBlur,
                                                   onPress,
                                                   customStyles,
                                                   children,
                                                   ...props
                                                 }) => {
  const [showBlurOverlay, setShowBlurOverlay] = useState<boolean>(false);
  const fadeIn = useRef(new Animated.Value(0)).current;

  const openOverlay = useCallback(() => {
    setShowBlurOverlay(true);
    fadeIn.setValue(0);
    Animated.timing(fadeIn, {
      toValue: 1,
      duration: 500,
      useNativeDriver: true,
    }).start();
  }, [fadeIn]);

  const closeOverlay = useCallback(() => {
    Animated.timing(fadeIn, {
      toValue: 0,
      duration: 500,
      useNativeDriver: true,
    }).start(() => setShowBlurOverlay(false));
  }, [fadeIn]);

  useEffect(() => {
    emitter.on(`drawer-open${idBlur}`, openOverlay);
    emitter.on(`drawer-close${idBlur}`, closeOverlay);

    return () => {
      emitter.off(`drawer-open${idBlur}`, openOverlay);
      emitter.off(`drawer-close${idBlur}`, closeOverlay);
    };
  }, [idBlur, openOverlay, closeOverlay]);

  if (!showBlurOverlay) return null;

  if (!RCTSajjadBlurOverlay) {
    console.warn("BlurOverlay native module is not linked properly.");
    return null;
  }


  return (
      <Animated.View style={[{ opacity: fadeIn }, styles.style]}>
        <TouchableWithoutFeedback onPress={onPress}>
          <RCTSajjadBlurOverlay {...props} style={[customStyles, styles.style]}>
            <View style={[customStyles, styles.style]}>{children}</View>
          </RCTSajjadBlurOverlay>
        </TouchableWithoutFeedback>
      </Animated.View>
  );
};

const styles = StyleSheet.create({
  style: {
    position: 'absolute',
    flex: 1,
    left: 0,
    top: 0,
    bottom: 0,
    right: 0,
    width: '100%',
    height: '100%',
    zIndex: 999,
  },
});

// Functions to control overlay visibility externally
export function openOverlay(idBlur: string) {
  emitter.emit(`drawer-open${idBlur}`);
}
export function closeOverlay(idBlur: string) {
  emitter.emit(`drawer-close${idBlur}`);
}

export default BlurOverlay;
