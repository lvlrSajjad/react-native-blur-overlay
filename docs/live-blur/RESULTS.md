# Live blur — measurements and decisions

Append-only. Each session adds a section; nothing here gets rewritten, because a later
phase may be built on a number recorded earlier.

Record the device, the Android version, the refresh rate, the build type and the exact
command used. A number without those is not reusable.

## Template

```
## Phase N — <what was measured> — YYYY-MM-DD

Device: Pixel 8a, Android 16, 60Hz / 120Hz, release build
Method: <command or harness, e.g. dumpsys gfxinfo framestats over a 10s fling>

| Variant | inputScale | P90 frame (ms) | Janky frames | Notes |
| --- | --- | --- | --- | --- |
| A — root capture + self-exclusion | 0.5 | | | recursive self-blur? yes/no |
| B — scoped BlurTarget | 0.5 | | | |

Decision: <what this means for the plan>
Follow-ups: <anything the next phase must know>
```

## Baseline (already known, 3.0.x)

- `StackBlur` on a 1080×1920 frame: **~43ms on a desktop JVM** (`scripts`-free harness,
  2026-09-09). Well over a frame budget on-device, which is why the shipped Android path
  is a one-time downsampled snapshot rather than anything live.
- Android snapshot capture excludes the overlay's own subtree via the `capturing` flag on
  `draw()`/`dispatchDraw()`, verified visually on an Android 16 emulator: the blurred
  backdrop contains no copy of the overlay or its children.
- Current Android behaviour is confirmed **static**: the snapshot is taken once when the
  overlay mounts and is not refreshed while content scrolls behind it.

---

<!-- New sessions append below this line. -->
