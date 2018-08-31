
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

1. Open up `android/app/src/main/java/[...]/MainActivity.java`
  - Add `import com.reactlibrary.SajjadBlurOverlayPackage;` to the imports at the top of the file
  - Add `new SajjadBlurOverlayPackage()` to the list returned by the `getPackages()` method
2. Append the following lines to `android/settings.gradle`:
  	```
  	include ':react-native-blur-overlay'
  	project(':react-native-blur-overlay').projectDir = new File(rootProject.projectDir, 	'../node_modules/react-native-blur-overlay/android')
  	```
3. Insert the following lines inside the dependencies block in `android/app/build.gradle`:
  	```
      compile project(':react-native-blur-overlay')
  	```

#### Windows
[Read it! :D](https://github.com/ReactWindows/react-native)

1. In Visual Studio add the `SajjadBlurOverlay.sln` in `node_modules/react-native-blur-overlay/windows/SajjadBlurOverlay.sln` folder to their solution, reference from their app.
2. Open up your `MainPage.cs` app
  - Add `using Blur.Overlay.SajjadBlurOverlay;` to the usings at the top of the file
  - Add `new SajjadBlurOverlayPackage()` to the `List<IReactPackage>` returned by the `Packages` method


## Usage
```javascript
import SajjadBlurOverlay from 'react-native-blur-overlay';

// TODO: What to do with the module?
SajjadBlurOverlay;
```
  