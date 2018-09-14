
[![Node version](https://img.shields.io/node/v/sajjad-blur-overlay.svg?style=flat)](http://nodejs.org/sajjad-blur-overlay/)

# sajjad-blur-overlay
<img src="https://raw.githubusercontent.com/lvlrSajjad/sajjad-blur-overlay/master/giphy.gif" width="250">   <img src="https://raw.githubusercontent.com/lvlrSajjad/sajjad-blur-overlay/master/Untitled.jpg" width="250">  <img src="https://raw.githubusercontent.com/lvlrSajjad/sajjad-blur-overlay/master/Untitled2.jpg" width="250">


## Getting started

`$ npm install sajjad-blur-overlay --save`

### Mostly automatic installation

`$ react-native link sajjad-blur-overlay`

### Manual installation


#### iOS

1. In XCode right click on project's name and choose Add Files to..
2. Go to node_modules/sajjad-blur-overlay and select ios folder
   Now you're ready to require('sajjad-blur-overlay') inside your app!


#### Android

1. Open up `android/app/src/main/java/[...]/MainApplication.java`
  - Add `import com.bluroverly.SajjadBlurOverlayPackage;` to the imports at the top of the file
  - Add `new SajjadBlurOverlayPackage()` to the list returned by the `getPackages()` method
2. Append the following lines to `android/settings.gradle`:
  	```
      include ':sajjad-blur-overlay'
      project(':sajjad-blur-overlay').projectDir = new File(rootProject.projectDir, '../node_modules/sajjad-blur-overlay/android')

  	```
3. Insert the following lines inside the dependencies block in `android/app/build.gradle`:
  	```
      compile project(':sajjad-blur-overlay')
  	```


## Usage
```javascript
import React, {Component} from 'react';
import {Platform, StyleSheet, Text, View, TouchableOpacity} from 'react-native';
import BlurOverlay from 'sajjad-blur-overlay';

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
        this.state = {
            showBlurOverlay: false,
        }
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
                        this.setState({showBlurOverlay:true})
                    }}
                    style={{width: '90%', height: 36, backgroundColor: "#03A9F4", borderRadius: 4, margin: 16}}/>

                <Text style={styles.welcome}>Welcome to React Native!</Text>
                <Text style={styles.instructions}>To get started, edit App.js</Text>
                <Text style={styles.instructions}>{instructions}</Text>
                {this.state.showBlurOverlay &&

                <BlurOverlay
                    radius={14}
                    brightness={-200}
                    onPress={() => {
                        this.setState({showBlurOverlay: !this.state.showBlurOverlay});
                    }}
                    customStyles={{alignItems: 'center', justifyContent: 'center'}}
                    blurStyle="dark"
                    children={this.renderBlurChilds()}
                />
                }
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
   radius : Int (Between  0 to 24)
   brightness : float (Between -255 to 255 , 0 = nochange)
  
  ios only : 
    blurStyle: string ("light" , "extraLight" , "dark")
	
  both platforms :
    onPress : func
    customStyles: style

	  
```
