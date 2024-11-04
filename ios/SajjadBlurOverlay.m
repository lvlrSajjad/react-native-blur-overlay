#import "SajjadBlurOverlay.h"

@interface SajjadBlurOverlay ()

@property (nonatomic, strong) UIVisualEffectView *effectsView;

@end

@implementation SajjadBlurOverlay

- (instancetype)init
{
    self = [super init];
    if (self) {
        // Default values for properties
        _blurStyle = @"light"; // default to light if not specified
        _vibrant = NO;

        _onPress = nil;

        UITapGestureRecognizer *tapGesture = [[UITapGestureRecognizer alloc] initWithTarget:self action:@selector(handleTap)];
        [self addGestureRecognizer:tapGesture];

    }
    return self;
}

- (void)handleTap {
    if (self.onPress) {
        self.onPress(@{});
    } else {
        NSLog(@"onPress event is nil");
    }
}

// Setters for properties to trigger layout update on change
- (void)setBlurStyle:(NSString *)blurStyle {
    if (![_blurStyle isEqualToString:blurStyle]) {
        _blurStyle = blurStyle;
        [self updateEffect];
    }
}

- (void)setVibrant:(BOOL)vibrant {
    if (_vibrant != vibrant) {
        _vibrant = vibrant;
        [self updateEffect];
    }
}

- (void)setRadius:(NSNumber *)radius {
    if (![_radius isEqualToNumber:radius]) {
        _radius = radius;
        [self updateEffect]; // Call updateEffect if this property affects the blur effect
    }
}

- (void)setBrightness:(NSNumber *)brightness {
    if (![_brightness isEqualToNumber:brightness]) {
        _brightness = brightness;
        [self updateEffect]; // Call updateEffect if this property affects the visual effect
    }
}

- (void)setDownsampling:(NSNumber *)downsampling {
    if (![_downsampling isEqualToNumber:downsampling]) {
        _downsampling = downsampling;
        [self updateEffect]; // Call updateEffect if this property affects the visual effect
    }
}

- (void)layoutSubviews
{
    [super layoutSubviews];

    if (![self.subviews containsObject:self.effectsView]) {
        [self updateEffect];
    } else {
        self.effectsView.frame = self.bounds;
    }
}

// Helper method to update the effect based on current properties
- (void)updateEffect
{
    [self.effectsView removeFromSuperview]; // Remove any existing effect view

    UIBlurEffect *blurEffect = [self createBlurEffect];
    self.effectsView = [[UIVisualEffectView alloc] initWithEffect:blurEffect];
    [self.effectsView setFrame:self.bounds];
    [self insertSubview:self.effectsView atIndex:[self.subviews count]];

    if (self.vibrant) {
        UIVibrancyEffect *vibrancyEffect = [UIVibrancyEffect effectForBlurEffect:blurEffect];
        UIVisualEffectView *vibrancyEffectView = [[UIVisualEffectView alloc] initWithEffect:vibrancyEffect];
        [vibrancyEffectView setFrame:self.bounds];

        // Add vibrancy effect content view
        [[self.effectsView contentView] addSubview:vibrancyEffectView];
    }
}

// Method to create a blur effect based on the current blur style
- (UIBlurEffect *)createBlurEffect
{
    if ([self.blurStyle isEqualToString:@"extraLight"]) {
        return [UIBlurEffect effectWithStyle:UIBlurEffectStyleExtraLight];
    } else if ([self.blurStyle isEqualToString:@"dark"]) {
        return [UIBlurEffect effectWithStyle:UIBlurEffectStyleDark];
    } else {
        return [UIBlurEffect effectWithStyle:UIBlurEffectStyleLight];
    }
}

@end