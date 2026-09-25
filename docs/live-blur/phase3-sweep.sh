#!/usr/bin/env bash
#
# Frame sweep for Phase 3: what the periodic re-blur costs, and whether moving
# both blur paths out of setBackground() and into draw() cost Phase 1 anything.
#
# Same harness as example-sweep.sh — read that one first; everything it says
# about interleaved baselines, gfxinfo resets and pinned refresh rates applies
# here unchanged. The variant list is the only real difference, plus SHUFFLE.
#
#   HZ=60 REPS=3 SERIAL=R58R85DAD4F ./docs/live-blur/phase3-sweep.sh
#
# ONLY=reblur-15-ds2 restricts it to one variant, which still gets its adjacent
# baseline. SHUFFLE=1 randomises the variant order within each repetition —
# Phase 1 left two anomalies whose obvious confound is a fixed run order, so
# anything chasing those should turn it on. It is off by default here so that
# live-30fps-ds2 stays comparable with the Phase 1 sweep it is checked against.
#
set -euo pipefail

PKG=com.bluroverlayexample
ACT="$PKG/.MainActivity"
HZ="${HZ:-60}"
REPS="${REPS:-3}"
SWIPES="${SWIPES:-10}"
SETTLE="${SETTLE:-5}"
OUT="${OUT:-/tmp/phase3-sweep-${HZ}hz.tsv}"

adb() { command adb ${SERIAL:+-s "$SERIAL"} "$@"; }

# `glassRadius` is pinned on every variant. The example app scales its panel
# radius by screen density now, so leaving it out would measure a different blur
# than the Phase 1 sweep did and quietly break the comparison below.
R=20

VARIANTS=(
  "off|--ez panel false"
  "snapshot|--ez panel true --es blurMode snapshot --ei downsampling 2 --ei glassRadius $R"
  "live-30fps-ds2|--ez panel true --es blurMode live --ei maxUpdateFps 30 --ei downsampling 2 --ei glassRadius $R"
  "reblur-5-ds2|--ez panel true --es blurMode snapshot --ei downsampling 2 --ei snapshotUpdateFps 5 --ei glassRadius $R"
  "reblur-15-ds2|--ez panel true --es blurMode snapshot --ei downsampling 2 --ei snapshotUpdateFps 15 --ei glassRadius $R"
  "reblur-15-ds4|--ez panel true --es blurMode snapshot --ei downsampling 4 --ei snapshotUpdateFps 15 --ei glassRadius $R"
  "reblur-30-ds4|--ez panel true --es blurMode snapshot --ei downsampling 4 --ei snapshotUpdateFps 30 --ei glassRadius $R"
  # What `radius` itself costs, which nothing before Phase 3 ever measured —
  # Phases 0, 1 and 2 all held it at 20.
  "live-r80-ds2|--ez panel true --es blurMode live --ei maxUpdateFps 30 --ei downsampling 2 --ei glassRadius 80"
  "live-r160-ds2|--ez panel true --es blurMode live --ei maxUpdateFps 30 --ei downsampling 2 --ei glassRadius 160"
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

# Every sweep so far has thrown a 19-21ms P90 on its very first run and normal
# numbers thereafter — ART warm-up and the list's first layout, charged to
# whatever happened to run first. Burn one run rather than let it land on a
# variant.
echo "Warming up..." >&2
adb shell am force-stop "$PKG"
adb shell am start -n "$ACT" --ez panel false > /dev/null
sleep "$SETTLE"
scroll
adb shell am force-stop "$PKG"

printf 'hz\trep\tvariant\tframes\tjanky\tp50\tp90\tp95\tp99\n' | tee "$OUT"

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
      run "off" "$rep" --ez panel false
    fi

    run "$name" "$rep" $extras
  done
done

echo "Wrote $OUT" >&2
