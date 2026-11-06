# Dualsense Manager

An Android app for telling apart the PS5 controllers (DualSense and DualSense Edge)
connected to a phone or tablet. Each controller gets its own light-bar colour, a number
and its player lights, and the app shows its battery, whether it is charging, and whether
it is connected over Bluetooth or a USB cable. It also controls Sony headphones (built for
the WH-XB910N) and OnePlus earbuds (built for the Buds Pro 2): battery, noise cancelling and
ambient sound or transparency, and the equalizer.

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
  (and their player lights and automatic colours). Or tap the numbered list in the bar: the
  list turns into empty places showing L2 + R2, and as on a console, the first controller to
  hold both triggers takes place 1, the next place 2, and so on. Each buzzes twice when it
  takes its place, and its player lights show it at once (they are off while it waits). When
  one controller is left it takes the last place by itself, so with two controllers one press
  is enough. Leaving earlier (✕, back, or the app) leaves those still waiting without a number
  ("DualSense", lights off) until they reconnect; leaving before anyone pressed changes
  nothing.
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
- **Battery.** Level, charging state and connection (Bluetooth or USB) on every cell, and
  about how long the battery lasts, or how long until it is full while charging (see How
  it works); with notifications when a controller's battery runs low (10 %, then 0 %) and when it is fully
  charged. Both can be turned off in Settings.
- **At a glance.** A one-cell home-screen widget: a card per connected controller, filling
  the widget, drawn as in the app (the controller in lines over a glow of its colour) with its
  name, battery and connection. Swipe down to snap to the next one; a pill on its right has a
  dot per controller in its colour and a thumb on the one shown. Also a Quick Settings tile,
  and the service's notification showing the controllers the same way.
- **App icon.** The controller in white lines on black; notifications show it too. With
  "Controllers' colours" on in Settings, the icon takes the colours of the connected
  controllers, as lights behind glass.
- **Remembered controllers.** Controllers that are not connected stay listed, dimmed, with
  when they were last seen. Their settings wait until they are back; "Forget" frees their
  number and colour.
- **Light and dark, and a monospaced font**, in Settings.
- **Headphones.** Sony headphones paired with the phone get their own section, above the
  controllers: battery and noise control at a glance, a button that steps the noise
  control as the headphones' own does (noise cancelling, ambient sound, off), and a wave
  across the panel when they are used (their noise-control button, equalizer or volume),
  as a controller's panel does when pressed. Their sheet has Disconnect and Turn off, the
  noise control with the ambient sound level and voice focus, the media volume, and the
  equalizer presets in one row; every row stays in place, faint when it does not apply, so
  the sheet keeps its size. Built for the WH-XB910N; other headphones that speak either
  version of Sony's protocol (such as the WH-1000XM3 to XM5) should work; earbuds are not
  read yet. When the app cannot reach them, the card says why: the channel is busy (Sony's
  app holds it) or they do not answer.
  OnePlus earbuds (built for the Buds Pro 2) show each bud's battery and the case's, noise
  cancelling at its four strengths (Smart, Extreme, Moderate, Mild), transparency or off,
  and the equalizer presets; their panel waves at a tap, squeeze or swipe on a bud. They
  have no Turn off: they turn off in their case. They use the headphones' drawing for now.
- **List or grid.** The bar's first button shows the panels as a list, or as square cards in
  two columns; dragging and putting controllers in order work in both. The bar floats at the
  bottom, and the app's name scrolls away with the list.

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
- **Time left.** The controller reports its charge only as the 10 % band it is in, so the
  informative moments are the band changes: when the reading drops from the band starting at
  b, the charge is b (to within the polling delay, 5 s with the app open, 60 s otherwise).
  Taking the charge as linear in time, the rate is the least-squares slope through the last
  few such crossings, and the last crossing gives the charge now. Each new step also updates
  a rate remembered per controller (separately for draining and charging), which stands in
  until two steps are seen; the charge at the first reading is then only known to lie in its
  band, and its expected value is used, given that the charge has not left the band yet.
  Staying in a band for a time D bounds the rate by 10 / D, so the estimate follows when use
  slows down. It is shown rounded (to 5 min below an hour, 10 min below ten hours) with a
  "~" (`core/BatteryForecast`, with tests).
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
- **Headphones.** Sony's own app talks to its headphones over a Bluetooth RFCOMM channel,
  in a protocol Sony does not publish; this app speaks both its versions, written from the
  descriptions in [Gadgetbridge](https://codeberg.org/Freeyourgadget/Gadgetbridge) and
  [Cans](https://github.com/unhingedpanda/cans), with facts about the WH-XB910N from
  [MelodyLink](https://github.com/jerry-2009/MelodyLink) and
  [BudsLink](https://github.com/maniacx/budslink) (no code taken; their test vectors are the
  tests in `core/SonyHeadphones`). A message is 0x3E, then type, sequence number, payload
  length and payload, a checksum (the sum of those bytes), and 0x3C, with those markers
  escaped inside. Each request waits for the headphones' acknowledgement, which gives the
  next sequence number (0 or 1). The app connects to version 2's service when offered, else
  version 1's; the answer to the first request tells the version (4 bytes: 1; longer: 2),
  and it is asked again every 1.25 s, up to 8 times, as headphones often ignore it just
  after connecting. Version 2 headphones then tell their functions, which give the form of
  their noise control (Cans' table); when they give none, as the WH-XB910N's do, form 0x17
  is asked (as MelodyLink does), and if nothing answers within a few seconds the other
  forms are tried one at a time until one does. The form that answered is kept for next
  time, and every change is read back, as the XB910N does not report changes it was asked
  for. The app opens the channel only while it is on screen, to headphones that are
  connected; Sony's app, if open at the same time, may hold it. The volume is Android's
  media volume, the headphones' own while they play the phone's sound; the app follows it
  through Android's volume broadcast, and a change it did not make itself counts as the
  headphones being used.
- **OnePlus earbuds.** HeyMelody, OnePlus's app, speaks to its earbuds over RFCOMM too, in
  a protocol of its own (OPPO's and realme's earbuds speak it as well), written here from
  [MacMelody](https://github.com/Omraj09/MacMelody)'s description (MIT), with facts checked
  against [QuickBuds](https://github.com/spizganed/QuickBuds) and BudsLink (no code taken;
  QuickBuds' captured frames are the tests in `core/OnePlusBuds`). A message is 0xAA, its
  length (LEB128), two zero bytes, the command (2 bytes, little-endian), a sequence number
  the reply repeats (0xFF in pushes), the payload's length and the payload; a reply sets
  bit 15 of the command. On connecting (after 300 ms, as HeyMelody waits): the handshake,
  the product id (062014 is the Buds Pro 2), the events offered, of which battery,
  wearing, noise control and gestures are asked for (without that the buds report no
  change made on them), then the noise control, battery and equalizer. The buds' own
  requests (the time, and three questions about the phone) are answered, as some reset
  the link otherwise. The noise control is set with one bit and reported in another table;
  each change is read back.
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
  batteries, rumbles), `HeadphonesManager` (Sony headphones), `DeviceDisconnect`,
  `InputHighlights` (buttons and sticks), `AppIcon`, `ProfileStore` (controllers and
  players), `SettingsStore`.
- `tools/app_icons.py` — generates the launcher icons and their manifest aliases.
- `service/` — the foreground service and notifications, the Bluetooth receiver, the
  Quick Settings tile. `widget/` — the home-screen widget.
- `ui/` — Compose UI: `glass/` (backdrop blur and flat controls), `art/` (controller and
  headphones drawings, and icons), the cells, the sheets and the main screen.

## Credits

- The controller drawing is adapted from `design/dualsense.svg` in
  [daidr/dualsense-tester](https://github.com/daidr/dualsense-tester), © 2023 Xuezhou
  Dai, MIT License (full notice in `ui/art/ControllerArt.kt`).
- The headphones drawing is traced from a front photograph of the WH-XB910N: its outline
  and main lines, with the left half mirrored.
- The OnePlus earbuds' protocol follows [MacMelody](https://github.com/Omraj09/MacMelody)'s
  description, © 2026 Omraj Sawant, MIT License (credited in `core/OnePlusBuds.kt`).
- The settings, delete and numbered-list icons follow Google's Material Icons (Apache License 2.0).
- The app icon, widget tiles and notification use the outline of the controller drawing.
