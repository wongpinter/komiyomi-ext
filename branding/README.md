# Komiyomi Ext

Komiyomi Ext is the extension-enabled manga reader that complements the standalone Komiyomi CBZ/CBR reader. The shared mark combines stylized hiragana **こ** (ko) and **よ** (yo); the `Ext` wordmark distinguishes this app.

- Launcher coral: `#F63E50`
- Launcher background: `#F0D5C0`
- App logo vermilion: `#B63736`
- Warm cream: `#FFF5E6`
- Wordmark ink: `#282321`

`komiyomi-ext-icon.svg` and `komiyomi-ext-logo.svg` remain the scalable sources for in-app branding. Android's launcher uses the selected solid coral icon set in its density-specific mipmap resources, with adaptive foreground and monochrome layers in `app/src/main/res/drawable-nodpi/`. `branding/android/komiyomi-ext-playstore-512.png` is the Play Store export. `komiyomi-reader-icon-reference.png` is retained only as a reference to the sibling reader's earlier mark.

The Android application ID is `com.wongpinter.komiyomi.ext` (`com.wongpinter.komiyomi.ext.dev` for debug). This is a new Android app identity; legacy `app.komikku` installations cannot update into it or share their private app data automatically. The visible name is Komiyomi Ext in every locale through an app-level resource alias. Upstream strings and translations are preserved.

Komikku, Mihon, and TachiyomiSY remain credited for inherited code and applicable open-source notices. Those credits describe source history, not product affiliation.
