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
    /** Variant B plus the AGSL glass edge (API 33). */
    public static final int MODE_GLASS = 5;

    private static final String TAG = "BLURSPIKE";

    private int mode = MODE_OFF;
    private View target;
    private float inputScale = 0.5f;
    private float radiusPx = 25f;
    private float cornerPx = 96f;
    private float bandPx = 48f;
    private float strengthPx = 28f;
    private float specular = 0.35f;
    private float tint = 0.05f;
    private float rim = 0.45f;
    private float thicknessPx = 140f;
    private float ior = 1.5f;
    private int tintColor = 0;
    /** -1 = use the raw band/thickness/specular. 0..1 = derive them. */
    private float flatness = -1f;
    private float density = 3f;
    /** <0 = derive from the interior frost. Otherwise an absolute rim blur radius. */
    private float edgeBlurPx = -1f;
    private float lineWidthPx = 5f;
    private float bleedPx = 64f;

    /** Set while we are recording the target. Both draw overrides honour it. */
    private boolean capturing;

    private final RenderNode captureNode = new RenderNode("blur-capture");
    /** Glass draws in two layers: a frosted interior, and a sharp refracted rim on top. */
    private final RenderNode frostNode = new RenderNode("glass-frost");
    private final RenderNode rimNode = new RenderNode("glass-rim");
    private Object glass;   // Glass, lazily created; API 33 only
    private RenderEffect cachedFrostEffect;
    private float cachedFrostRadius = -1f;
    private RenderEffect cachedRimChain;
    private float cachedEdge = -1f;
    private RenderEffect cachedRimBase;
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

    public void setTintColor(int argb) { this.tintColor = argb; }

    public void setEdgeBlur(float px) { this.edgeBlurPx = px; }

    public void setLineWidth(float px) { this.lineWidthPx = px; }

    /**
     * One knob instead of three. 1 = a flat slab with a shallow edge (the iOS look),
     * 0 = a deep bevel that domes the whole panel.
     *
     * band and thickness have to move together -- setting them independently mostly
     * produces either nothing visible or a dome -- so a public API should expose this and
     * keep the other two internal.
     *
     * The bevel is absolute dp, not a fraction of the panel: a real sheet of glass has a
     * physical edge thickness, and a bigger pane does not get a bigger bevel. Only the
     * clamp below is size-relative, to stop a tiny panel being all edge.
     */
    public void setFlatness(float f, float density) {
        this.flatness = f;
        this.density = density;
    }

    private void applyFlatness(int w, int h) {
        if (flatness < 0f) return;
        float f = Math.max(0f, Math.min(1f, flatness));
        float minHalf = Math.min(w, h) * 0.5f;
        // Band and depth move in OPPOSITE directions. Shrinking both together (the first
        // mapping) also shrinks the compression ratio, so a high flatness produced a thin
        // rim with almost no distortion left in it. What reads as glass is a THIN band
        // pulling from FAR out -- iOS runs something like 15:1.
        bandPx = Math.min(lerp(20f, 4f, f) * density, minHalf * 0.45f);
        thicknessPx = Math.min(lerp(150f, 70f, f) * density, minHalf * 2.0f);
        specular = lerp(0.9f, 0.55f, f);
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    public void setGlassParams(float cornerPx, float bandPx, float thicknessPx, float ior,
                              float specular, float rim, float tint, float bleedPx) {
        this.cornerPx = cornerPx;
        this.bandPx = bandPx;
        this.thicknessPx = thicknessPx;
        this.ior = ior;
        this.specular = specular;
        this.rim = rim;
        this.tint = tint;
        this.bleedPx = bleedPx;
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
        frostNode.discardDisplayList();
        rimNode.discardDisplayList();
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

        if (mode == MODE_GLASS) applyFlatness(w, h);
        float bleed = (mode == MODE_GLASS) ? bleedPx : 0f;
        int sw = Math.max(1, Math.round((w + 2f * bleed) * inputScale));
        int sh = Math.max(1, Math.round((h + 2f * bleed) * inputScale));

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
            canvas.translate(-(dx - bleed), -(dy - bleed));
            capturing = true;
            target.draw(canvas);
        } finally {
            capturing = false;
            captureNode.endRecording();
        }

        if (mode == MODE_GLASS && Build.VERSION.SDK_INT >= 33) {
            buildGlassLayers(sw, sh, w * inputScale, h * inputScale, bleed * inputScale,
                    inputScale, bleed);
        } else if (mode != MODE_B_CAPTURE_ONLY && Build.VERSION.SDK_INT >= 31) {
            applyBlur(captureNode, Math.max(0.5f, radiusPx * inputScale));
        }

        if (mode == MODE_GLASS) {
            // frostNode/rimNode reference captureNode and carry the scale and bleed shift
            // themselves. Leaving a transform on captureNode too would apply it twice.
            captureNode.setScaleX(1f);
            captureNode.setScaleY(1f);
            captureNode.setTranslationX(0f);
            captureNode.setTranslationY(0f);
        } else {
            captureNode.setPivotX(0f);
            captureNode.setPivotY(0f);
            captureNode.setScaleX(1f / inputScale);
            captureNode.setScaleY(1f / inputScale);
            captureNode.setTranslationX(-bleed);
            captureNode.setTranslationY(-bleed);
        }

        record(System.nanoTime() - t0);
    }

    /**
     * captureNode stays sharp. frostNode blurs it for the interior; rimNode refracts the
     * same sharp pixels for the edge and is transparent everywhere else. Drawing frost then
     * rim is what gets a crisp bevel over a frosted panel out of a single capture.
     */
    @RequiresApi(33)
    private void buildGlassLayers(int cw, int ch, float pw, float ph, float sBleed,
                                  float scale, float bleedUnscaled) {
        captureNode.setRenderEffect(null);

        frostNode.setPosition(0, 0, cw, ch);
        RecordingCanvas fc = frostNode.beginRecording(cw, ch);
        fc.drawRenderNode(captureNode);
        frostNode.endRecording();
        float fr = Math.max(0.5f, radiusPx * scale);
        if (cachedFrostEffect == null || fr != cachedFrostRadius) {
            cachedFrostEffect = RenderEffect.createBlurEffect(fr, fr, Shader.TileMode.CLAMP);
            cachedFrostRadius = fr;
        }
        frostNode.setRenderEffect(cachedFrostEffect);

        rimNode.setPosition(0, 0, cw, ch);
        RecordingCanvas rc = rimNode.beginRecording(cw, ch);
        rc.drawRenderNode(captureNode);
        rimNode.endRecording();
        if (glass == null) glass = new Glass();
        RenderEffect glassEffect = ((Glass) glass).rimEffect(pw, ph, sBleed,
                cornerPx * scale, bandPx * scale, thicknessPx * scale, ior,
                specular, rim, tint, lineWidthPx * scale);
        // The rim gets its OWN blur, separate from the interior frost. At 0 it refracts a
        // sharp image; wound up, the bevel frosts too, which is what real frosted glass
        // does. Blurring the whole capture instead -- one node for both -- is what made the
        // early version look like a smudge, so keep these independent.
        float edge = (edgeBlurPx < 0f ? radiusPx * 0.35f : edgeBlurPx) * scale;
        if (cachedRimChain == null || edge != cachedEdge || glassEffect != cachedRimBase) {
            cachedRimChain = edge <= 0.5f ? glassEffect
                    : RenderEffect.createChainEffect(glassEffect,
                            RenderEffect.createBlurEffect(edge, edge, Shader.TileMode.CLAMP));
            cachedEdge = edge;
            cachedRimBase = glassEffect;
        }
        rimNode.setRenderEffect(cachedRimChain);

        for (RenderNode n : new RenderNode[] { frostNode, rimNode }) {
            n.setPivotX(0f);
            n.setPivotY(0f);
            n.setScaleX(1f / scale);
            n.setScaleY(1f / scale);
            n.setTranslationX(-bleedUnscaled);
            n.setTranslationY(-bleedUnscaled);
        }
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
        if (!(canvas instanceof RecordingCanvas)) return;
        RecordingCanvas rc = (RecordingCanvas) canvas;
        if (mode == MODE_GLASS) {
            if (frostNode.hasDisplayList()) rc.drawRenderNode(frostNode);
            // clipToOutline keeps this inside the rounded rect; the rim is drawn after so
            // it keeps its own tint and stays the brightest thing on the panel
            if (tintColor != 0) rc.drawColor(tintColor);
            if (rimNode.hasDisplayList()) rc.drawRenderNode(rimNode);
        } else if (captureNode.hasDisplayList()) {
            rc.drawRenderNode(captureNode);
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
