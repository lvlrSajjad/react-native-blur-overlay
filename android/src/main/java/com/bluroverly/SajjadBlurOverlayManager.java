package com.bluroverly;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
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



public class SajjadBlurOverlayManager extends ViewGroupManager<ReactViewGroup> {
    public static final String REACT_CLASS = "RCTSajjadBlurOverlay";
    private final ReactApplicationContext reactContext;
    private static RenderScript rs;
    private int mRadius = 20;
    private float mBrightness = 0;


    public SajjadBlurOverlayManager(ReactApplicationContext reactContext) {
        this.reactContext = reactContext;
        rs = RenderScript.create(reactContext);

    }

    private ThemedReactContext mContext = null;


    @Override
    public String getName() {
        return REACT_CLASS;
    }

    @Override
    protected ReactViewGroup createViewInstance(ThemedReactContext reactContext) {
        mContext = reactContext;
        ReactViewGroup iv = new ReactViewGroup(reactContext);
        iv.setBackgroundDrawable(screenShot());
        return iv;
    }


    private Drawable screenShot() {
        final Activity activity = reactContext.getCurrentActivity();
        try {
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
                b1 = Bitmap.createBitmap(b1, 0, statusBarHeight, width, height - statusBarHeight);
                view.destroyDrawingCache();
                b1 =blur(reactContext,b1,mRadius,1,mBrightness);
                return new BitmapDrawable(reactContext.getResources(),b1);
            } else {
                return null;
            }
        } catch (Exception e) {
            return  null;
        }
    }

    /**
     *
     * @param context
     * @param image screenshot bitmap
     * @param Radius integer between 1 to 24
     * @param contrast 0..10 1 is default
     * @param brightness -255..255 0 is default
     * @return blurred Bitmap
     */
    public static Bitmap blur(Context context, Bitmap image, int Radius, float contrast, float brightness) {
        Bitmap outputBitmap = Bitmap.createBitmap(image);
        rs = RenderScript.create(context);
        ScriptIntrinsicBlur theIntrinsic = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
        Allocation tmpIn = Allocation.createFromBitmap(rs, image);
        Allocation tmpOut = Allocation.createFromBitmap(rs, outputBitmap);
        theIntrinsic.setRadius(Radius);
        theIntrinsic.setInput(tmpIn);
        theIntrinsic.forEach(tmpOut);
        tmpOut.copyTo(outputBitmap);
        rs.destroy();

        ColorMatrix cm = new ColorMatrix(new float[]
                {
                        contrast, 0, 0, 0, brightness,
                        0, contrast, 0, 0, brightness,
                        0, 0, contrast, 0, brightness,
                        0, 0, 0, 1, 0
                });


        Canvas canvas = new Canvas(outputBitmap);

        Paint paint = new Paint();
        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        canvas.drawBitmap(outputBitmap, 0, 0, paint);

        return outputBitmap;
    }


    @ReactProp(name = "radius")
    public void setRadius(ReactViewGroup view, int Radius) {
        mRadius = Radius;
      //  view.setBackgroundDrawable(screenShot());
      //  view.requestFocus();
    }

    @ReactProp(name = "brightness")
    public void setBrightness(ReactViewGroup view, float brightness) {
        mBrightness = brightness;

       // view.setBackgroundDrawable(screenShot());
       // view.requestFocus();
    }
}
