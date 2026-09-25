#import "SajjadBlurOverlayManager.h"

#ifndef RCT_NEW_ARCH_ENABLED

// Imported inside the guard on purpose: on the new architecture the view
// header derives from RCTViewComponentView and pulls in C++, which cannot be
// compiled as part of this Objective-C file.
#import "SajjadBlurOverlay.h"

@implementation SajjadBlurOverlayManager

RCT_EXPORT_MODULE(SajjadBlurOverlay)

- (UIView *)view
{
  return [SajjadBlurOverlay new];
}

RCT_EXPORT_VIEW_PROPERTY(blurStyle, NSString)
RCT_EXPORT_VIEW_PROPERTY(vibrant, BOOL)
RCT_EXPORT_VIEW_PROPERTY(blurMode, NSString)

// Android-only props, accepted so the same JS props work on both platforms.
RCT_EXPORT_VIEW_PROPERTY(radius, NSNumber)
RCT_EXPORT_VIEW_PROPERTY(brightness, NSNumber)
RCT_EXPORT_VIEW_PROPERTY(downsampling, NSNumber)

@end

#endif
