# Tapflow: liquid-glass auto clicker for Android

Tapflow is an auto clicker with three modes, set up on top of a screenshot of the app you
want to automate:

| Mode | What it does |
| --- | --- |
| **Single** | Taps one point on a steady rhythm (10 ms to hours between taps), until stopped, a set number of times, or for a set time. |
| **Multi** | Taps several points in order, each with its own wait afterwards, cycling as many times as you like. |
| **Chain** | A Scratch-style block editor: snap together *tap*, *hold*, *swipe*, *wait*, *wait random* and C-shaped *repeat* blocks (nestable, or forever). The default chain is the classic example: tap P1 → wait 20 s → tap P2 → repeat 5× { tap P1, wait 1 s }. |

## How to use it

1. **Turn on the service.** Android only lets accessibility services tap other apps.
   The app links you to the right settings screen. On Android 13+ a sideloaded APK may
   need *App info → ⋮ → Allow restricted settings* first.
2. **Add a screenshot** of the target app (screenshot it first, then pick it in Tapflow).
3. **Place your points** in the full-screen editor. The screenshot is shown edge to edge,
   so a point lands exactly where the tap will happen. Drag to move (a loupe magnifies
   under your finger); tap a point to delete it.
4. **Press Start.** Tapflow steps aside and shows a floating glass panel. Open your app
   and press **▶**. Use **❚❚** to pause and **■** to stop. Volume-down also stops it
   from any app. Numbered rings show where taps land and ripple on every tap.

### Chain editor

* Tap a block in the tray above the dock to add it (after the selected block, or inside
  a selected *repeat*).
* **Long-press and drag** any block to move it. Repeats carry their contents, and a glowing
  marker shows where it will land (including inside or outside a repeat).
* Tap the white inputs to change points, times and counts.
* Tap a block to select it, then duplicate or delete it from the tray.
* A **!** badge flags problems (missing point, forever loop without a wait, unreachable
  blocks). Tap it for details.
* The block that's running glows if you come back to the app mid-run.

## Design

The UI follows liquid-glass rules: content stays in front, chrome is glass that bends
the light behind it.

* **Real refraction.** Glass surfaces re-draw what's behind them blurred, with a saturation
  boost, and on Android 13+ through an AGSL shader that bends the backdrop along a curved
  rim, with a hint of chromatic dispersion (`ui/glass/LiquidGlass.kt`). Android 12 gets
  blur only; older versions get a frosted tint.
* **The dock** (`ui/dock/LiquidDock.kt`) is a floating glass capsule. The selected tab
  sits under a lens that magnifies the icons. While you swipe between pages its leading
  edge rushes ahead and its trailing edge lags, so it stretches like a droplet and snaps
  back. Press and drag along the dock to scrub through pages, and the lens lifts and
  grows while you do.
* **Page transitions.** While a page is moving it shrinks slightly, its corners round
  off and a refracting glass slab fades in behind it. The aurora background shifts colour
  per page, with parallax.
* Liquid segmented controls, switches whose thumb stretches as it travels, lens-thumb
  sliders, spring-loaded press states and haptics throughout.
* Light and dark themes (or follow the system), and a reduce-motion option.

## Project layout

```
AutoClicker/
├── core/   Pure Kotlin, no Android: models, block-tree editing, program builder,
│           the runner (pause, time limits, humanize), and unit tests.
├── app/    Android app (Jetpack Compose).
│   ├── service/   Accessibility service, floating panel and marker overlays
│   ├── ui/glass/  Liquid glass rendering, aurora, glass components
│   ├── ui/dock/   The liquid dock
│   ├── ui/board/  Screenshot preview card and full-screen point editor
│   ├── ui/chain/  Block shapes, block rows, drag & drop, palette tray
│   └── ui/pages/  Single, Multi, Settings
└── ci/screenshots.sh   Drives the app on an emulator and captures every screen
```

## Building

Needs JDK 17 and the Android SDK (compileSdk 35).

```bash
cd AutoClicker
./gradlew :core:test            # engine tests (no Android needed)
./gradlew :app:assembleDebug    # app/build/outputs/apk/debug/app-debug.apk
```

The GitHub Actions workflow (`.github/workflows/autoclicker.yml`) runs the tests, builds
debug and release APKs (the `tapflow-apk` artifact) and uploads emulator screenshots
(`tapflow-screenshots`). The release APK is signed with the debug key so it installs
directly; set up your own signing before you distribute it.

Min SDK 26 (Android 8.0), target SDK 35.
