#!/usr/bin/env bash
# Takes screenshots of the debug build with sample controllers (MainActivity's demo mode).
# Taps use the coordinates of the pixel_6 emulator profile (1080 × 2400).
set -euo pipefail
PACKAGE=com.philpf.dualsensemanager
adb install -r Dualsense-Manager.apk
mkdir -p screenshots

open_app() {
  adb shell am force-stop "$PACKAGE"
  adb shell am start -W -n "$PACKAGE/.MainActivity" --ez demo true "$@"
  sleep 5
}

capture() {
  adb exec-out screencap -p > "screenshots/$1.png"
}

shot() {
  local name=$1
  shift
  open_app "$@"
  capture "$name"
}

shot main-dark --es theme dark
shot main-light --es theme light
shot pressed-dark --es theme dark --es press CROSS,L1,LEFT_STICK
shot controller-dark --es theme dark --es open demo-1
shot controller-light --es theme light --es open demo-2
shot settings-light --es theme light --es open settings
shot widget --es theme light --es open widget

# The colour wheel: the "+" next to white in Ana's controller sheet, then scroll down to it.
open_app --es theme dark --es open demo-1
adb shell input tap 946 2198
sleep 2
adb shell input swipe 540 1000 540 300 600
sleep 2
capture wheel-dark

# Editing a player: long-press "Ana" in the same sheet.
open_app --es theme light --es open demo-1
adb shell input swipe 350 1844 350 1844 1000
sleep 2
capture player-light

ls -l screenshots
