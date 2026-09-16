package com.bluroverly;

import androidx.annotation.Nullable;

import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.ViewGroupManager;
import com.facebook.react.uimanager.ViewManagerDelegate;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.viewmanagers.SajjadBlurTargetManagerDelegate;
import com.facebook.react.viewmanagers.SajjadBlurTargetManagerInterface;

/** View manager for {@code <BlurTarget />}. */
@ReactModule(name = SajjadBlurTargetManager.NAME)
public class SajjadBlurTargetManager extends ViewGroupManager<SajjadBlurTargetView>
    implements SajjadBlurTargetManagerInterface<SajjadBlurTargetView> {

  public static final String NAME = "SajjadBlurTarget";

  private final ViewManagerDelegate<SajjadBlurTargetView> delegate =
      new SajjadBlurTargetManagerDelegate<SajjadBlurTargetView, SajjadBlurTargetManager>(this);

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  protected ViewManagerDelegate<SajjadBlurTargetView> getDelegate() {
    return delegate;
  }

  @Override
  public SajjadBlurTargetView createViewInstance(ThemedReactContext context) {
    return new SajjadBlurTargetView(context);
  }

  @Override
  public void onDropViewInstance(SajjadBlurTargetView view) {
    BlurTargetRegistry.unregister(view);
    super.onDropViewInstance(view);
  }

  @Override
  @ReactProp(name = "blurTargetId")
  public void setBlurTargetId(SajjadBlurTargetView view, @Nullable String value) {
    view.setBlurTargetId(value);
  }
}
