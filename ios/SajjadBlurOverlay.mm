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
  /** `blurMode="glass"`: the system's own Liquid Glass, where there is one. */
  BOOL _currentGlass;
  /**
   * The overlay's uniform corner radius in points, for glass to take as its
   * shape. Read from the props rather than the layer: with a visible border and
   * no clipping, React Native draws the border itself and leaves
   * `layer.cornerRadius` at 0.
   */
  CGFloat _glassCornerRadius;
  /** `glassVariant`: YES for `"clear"`, NO for `"regular"`. */
  BOOL _glassClear;
  UIColor *_Nullable _glassTint;
  BOOL _glassInteractive;
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

    // Deliberately not `self.contentView`: React Native clears that when it
    // recycles a component view, which would orphan the container while
    // `betterHitTest:` kept looking for children inside it. Children are
    // routed here by the mounting methods below instead.
    [self addSubview:_containerView];

    [self applyEffect];
  }

  return self;
}

- (void)layoutSubviews
{
  [super layoutSubviews];

  _blurView.frame = self.bounds;
  _vibrancyView.frame = self.bounds;
  _containerView.frame = self.bounds;

  [self applyGlassShape];
}

/**
 * Glass draws its own shape, rim and highlight, so it has to be told the
 * overlay's corners rather than be clipped to them: a clip would cut the rim
 * off. A radius of half the short side or more is a capsule, which iOS 26 has
 * its own configuration for.
 */
- (void)applyGlassShape
{
#if defined(__IPHONE_26_0) && __IPHONE_OS_VERSION_MAX_ALLOWED >= __IPHONE_26_0
  if (@available(iOS 26.0, *)) {
    if (!_currentGlass) {
      return;
    }

    const CGSize size = self.bounds.size;
    const CGFloat half = MIN(size.width, size.height) / 2;

    if (_glassCornerRadius <= 0) {
      _blurView.cornerConfiguration =
          [UICornerConfiguration configurationWithUniformRadius:[UICornerRadius fixedRadius:0]];
    } else if (_glassCornerRadius >= half - 0.5) {
      _blurView.cornerConfiguration = [UICornerConfiguration capsuleConfiguration];
    } else {
      _blurView.cornerConfiguration = [UICornerConfiguration
          configurationWithUniformRadius:[UICornerRadius fixedRadius:_glassCornerRadius]];
    }
  }
#endif
}

#pragma mark - Effect

/** Rebuilds the effect views for the current `blurStyle` / `vibrant` pair. */
- (void)applyEffect
{
  UIBlurEffect *blurEffect = [self blurEffectForStyle:_currentBlurStyle];
  UIVisualEffect *glassEffect = _currentGlass ? [self glassEffect] : nil;

  [_blurView removeFromSuperview];
  [_vibrancyView removeFromSuperview];
  _vibrancyView = nil;

  _blurView = [[UIVisualEffectView alloc] initWithEffect:glassEffect ?: blurEffect];
  _blurView.frame = self.bounds;
  _blurView.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
  // Index 0 keeps the blur behind the children.
  [self insertSubview:_blurView atIndex:0];

  if (glassEffect != nil) {
    // Glass reacts to touches, and treats content for legibility, only inside
    // its own content view — outside it, `interactive` would never see a touch.
    [_blurView.contentView addSubview:_containerView];
  } else if (_currentVibrant) {
    // A vibrancy effect is derived from a blur effect, and glass is not one.
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

/**
 * iOS 26's Liquid Glass, or nil where there is none — an older OS, or an SDK
 * too old to know the class — in which case `glass` is the ordinary blur.
 */
- (nullable UIVisualEffect *)glassEffect
{
#if defined(__IPHONE_26_0) && __IPHONE_OS_VERSION_MAX_ALLOWED >= __IPHONE_26_0
  if (@available(iOS 26.0, *)) {
    UIGlassEffect *glass = [UIGlassEffect
        effectWithStyle:_glassClear ? UIGlassEffectStyleClear : UIGlassEffectStyleRegular];
    glass.tintColor = _glassTint;
    glass.interactive = _glassInteractive;
    return glass;
  }
#endif

  return nil;
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

/** The glass-only props; they rebuild the effect only while it is glass. */
- (void)setGlassClear:(BOOL)clear tint:(nullable UIColor *)tint interactive:(BOOL)interactive
{
  if (clear == _glassClear && interactive == _glassInteractive &&
      (tint == _glassTint || [tint isEqual:_glassTint])) {
    return;
  }

  _glassClear = clear;
  _glassTint = tint;
  _glassInteractive = interactive;

  if (_currentGlass) {
    [self applyEffect];
    [self setNeedsLayout];
  }
}

- (void)setBlurStyle:(nullable NSString *)blurStyle vibrant:(BOOL)vibrant
{
  [self setBlurStyle:blurStyle vibrant:vibrant glass:_currentGlass];
}

- (void)setBlurStyle:(nullable NSString *)blurStyle vibrant:(BOOL)vibrant glass:(BOOL)glass
{
  NSString *style = blurStyle.length > 0 ? blurStyle : SajjadBlurOverlayDefaultStyle;

  if ([style isEqualToString:_currentBlurStyle] && vibrant == _currentVibrant &&
      glass == _currentGlass) {
    return;
  }

  _currentBlurStyle = style;
  _currentVibrant = vibrant;
  _currentGlass = glass;

  [self applyEffect];
  [self setNeedsLayout];
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
             vibrant:newViewProps.vibrant
               glass:newViewProps.blurMode == "glass"];
  [self setGlassClear:newViewProps.glassVariant == "clear"
                 tint:RCTUIColorFromSharedColor(newViewProps.glassTint)
          interactive:newViewProps.interactive];

  [super updateProps:props oldProps:oldProps];
}

- (void)finalizeUpdates:(RNComponentViewUpdateMask)updateMask
{
  [super finalizeUpdates:updateMask];

  // Resolved the way React Native resolves it for its own border drawing, now
  // that both the props and the layout are current. Only a uniform radius has
  // a glass shape; per-corner radii leave it square.
  const auto borderMetrics = _props->resolveBorderMetrics(_layoutMetrics);
  const auto &radii = borderMetrics.borderRadii;
  const bool uniform = radii.topLeft == radii.topRight && radii.topLeft == radii.bottomLeft &&
      radii.topLeft == radii.bottomRight;
  const CGFloat radius = uniform ? (CGFloat)radii.topLeft.horizontal : 0;

  if (radius != _glassCornerRadius) {
    _glassCornerRadius = radius;
    [self setNeedsLayout];
  }
}

- (void)mountChildComponentView:(UIView<RCTComponentViewProtocol> *)childComponentView index:(NSInteger)index
{
  [_containerView insertSubview:childComponentView atIndex:index];
}

- (void)unmountChildComponentView:(UIView<RCTComponentViewProtocol> *)childComponentView index:(NSInteger)index
{
  [childComponentView removeFromSuperview];
}

- (void)prepareForRecycle
{
  [super prepareForRecycle];

  static const auto defaultProps = std::make_shared<const SajjadBlurOverlayProps>();
  _props = defaultProps;

  _glassCornerRadius = 0;
  [self setGlassClear:NO tint:nil interactive:NO];
  [self setBlurStyle:SajjadBlurOverlayDefaultStyle vibrant:NO glass:NO];
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

- (void)setGlassVariant:(nullable NSString *)glassVariant
{
  [self setGlassClear:[glassVariant isEqualToString:@"clear"]
                 tint:_glassTint
          interactive:_glassInteractive];
}

- (nullable NSString *)glassVariant
{
  return _glassClear ? @"clear" : @"regular";
}

- (void)setGlassTint:(nullable UIColor *)glassTint
{
  [self setGlassClear:_glassClear tint:glassTint interactive:_glassInteractive];
}

- (nullable UIColor *)glassTint
{
  return _glassTint;
}

- (void)setInteractive:(BOOL)interactive
{
  [self setGlassClear:_glassClear tint:_glassTint interactive:interactive];
}

- (BOOL)interactive
{
  return _glassInteractive;
}

- (void)setBlurMode:(nullable NSString *)blurMode
{
  [self setBlurStyle:_currentBlurStyle
             vibrant:_currentVibrant
               glass:[blurMode isEqualToString:@"glass"]];
}

- (nullable NSString *)blurMode
{
  return _currentGlass ? @"glass" : nil;
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
