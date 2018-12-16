package com.bluroverly;

import android.app.Activity;
import android.view.View;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.ViewGroupManager;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.views.view.ReactViewGroup;

public class SajjadBlurOverlayManager extends ViewGroupManager<ReactViewGroup> {
    private static final String REACT_CLASS = "RCTSajjadBlurOverlay";
    private final ReactApplicationContext reactContext;
    private int mRadius = 20;
    private float mBrightness = 0;
    private float mFactor = 1;

    SajjadBlurOverlayManager(ReactApplicationContext reactContext) {
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return REACT_CLASS;
    }

    @Override
    protected ReactViewGroup createViewInstance(ThemedReactContext reactContext) {
        return new ReactViewGroup(reactContext);
    }


    @ReactProp(name = "radius")
    public void setRadius(ReactViewGroup view, int Radius) {
        mRadius = Radius;
    }

    @ReactProp(name = "downsampling")
    public void setRadius(ReactViewGroup view, float factor) {
        mFactor = factor;
    }

    private void setBlurred(final View view){
        final Activity activity = reactContext.getCurrentActivity();
        if(activity==null) return;
        new BlurTask(view,reactContext,activity,mRadius,mFactor,mBrightness).execute();
    }

    @ReactProp(name = "brightness")
    public void setBrightness(ReactViewGroup view, float brightness) {
        mBrightness = brightness;
        setBlurred(view);
        view.requestFocus();
    }
}
