# Branding verification

The README now uses the 512 px light-background logo export from the supplied Koyo asset pack. Its file hash matches the pack's `android/solid_coral_on_light_F0D5C0/ic_launcher_playstore_512.png` export. The launcher and in-app logo use the pack's transparent coral artwork; adaptive monochrome and notification icons use its transparent black artwork.

The supplied artwork was visually inspected. `:app:processDebugResources` passed after the Android drawable replacements. Android launcher, splash, and in-app rendering were not re-tested on a device; no APK build or automated tests were run for this correction.
