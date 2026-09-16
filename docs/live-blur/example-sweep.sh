#!/usr/bin/env bash
#
# Frame sweep for Phase 1, run against the example app's release build.
#
# Phase 0's sweep measured plain Views in a standalone harness; this one
# measures the same effect inside React Native's Fabric hierarchy, which is the
# number Phase 1 owes. The variants are picked with launch-intent extras (see
# MainActivity), so nothing depends on tapping the right button.
#
#   HZ=60 REPS=3 SERIAL=R58R85DAD4F ./docs/live-blur/example-sweep.sh
#
# ONLY=live-30fps-ds1 restricts it to one variant, which still gets its adjacent
# baseline.
#
# Three things Phase 0 learned, all of them applied here:
#   - the no-blur baseline runs immediately before each variant, every
#     repetition, because this phone throws environmental jank outliers on the
#     baseline as often as on a variant;
#   - gfxinfo is reset a few seconds after launch, so process start and the
#     list's first layout are not charged to the variant;
#   - the refresh rate is pinned rather than trusted, and restored afterwards.
set -euo pipefail

PKG=com.bluroverlayexample
ACT="$PKG/.MainActivity"
HZ="${HZ:-60}"
REPS="${REPS:-3}"
SWIPES="${SWIPES:-10}"
SETTLE="${SETTLE:-5}"
OUT="${OUT:-/tmp/phase1-sweep-${HZ}hz.tsv}"

adb() { command adb ${SERIAL:+-s "$SERIAL"} "$@"; }

VARIANTS=(
  "off|--ez panel false"
  "snapshot|--ez panel true --es blurMode snapshot --ei downsampling 2"
  "live-30fps-ds2|--ez panel true --es blurMode live --ei maxUpdateFps 30 --ei downsampling 2"
  "live-30fps-ds1|--ez panel true --es blurMode live --ei maxUpdateFps 30 --ei downsampling 1"
  "live-every-ds2|--ez panel true --es blurMode live --ei maxUpdateFps 0 --ei downsampling 2"
  "live-every-ds1|--ez panel true --es blurMode live --ei maxUpdateFps 0 --ei downsampling 1"
)

ORIGINAL_MIN="$(adb shell settings get system min_refresh_rate | tr -d '\r')"
ORIGINAL_PEAK="$(adb shell settings get system peak_refresh_rate | tr -d '\r')"

restore() {
  echo "Restoring the refresh rate to ${ORIGINAL_MIN}/${ORIGINAL_PEAK}." >&2
  adb shell settings put system min_refresh_rate "$ORIGINAL_MIN" || true
  adb shell settings put system peak_refresh_rate "$ORIGINAL_PEAK" || true
  adb shell am force-stop "$PKG" || true
}

trap restore EXIT

adb shell settings put system min_refresh_rate "$HZ"
adb shell settings put system peak_refresh_rate "$HZ"
echo "Pinned to $(adb shell settings get system min_refresh_rate | tr -d '\r')Hz." >&2

scroll() {
  local i
  for i in $(seq 1 "$SWIPES"); do
    if (( i % 2 )); then
      # Clear of the glass panel, which sits across the bottom quarter.
      adb shell input swipe 360 1050 360 250 900
    else
      adb shell input swipe 360 250 360 1050 900
    fi
  done
}

measure() {
  adb shell dumpsys gfxinfo "$PKG" |
    awk '
      /^[[:space:]]*Total frames rendered:/ { total = $NF }
      /^[[:space:]]*Janky frames:/          { janky = $3 " " $4 }
      /^[[:space:]]*50th percentile:/       { p50 = $NF }
      /^[[:space:]]*90th percentile:/       { p90 = $NF }
      /^[[:space:]]*95th percentile:/       { p95 = $NF }
      /^[[:space:]]*99th percentile:/       { p99 = $NF }
      END { printf "%s\t%s\t%s\t%s\t%s\t%s", total, janky, p50, p90, p95, p99 }
    '
}

run() {
  local name="$1" rep="$2"
  shift 2

  adb shell am force-stop "$PKG"
  # shellcheck disable=SC2086
  adb shell am start -n "$ACT" $* > /dev/null
  sleep "$SETTLE"
  adb shell dumpsys gfxinfo "$PKG" reset > /dev/null
  scroll
  printf '%s\t%s\t%s\t%s\n' "$HZ" "$rep" "$name" "$(measure)" | tee -a "$OUT"
}

printf 'hz\trep\tvariant\tframes\tjanky\tp50\tp90\tp95\tp99\n' | tee "$OUT"

for rep in $(seq 1 "$REPS"); do
  for entry in "${VARIANTS[@]}"; do
    name="${entry%%|*}"
    extras="${entry#*|}"

    if [ -n "${ONLY:-}" ] && [ "$name" != "$ONLY" ]; then
      continue
    fi

    if [ "$name" != "off" ]; then
      # An adjacent baseline is the only thing that makes an outlier dismissible.
      run "off" "$rep" --ez panel false
    fi

    run "$name" "$rep" $extras
  done
done

echo "Wrote $OUT" >&2
