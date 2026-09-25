import UIKit
import React
import React_RCTAppDelegate
import ReactAppDependencyProvider

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
  var window: UIWindow?

  var reactNativeDelegate: ReactNativeDelegate?
  var reactNativeFactory: RCTReactNativeFactory?

  func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
  ) -> Bool {
    let delegate = ReactNativeDelegate()
    let factory = RCTReactNativeFactory(delegate: delegate)
    delegate.dependencyProvider = RCTAppDependencyProvider()

    reactNativeDelegate = delegate
    reactNativeFactory = factory

    window = UIWindow(frame: UIScreen.main.bounds)

    factory.startReactNative(
      withModuleName: "BlurOverlayExample",
      in: window,
      initialProperties: launchProperties(),
      launchOptions: launchOptions
    )

    return true
  }

  /**
   * Launch arguments as initial props, the iOS twin of MainActivity's intent
   * extras, so a demo can open in a given state without anyone scrolling to the
   * buttons:
   *
   *   xcrun simctl launch booted org.reactjs.native.example.BlurOverlayExample \
   *     -tabBar true -blurMode glass
   */
  private func launchProperties() -> [AnyHashable: Any] {
    var props: [AnyHashable: Any] = [:]
    let args = ProcessInfo.processInfo.arguments.dropFirst()
    var iterator = args.makeIterator()

    while let key = iterator.next() {
      guard key.hasPrefix("-"), let value = iterator.next() else { continue }
      let name = String(key.dropFirst())

      switch value {
      case "true": props[name] = true
      case "false": props[name] = false
      default: props[name] = Double(value) ?? value
      }
    }

    return props
  }
}

class ReactNativeDelegate: RCTDefaultReactNativeFactoryDelegate {
  override func sourceURL(for bridge: RCTBridge) -> URL? {
    self.bundleURL()
  }

  override func bundleURL() -> URL? {
#if DEBUG
    RCTBundleURLProvider.sharedSettings().jsBundleURL(forBundleRoot: "index")
#else
    Bundle.main.url(forResource: "main", withExtension: "jsbundle")
#endif
  }
}
