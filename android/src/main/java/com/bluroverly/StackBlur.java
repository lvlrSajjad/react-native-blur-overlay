package com.bluroverly;

import android.graphics.Bitmap;

/**
 * Stack Blur: a fast box-blur pyramid that approximates a Gaussian blur, based
 * on the algorithm by Mario Klingemann (http://quasimondo.com/StackBlurForCanvas/).
 *
 * <p>This replaces the RenderScript {@code ScriptIntrinsicBlur} the library
 * used to rely on: RenderScript was deprecated in Android 12 and is no longer
 * available to new builds, while this runs on any supported API level.
 */
final class StackBlur {

  /**
   * Larger radii are of little visual benefit once the snapshot is downsampled,
   * and the divisor lookup table below grows with the square of the radius.
   */
  static final int MAX_RADIUS = 25;

  private StackBlur() {}

  /** Blurs {@code bitmap} in place. */
  static void blur(Bitmap bitmap, int radius) {
    final int width = bitmap.getWidth();
    final int height = bitmap.getHeight();
    final int[] pixels = new int[width * height];

    bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
    blurPixels(pixels, width, height, radius);
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
  }

  /**
   * Blurs an ARGB_8888 pixel array in place. Alpha is left untouched.
   *
   * @param radius blur radius in pixels, clamped to 1..{@link #MAX_RADIUS}
   */
  static void blurPixels(int[] pixels, int width, int height, int radius) {
    final int r = Math.max(1, Math.min(MAX_RADIUS, radius));

    if (width < 1 || height < 1) {
      return;
    }

    final int wm = width - 1;
    final int hm = height - 1;
    final int div = r + r + 1;

    final int[] red = new int[width * height];
    final int[] green = new int[width * height];
    final int[] blue = new int[width * height];
    final int[] vmin = new int[Math.max(width, height)];

    int divsum = (div + 1) >> 1;
    divsum *= divsum;

    final int[] dv = new int[256 * divsum];
    for (int i = 0; i < 256 * divsum; i++) {
      dv[i] = i / divsum;
    }

    final int[][] stack = new int[div][3];
    final int r1 = r + 1;

    int yw = 0;
    int yi = 0;

    for (int y = 0; y < height; y++) {
      int rinsum = 0, ginsum = 0, binsum = 0;
      int routsum = 0, goutsum = 0, boutsum = 0;
      int rsum = 0, gsum = 0, bsum = 0;

      for (int i = -r; i <= r; i++) {
        final int p = pixels[yi + Math.min(wm, Math.max(i, 0))];
        final int[] sir = stack[i + r];
        sir[0] = (p & 0xff0000) >> 16;
        sir[1] = (p & 0x00ff00) >> 8;
        sir[2] = p & 0x0000ff;

        final int rbs = r1 - Math.abs(i);
        rsum += sir[0] * rbs;
        gsum += sir[1] * rbs;
        bsum += sir[2] * rbs;

        if (i > 0) {
          rinsum += sir[0];
          ginsum += sir[1];
          binsum += sir[2];
        } else {
          routsum += sir[0];
          goutsum += sir[1];
          boutsum += sir[2];
        }
      }

      int stackpointer = r;

      for (int x = 0; x < width; x++) {
        red[yi] = dv[rsum];
        green[yi] = dv[gsum];
        blue[yi] = dv[bsum];

        rsum -= routsum;
        gsum -= goutsum;
        bsum -= boutsum;

        int[] sir = stack[(stackpointer - r + div) % div];

        routsum -= sir[0];
        goutsum -= sir[1];
        boutsum -= sir[2];

        if (y == 0) {
          vmin[x] = Math.min(x + r1, wm);
        }

        final int p = pixels[yw + vmin[x]];
        sir[0] = (p & 0xff0000) >> 16;
        sir[1] = (p & 0x00ff00) >> 8;
        sir[2] = p & 0x0000ff;

        rinsum += sir[0];
        ginsum += sir[1];
        binsum += sir[2];

        rsum += rinsum;
        gsum += ginsum;
        bsum += binsum;

        stackpointer = (stackpointer + 1) % div;
        sir = stack[stackpointer];

        routsum += sir[0];
        goutsum += sir[1];
        boutsum += sir[2];

        rinsum -= sir[0];
        ginsum -= sir[1];
        binsum -= sir[2];

        yi++;
      }

      yw += width;
    }

    for (int x = 0; x < width; x++) {
      int rinsum = 0, ginsum = 0, binsum = 0;
      int routsum = 0, goutsum = 0, boutsum = 0;
      int rsum = 0, gsum = 0, bsum = 0;

      int yp = -r * width;

      for (int i = -r; i <= r; i++) {
        yi = Math.max(0, yp) + x;
        final int[] sir = stack[i + r];
        sir[0] = red[yi];
        sir[1] = green[yi];
        sir[2] = blue[yi];

        final int rbs = r1 - Math.abs(i);
        rsum += red[yi] * rbs;
        gsum += green[yi] * rbs;
        bsum += blue[yi] * rbs;

        if (i > 0) {
          rinsum += sir[0];
          ginsum += sir[1];
          binsum += sir[2];
        } else {
          routsum += sir[0];
          goutsum += sir[1];
          boutsum += sir[2];
        }

        if (i < hm) {
          yp += width;
        }
      }

      yi = x;
      int stackpointer = r;

      for (int y = 0; y < height; y++) {
        // Preserve alpha.
        pixels[yi] =
            (0xff000000 & pixels[yi]) | (dv[rsum] << 16) | (dv[gsum] << 8) | dv[bsum];

        rsum -= routsum;
        gsum -= goutsum;
        bsum -= boutsum;

        int[] sir = stack[(stackpointer - r + div) % div];

        routsum -= sir[0];
        goutsum -= sir[1];
        boutsum -= sir[2];

        if (x == 0) {
          vmin[y] = Math.min(y + r1, hm) * width;
        }

        final int p = x + vmin[y];
        sir[0] = red[p];
        sir[1] = green[p];
        sir[2] = blue[p];

        rinsum += sir[0];
        ginsum += sir[1];
        binsum += sir[2];

        rsum += rinsum;
        gsum += ginsum;
        bsum += binsum;

        stackpointer = (stackpointer + 1) % div;
        sir = stack[stackpointer];

        routsum += sir[0];
        goutsum += sir[1];
        boutsum += sir[2];

        rinsum -= sir[0];
        ginsum -= sir[1];
        binsum -= sir[2];

        yi += width;
      }
    }
  }
}
