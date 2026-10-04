# Super Bros — Retro Runner (Android)

A self-contained original Android side-scrolling platform game written in plain Java. No external game engine or third-party runtime is required.

## Gameplay
- Procedurally generated, progressively harder levels
- Smooth fixed-timestep physics with coyote time and jump buffering
- Touch controls with proper multi-touch handling
- Enemies, stomping, coins, extra lives, question blocks and breakable blocks
- Power gems that make the hero bigger and allow brick breaking
- Particles, parallax scenery, animated flag and score/best-score saving
- Android back button and app backgrounding pause the game safely
- Landscape fullscreen presentation

All graphics are original shapes drawn by the game engine; no Nintendo/Mario copyrighted assets are included.

## Build on GitHub
Every push to `main` starts the **Build Super Bros APK** GitHub Actions workflow. It uses Java 17 and Gradle 8.7, builds the debug APK, verifies that the APK exists, and uploads it as `super-bros-debug-apk`.

Manual build is also available from **Actions → Build Super Bros APK → Run workflow**.
