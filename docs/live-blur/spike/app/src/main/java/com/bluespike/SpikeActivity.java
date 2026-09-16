package com.bluespike;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Phase 0 harness. Everything is configured from the launch intent so a run is one
 * adb command:
 *
 *   am start -n com.bluespike/.SpikeActivity \
 *     --es variant B --ef scale 0.5 --ef radius 25 --ei speed 24 --ei durationMs 10000
 */
public class SpikeActivity extends Activity {

    private static final String TAG = "BLURSPIKE";

    private RecyclerView list;
    private BlurPanel panel;
    private TextView hud;

    private String variant = "off";
    private float scale = 0.5f;
    private float radiusDp = 25f;
    private int speedPx = 24;
    private int durationMs = 10000;
    private int scrollDir = 1;

    private long autoScrollFrames;
    private long autoScrollStart;
    private boolean autoScrolling;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        variant = str(getIntent().getStringExtra("variant"), "off");
        scale = getIntent().getFloatExtra("scale", 0.5f);
        radiusDp = getIntent().getFloatExtra("radius", 25f);
        speedPx = getIntent().getIntExtra("speed", 24);
        durationMs = getIntent().getIntExtra("durationMs", 10000);

        FrameLayout root = new FrameLayout(this);

        // Variant B's target: a subtree that deliberately does not contain the panel.
        FrameLayout blurTarget = new FrameLayout(this);
        list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(new TileAdapter());
        list.setHasFixedSize(true);
        blurTarget.addView(list, match());
        root.addView(blurTarget, match());

        panel = new BlurPanel(this);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(getIntent().getIntExtra("panelH", 400)));
        plp.gravity = Gravity.CENTER_VERTICAL;
        plp.leftMargin = dp(16);
        plp.rightMargin = dp(16);
        root.addView(panel, plp);

        // Panel content. The magenta bar is the tell: if the panel ends up inside its own
        // capture, a blurred magenta smear shows up in the backdrop.
        TextView label = new TextView(this);
        label.setText("PANEL");
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setTextColor(Color.WHITE);
        label.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(80));
        llp.gravity = Gravity.CENTER;
        panel.addView(label, llp);

        boolean pill = getIntent().getBooleanExtra("pill", false);
        if (pill) {
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
            llp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            plp.leftMargin = dp(60);
            plp.rightMargin = dp(60);
        }
        View magenta = new View(this);
        magenta.setBackgroundColor(Color.MAGENTA);
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        mlp.gravity = Gravity.BOTTOM;
        if (!pill) panel.addView(magenta, mlp);

        hud = new TextView(this);
        hud.setBackgroundColor(0xCC000000);
        hud.setTextColor(Color.WHITE);
        hud.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hud.setPadding(dp(8), dp(8), dp(8), dp(8));
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.gravity = Gravity.TOP;
        hlp.topMargin = dp(32);
        root.addView(hud, hlp);

        setContentView(root);

        View content = findViewById(android.R.id.content);
        int mode = "A".equalsIgnoreCase(variant) ? BlurPanel.MODE_A
                : "An".equalsIgnoreCase(variant) ? BlurPanel.MODE_A_NOEXCL
                : "G".equalsIgnoreCase(variant) ? BlurPanel.MODE_GLASS
                : "Bc".equalsIgnoreCase(variant) ? BlurPanel.MODE_B_CAPTURE_ONLY
                : "B".equalsIgnoreCase(variant) ? BlurPanel.MODE_B
                : BlurPanel.MODE_OFF;
        View target = (mode == BlurPanel.MODE_A || mode == BlurPanel.MODE_A_NOEXCL)
                ? content : blurTarget;
        float corner = getIntent().getFloatExtra("corner", 32f);
        // narrow band + thick bevel == a FLAT slab with a sharp edge. A wide band ramps
        // the bend across the whole panel and domes it, which is not what iOS does.
        panel.setGlassParams(dp(corner),
                dp(getIntent().getFloatExtra("band", 14f)),
                dp(getIntent().getFloatExtra("thickness", 120f)),
                getIntent().getFloatExtra("ior", 1.45f),
                getIntent().getFloatExtra("specular", 0.55f),
                getIntent().getFloatExtra("rim", 0.35f),
                getIntent().getFloatExtra("tint", 0.03f),
                dp(getIntent().getFloatExtra("bleed", 120f)));
        panel.setElevation(dp(getIntent().getFloatExtra("elevation", 10f)));
        panel.setTintColor((int) getIntent().getLongExtra("tintColor", 0L));
        panel.setEdgeBlur(getIntent().hasExtra("edgeBlur")
                ? dp(getIntent().getFloatExtra("edgeBlur", 0f)) : -1f);
        panel.setFlatness(getIntent().getFloatExtra("flatness", -1f),
                getResources().getDisplayMetrics().density);
        if (mode == BlurPanel.MODE_GLASS) {
            // round the panel itself so its children clip to the same shape the shader
            // is refracting around
            final float r = dp(corner);
            panel.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View v, android.graphics.Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r);
                }
            });
            panel.setClipToOutline(true);
        }
        panel.configure(mode, target, scale, dp(radiusDp));

        hud.setText(config());
        Log.i(TAG, "RUN " + config());

        if (durationMs > 0) {
            panel.postDelayed(this::startAutoScroll, 1200);
        }
    }

    private String config() {
        return "variant=" + variant + " scale=" + scale + " radiusDp=" + radiusDp
                + " speed=" + speedPx + "px/frame"
                + " sdk=" + Build.VERSION.SDK_INT
                + " sdkFull=" + sdkIntFull()
                + " dev=" + Build.MODEL;
    }

    private static String sdkIntFull() {
        try {
            return String.valueOf(Build.VERSION.class.getField("SDK_INT_FULL").getInt(null));
        } catch (Throwable t) {
            return "n/a";
        }
    }

    private void startAutoScroll() {
        panel.resetStats();
        autoScrollFrames = 0;
        autoScrollStart = System.nanoTime();
        autoScrolling = true;
        Log.i(TAG, "AUTOSCROLL_START " + config());
        Choreographer.getInstance().postFrameCallback(this::onFrame);
    }

    private void onFrame(long frameTimeNanos) {
        if (!autoScrolling) return;
        list.scrollBy(0, speedPx * scrollDir);
        autoScrollFrames++;
        long elapsedMs = (System.nanoTime() - autoScrollStart) / 1_000_000L;
        if (elapsedMs >= durationMs) {
            autoScrolling = false;
            float fps = autoScrollFrames * 1000f / Math.max(1, elapsedMs);
            String s = panel.stats("STATS " + config());
            Log.i(TAG, "AUTOSCROLL_END frames=" + autoScrollFrames
                    + " elapsedMs=" + elapsedMs
                    + String.format(java.util.Locale.US, " scrollFps=%.1f", fps));
            hud.setText(config() + "\n" + s
                    + String.format(java.util.Locale.US, "\nscrollFps=%.1f", fps));
            return;
        }
        Choreographer.getInstance().postFrameCallback(this::onFrame);
    }

    private static String str(String v, String d) { return v == null ? d : v; }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // --- a busy, high-contrast list so a stale or recursive blur is visible ------------

    private class TileAdapter extends RecyclerView.Adapter<TileVH> {
        private final int[] palette = {
                0xFFE53935, 0xFF8E24AA, 0xFF1E88E5, 0xFF00897B,
                0xFF7CB342, 0xFFFDD835, 0xFFFB8C00, 0xFF212121,
        };

        @NonNull
        @Override
        public TileVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(SpikeActivity.this);
            tv.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(110)));
            tv.setGravity(Gravity.CENTER);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            tv.setTextColor(Color.WHITE);
            return new TileVH(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull TileVH h, int position) {
            h.itemView.setBackgroundColor(palette[position % palette.length]);
            ((TextView) h.itemView).setText("TILE " + position);
        }

        @Override
        public int getItemCount() { return 5000; }
    }

    private static class TileVH extends RecyclerView.ViewHolder {
        TileVH(View v) { super(v); }
    }
}
