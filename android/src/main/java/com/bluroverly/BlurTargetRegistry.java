package com.bluroverly;

import android.view.View;

import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Where {@code <BlurTarget />} views make themselves findable, so that an
 * overlay elsewhere in the tree can capture one by id.
 *
 * <p>Held weakly: a target that is garbage collected, or one whose manager
 * never got to unregister it, drops out on the next lookup.
 */
final class BlurTargetRegistry {

  private static final CopyOnWriteArrayList<WeakReference<SajjadBlurTargetView>> TARGETS =
      new CopyOnWriteArrayList<>();

  private BlurTargetRegistry() {}

  static void register(SajjadBlurTargetView target) {
    for (WeakReference<SajjadBlurTargetView> reference : TARGETS) {
      if (reference.get() == target) {
        return;
      }
    }

    TARGETS.add(new WeakReference<>(target));
  }

  static void unregister(SajjadBlurTargetView target) {
    for (Iterator<WeakReference<SajjadBlurTargetView>> it = TARGETS.iterator(); it.hasNext(); ) {
      final WeakReference<SajjadBlurTargetView> reference = it.next();
      final SajjadBlurTargetView registered = reference.get();

      if (registered == null || registered == target) {
        TARGETS.remove(reference);
      }
    }
  }

  /**
   * The target with this id in the same window as {@code overlay}, or null.
   *
   * <p>The window check matters: an overlay inside a Dialog (which is what an
   * RN {@code <Modal />} is) cannot capture a target in the activity's window,
   * and capturing one would silently blur the wrong pixels.
   */
  @Nullable
  static SajjadBlurTargetView find(String id, View overlay) {
    final View root = overlay.getRootView();

    for (Iterator<WeakReference<SajjadBlurTargetView>> it = TARGETS.iterator(); it.hasNext(); ) {
      final WeakReference<SajjadBlurTargetView> reference = it.next();
      final SajjadBlurTargetView target = reference.get();

      if (target == null) {
        TARGETS.remove(reference);
        continue;
      }

      if (target.isAttachedToWindow()
          && target.getRootView() == root
          && id.equals(target.getBlurTargetId())) {
        return target;
      }
    }

    return null;
  }
}
