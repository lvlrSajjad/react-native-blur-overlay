package com.bluroverly;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.view.View;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.ViewGroupManager;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.views.view.ReactViewGroup;

import java.lang.ref.WeakReference;

public class SajjadBlurOverlayManager extends ViewGroupManager<ReactViewGroup> {
    public static final String REACT_CLASS = "RCTSajjadBlurOverlay";
    private final ReactApplicationContext reactContext;
    private static RenderScript rs;
    private int mRadius = 20;
    private float mBrightness = 0;
    private float mFactor = 1;

    public SajjadBlurOverlayManager(ReactApplicationContext reactContext) {
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
    public void setBlurred(final View view){
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
