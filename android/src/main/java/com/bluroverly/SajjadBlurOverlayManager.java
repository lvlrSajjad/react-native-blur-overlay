package com.bluroverly;

import androidx.annotation.Nullable;

import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.ViewGroupManager;
import com.facebook.react.uimanager.ViewManagerDelegate;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.viewmanagers.SajjadBlurOverlayManagerDelegate;
import com.facebook.react.viewmanagers.SajjadBlurOverlayManagerInterface;

/**
 * View manager for {@code <BlurOverlay />}. Implementing the Codegen interface
 * keeps it working on both the new architecture (through the delegate) and the
 * legacy one (through the {@code @ReactProp} annotations).
 */
@ReactModule(name = SajjadBlurOverlayManager.NAME)
public class SajjadBlurOverlayManager extends ViewGroupManager<SajjadBlurOverlayView>
    implements SajjadBlurOverlayManagerInterface<SajjadBlurOverlayView> {

  public static final String NAME = "SajjadBlurOverlay";

  private final ViewManagerDelegate<SajjadBlurOverlayView> delegate =
      new SajjadBlurOverlayManagerDelegate<SajjadBlurOverlayView, SajjadBlurOverlayManager>(this);

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  protected ViewManagerDelegate<SajjadBlurOverlayView> getDelegate() {
    return delegate;
  }

  @Override
  public SajjadBlurOverlayView createViewInstance(ThemedReactContext context) {
    return new SajjadBlurOverlayView(context);
  }

  @Override
  public void onDropViewInstance(SajjadBlurOverlayView view) {
    view.reset();
    super.onDropViewInstance(view);
  }

  @Override
  @ReactProp(name = "radius", defaultInt = 20)
  public void setRadius(SajjadBlurOverlayView view, int value) {
    view.setBlurRadius(value);
  }

  @Override
  @ReactProp(name = "downsampling", defaultFloat = 1f)
  public void setDownsampling(SajjadBlurOverlayView view, float value) {
    view.setDownsamplingFactor(value);
  }

  @Override
  @ReactProp(name = "brightness", defaultFloat = 0f)
  public void setBrightness(SajjadBlurOverlayView view, float value) {
    view.setBrightnessOffset(value);
  }

  @Override
  @ReactProp(name = "blurMode")
  public void setBlurMode(SajjadBlurOverlayView view, @Nullable String value) {
    view.setBlurMode(value);
  }

  @Override
  @ReactProp(name = "blurTargetId")
  public void setBlurTargetId(SajjadBlurOverlayView view, @Nullable String value) {
    view.setBlurTargetId(value);
  }

  @Override
  @ReactProp(name = "maxUpdateFps", defaultInt = 30)
  public void setMaxUpdateFps(SajjadBlurOverlayView view, int value) {
    view.setMaxUpdateFps(value);
  }

  @Override
  @ReactProp(name = "captureOutset", defaultFloat = 0f)
  public void setCaptureOutset(SajjadBlurOverlayView view, float value) {
    view.setCaptureOutset(value);
  }

  @Override
  @ReactProp(name = "blurStyle")
  public void setBlurStyle(SajjadBlurOverlayView view, @Nullable String value) {
    // iOS only: Android renders a blurred snapshot rather than a UIBlurEffect.
  }

  @Override
  @ReactProp(name = "vibrant")
  public void setVibrant(SajjadBlurOverlayView view, boolean value) {
    // iOS only.
  }
}
