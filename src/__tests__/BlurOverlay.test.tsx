import { act, render, screen } from '@testing-library/react-native';
import { Platform, Text } from 'react-native';

import BlurOverlay, { BlurTarget, closeOverlay, openOverlay } from '../index';
import NativeBlurOverlay from '../SajjadBlurOverlayNativeComponent';
import NativeBlurTarget from '../SajjadBlurTargetNativeComponent';

/**
 * The nearest ancestor that claims the touch responder, i.e. the wrapper that
 * keeps presses on the children from reaching the backdrop.
 */
type Instance = ReturnType<typeof screen.getByText>;

const responderAncestorOf = (node: Instance | null) => {
  for (let current = node; current; current = current.parent) {
    if (typeof current.props.onStartShouldSetResponder === 'function') {
      return current;
    }
  }

  return null;
};

const CONTENT = 'blurred content';

const children = <Text>{CONTENT}</Text>;

describe('<BlurOverlay />', () => {
  it('renders nothing until it is opened', () => {
    render(<BlurOverlay fadeDuration={0}>{children}</BlurOverlay>);

    expect(screen.queryByText(CONTENT)).toBeNull();

    act(() => openOverlay());

    expect(screen.getByText(CONTENT)).toBeOnTheScreen();
  });

  it('opens and closes without an id, matching the documented usage', () => {
    // Regression test for #8: openOverlay() with no argument used to be a
    // no-op for an overlay mounted without an id.
    render(<BlurOverlay fadeDuration={0}>{children}</BlurOverlay>);

    act(() => openOverlay());
    expect(screen.getByText(CONTENT)).toBeOnTheScreen();

    act(() => closeOverlay());
    expect(screen.queryByText(CONTENT)).toBeNull();
  });

  it('only reacts to its own id when several overlays are mounted', () => {
    render(
      <>
        <BlurOverlay id="first" fadeDuration={0}>
          <Text>first</Text>
        </BlurOverlay>
        <BlurOverlay id="second" fadeDuration={0}>
          <Text>second</Text>
        </BlurOverlay>
      </>
    );

    act(() => openOverlay('second'));

    expect(screen.queryByText('first')).toBeNull();
    expect(screen.getByText('second')).toBeOnTheScreen();
  });

  it('still accepts the deprecated idBlur prop', () => {
    render(
      <BlurOverlay idBlur="ID_BLUR" fadeDuration={0}>
        {children}
      </BlurOverlay>
    );

    act(() => openOverlay('ID_BLUR'));

    expect(screen.getByText(CONTENT)).toBeOnTheScreen();
  });

  it('warns instead of silently doing nothing on an unknown id', () => {
    const warn = jest.spyOn(console, 'warn').mockImplementation(() => {});

    openOverlay('not-mounted');

    expect(warn).toHaveBeenCalledWith(
      expect.stringContaining('no mounted <BlurOverlay />')
    );

    warn.mockRestore();
  });

  it('stays visible for as long as the visible prop says so', () => {
    // Regression test for #22: there was no way to keep the overlay up.
    render(
      <BlurOverlay visible fadeDuration={0}>
        {children}
      </BlurOverlay>
    );

    expect(screen.getByText(CONTENT)).toBeOnTheScreen();

    // A controlled overlay deliberately ignores the imperative API, and says
    // so through a warning.
    const warn = jest.spyOn(console, 'warn').mockImplementation(() => {});

    act(() => closeOverlay());

    expect(screen.getByText(CONTENT)).toBeOnTheScreen();
    expect(warn).toHaveBeenCalled();

    warn.mockRestore();
  });

  it('follows the visible prop when it changes', () => {
    const view = render(
      <BlurOverlay visible={false} fadeDuration={0}>
        {children}
      </BlurOverlay>
    );

    expect(screen.queryByText(CONTENT)).toBeNull();

    view.update(
      <BlurOverlay visible fadeDuration={0}>
        {children}
      </BlurOverlay>
    );

    expect(screen.getByText(CONTENT)).toBeOnTheScreen();
  });

  it('exposes open and close on its ref', () => {
    const ref = { current: null } as { current: null | { open: () => void; close: () => void } };

    render(
      <BlurOverlay ref={ref} fadeDuration={0}>
        {children}
      </BlurOverlay>
    );

    act(() => ref.current?.open());
    expect(screen.getByText(CONTENT)).toBeOnTheScreen();

    act(() => ref.current?.close());
    expect(screen.queryByText(CONTENT)).toBeNull();
  });

  it('ignores the ref API on a controlled overlay', () => {
    const warn = jest.spyOn(console, 'warn').mockImplementation(() => {});
    const ref = { current: null } as {
      current: null | { open: () => void; close: () => void };
    };

    render(
      <BlurOverlay ref={ref} visible fadeDuration={0}>
        {children}
      </BlurOverlay>
    );

    act(() => ref.current?.close());

    expect(screen.getByText(CONTENT)).toBeOnTheScreen();
    expect(warn).toHaveBeenCalledWith(
      expect.stringContaining('driven by its `visible` prop')
    );

    warn.mockRestore();
  });

  it('fades in and out over fadeDuration', () => {
    jest.useFakeTimers();

    const onShow = jest.fn();
    const onHide = jest.fn();

    render(
      <BlurOverlay fadeDuration={300} onShow={onShow} onHide={onHide}>
        {children}
      </BlurOverlay>
    );

    act(() => openOverlay());
    expect(onShow).not.toHaveBeenCalled();

    act(() => jest.advanceTimersByTime(400));
    expect(onShow).toHaveBeenCalledTimes(1);

    act(() => closeOverlay());
    expect(screen.getByText(CONTENT)).toBeOnTheScreen();

    act(() => jest.advanceTimersByTime(400));
    expect(onHide).toHaveBeenCalledTimes(1);
    expect(screen.queryByText(CONTENT)).toBeNull();

    jest.useRealTimers();
  });

  it('keeps presses on the children away from the backdrop', () => {
    // Regression test for #21: pressing the overlay's content used to trigger
    // onPress, which closed the overlay.
    const onPress = jest.fn();

    render(
      <BlurOverlay visible onPress={onPress} fadeDuration={0}>
        {children}
      </BlurOverlay>
    );

    const content = responderAncestorOf(screen.getByText(CONTENT));

    expect(content?.props.onStartShouldSetResponder()).toBe(true);
    expect(onPress).not.toHaveBeenCalled();
  });

  it('lets presses through to the backdrop with closeOnChildPress', () => {
    const onPress = jest.fn();

    render(
      <BlurOverlay visible onPress={onPress} closeOnChildPress fadeDuration={0}>
        {children}
      </BlurOverlay>
    );

    expect(responderAncestorOf(screen.getByText(CONTENT))).toBeNull();
    expect(onPress).not.toHaveBeenCalled();
  });

  it('calls onPress when the backdrop is pressed', () => {
    const onPress = jest.fn();

    render(
      <BlurOverlay visible onPress={onPress} fadeDuration={0}>
        {children}
      </BlurOverlay>
    );

    screen.getByRole('button', { name: 'Close overlay' }).props.onClick?.();

    expect(onPress).toHaveBeenCalledTimes(1);
  });
});

describe('live blur', () => {
  const overlayProps = () =>
    screen.UNSAFE_getByType(NativeBlurOverlay as never).props;

  it('stays on the snapshot blur unless asked for a live one', () => {
    render(
      <BlurOverlay visible fadeDuration={0}>
        {children}
      </BlurOverlay>
    );

    expect(overlayProps()).toMatchObject({
      blurMode: 'snapshot',
      blurTargetId: 'default',
      // A snapshot is frozen unless it is explicitly asked not to be, which is
      // what every 3.0 app already gets.
      snapshotUpdateFps: 0,
    });
  });

  it('passes a periodic re-blur through to the native view', () => {
    render(
      <BlurOverlay visible fadeDuration={0} snapshotUpdateFps={15}>
        {children}
      </BlurOverlay>
    );

    expect(overlayProps()).toMatchObject({
      blurMode: 'snapshot',
      snapshotUpdateFps: 15,
    });
  });

  it('passes the live settings through to the native view', () => {
    render(
      <BlurOverlay
        visible
        fadeDuration={0}
        blurMode="live"
        blurTargetId="list"
        maxUpdateFps={0}
        captureOutset={24}
      >
        {children}
      </BlurOverlay>
    );

    expect(overlayProps()).toMatchObject({
      blurMode: 'live',
      blurTargetId: 'list',
      maxUpdateFps: 0,
      captureOutset: 24,
    });
  });

  it('downsamples a live blur by default, and a snapshot not at all', () => {
    // Phase 0 measured RenderEffect at ~2ms on a Mali-G52 at full resolution
    // against under 1ms at half, so the per-frame path starts at half.
    render(
      <BlurOverlay visible fadeDuration={0} blurMode="live">
        {children}
      </BlurOverlay>
    );

    expect(overlayProps().downsampling).toBe(2);

    screen.rerender(
      <BlurOverlay visible fadeDuration={0}>
        {children}
      </BlurOverlay>
    );

    expect(overlayProps().downsampling).toBe(1);
  });

  it('lets an explicit downsampling win over the live default', () => {
    render(
      <BlurOverlay visible fadeDuration={0} blurMode="live" downsampling={1}>
        {children}
      </BlurOverlay>
    );

    expect(overlayProps().downsampling).toBe(1);
  });
});

describe('<BlurTarget />', () => {
  const platform = Platform as { OS: string };
  const original = platform.OS;

  afterEach(() => {
    platform.OS = original;
  });

  it('renders its children like any other view', () => {
    render(
      <BlurTarget>
        <Text>{CONTENT}</Text>
      </BlurTarget>
    );

    expect(screen.getByText(CONTENT)).toBeOnTheScreen();
  });

  it('is a plain view off Android, where the overlay is already live', () => {
    platform.OS = 'ios';

    render(
      <BlurTarget>
        <Text>{CONTENT}</Text>
      </BlurTarget>
    );

    expect(screen.UNSAFE_queryByType(NativeBlurTarget as never)).toBeNull();
  });

  it('registers under its id on Android', () => {
    platform.OS = 'android';

    render(
      <BlurTarget id="list">
        <Text>{CONTENT}</Text>
      </BlurTarget>
    );

    expect(screen.UNSAFE_getByType(NativeBlurTarget as never).props).toMatchObject(
      { blurTargetId: 'list' }
    );
  });

  it('defaults to the same id the overlay looks for', () => {
    platform.OS = 'android';

    render(<BlurTarget />);

    expect(screen.UNSAFE_getByType(NativeBlurTarget as never).props).toMatchObject(
      { blurTargetId: 'default' }
    );
  });
});
