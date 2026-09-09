package com.bluroverly;

import androidx.annotation.Nullable;

import com.facebook.react.BaseReactPackage;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.module.model.ReactModuleInfoProvider;
import com.facebook.react.uimanager.ViewManager;

import java.util.Collections;
import java.util.List;

/**
 * Registered automatically by autolinking. Manual registration in
 * {@code MainApplication} keeps working, but is no longer needed.
 */
@SuppressWarnings("rawtypes") // ReactPackage declares a raw List<ViewManager>.
public class SajjadBlurOverlayPackage extends BaseReactPackage {

  @Override
  public List<ViewManager> createViewManagers(ReactApplicationContext reactContext) {
    return Collections.singletonList(new SajjadBlurOverlayManager());
  }

  @Nullable
  @Override
  public NativeModule getModule(String name, ReactApplicationContext reactContext) {
    return null;
  }

  @Override
  public ReactModuleInfoProvider getReactModuleInfoProvider() {
    return Collections::emptyMap;
  }
}
