
# react-native-blur-overlay [![npm version](https://img.shields.io/npm/v/react-native-blur-overlay.svg)](https://www.npmjs.com/package/react-native-blur-overlay) [![Maintainability](https://api.codeclimate.com/v1/badges/a99a88d28ad37a79dbf6/maintainability)](https://codeclimate.com/github/lvlrSajjad/react-native-blur-overlay/maintainability) [![Test Coverage](https://api.codeclimate.com/v1/badges/a99a88d28ad37a79dbf6/test_coverage)](https://codeclimate.com/github/lvlrSajjad/react-native-blur-overlay/test_coverage)


<img src="https://raw.githubusercontent.com/lvlrSajjad/react-native-blur-overlay/master/giphy.gif" width="250">   <img src="https://raw.githubusercontent.com/lvlrSajjad/react-native-blur-overlay/master/Untitled.jpg" width="250">  <img src="https://raw.githubusercontent.com/lvlrSajjad/react-native-blur-overlay/master/Untitled2.jpg" width="250">


## Getting started

`$ npm install react-native-blur-overlay --save`

### Mostly automatic installation

`$ react-native link react-native-blur-overlay`

### Manual installation


#### iOS

1. In XCode right click on project's name and choose Add Files to..
2. Go to node_modules/react-native-blur-overlay and select ios folder
   Now you're ready to require('react-native-blur-overlay') inside your app!


#### Android

**Only works on Android >= 17 !!!**

1. Open up `android/app/src/main/java/[...]/MainApplication.java`
  - Add `import com.bluroverly.SajjadBlurOverlayPackage;` to the imports at the top of the file
  - Add `new SajjadBlurOverlayPackage()` to the list returned by the `getPackages()` method
2. Append the following lines to `android/settings.gradle`:
  	```
      include ':react-native-blur-overlay'
      project(':react-native-blur-overlay').projectDir = new File(rootProject.projectDir, '../node_modules/react-native-blur-overlay/android')

  	```
3. Insert the following lines inside the dependencies block in `android/app/build.gradle`:
  	```
      compile project(':react-native-blur-overlay')
  	```


## Usage
```javascript
import React, {Component} from 'react';
import {Platform, StyleSheet, Text, View, TouchableOpacity} from 'react-native';
import BlurOverlay,{closeOverlay,openOverlay} from 'react-native-blur-overlay';

const instructions = Platform.select({
    ios: 'Press Cmd+R to reload,\n' + 'Cmd+D or shake for dev menu',
    android:
    'Double tap R on your keyboard to reload,\n' +
    'Shake or press menu button for dev menu',
});

type Props = {};
export default class App extends Component<Props> {
    constructor(props) {
        super(props);
    }

    renderBlurChilds() {
        return (
          <View style={styles.image}>
              <Text style={styles.instructions2}>{instructions}</Text>

              <Text style={styles.instructions2}>{instructions}</Text>
          </View>
        );
    }

    render() {
        return (

            <View style={styles.container}>
                <TouchableOpacity
                    onPress={() => {
                        openOverlay();
                    }}
                    style={{width: '90%', height: 36, backgroundColor: "#03A9F4", borderRadius: 4, margin: 16}}/>

                <Text style={styles.welcome}>Welcome to React Native!</Text>
                <Text style={styles.instructions}>To get started, edit App.js</Text>
                <Text style={styles.instructions}>{instructions}</Text>
                

                <BlurOverlay
                    radius={14}
                    downsampling={2}
                    brightness={-200}
                    onPress={() => {
                        closeOverlay();
                    }}
                    customStyles={{alignItems: 'center', justifyContent: 'center'}}
                    blurStyle="dark"
                    children={this.renderBlurChilds()}
                />
                
            </View>
        );
    }
}

const styles = StyleSheet.create({
    container: {
        flex: 1,
        justifyContent: 'center',
        alignItems: 'center',
        backgroundColor: '#F5FCFF',
    },
    welcome: {
        fontSize: 20,
        textAlign: 'center',
        margin: 10,
    },
    instructions: {
        textAlign: 'center',
        color: '#333333',
        marginBottom: 5,
    },
    instructions2: {
        textAlign: 'center',
        color: 'white',
        marginBottom: 5,
    },
});

```
  
  
  
  ## Props
  ```
  android only:
   radius : Int (Between  0 to 25*downsampling)
   downsampling : float (>= 1)
   brightness : float (Between -255 to 255 , 0 = nochange)
  
  ios only : 
    blurStyle: string ("light" , "extraLight" , "dark")
	
  both platforms :
    onPress : func
    customStyles: style

	  
```
