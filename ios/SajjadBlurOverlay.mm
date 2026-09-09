#import "SajjadBlurOverlay.h"

#ifdef RCT_NEW_ARCH_ENABLED
#import <React/RCTConversions.h>

#import <react/renderer/components/RNBlurOverlaySpec/ComponentDescriptors.h>
#import <react/renderer/components/RNBlurOverlaySpec/Props.h>
#import <react/renderer/components/RNBlurOverlaySpec/RCTComponentViewHelpers.h>

using namespace facebook::react;
#endif

static NSString *const SajjadBlurOverlayDefaultStyle = @"light";

@implementation SajjadBlurOverlay {
  UIVisualEffectView *_blurView;
  UIVisualEffectView *_vibrancyView;
  /** Hosts the React children, above the blur. */
  UIView *_containerView;
  NSString *_currentBlurStyle;
  BOOL _currentVibrant;
}

#pragma mark - Lifecycle

- (instancetype)initWithFrame:(CGRect)frame
{
  if (self = [super initWithFrame:frame]) {
#ifdef RCT_NEW_ARCH_ENABLED
    static const auto defaultProps = std::make_shared<const SajjadBlurOverlayProps>();
    _props = defaultProps;
#endif

    _currentBlurStyle = SajjadBlurOverlayDefaultStyle;
    _currentVibrant = NO;

    _containerView = [[UIView alloc] initWithFrame:self.bounds];
    _containerView.backgroundColor = UIColor.clearColor;
    _containerView.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;

#ifdef RCT_NEW_ARCH_ENABLED
    // Fabric mounts children into `contentView` and keeps its frame in sync,
    // which is exactly what the container needs to be.
    self.contentView = _containerView;
#else
    [self addSubview:_containerView];
#endif

    [self applyEffect];
  }

  return self;
}

- (void)layoutSubviews
{
  [super layoutSubviews];

  _blurView.frame = self.bounds;
  _vibrancyView.frame = self.bounds;
#ifndef RCT_NEW_ARCH_ENABLED
  _containerView.frame = self.bounds;
#endif
}

#pragma mark - Effect

/** Rebuilds the effect views for the current `blurStyle` / `vibrant` pair. */
- (void)applyEffect
{
  UIBlurEffect *blurEffect = [self blurEffectForStyle:_currentBlurStyle];

  [_blurView removeFromSuperview];
  [_vibrancyView removeFromSuperview];
  _vibrancyView = nil;

  _blurView = [[UIVisualEffectView alloc] initWithEffect:blurEffect];
  _blurView.frame = self.bounds;
  _blurView.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
  // Index 0 keeps the blur behind the children.
  [self insertSubview:_blurView atIndex:0];

  if (_currentVibrant) {
    UIVibrancyEffect *vibrancyEffect = [UIVibrancyEffect effectForBlurEffect:blurEffect];
    _vibrancyView = [[UIVisualEffectView alloc] initWithEffect:vibrancyEffect];
    _vibrancyView.frame = self.bounds;
    _vibrancyView.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;

    [_blurView.contentView addSubview:_vibrancyView];
    // Vibrancy only affects content inside its own content view.
    [_vibrancyView.contentView addSubview:_containerView];
  } else {
    // Re-adding moves the container back on top of the blur.
    [self addSubview:_containerView];
  }
}

- (UIBlurEffect *)blurEffectForStyle:(NSString *)style
{
  if ([style isEqualToString:@"extraLight"]) {
    return [UIBlurEffect effectWithStyle:UIBlurEffectStyleExtraLight];
  }

  if ([style isEqualToString:@"dark"]) {
    return [UIBlurEffect effectWithStyle:UIBlurEffectStyleDark];
  }

  if ([style isEqualToString:@"regular"]) {
    return [UIBlurEffect effectWithStyle:UIBlurEffectStyleRegular];
  }

  if ([style isEqualToString:@"prominent"]) {
    return [UIBlurEffect effectWithStyle:UIBlurEffectStyleProminent];
  }

  if (@available(iOS 13.0, *)) {
    if ([style isEqualToString:@"systemUltraThinMaterial"]) {
      return [UIBlurEffect effectWithStyle:UIBlurEffectStyleSystemUltraThinMaterial];
    }

    if ([style isEqualToString:@"systemThinMaterial"]) {
      return [UIBlurEffect effectWithStyle:UIBlurEffectStyleSystemThinMaterial];
    }

    if ([style isEqualToString:@"systemMaterial"]) {
      return [UIBlurEffect effectWithStyle:UIBlurEffectStyleSystemMaterial];
    }

    if ([style isEqualToString:@"systemThickMaterial"]) {
      return [UIBlurEffect effectWithStyle:UIBlurEffectStyleSystemThickMaterial];
    }

    if ([style isEqualToString:@"systemChromeMaterial"]) {
      return [UIBlurEffect effectWithStyle:UIBlurEffectStyleSystemChromeMaterial];
    }
  }

  return [UIBlurEffect effectWithStyle:UIBlurEffectStyleLight];
}

- (void)setBlurStyle:(nullable NSString *)blurStyle vibrant:(BOOL)vibrant
{
  NSString *style = blurStyle.length > 0 ? blurStyle : SajjadBlurOverlayDefaultStyle;

  if ([style isEqualToString:_currentBlurStyle] && vibrant == _currentVibrant) {
    return;
  }

  _currentBlurStyle = style;
  _currentVibrant = vibrant;

  [self applyEffect];
}

#pragma mark - New architecture

#ifdef RCT_NEW_ARCH_ENABLED

+ (ComponentDescriptorProvider)componentDescriptorProvider
{
  return concreteComponentDescriptorProvider<SajjadBlurOverlayComponentDescriptor>();
}

- (void)updateProps:(const Props::Shared &)props oldProps:(const Props::Shared &)oldProps
{
  const auto &newViewProps = *std::static_pointer_cast<const SajjadBlurOverlayProps>(props);

  [self setBlurStyle:RCTNSStringFromStringNilIfEmpty(newViewProps.blurStyle)
             vibrant:newViewProps.vibrant];

  [super updateProps:props oldProps:oldProps];
}

- (void)prepareForRecycle
{
  [super prepareForRecycle];

  static const auto defaultProps = std::make_shared<const SajjadBlurOverlayProps>();
  _props = defaultProps;

  [self setBlurStyle:SajjadBlurOverlayDefaultStyle vibrant:NO];
}

#else

#pragma mark - Legacy architecture

- (void)setBlurStyle:(nullable NSString *)blurStyle
{
  [self setBlurStyle:blurStyle vibrant:_currentVibrant];
}

- (nullable NSString *)blurStyle
{
  return _currentBlurStyle;
}

- (void)setVibrant:(BOOL)vibrant
{
  [self setBlurStyle:_currentBlurStyle vibrant:vibrant];
}

- (BOOL)vibrant
{
  return _currentVibrant;
}

- (void)insertReactSubview:(UIView *)subview atIndex:(NSInteger)atIndex
{
  [super insertReactSubview:subview atIndex:atIndex];
  [_containerView insertSubview:subview atIndex:atIndex];
}

- (void)removeReactSubview:(UIView *)subview
{
  [super removeReactSubview:subview];
  [subview removeFromSuperview];
}

- (void)didUpdateReactSubviews
{
  // Children are mounted into `_containerView` above, so the default
  // implementation must not move them back onto `self`.
}

#endif

@end
