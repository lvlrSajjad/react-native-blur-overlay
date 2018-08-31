
# react-native-blur-overlay

## Getting started

`$ npm install react-native-blur-overlay --save`

### Mostly automatic installation

`$ react-native link react-native-blur-overlay`

### Manual installation


#### iOS

1. In XCode, in the project navigator, right click `Libraries` ➜ `Add Files to [your project's name]`
2. Go to `node_modules` ➜ `react-native-blur-overlay` and add `SajjadBlurOverlay.xcodeproj`
3. In XCode, in the project navigator, select your project. Add `libSajjadBlurOverlay.a` to your project's `Build Phases` ➜ `Link Binary With Libraries`
4. Run your project (`Cmd+R`)<

#### Android

1. Open up `android/app/src/main/java/[...]/MainApplication.java`
  - Add `import com.reactlibrary.SajjadBlurOverlayPackage;` to the imports at the top of the file
  - Add `new SajjadBlurOverlayPackage()` to the list returned by the `getPackages()` method
2. Append the following lines to `android/settings.gradle`:
  	```
  	include ':react-native-blur-overlay'
  	project(':react-native-blur-overlay').projectDir = new File(rootProject.projectDir, 	'../node_modules/react-native-blur-overlay/android')
  	```
3. Insert the following lines inside the dependencies block in `android/app/build.gradle`:
  	```
      compile project(':sajjad-blur-overlay')
  	```


## Usage
```javascript
import BlurOverlay from 'sajjad-blur-overlay';

constructor(props) {
        super(props);
        this.state = {
            showBlurOverlay: false,
        }
    }
    
    {this.state.showBlurOverlay &&

                    <BlurOverlay
                        radius={24}
                        brightness={-200}
                        onPress={()=>{
                            this.setState({showBlurOverlay: !this.state.showBlurOverlay});
                        }}
                    >
                        <View style={styles.image}>
                        <Text style={styles.instructions2}>{instructions}</Text>

                            <Text style={styles.instructions2}>{instructions}</Text>
                        </View>
                    </BlurOverlay>

                }
```
  
