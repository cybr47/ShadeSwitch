# ShadeSwitch for One UI 9.5 motion

ShadeSwitch is an LSPosed module for the Samsung notification shade and Quick Settings. The `ui-9.5` branch brings motion behavior observed in the `S948BXXU6ZZKN` SystemUI beta to the older One UI 9 panel implementation.

- **Separate mode** follows Samsung's native transition with wider diagonal capture, a short window to correct an initial downward gesture, and a release decision based on swipe velocity or distance. The fullscreen QS opening uses Samsung’s touch animator with the beta’s easing, 24 dp travel and a header fade that completes at 45% expansion. During a split switch the header controls follow the beta’s full-range fade, with 15 px of horizontal travel or 30 px on a downward switch.
- **Together mode** uses a damped 400 ms return from overdrag. **Hide Quick QS row** continues to hide the compact tiles independently.
- **Vertical swipe to switch panels** replaces horizontal switching with the existing `v2` header and date row gesture. Restart SystemUI after changing this setting or the compact tile row setting.

During shade movement, ShadeSwitch defers the media player’s decorative wave redraws in both panels. The native playback position, artwork, touch controls and settled waveform still update normally.

Older panel layouts post constraint updates at the start and end of a switch and when the layout changes. Repeated intermediate slide samples are skipped. The final panel states and overdrag still run through Samsung’s callbacks. The One UI 9.5 panel contract uses Samsung's implementation for motion.

The module keeps the `com.samsung.music.qs.lagfix` package ID and libxposed API 102 scope. The older bindings were checked against the Galaxy S25 One UI 9 SystemUI APK. SystemUI still renders the S25’s own view hierarchy and Compose scene. Device testing is needed to measure first-pull and transition frame times.
