#!/usr/bin/env bash
#
# Frame sweep for Phase 6: what blurMode="glass" costs, on the example app's
# floating capsule tab bar, against snapshot and live on the same bar.
#
# Same harness as phase3-sweep.sh and example-sweep.sh; read those first. The
# variant list is the difference, and SHUFFLE is on by default: the Phase 1
# anomalies had a fixed run order as their obvious confound.
#
#   HZ=60 REPS=3 SERIAL=R58R85DAD4F ./docs/live-blur/phase6-sweep.sh
#
# The tab bar pins its own blur (3dp radius, downsampling 2) so every variant
# blurs the same amount; only the mode changes. Covering all three modes on the
# final build is also what lets this double as the consolidated sweep Phase 5
# has been waiting for since Phase 3.
#
set -euo pipefail

PKG=com.bluroverlayexample
ACT="$PKG/.MainActivity"
HZ="${HZ:-60}"
REPS="${REPS:-3}"
SWIPES="${SWIPES:-10}"
SETTLE="${SETTLE:-5}"
OUT="${OUT:-/tmp/phase6-sweep-${HZ}hz.tsv}"
SHUFFLE="${SHUFFLE-1}"

adb() { command adb ${SERIAL:+-s "$SERIAL"} "$@"; }

VARIANTS=(
  "off|--ez tabBar false"
  "snapshot|--ez tabBar true --es blurMode snapshot"
  "live|--ez tabBar true --es blurMode live"
  "glass|--ez tabBar true --es blurMode glass"
)

ORIGINAL_MIN="$(adb shell settings get system min_refresh_rate | tr -d '\r')"
ORIGINAL_PEAK="$(adb shell settings get system peak_refresh_rate | tr -d '\r')"

# `settings get` prints "null" for a setting that was never written, and
# `settings put ... null` writes that back as the literal string rather than
# unsetting it. The phone is borrowed; put it back the way it was.
restore() {
  echo "Restoring the refresh rate to ${ORIGINAL_MIN}/${ORIGINAL_PEAK}." >&2

  for pair in "min_refresh_rate:$ORIGINAL_MIN" "peak_refresh_rate:$ORIGINAL_PEAK"; do
    if [ "${pair#*:}" = "null" ] || [ -z "${pair#*:}" ]; then
      adb shell settings delete system "${pair%%:*}" || true
    else
      adb shell settings put system "${pair%%:*}" "${pair#*:}" || true
    fi
  done

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
      # Clear of the tab bar, which floats at the bottom.
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
      /^[[:space:]]*50th gpu percentile:/   { g50 = $NF }
      /^[[:space:]]*90th gpu percentile:/   { g90 = $NF }
      END { printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s", total, janky, p50, p90, p95, p99, g50, g90 }
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

# GPU percentiles too: glass is the first variant whose cost is mostly a shader,
# and a frame-duration number alone cannot say whether the GPU is the bottleneck.

# Every sweep so far has thrown a 19-21ms P90 on its very first run and normal
# numbers thereafter — ART warm-up and the list's first layout, charged to
# whatever happened to run first. Burn one run rather than let it land on a
# variant.
echo "Warming up..." >&2
adb shell am force-stop "$PKG"
adb shell am start -n "$ACT" --ez tabBar false > /dev/null
sleep "$SETTLE"
scroll
adb shell am force-stop "$PKG"

printf 'hz\trep\tvariant\tframes\tjanky\tp50\tp90\tp95\tp99\tgpu50\tgpu90\n' | tee "$OUT"

# Collected into an array rather than piped into the loop: `adb shell` reads
# stdin, and a `while read` loop feeding it hands over the rest of the list.
shuffled() {
  if [ -n "${SHUFFLE:-}" ]; then
    printf '%s\n' "${VARIANTS[@]}" | sort -R
  else
    printf '%s\n' "${VARIANTS[@]}"
  fi
}

for rep in $(seq 1 "$REPS"); do
  order=()
  while IFS= read -r line; do order+=("$line"); done < <(shuffled)

  for entry in "${order[@]}"; do
    name="${entry%%|*}"
    extras="${entry#*|}"

    if [ -n "${ONLY:-}" ] && [ "$name" != "$ONLY" ]; then
      continue
    fi

    if [ "$name" != "off" ]; then
      # An adjacent baseline is the only thing that makes an outlier dismissible.
      run "off" "$rep" --ez tabBar false
    fi

    run "$name" "$rep" $extras
  done
done

echo "Wrote $OUT" >&2
