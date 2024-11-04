#import <UIKit/UIKit.h>
#import <React/RCTView.h>
#import <React/RCTComponent.h>

@interface SajjadBlurOverlay : RCTView

@property (nonatomic, strong) NSNumber *radius;
@property (nonatomic, copy) NSString *blurStyle; // Use copy for NSString to avoid issues with mutable strings
@property (nonatomic, assign) BOOL vibrant;
@property (nonatomic, copy) RCTBubblingEventBlock onPress;
@property (nonatomic, strong) NSNumber *brightness;
@property (nonatomic, strong) NSNumber *downsampling;

@end