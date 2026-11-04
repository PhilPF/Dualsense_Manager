# Dualsense Manager

An Android app for telling apart the PS5 controllers (DualSense and DualSense Edge)
connected to a phone or tablet. Each controller gets its own light-bar colour, a number
and its player lights, and the app shows its battery, whether it is charging, and whether
it is connected over Bluetooth or a USB cable.

Requires **Android 12 or newer** (the first version that lets apps set a controller's
lights and read its battery).

## Features

- **Light-bar colours.** A controller seen for the first time gets the next free colour
  automatically: DualSense 1 blue, 2 red, 3 green, 4 pink (the PS5's own player colours),
  then orange, cyan, purple, yellow and white, skipping colours already in use. Pick any
  colour from the presets, or tap "+" for a colour wheel in a popup; the real light bar
  follows the wheel live.
- **Player lights.** The five small LEDs show the controller's number with the PS5's
  patterns (1 = centre, 2 = inner pair, 3 = centre and outer pair, 4 = all but the centre,
  5 = all).
- **Players.** Create players (a name and a colour) with "+" and give each one the
  controller they pick up; the controller then shows the player's name and colour. A player
  uses one controller at a time. Long-press a player to rename, recolour or delete them.
  Without a player a controller is "DualSense n".
- **Order.** Long-press a connected controller and drag it to move it: controllers are
  numbered in the order shown, so dragging the second above the first swaps their numbers
  (and their player lights and automatic colours).
- **Find.** Makes a controller rumble with both motors at full strength (the most Android
  allows) and blink its lights, for about 3.4 seconds.
- **Which one is this?** While the app is open, pressing anything on a controller floods its
  cell with its colour, in a wave from the controller outward. Its sheet shows the buttons
  being pressed and the sticks where they are, moving as they move.
- **Battery.** Level, charging state and connection (Bluetooth or USB) on every cell, with
  notifications when a controller's battery runs low (10 %, then 0 %) and when it is fully
  charged. Both can be turned off in Settings.
- **At a glance.** A home-screen widget with a square tile per connected controller, drawn
  as in the app (the controller in lines over a glow of its colour), with its name and
  battery below. The tiles are as large as
  the widget allows while showing every controller (a 3 × 2 widget shows four to six,
  depending on the home screen); beyond that the tiles scroll. Also a Quick Settings tile, and
  the service's notification showing the controllers the same way.
- **Live icon.** The app's icon is the controller in lines over coloured lights behind glass.
  Settings can add a copy to the home screen that takes the colours of the connected
  controllers (Android does not let an app change its own launcher icon, so this one is a
  shortcut the app redraws).
- **Remembered controllers.** Controllers that are not connected stay listed, dimmed, with
  when they were last seen; "Forget" frees their number and colour.
- **Light and dark.** System, Light or Dark, in Settings.

## Design

Frosted "liquid glass" in the style of the solar-system wallpaper app
([PhilPF/20137-1996-PX8](https://github.com/PhilPF/20137-1996-PX8)): panels with a real
backdrop blur, a faint white gradient and a thin light rim, no shadows, flat capsule
controls. Every cell is the same size, and each connected controller glows in its
light-bar colour behind its cell, so the colour diffuses through the glass and spills
around it.

Android has no system glass material, so `ui/glass/Backdrop.kt` records what is behind a
panel into a graphics layer and draws it again inside the panel through a blur
(`RenderEffect`). The layer is shared, not copied, so panels follow the background without
redrawing.

## How it works

- **Finding controllers.** `InputManager` lists input devices; a DualSense is Sony's vendor
  id `054C` with product `0CE6` (DualSense) or `0DF2` (Edge), over Bluetooth and USB alike.
  A controller is identified by its input-device *descriptor*: the Linux driver
  (`hid-playstation`) sets the controller's MAC address as its unique id, so the
  descriptor survives reconnections, reboots and switching between USB and Bluetooth.
- **Lights.** `InputDevice.getLightsManager()` exposes the light bar as an RGB light and the
  five player LEDs as five lights named `…player-1` to `…player-5`, all set through one
  `LightsSession`. Android switches lights off when the session that set them closes,
  including when the app's process dies, so a small foreground service
  (`LightKeeperService`, type `connectedDevice`) runs while controllers are connected and
  stops 20 s after the last one disconnects.
- **Battery.** `InputDevice.getBatteryState()`. The controller reports its charge as a
  level d in {0, …, 10} and the driver publishes p = min(10d + 5, 100) %, so a reported
  p with p ≡ 5 (mod 10) only says that the charge lies in [p − 5, p + 5). The app shows
  the lower end, p − 5 (e.g. "50%"), a value the charge is guaranteed to reach.
- **Bluetooth or USB.** There is no public API for this. Over USB the kernel names the
  device from its USB strings ("Sony Interactive Entertainment DualSense Wireless
  Controller"), over Bluetooth from its Bluetooth name ("DualSense Wireless Controller");
  and if no Sony controller is attached over USB at all (`UsbManager`), all are on
  Bluetooth.
- **Rumble.** `InputDevice.getVibratorManager()`, both motors at amplitude 255.
- **Sticks.** The joystick axes (X, Y for the left stick, Z, RZ for the right) with a round
  dead zone: a position v with |v| = m becomes 0 when m < 0.08, else
  v/m · min(1, (m − 0.08)/0.92), so it keeps its direction and its length is continuous.
- **Widget layout** (`core/WidgetGrid.kt`). For n controllers in an area of W × H dp with gaps
  of g = 4 dp, c columns give r(c) = ⌈n / c⌉ rows and squares of side
  s(c) = min((W − (c − 1)g) / c, (H − (r(c) − 1)g) / r(c)). With s* = max s(c), if
  s* ≥ 80 dp the widget shows every tile, taking among the c with
  s(c) ≥ max(0.9 s*, 80 dp) the one with the fewest empty places c·r(c) − n, then the one with
  more columns. Otherwise the tiles keep at least 80 dp and the grid scrolls. Android widgets
  can only scroll vertically, so there is no sideways scrolling. The widget is rebuilt for each
  size the launcher reports (portrait and landscape) and when it is resized.
- **Pressed buttons.** The activity receives the controllers' key and joystick events while
  it is in front and maps them to parts of the drawing; it keeps them, so a button such as
  Circle doesn't act as Back in the app.
- **Starting in the background.** `ConnectionReceiver` listens for Bluetooth connections;
  Android allows starting the light service from that broadcast.

## What apps cannot do

- **Disconnect, connect or turn off a controller.** A DualSense on Bluetooth turns off when
  the phone drops the connection, so turning it off and disconnecting it are the same thing.
  Android keeps that to the system: the HID-host profile (`BluetoothHidHost`) and
  `BluetoothDevice` connect/disconnect are not in the public SDK (checked for Android 14 and
  16) and need the `BLUETOOTH_PRIVILEGED` permission, which only system apps (and the shell,
  e.g. through Shizuku) have. Reconnecting always starts at the controller (its PS button):
  once it is off, its radio is off too.

## Building

A standard Gradle project (AGP 8.5.2, Kotlin 2.0.21, Jetpack Compose, compileSdk 34,
minSdk 31).

- **GitHub Actions.** `.github/workflows/build-apk.yml` runs on pushes to `claude/`
  branches and from the Actions tab ("Build APK" > "Run workflow"). It runs the unit tests,
  builds a debug APK and publishes it on the **`debug-build`** release as
  `Dualsense-Manager.apk` (installs straight from a phone browser), then opens the app on
  an emulator with sample controllers and adds screenshots to the same release. Debug
  builds are signed with the committed `app/debug.keystore`, so each build installs over
  the previous one.
- **Android Studio.** Open the project and run it, or `./gradlew assembleDebug`.

Debug builds show sample controllers when started with
`adb shell am start -n com.philpf.dualsensemanager/.MainActivity --ez demo true`
(optionally `--es theme light|dark`, `--es open settings|demo-1|widget` and
`--es press CROSS,L1`).

## Project layout

- `core/` — plain Kotlin: models, palette and numbering, players, player-LED patterns,
  battery rules and alerts, connection rule, storage format, colour maths. Unit-tested in
  `app/src/test`.
- `device/` — `ControllerManager` (finds controllers, holds their light sessions, reads
  batteries, rumbles), `ProfileStore` (controllers and players), `SettingsStore`.
- `service/` — the foreground service and notifications, the Bluetooth receiver, the
  Quick Settings tile. `widget/` — the home-screen widget.
- `ui/` — Compose UI: `glass/` (backdrop blur and flat controls), `art/` (controller
  drawing and icons), the cells, the sheets and the main screen.

## Credits

- The controller drawing is adapted from `design/dualsense.svg` in
  [daidr/dualsense-tester](https://github.com/daidr/dualsense-tester), © 2023 Xuezhou
  Dai, MIT License (full notice in `ui/art/ControllerArt.kt`).
- The settings and delete icons follow Google's Material Icons (Apache License 2.0).
- The app icon, widget tiles and notification use the outline of the same drawing.
