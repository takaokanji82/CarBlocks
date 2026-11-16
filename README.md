# Car Blocks

A block puzzle game for Android car head units. Drag pieces onto an 8×8 board and fill whole rows or columns to clear them. Clearing lines on consecutive moves builds a streak bonus, and emptying the whole board earns extra points. The game ends when none of the remaining pieces fit on the board.

The game was developed and tested on **BYD** in-car infotainment systems.

## Features

- 8×8 board with drag-and-drop placement and a preview of where the piece will land
- Scoring based on line clears, streaks and full-board clears
- Best score is saved on the device
- Layout adapts to landscape and portrait screens
- Sound effects, with an on-screen toggle
- No ads, no network access, no extra permissions
- Plain Android SDK only (no third-party libraries), min SDK 21

## Installing on a BYD car

BYD head units don't allow installing apps from unknown sources by default, so you first need to enable ADB.

1. **Enable ADB on your BYD:** follow the instructions at [bydunlock.com](https://bydunlock.com).
2. **Build the APK** (see below), or use a prebuilt APK if one is provided.
3. **Install over ADB:**
   ```sh
   adb connect <car-ip-address>     # if using wireless ADB
   adb install -r app-debug.apk
   ```
4. Open **Car Blocks** from the app launcher.

> Only play while the vehicle is parked.

## Building

You need either Android Studio or a local Gradle install with the Android SDK (compileSdk 34). The repo doesn't include a Gradle wrapper (`gradlew`).

- **Android Studio:** open the project folder and run **Build → Build APK(s)**.
- **Command line:**
  ```sh
  gradle assembleDebug
  ```
  The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Project structure

```
app/src/main/java/com/carblocks/game/
├── MainActivity.java   # Entry point
├── GameView.java       # Android View: rendering, touch input, save/load, sound
├── Scene.java          # Layout, drawing, animations, UI
├── Game.java           # Game logic (board, pieces, scoring); no Android dependencies
├── Gfx.java            # Drawing interface used by Scene
└── Tones.java          # Generated sound effects
```

## Disclaimer

This is an independent project. It is not affiliated with or endorsed by BYD. Enabling ADB or installing apps on your vehicle is at your own risk.
