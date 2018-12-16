package com.bluroverly;


import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
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

import java.lang.ref.WeakReference;
import java.util.concurrent.CountDownLatch;

public class BlurTask extends AsyncTask<Void, Void, Drawable> {
    static private Drawable screenShot(
            ReactApplicationContext reactContext,
            Bitmap b1,
            int width,
            int height,
            int radius,
            float factor,
            float brightness) {
        try {
            if(factor!=1)
                b1 = Bitmap.createScaledBitmap(b1,(int)(width/factor),(int)(height/factor),false);
            b1 = blur(reactContext,b1,radius,1,brightness,factor);
            return new BitmapDrawable(reactContext.getResources(),b1);
        } catch (Exception e) {
            e.printStackTrace();
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
    public static Bitmap blur(Context context, Bitmap image, int Radius, float contrast, float brightness, float factor) {
        Bitmap outputBitmap;
        if(Radius > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            outputBitmap = Bitmap.createBitmap(image);
            RenderScript rs = RenderScript.create(context);
            ScriptIntrinsicBlur theIntrinsic = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            Allocation tmpIn = Allocation.createFromBitmap(rs, image);
            Allocation tmpOut = Allocation.createFromBitmap(rs, outputBitmap);
            theIntrinsic.setRadius(Radius/factor);
            theIntrinsic.setInput(tmpIn);
            theIntrinsic.forEach(tmpOut);
            tmpOut.copyTo(outputBitmap);
            rs.destroy();
        } else {
            outputBitmap = image;
        }
        if(brightness!=0){
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
        }
        return outputBitmap;
    }
    private WeakReference<View> view;
    private WeakReference<Activity> activity;
    private WeakReference<ReactApplicationContext> ctx;
    private float factor;
    private int radius;
    private float brightness;
    private Bitmap b1;
    private int width,height;
    // only retain a weak reference to the activity
    BlurTask(View view, ReactApplicationContext ctx, Activity activity, int radius, float factor, float brightness ) {
        this.view = new WeakReference<>(view);
        this.activity = new WeakReference<>(activity);
        this.ctx = new WeakReference<>(ctx);
        this.factor = factor;
        this.radius = radius;
        this.brightness = brightness;
    }

    protected void onPreExecute() {
        try {
            final View view = activity.get().getWindow().getDecorView().findViewById(android.R.id.content);
            width = view.getWidth();
            height = view.getHeight();
            b1 = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            final Canvas c = new Canvas(b1);
            view.draw(c);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected Drawable doInBackground(Void... param) {
        return screenShot(ctx.get(),b1, width,height,radius,factor,brightness);
    }

    protected void onPostExecute(final Drawable result) {
        try{
            try {
                view.get().setBackgroundDrawable(result);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }  catch (Exception e) {
            e.printStackTrace();
        }
    }
}