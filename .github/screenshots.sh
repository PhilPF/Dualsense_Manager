#!/usr/bin/env bash
# Takes screenshots of the debug build with sample controllers (MainActivity's demo mode).
# Taps use the coordinates of the pixel_6 emulator profile (1080 × 2400).
set -euo pipefail
PACKAGE=com.philpf.dualsensemanager
adb install -r Dualsense-Manager.apk
mkdir -p screenshots
# The emulator draws on the CPU and its launcher sometimes stalls: no "isn't responding"
# dialogs over the shots, and a moment for the home screen to settle.
adb shell settings put global hide_error_dialogs 1
adb shell input keyevent KEYCODE_HOME
sleep 10

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
# Pressing on the first sample (again every 1.4 s): its cell floods with its colour. A few
# frames in a row, to catch a wave on its way.
open_app --es theme dark --es press CROSS,L1
capture pressed-dark
for i in 2 3 4 5; do capture "pressed-dark-$i"; done
shot controller-dark --es theme dark --es open demo-1
# Its sheet shows the buttons held and where the sticks are.
shot sticks-dark --es theme dark --es open demo-1 --es press CROSS,R1 --es sticks 0.75,-0.55,-0.3,0.85
shot controller-light --es theme light --es open demo-2
shot settings-light --es theme light --es open settings
shot widget --es theme light --es open widget
# Swipe down on the first widget: the next controller's card, its pill's thumb one dot down.
adb shell input swipe 147 180 147 340 250
sleep 2
capture widget-swiped
shot mono-dark --es theme dark --ez mono true

# The colour wheel: the "+" next to white in Ana's controller sheet opens it in a popup.
open_app --es theme dark --es open demo-1
adb shell input tap 944 2194
sleep 4
capture wheel-dark

# Editing a player: long-press "Ana" in the same sheet.
open_app --es theme light --es open demo-1
adb shell input swipe 350 1844 350 1844 1000
sleep 2
capture player-light

ls -l screenshots
