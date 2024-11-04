#import "SajjadBlurOverlayManager.h"

@implementation SajjadBlurOverlayManager

RCT_EXPORT_MODULE(SajjadBlurOverlay);

- (UIView *)view
{
    return [[SajjadBlurOverlay alloc] init];
}

RCT_EXPORT_VIEW_PROPERTY(brightness, NSNumber);
RCT_EXPORT_VIEW_PROPERTY(radius, NSNumber);
RCT_EXPORT_VIEW_PROPERTY(downsampling, NSNumber);
RCT_EXPORT_VIEW_PROPERTY(onPress, RCTBubblingEventBlock);

@end