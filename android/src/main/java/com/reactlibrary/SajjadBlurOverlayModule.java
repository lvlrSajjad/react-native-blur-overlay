
package com.reactlibrary;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.View;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

public class SajjadBlurOverlayModule extends ReactContextBaseJavaModule {

  private final ReactApplicationContext reactContext;

  public SajjadBlurOverlayModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
  }



  @ReactMethod
  public Bitmap screenShot() {
    final Activity activity = getCurrentActivity();

    if (activity != null) {
      View view = activity.getWindow().getDecorView();
      view.setDrawingCacheEnabled(true);
      view.buildDrawingCache();
      Bitmap b1 = view.getDrawingCache();
      Rect frame = new Rect();
      activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(frame);
      int statusBarHeight = frame.top;
      int width = activity.getWindowManager().getDefaultDisplay().getWidth();
      int height = activity.getWindowManager().getDefaultDisplay().getHeight();

      Bitmap b = Bitmap.createBitmap(b1, 0, statusBarHeight, width, height - statusBarHeight);
      view.destroyDrawingCache();
      return b;
    } else {
      return null;
    }
  }


  @Override
  public String getName() {
    return "SajjadBlurOverlay";
  }
}