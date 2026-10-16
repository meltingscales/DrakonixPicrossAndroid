# Drakonix Picross

Simple Android picross (nonogram) game.

- 17 levels (six 5×5, eleven 10×10) — dragon and furry themed ones included
- Gallery mode: solved levels show their pixel art, unsolved stay "???"
- Levels are plain ASCII data files: `app/src/main/assets/levels/NN_name.txt`
  (line 1 = name, then '#'/'.' grid rows; file order = level order)
- No ads
- Free
- No dependencies beyond the Android SDK — plain Kotlin + custom Canvas view
- Made to test Claude's models
- Made with Fable 5

## Play

Tap **Fill** to paint cells, **Mark ✕** to flag empties. Drag to paint runs.
A puzzle counts as solved when every row/column clue is satisfied.
Solved levels get a ✓ on the level list (saved in SharedPreferences).

## Build

```sh
just run    # build + install + launch on connected device
just build  # APK: app/build/outputs/apk/debug/app-debug.apk
```

`just --list` shows all commands (build, bundle, install, launch, run,
uninstall, clean, screenshot). Without `just`: `./gradlew assembleDebug`.

Requires Android SDK (compileSdk 36) and JDK 17+. Point `local.properties`
at your SDK (`sdk.dir=...`). minSdk 26.

## Install on device

```sh
adb install app/build/outputs/apk/debug/app-debug.apk
```
