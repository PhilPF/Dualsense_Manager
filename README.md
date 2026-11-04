# Dualsense Manager

An Android app for telling apart the PS5 controllers (DualSense and DualSense Edge)
connected to a phone or tablet: each controller gets its own light-bar colour, a
number and an optional nickname, and the app shows its battery level, whether it is
charging, and whether it is connected over Bluetooth or a USB cable.

Requires **Android 12 or newer** (the first version that lets apps set a controller's
lights and read its battery).

## What it does

- **Light-bar colours.** A controller seen for the first time gets the next free
  colour automatically: DualSense 1 blue, 2 red, 3 green, 4 pink (the PS5's own player
  colours), then orange, cyan, purple, yellow and white, skipping colours another
  remembered controller already uses. Tap a controller to pick any colour, from the
  presets or with hue, saturation and brightness sliders. The real light bar follows the
  sliders live, and "Automatic colour" goes back to the automatic one.
- **Names.** Controllers are numbered in the order they are first seen ("DualSense 1",
  "DualSense 2", ...); a nickname (for example the player's name) replaces the name on
  screen. Numbers, nicknames and colours are remembered per controller and come back
  whenever it reconnects, over Bluetooth or USB.
- **Battery.** Level and charging state ("Charging", "Charged"), low levels in red.
- **Connection.** Bluetooth or USB cable.
- **Remembered controllers.** Controllers that are not connected are listed with when
  they were last seen and their last battery level; "Forget" frees their number and
  colour.
- **In the background.** While controllers are connected, a quiet notification lists
  them with their battery levels. With the "Nearby devices" permission the app also
  notices Bluetooth connections while it is closed and applies the colour straight away.

## Design

The flat "liquid glass" look of the solar-system wallpaper app
([PhilPF/20137-1996-PX8](https://github.com/PhilPF/20137-1996-PX8), `GlassUi.kt`):
frosted panels with a real backdrop blur, a faint white gradient and a thin light rim,
no shadows or bevels, flat capsule controls. Behind the glass, slow glows in the colours
of the connected controllers' light bars, so the whole screen takes on the colour you
are picking.

Android has no system glass material, so `ui/glass/Backdrop.kt` records the content
behind a panel into a graphics layer and draws it again inside the panel through a blur
(`RenderEffect`, Android 12+). The layer is shared rather than copied, so the panels
follow the animated background without redrawing. Cards blur the background; the top
bar and the controller sheet blur everything behind them, cards included.

## How it works

- **Finding controllers.** `InputManager` lists input devices; DualSense controllers are
  Sony's vendor id `054C` with product `0CE6` (DualSense) or `0DF2` (Edge), over both
  Bluetooth and USB. A controller is identified by its input-device *descriptor*: the
  Linux driver (`hid-playstation`) sets the controller's MAC address as its unique id, so
  the descriptor is the same across reconnections, reboots and both connection types.
- **Light bar.** `InputDevice.getLightsManager()` exposes the light bar as an RGB light,
  set through a `LightsSession`. Android switches a light off when the session that set
  it closes, including when the app's process dies. So the app keeps a small foreground
  service (`LightKeeperService`, type `connectedDevice`) running while controllers are
  connected, and stops it 20 s after the last one disconnects.
- **Battery.** `InputDevice.getBatteryState()`. The controller reports its charge as a
  level d in {0, ..., 10}, and the driver publishes p = min(10d + 5, 100) %. A reported
  p with p = 5 (mod 10) therefore only says that the charge lies in [p - 5, p + 5), and
  the app shows it as that interval, e.g. "50–60%". (Before the first report the driver
  says 100 % with an unknown status; the app shows "Reading…" instead.)
- **Bluetooth or USB.** Android has no public API for this, so the app uses two facts:
  over USB the kernel names the device from its USB strings ("Sony Interactive
  Entertainment DualSense Wireless Controller"), over Bluetooth from its Bluetooth name
  ("DualSense Wireless Controller"); and if no Sony controller is attached over USB at
  all (`UsbManager`), every connected controller is on Bluetooth.
- **Starting in the background.** `ConnectionReceiver` listens for Bluetooth
  connections; Android allows starting the light service from that broadcast. USB
  connections are picked up while the app or its service is running.

What depends on the phone: the light bar and battery need the kernel's PlayStation
driver, which Android 12+ devices normally have. Where a phone doesn't expose the light
bar, the app says so and uses the colour on screen only.

## Building

The build is a standard Gradle project (AGP 8.5.2, Kotlin 2.0.21, Jetpack Compose,
compileSdk 34, minSdk 31).

1. **GitHub Actions.** `.github/workflows/build-apk.yml` is a manual workflow: in the
   repository's **Actions** tab, choose "Build APK" > "Run workflow". It runs the unit
   tests, builds a debug APK and publishes it on the **`debug-build`** release as
   `Dualsense-Manager.apk`, which downloads and installs straight from a phone browser.
   Debug builds are signed with the committed `app/debug.keystore`, so each new build
   installs over the previous one.
2. **Android Studio.** Open the project and run it, or `./gradlew assembleDebug`.

## Project layout

- `core/` — plain Kotlin, no Android: models, palette and numbering, battery rules and
  text, connection rule, storage format, colour maths. Unit-tested in `app/src/test`.
- `device/ControllerManager.kt` — finds controllers, holds their light sessions, reads
  batteries; `device/ProfileStore.kt` — remembers controllers.
- `service/` — the foreground service, its notification and the Bluetooth receiver.
- `ui/` — Compose UI: `glass/` (backdrop blur and flat controls), `art/` (controller
  drawing and icons), the main screen, cards and the controller sheet.
