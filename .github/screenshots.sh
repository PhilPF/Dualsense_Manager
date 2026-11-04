#!/usr/bin/env bash
# Takes screenshots of the debug build with sample controllers (MainActivity's demo mode).
set -euo pipefail
PACKAGE=com.philpf.dualsensemanager
adb install -r Dualsense-Manager.apk
mkdir -p screenshots

shot() {
  local name=$1
  shift
  adb shell am force-stop "$PACKAGE"
  adb shell am start -W -n "$PACKAGE/.MainActivity" --ez demo true "$@"
  sleep 5
  adb exec-out screencap -p > "screenshots/$name.png"
}

shot main-dark --es theme dark
shot main-light --es theme light
shot pressed-dark --es theme dark --es press CROSS,L1,LEFT_STICK
shot controller-dark --es theme dark --es open demo-1
shot controller-light --es theme light --es open demo-2
shot settings-light --es theme light --es open settings
shot widget --es theme light --es open widget
ls -l screenshots
