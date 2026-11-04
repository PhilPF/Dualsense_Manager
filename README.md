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
  cell with its colour, in a wave from the controller outward; every press gets its own full
  wave. Its sheet shows the buttons being pressed and the sticks where they are, moving as
  they move.
- **Disconnect.** Turns a controller on Bluetooth off, from its sheet. From Android 17 the app
  does it; the first time for each controller, Android asks you to let the app manage it. On
  Android 12 to 16 the button opens the controller's own page in the system's Bluetooth
  settings, where "Disconnect" is one tap (the list of Bluetooth devices instead, on phones
  whose settings lack that page). Turn it back on with its PS button; it reconnects by itself.
- **Battery.** Level, charging state and connection (Bluetooth or USB) on every cell, with
  notifications when a controller's battery runs low (10 %, then 0 %) and when it is fully
  charged. Both can be turned off in Settings.
- **At a glance.** A one-cell home-screen widget: a card per connected controller, filling
  the widget, drawn as in the app (the controller in lines over a glow of its colour) with its
  name, battery and connection. Swipe down to snap to the next one; a pill on its right has a
  dot per controller in its colour and a thumb on the one shown. Also a Quick Settings tile, and the service's
  notification showing the controllers the same way.
- **App icon.** The controller in white lines on black; notifications show it too. With
  "Controllers' colours" on in Settings, the icon takes the colours of the connected
  controllers, as lights behind glass.
- **Remembered controllers.** Controllers that are not connected stay listed, dimmed, with
  when they were last seen. Their settings wait until they are back; "Forget" frees their
  number and colour.
- **Light and dark, and a monospaced font**, in Settings.

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
- **App icon.** Android has no way to draw a new launcher icon, only to switch between icons
  an app ships. `tools/app_icons.py` generates the plain icon, which is also the application's
  own icon (newer Android versions and some makers' skins show that one in notifications,
  rather than the small icon), and one for every set of 1 to 4 of the 9 preset
  colours (255 icons, each an `activity-alias` of MainActivity); `core/AppIconChoice` maps the
  connected controllers' colours to their nearest presets and picks one, and `device/AppIcon`
  enables it and disables the old one. That closes the app's window, so it waits until the
  app is off screen. Some launchers take a moment to show the new icon, and some may drop a
  home-screen icon whose alias was disabled; that is why it is a setting.
- **Widget.** Android widgets can only scroll lists freely; the one view that snaps from item
  to item is `StackView`, so the widget is a stack. A stack draws its front card at 90 % of
  its size, inset by a frame and lowered by a tenth of its height, with the cards behind it
  peeking above. So the widget's stack is larger than the widget and shifted up and left, by
  amounts computed for each widget size (`core/StackGeometry`, with tests), that its front
  card covers the widget exactly; the widget's rounded outline cuts off the rest. A widget is
  not told which card is showing, so each card carries the pill with its own dot marked; only
  the front card is ever seen, so the pill stays in place while its thumb moves. The stack's
  outline colours (blue by default) come from a theme style, and the widget's stack gets one
  without them.
- **Disconnecting.** A DualSense on Bluetooth turns off when the phone drops the connection.
  Android 17 (API 37) made `BluetoothDevice.disconnect()` public for apps that hold
  `BLUETOOTH_CONNECT` and are associated with the device through the Companion Device Manager
  (before, it needed `BLUETOOTH_PRIVILEGED`, which only system apps have). On Android 12 to 16
  the app opens Android's own page for the device: AOSP's Settings exports it to apps holding
  `BLUETOOTH_CONNECT` (action `com.android.settings.BLUETOOTH_DEVICE_DETAIL_SETTINGS`, the
  address in the `:settings:show_fragment_args` bundle as `device_address`), and its second
  button disconnects. To find the paired device of a controller, the app hashes each paired
  address as Android does for input devices: the descriptor is the SHA-1 of
  ":054c:0ce6:uniqueId:" followed by the address, and the driver reports the address as the
  unique id (`core/InputDescriptors`).
- **Pressed buttons.** The activity receives the controllers' key and joystick events while
  it is in front and maps them to parts of the drawing; it keeps them, so a button such as
  Circle doesn't act as Back in the app.
- **Starting in the background.** `ConnectionReceiver` listens for Bluetooth connections;
  Android allows starting the light service from that broadcast.

## What apps cannot do

- **Reconnect a controller.** Once a DualSense is off its radio is off too, so no phone (not
  even a PS5) can reach it; it reconnects when you press its PS button.
- **Disconnect by themselves before Android 17.** Older versions keep that to system apps;
  the app leads to the system's page for the controller instead.

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
- **App icons.** After changing the presets or the icon's design, run
  `python3 tools/app_icons.py` to regenerate the icons and the manifest's aliases.

Debug builds show sample controllers when started with
`adb shell am start -n com.philpf.dualsensemanager/.MainActivity --ez demo true`
(optionally `--es theme light|dark`, `--es open settings|demo-1|widget`,
`--es press CROSS,L1`, `--es sticks 0.7,-0.5,0,0` and `--ez mono true`).

## Project layout

- `core/` — plain Kotlin: models, palette and numbering, players, player-LED patterns,
  battery rules and alerts, connection rule, storage format, colour maths, stick dead zone,
  icon choice, input descriptors. Unit-tested in `app/src/test`.
- `device/` — `ControllerManager` (finds controllers, holds their light sessions, reads
  batteries, rumbles), `ControllerDisconnect`, `InputHighlights` (buttons and sticks),
  `AppIcon`, `ProfileStore` (controllers and players), `SettingsStore`.
- `tools/app_icons.py` — generates the launcher icons and their manifest aliases.
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
