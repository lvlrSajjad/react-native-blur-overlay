#import "SajjjadBlurOverlayManager.h"

@implementation SajjadBlurOverlayManager

RCT_EXPORT_MODULE();

- (UIView *)view
{
    return [[SajjadBlurOverlay alloc] init];
}

RCT_EXPORT_VIEW_PROPERTY(blurStyle, NSString);
RCT_EXPORT_VIEW_PROPERTY(vibrant, BOOL);

@end
