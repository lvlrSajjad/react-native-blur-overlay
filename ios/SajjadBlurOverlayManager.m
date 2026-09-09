#import "SajjadBlurOverlayManager.h"

#import "SajjadBlurOverlay.h"

#ifndef RCT_NEW_ARCH_ENABLED

@implementation SajjadBlurOverlayManager

RCT_EXPORT_MODULE(SajjadBlurOverlay)

- (UIView *)view
{
  return [SajjadBlurOverlay new];
}

RCT_EXPORT_VIEW_PROPERTY(blurStyle, NSString)
RCT_EXPORT_VIEW_PROPERTY(vibrant, BOOL)

// Android-only props, accepted so the same JS props work on both platforms.
RCT_EXPORT_VIEW_PROPERTY(radius, NSNumber)
RCT_EXPORT_VIEW_PROPERTY(brightness, NSNumber)
RCT_EXPORT_VIEW_PROPERTY(downsampling, NSNumber)

@end

#endif
