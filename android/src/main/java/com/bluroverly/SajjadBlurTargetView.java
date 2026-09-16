package com.bluroverly;

import android.content.Context;
import android.graphics.Canvas;

import androidx.annotation.Nullable;

import com.facebook.react.views.view.ReactViewGroup;

/**
 * The subtree a live overlay captures: {@code <BlurTarget />}.
 *
 * <p>Phase 0 established that an overlay cannot capture an ancestor of itself
 * — on a hardware canvas the self-exclusion trick either blanks the overlay or
 * makes HWUI drop the backdrop entirely — so the content to blur has to live in
 * a subtree that does not contain the overlay. This is that subtree.
 *
 * <p>Otherwise it is a plain view: it lays out, draws and receives touches like
 * any other. All it adds is registration and a draw counter.
 */
public class SajjadBlurTargetView extends ReactViewGroup {

  static final String DEFAULT_ID = "default";

  private String blurTargetId = DEFAULT_ID;

  /** Set while an overlay is recording us, so our own capture is not counted. */
  private boolean capturing;

  /**
   * Bumped whenever HWUI rebuilds this view's own display list.
   *
   * <p>That is the only moment a capture has to be re-recorded: a capture node
   * holds live references to the child render nodes, so content changes deeper
   * in the subtree reach the blur without us doing anything. What does not
   * propagate is a change to this view's own display list — a child appearing,
   * moving, or a scroll offset recorded here — and that is exactly when this
   * counter moves.
   */
  private int drawGeneration;

  public SajjadBlurTargetView(Context context) {
    super(context);
  }

  public void setBlurTargetId(@Nullable String id) {
    final String next = id == null || id.isEmpty() ? DEFAULT_ID : id;

    if (!next.equals(blurTargetId)) {
      BlurTargetRegistry.unregister(this);
      blurTargetId = next;

      if (isAttachedToWindow()) {
        BlurTargetRegistry.register(this);
      }
    }
  }

  String getBlurTargetId() {
    return blurTargetId;
  }

  int getDrawGeneration() {
    return drawGeneration;
  }

  void setCapturing(boolean capturing) {
    this.capturing = capturing;
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    BlurTargetRegistry.register(this);
  }

  @Override
  protected void onDetachedFromWindow() {
    BlurTargetRegistry.unregister(this);
    super.onDetachedFromWindow();
  }

  // Note the difference from the overlay's own overrides: these never skip the
  // draw. Early-returning here would be the variant A failure all over again.

  @Override
  public void draw(Canvas canvas) {
    if (!capturing) {
      drawGeneration++;
    }

    super.draw(canvas);
  }

  @Override
  protected void dispatchDraw(Canvas canvas) {
    if (!capturing) {
      drawGeneration++;
    }

    super.dispatchDraw(canvas);
  }
}
