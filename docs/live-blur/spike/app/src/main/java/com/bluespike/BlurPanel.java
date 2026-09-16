package com.bluespike;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import androidx.annotation.RequiresApi;

import java.util.Arrays;

/**
 * The thing under test: a panel that re-captures a target subtree into a RenderNode every
 * frame, blurs it with RenderEffect, and draws it as its own backdrop.
 *
 * Mode OFF   — no capture at all. The baseline the other modes are charged against.
 * Mode A     — target is android.R.id.content, which CONTAINS this panel. Self-exclusion is
 *              supposed to come from the draw()/dispatchDraw() capturing flag the library
 *              already ships. Whether that survives a hardware RecordingCanvas is the
 *              question Phase 0 exists to answer.
 * Mode B     — target is a subtree that does not contain this panel, so no exclusion is
 *              needed. This is the <BlurTarget> shape every other RN blur library uses.
 */
public class BlurPanel extends FrameLayout implements ViewTreeObserver.OnPreDrawListener {

    public static final int MODE_OFF = 0;
    public static final int MODE_A = 1;
    public static final int MODE_B = 2;
    /** Variant A with the self-exclusion flag disabled: the control that shows what the
     *  flag is (or is not) actually doing. */
    public static final int MODE_A_NOEXCL = 3;
    /** Variant B with the RenderEffect left off: isolates the cost of re-recording the
     *  target subtree from the cost of blurring it, and runs below API 31. */
    public static final int MODE_B_CAPTURE_ONLY = 4;

    private static final String TAG = "BLURSPIKE";

    private int mode = MODE_OFF;
    private View target;
    private float inputScale = 0.5f;
    private float radiusPx = 25f;

    /** Set while we are recording the target. Both draw overrides honour it. */
    private boolean capturing;

    private final RenderNode captureNode = new RenderNode("blur-capture");
    private final int[] targetLoc = new int[2];
    private final int[] selfLoc = new int[2];

    /** Set if drawing the panel is ever reached through the flag path during a capture. */
    private volatile int excludedViaDraw;
    private volatile int excludedViaDispatchDraw;
    private volatile long firstExclusionFrame = -1;
    private volatile long frameCounter;

    private long[] samples = new long[8192];
    private int sampleCount;

    public BlurPanel(Context context) {
        super(context);
        setWillNotDraw(false);
    }

    public void configure(int mode, View target, float inputScale, float radiusPx) {
        this.mode = mode;
        this.target = target;
        this.inputScale = inputScale;
        this.radiusPx = radiusPx;
        resetStats();
        excludedViaDraw = 0;
        excludedViaDispatchDraw = 0;
        firstExclusionFrame = -1;
        frameCounter = 0;
        invalidate();
    }

    public void resetStats() {
        sampleCount = 0;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnPreDrawListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnPreDrawListener(this);
        captureNode.discardDisplayList();
        super.onDetachedFromWindow();
    }

    @Override
    public boolean onPreDraw() {
        if (mode != MODE_OFF && target != null) {
            capture();
        }
        return true;
    }

    private void capture() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        int sw = Math.max(1, Math.round(w * inputScale));
        int sh = Math.max(1, Math.round(h * inputScale));

        target.getLocationInWindow(targetLoc);
        getLocationInWindow(selfLoc);
        float dx = selfLoc[0] - targetLoc[0];
        float dy = selfLoc[1] - targetLoc[1];

        frameCounter++;
        long t0 = System.nanoTime();
        captureNode.setPosition(0, 0, sw, sh);
        RecordingCanvas canvas = captureNode.beginRecording(sw, sh);
        try {
            canvas.scale(inputScale, inputScale);
            canvas.translate(-dx, -dy);
            capturing = true;
            target.draw(canvas);
        } finally {
            capturing = false;
            captureNode.endRecording();
        }

        if (mode != MODE_B_CAPTURE_ONLY && Build.VERSION.SDK_INT >= 31) {
            applyBlur(captureNode, Math.max(0.5f, radiusPx * inputScale));
        }
        captureNode.setPivotX(0f);
        captureNode.setPivotY(0f);
        captureNode.setScaleX(1f / inputScale);
        captureNode.setScaleY(1f / inputScale);

        record(System.nanoTime() - t0);
    }

    /** Kept out of line so ART never resolves RenderEffect on an API 30 device. */
    @RequiresApi(31)
    private static void applyBlur(RenderNode node, float r) {
        node.setRenderEffect(RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP));
    }

    // --- self-exclusion, exactly as SajjadBlurOverlayView does it today -----------------

    @Override
    public void draw(Canvas canvas) {
        if (capturing && mode != MODE_A_NOEXCL) {
            excludedViaDraw++;
            if (firstExclusionFrame < 0) firstExclusionFrame = frameCounter;
            return;
        }
        super.draw(canvas);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (capturing && mode != MODE_A_NOEXCL) {
            excludedViaDispatchDraw++;
            if (firstExclusionFrame < 0) firstExclusionFrame = frameCounter;
            return;
        }
        super.dispatchDraw(canvas);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mode == MODE_OFF) return;
        if (canvas instanceof RecordingCanvas && captureNode.hasDisplayList()) {
            ((RecordingCanvas) canvas).drawRenderNode(captureNode);
        }
    }

    // --- stats -------------------------------------------------------------------------

    private void record(long ns) {
        if (sampleCount < samples.length) samples[sampleCount++] = ns;
    }

    /** Dumps capture-time percentiles to logcat under BLURSPIKE. */
    public String stats(String label) {
        if (sampleCount == 0) {
            return label + " capture=n/a samples=0 exclDraw=" + excludedViaDraw
                    + " exclDispatch=" + excludedViaDispatchDraw + " firstExclFrame=" + firstExclusionFrame;
        }
        long[] s = Arrays.copyOf(samples, sampleCount);
        Arrays.sort(s);
        String msg = label
                + " samples=" + sampleCount
                + " captureMedian=" + fmt(s[sampleCount / 2])
                + " captureP90=" + fmt(s[(int) (sampleCount * 0.90)])
                + " captureP99=" + fmt(s[Math.min(sampleCount - 1, (int) (sampleCount * 0.99))])
                + " captureMax=" + fmt(s[sampleCount - 1])
                + " exclDraw=" + excludedViaDraw
                + " exclDispatch=" + excludedViaDispatchDraw
                + " firstExclFrame=" + firstExclusionFrame
                + " captureFrames=" + frameCounter;
        Log.i(TAG, msg);
        return msg;
    }

    private static String fmt(long ns) {
        return String.format(java.util.Locale.US, "%.2fms", ns / 1_000_000f);
    }
}
