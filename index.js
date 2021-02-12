import {View, Platform, NativeModules, requireNativeComponent, StyleSheet, TouchableWithoutFeedback,Animated} from 'react-native';
import React, {Component,} from 'react';
import PropTypes from 'prop-types'
var emitter = require('tiny-emitter/instance');

const {SajjadBlurOverlay} = NativeModules;
var iface = {
    name: 'BlurView',
    propTypes: {
        ...View.propTypes,
        brightness: PropTypes.any,
        radius: PropTypes.number,
        downsampling: PropTypes.number,
        blurStyle: PropTypes.string,
        vibrant: PropTypes.bool,
    }
};
var RCTSajjadBlurOverlay = Platform.select({
  ios: () => requireNativeComponent('SajjadBlurOverlay', iface),
  android: () => requireNativeComponent('RCTSajjadBlurOverlay', iface),
})();
export default class BlurOverlay extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
          showBlurOverlay: false,
          fadeIn: new Animated.Value(0),
        }
        this._openOverlay = this.openOverlay.bind(this)
        this._closeOverlay = this.closeOverlay.bind(this)
    }
    openOverlay(){
       this.setState({
           showBlurOverlay: true,
           fadeIn: new Animated.Value(0),
       }, () => {
            Animated.timing(
                this.state.fadeIn,
                {
                    toValue: 1,
                    duration: 500,
                    useNativeDriver: true
                }
            ).start();
       })
    }
    closeOverlay(){
        Animated.timing(
            this.state.fadeIn,
            {
                toValue: 0,
                duration: 500,
                useNativeDriver: true
            }
        ).start(()=>this.setState({showBlurOverlay: false}));
    }
    componentDidMount(){
        emitter.on('drawer-open',this._openOverlay);
        emitter.on('drawer-close',this._closeOverlay);
    }
    componentWillUnmount(){
        emitter.off('drawer-open',this._openOverlay);
        emitter.off('drawer-close',this._closeOverlay);
    }

    render() {
        const { children } = this.props;
        return (
            this.state.showBlurOverlay ?
            <Animated.View style={[ {opacity: this.state.fadeIn},styles.style]}>
            <TouchableWithoutFeedback style={styles.style} onPress={this.props.onPress} {...this.props.touchableProps}>
                <RCTSajjadBlurOverlay {...this.props} style={[this.props.customStyles,styles.style]}>
                <View style={[this.props.customStyles,styles.style]}>
                    {children}
                </View>
                </RCTSajjadBlurOverlay>
            </TouchableWithoutFeedback>
            </Animated.View> :
                null
        );
    }
}

const styles = StyleSheet.create({
    style: {
        position: 'absolute',
        flex: 1,
        left: 0,
        top: 0,
        bottom: 0,
        right: 0,
        //  resizeMode: 'cover',
        width: null,
        height: null,
        zIndex: 999,
    },
});
export function openOverlay() {
    emitter.emit('drawer-open');
}
export function closeOverlay() {
    emitter.emit('drawer-close');
}
