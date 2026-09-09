# react-native-blur-overlay example

A small app that exercises the library end to end: imperative and declarative
opening, the iOS blur styles, the Android radius/downsampling knobs, an overlay
that covers only part of the screen, and press handling.

```bash
# from the repository root
npm install

npm run example:start     # Metro
npm run example:android   # or
npm run example:ios       # runs pod install on the first go
```

The app is an npm workspace of the library, so its dependencies are hoisted to
the repository root and Metro resolves `react-native-blur-overlay` to the
TypeScript sources in `../src` — editing the library refreshes the app without
a rebuild.

Two things differ from a stock `react-native init` app because of that layout,
both marked with comments:

- `android/settings.gradle` and `android/app/build.gradle` point at
  `../../node_modules` instead of `../node_modules`
- `metro.config.js` watches the repository root and prefers the library's
  `source` export condition
