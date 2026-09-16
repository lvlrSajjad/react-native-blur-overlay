#!/bin/bash
export PATH=~/Library/Android/sdk/platform-tools:$PATH
PKG=com.bluespike; DEV="$1"; OUT="$2"; shift 2
: > "$OUT"
run() {
  adb -s $DEV shell am force-stop $PKG; adb -s $DEV logcat -c
  adb -s $DEV shell am start -n $PKG/.SpikeActivity --es variant "$1" --ef scale "$2" \
      --ef radius 25 --ei speed 24 --ei durationMs 12000 > /dev/null
  adb -s $DEV shell "sleep 3"; adb -s $DEV shell dumpsys gfxinfo $PKG reset > /dev/null
  adb -s $DEV shell "sleep 11"
  local g; g=$(adb -s $DEV shell dumpsys gfxinfo $PKG | grep -E "^(Janky frames:|50th percentile|90th percentile|99th percentile)" | sed 's/.*: //' | tr -d '\r' | tr '\n' ' ')
  local s; s=$(adb -s $DEV logcat -d -s BLURSPIKE | grep -o "captureMedian=[^ ]* captureP90=[^ ]*" | tail -1)
  local f; f=$(adb -s $DEV logcat -d -s BLURSPIKE | grep -o "scrollFps=[0-9.]*" | tail -1)
  printf '%-4s scale=%-5s rep=%s :: jank=%s p50/p90/p99 :: %s :: %s\n' "$1" "$2" "$3" "$g" "$s" "$f" >> "$OUT"
}
for rep in 1 2 3; do for spec in "$@"; do
  run ${spec%%:*} ${spec##*:} $rep
done; done
