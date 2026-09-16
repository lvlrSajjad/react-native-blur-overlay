package com.bluroverlayexample

import android.os.Bundle
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate

class MainActivity : ReactActivity() {

  /**
   * Returns the name of the main component registered from JavaScript. This is used to schedule
   * rendering of the component.
   */
  override fun getMainComponentName(): String = "BlurOverlayExample"

  /**
   * Returns the instance of the [ReactActivityDelegate]. We use [DefaultReactActivityDelegate]
   * which allows you to enable New Architecture with a single boolean flags [fabricEnabled]
   */
  override fun createReactActivityDelegate(): ReactActivityDelegate =
      object : DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled) {
        /**
         * Hands the launch intent's extras to JS as initial props, so that a frame
         * measurement can pick a variant without anyone tapping the right buttons:
         *
         * ```
         * adb shell am start -n com.bluroverlayexample/.MainActivity \
         *   --ez panel true --es blurMode live --ei maxUpdateFps 0
         * ```
         */
        override fun getLaunchOptions(): Bundle? = intent?.extras
      }
}
