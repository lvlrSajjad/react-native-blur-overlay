package com.bluroverly;

import androidx.annotation.Nullable;

import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.uimanager.BackgroundStyleApplicator;
import com.facebook.react.uimanager.LengthPercentage;
import com.facebook.react.uimanager.LengthPercentageType;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.ViewGroupManager;
import com.facebook.react.uimanager.ViewManagerDelegate;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.uimanager.annotations.ReactPropGroup;
import com.facebook.react.uimanager.style.BorderRadiusProp;
import com.facebook.react.uimanager.style.LogicalEdge;
import com.facebook.react.viewmanagers.SajjadBlurOverlayManagerDelegate;
import com.facebook.react.viewmanagers.SajjadBlurOverlayManagerInterface;

import java.util.HashMap;
import java.util.Map;

/**
 * View manager for {@code <BlurOverlay />}. Implementing the Codegen interface
 * keeps it working on both the new architecture (through the delegate) and the
 * legacy one (through the {@code @ReactProp} annotations).
 */
@ReactModule(name = SajjadBlurOverlayManager.NAME)
public class SajjadBlurOverlayManager extends ViewGroupManager<SajjadBlurOverlayView>
    implements SajjadBlurOverlayManagerInterface<SajjadBlurOverlayView> {

  public static final String NAME = "SajjadBlurOverlay";

  private final ViewManagerDelegate<SajjadBlurOverlayView> delegate =
      new SajjadBlurOverlayManagerDelegate<SajjadBlurOverlayView, SajjadBlurOverlayManager>(this) {
        @Override
        public void setProperty(
            SajjadBlurOverlayView view, String propName, @Nullable Object value) {
          // The new architecture hands the delegate every style prop on the
          // view, which is the only place the border props can be caught —
          // see setBorderProp below for why they need catching at all.
          if (!setBorderProp(view, propName, value)) {
            super.setProperty(view, propName, value);
          }
        }
      };

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  protected ViewManagerDelegate<SajjadBlurOverlayView> getDelegate() {
    return delegate;
  }

  @Override
  public SajjadBlurOverlayView createViewInstance(ThemedReactContext context) {
    return new SajjadBlurOverlayView(context);
  }

  @Override
  public void onDropViewInstance(SajjadBlurOverlayView view) {
    view.reset();
    super.onDropViewInstance(view);
  }

  @Override
  @ReactProp(name = "radius", defaultInt = 20)
  public void setRadius(SajjadBlurOverlayView view, int value) {
    view.setBlurRadius(value);
  }

  @Override
  @ReactProp(name = "downsampling", defaultFloat = 1f)
  public void setDownsampling(SajjadBlurOverlayView view, float value) {
    view.setDownsamplingFactor(value);
  }

  @Override
  @ReactProp(name = "brightness", defaultFloat = 0f)
  public void setBrightness(SajjadBlurOverlayView view, float value) {
    view.setBrightnessOffset(value);
  }

  @Override
  @ReactProp(name = "saturation", defaultFloat = 1f)
  public void setSaturation(SajjadBlurOverlayView view, float value) {
    view.setSaturation(value);
  }

  @Override
  @ReactProp(name = "blurMode")
  public void setBlurMode(SajjadBlurOverlayView view, @Nullable String value) {
    view.setBlurMode(value);
  }

  @Override
  @ReactProp(name = "blurTargetId")
  public void setBlurTargetId(SajjadBlurOverlayView view, @Nullable String value) {
    view.setBlurTargetId(value);
  }

  @Override
  @ReactProp(name = "maxUpdateFps", defaultInt = 30)
  public void setMaxUpdateFps(SajjadBlurOverlayView view, int value) {
    view.setMaxUpdateFps(value);
  }

  @Override
  @ReactProp(name = "captureOutset", defaultFloat = 0f)
  public void setCaptureOutset(SajjadBlurOverlayView view, float value) {
    view.setCaptureOutset(value);
  }

  @Override
  @ReactProp(name = "snapshotUpdateFps", defaultInt = 0)
  public void setSnapshotUpdateFps(SajjadBlurOverlayView view, int value) {
    view.setSnapshotUpdateFps(value);
  }

  @Override
  @ReactProp(name = "blurStyle")
  public void setBlurStyle(SajjadBlurOverlayView view, @Nullable String value) {
    // iOS only: Android renders a blurred snapshot rather than a UIBlurEffect.
  }

  @Override
  @ReactProp(name = "vibrant")
  public void setVibrant(SajjadBlurOverlayView view, boolean value) {
    // iOS only.
  }

  // --- border props -------------------------------------------------------
  //
  // React Native applies these from ReactViewManager, which only serves a plain
  // <View>. A custom component gets BaseViewManager instead, where
  // setBorderRadius is a no-op that logs "doesn't support property
  // 'borderRadius'", and where borderWidth and borderColor are dropped without
  // saying anything at all. So the overlay has to ask for them itself.
  //
  // They are applied through BackgroundStyleApplicator, the same API
  // ReactViewManager uses, which leaves the overlay with the same
  // CompositeBackgroundDrawable a <View> would have — and that drawable is what
  // SajjadBlurOverlayView clips its blur to.

  @ReactPropGroup(names = {
    "borderRadius",
    "borderTopLeftRadius",
    "borderTopRightRadius",
    "borderBottomRightRadius",
    "borderBottomLeftRadius",
    "borderTopStartRadius",
    "borderTopEndRadius",
    "borderBottomStartRadius",
    "borderBottomEndRadius",
    "borderStartStartRadius",
    "borderStartEndRadius",
    "borderEndStartRadius",
    "borderEndEndRadius",
  }, defaultFloat = Float.NaN)
  public void setBorderRadius(SajjadBlurOverlayView view, int index, float value) {
    setBorderProp(view, RADIUS_NAMES[index], Float.isNaN(value) ? null : value);
  }

  @ReactPropGroup(names = {
    "borderWidth",
    "borderLeftWidth",
    "borderRightWidth",
    "borderTopWidth",
    "borderBottomWidth",
    "borderStartWidth",
    "borderEndWidth",
  }, defaultFloat = Float.NaN)
  public void setBorderWidth(SajjadBlurOverlayView view, int index, float value) {
    setBorderProp(view, WIDTH_NAMES[index], Float.isNaN(value) ? null : value);
  }

  @ReactPropGroup(names = {
    "borderColor",
    "borderLeftColor",
    "borderRightColor",
    "borderTopColor",
    "borderBottomColor",
    "borderStartColor",
    "borderEndColor",
  }, customType = "Color")
  public void setBorderColor(SajjadBlurOverlayView view, int index, @Nullable Integer value) {
    setBorderProp(view, COLOR_NAMES[index], value);
  }

  /** @return whether the prop was a border prop, and has been applied. */
  private static boolean setBorderProp(
      SajjadBlurOverlayView view, String propName, @Nullable Object value) {
    final BorderRadiusProp corner = RADII.get(propName);

    if (corner != null) {
      BackgroundStyleApplicator.setBorderRadius(view, corner, length(value));
      view.invalidate();
      return true;
    }

    final LogicalEdge widthEdge = WIDTHS.get(propName);

    if (widthEdge != null) {
      BackgroundStyleApplicator.setBorderWidth(view, widthEdge, number(value));
      return true;
    }

    final LogicalEdge colorEdge = COLORS.get(propName);

    if (colorEdge != null) {
      BackgroundStyleApplicator.setBorderColor(view, colorEdge, color(value));
      return true;
    }

    return false;
  }

  /** A radius is a length or a percentage of the overlay's own size. */
  @Nullable
  private static LengthPercentage length(@Nullable Object value) {
    if (value instanceof String) {
      final String text = ((String) value).trim();

      if (text.endsWith("%")) {
        try {
          return new LengthPercentage(
              Float.parseFloat(text.substring(0, text.length() - 1)),
              LengthPercentageType.PERCENT);
        } catch (NumberFormatException error) {
          return null;
        }
      }
    }

    final Float number = number(value);

    return number == null ? null : new LengthPercentage(number, LengthPercentageType.POINT);
  }

  @Nullable
  private static Float number(@Nullable Object value) {
    return value instanceof Number ? ((Number) value).floatValue() : null;
  }

  @Nullable
  private static Integer color(@Nullable Object value) {
    return value instanceof Number ? ((Number) value).intValue() : null;
  }

  private static final String[] RADIUS_NAMES = {
    "borderRadius",
    "borderTopLeftRadius",
    "borderTopRightRadius",
    "borderBottomRightRadius",
    "borderBottomLeftRadius",
    "borderTopStartRadius",
    "borderTopEndRadius",
    "borderBottomStartRadius",
    "borderBottomEndRadius",
    "borderStartStartRadius",
    "borderStartEndRadius",
    "borderEndStartRadius",
    "borderEndEndRadius",
  };

  private static final BorderRadiusProp[] RADIUS_PROPS = {
    BorderRadiusProp.BORDER_RADIUS,
    BorderRadiusProp.BORDER_TOP_LEFT_RADIUS,
    BorderRadiusProp.BORDER_TOP_RIGHT_RADIUS,
    BorderRadiusProp.BORDER_BOTTOM_RIGHT_RADIUS,
    BorderRadiusProp.BORDER_BOTTOM_LEFT_RADIUS,
    BorderRadiusProp.BORDER_TOP_START_RADIUS,
    BorderRadiusProp.BORDER_TOP_END_RADIUS,
    BorderRadiusProp.BORDER_BOTTOM_START_RADIUS,
    BorderRadiusProp.BORDER_BOTTOM_END_RADIUS,
    BorderRadiusProp.BORDER_START_START_RADIUS,
    BorderRadiusProp.BORDER_START_END_RADIUS,
    BorderRadiusProp.BORDER_END_START_RADIUS,
    BorderRadiusProp.BORDER_END_END_RADIUS,
  };

  private static final String[] WIDTH_NAMES = {
    "borderWidth",
    "borderLeftWidth",
    "borderRightWidth",
    "borderTopWidth",
    "borderBottomWidth",
    "borderStartWidth",
    "borderEndWidth",
  };

  private static final String[] COLOR_NAMES = {
    "borderColor",
    "borderLeftColor",
    "borderRightColor",
    "borderTopColor",
    "borderBottomColor",
    "borderStartColor",
    "borderEndColor",
  };

  private static final LogicalEdge[] EDGES = {
    LogicalEdge.ALL,
    LogicalEdge.LEFT,
    LogicalEdge.RIGHT,
    LogicalEdge.TOP,
    LogicalEdge.BOTTOM,
    LogicalEdge.START,
    LogicalEdge.END,
  };

  private static final Map<String, BorderRadiusProp> RADII = new HashMap<>();
  private static final Map<String, LogicalEdge> WIDTHS = new HashMap<>();
  private static final Map<String, LogicalEdge> COLORS = new HashMap<>();

  static {
    for (int i = 0; i < RADIUS_NAMES.length; i++) {
      RADII.put(RADIUS_NAMES[i], RADIUS_PROPS[i]);
    }

    for (int i = 0; i < EDGES.length; i++) {
      WIDTHS.put(WIDTH_NAMES[i], EDGES[i]);
      COLORS.put(COLOR_NAMES[i], EDGES[i]);
    }
  }
}
