# Remote control implementation notes

The supplied project is an Android Java app. The README's `remotecontrolled.gif` is a demonstration video, not source code for the companion remote app. The current game source has local click listeners:
- Dice click is handled by `Dice` constructor and calls `roll()`.
- Piece click is handled by `Piece` constructor and calls `move()` / `makeAlive()`.

A proper companion remote requires a LAN transport (for example, a TCP/WebSocket server in the game app and a controller client on the other phone), plus safe main-thread dispatch into the existing dice and piece actions. This is not implemented by the workflow in `.github/workflows/android-build.yml`; that workflow only builds the original game APK so the source can be validated first.
