import {NativeModules, requireNativeComponent, StyleSheet, TouchableWithoutFeedback} from 'react-native';
import React, {Component} from 'react';

const {SajjadBlurOverlay} = NativeModules;
var iface = {
    name: 'BlurView',
};
var RCTSajjadBlurOverlay = requireNativeComponent('RCTSajjadBlurOverlay', iface);

export default class BlurOverlay extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            showBlurOverlay: false,
        }
    }

    render() {
        return (
            <TouchableWithoutFeedback style={styles.style} onPress={this.props.onPress}>
                <RCTSajjadBlurOverlay {...this.props} style={styles.style}/>
            </TouchableWithoutFeedback>
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

//export default SajjadBlurOverlay;
//module.exports = requireNativeComponent('RCTSajjadBlurOverlay', iface);
