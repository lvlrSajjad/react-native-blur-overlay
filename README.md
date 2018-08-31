
# react-native-blur-overlay

## Getting started

`$ npm install react-native-blur-overlay --save`

### Mostly automatic installation

`$ react-native link react-native-blur-overlay`

### Manual installation


#### iOS

Currently Not Supported , Comming Soon

#### Android

1. Open up `android/app/src/main/java/[...]/MainApplication.java`
  - Add `import com.reactlibrary.SajjadBlurOverlayPackage;` to the imports at the top of the file
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
  
