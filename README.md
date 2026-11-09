# Arrow Route (Grid Rush clone)

Kotlin + Jetpack Compose puzzle game.

- Tap an arrow: if the path in front of its head is clear, it slides out (+2 coins).
- If it is blocked, it flashes red and you lose a heart (3 hearts per level).
- Clear every arrow to finish the level. Levels get bigger and harder.
- Zoom slider + drag to pan, hint button, Classic / Neon themes, sound + vibration settings.

Build: push to GitHub, the workflow in `.github/workflows/build.yml` builds `app-debug.apk`
(Actions tab -> latest run -> Artifacts -> ArrowRoute-debug-apk).
