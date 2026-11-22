# ShadeSwitch for One UI 9.5 motion

ShadeSwitch is an LSPosed module for the Samsung notification shade and Quick Settings. The `ui-9.5` branch brings motion behavior observed in the `S948BXXU6ZZKN` SystemUI beta to the older One UI 9 panel implementation.

- **Separate mode** follows Samsung's native transition with wider diagonal capture, a short window to correct an initial downward gesture, and a release decision based on swipe velocity or distance. The fullscreen QS header and blur follow the expansion fraction with a 24 dp travel and an early header fade.
- **Together mode** uses a damped 400 ms return from overdrag. **Hide Quick QS row** continues to hide the compact tiles independently.
- **Vertical swipe to switch panels** replaces horizontal switching with the existing `v2` header and date row gesture. Restart SystemUI after changing this setting or the compact tile row setting.

Older panel layouts post constraint updates at the start and end of a switch and when the layout changes. Animator callbacks retain their order. The One UI 9.5 panel contract uses Samsung's implementation for motion.

The module keeps the `com.samsung.music.qs.lagfix` package ID and libxposed API 102 scope. The older bindings were checked against the Galaxy S25 One UI 9 SystemUI APK. Device testing is needed to measure first-pull and transition frame times.
