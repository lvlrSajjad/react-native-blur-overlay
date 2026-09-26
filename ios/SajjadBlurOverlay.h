#import <UIKit/UIKit.h>

#ifdef RCT_NEW_ARCH_ENABLED
#import <React/RCTViewComponentView.h>
#else
#import <React/RCTView.h>
#import <React/UIView+React.h>
#endif

NS_ASSUME_NONNULL_BEGIN

/**
 * A `UIVisualEffectView` backed overlay that blurs whatever is rendered behind
 * it. React children are mounted into a container view that sits on top of the
 * blur (or inside a `UIVibrancyEffect` view, when `vibrant` is set).
 */
#ifdef RCT_NEW_ARCH_ENABLED
@interface SajjadBlurOverlay : RCTViewComponentView
#else
@interface SajjadBlurOverlay : RCTView

@property (nonatomic, copy, nullable) NSString *blurStyle;
@property (nonatomic, assign) BOOL vibrant;
/** Only `"glass"` means anything on iOS: the system's Liquid Glass, iOS 26+. */
@property (nonatomic, copy, nullable) NSString *blurMode;
/** `"regular"` or `"clear"` Liquid Glass, iOS 26+. */
@property (nonatomic, copy, nullable) NSString *glassVariant;
@property (nonatomic, strong, nullable) UIColor *glassTint;
@property (nonatomic, assign) BOOL interactive;

// Android-only props, accepted here so that the same JS props can be passed on
// both platforms without warnings.
@property (nonatomic, strong, nullable) NSNumber *radius;
@property (nonatomic, strong, nullable) NSNumber *brightness;
@property (nonatomic, strong, nullable) NSNumber *downsampling;
#endif

@end

NS_ASSUME_NONNULL_END
