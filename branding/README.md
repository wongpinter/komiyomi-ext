# Komiyomi Ext

Komiyomi Ext is the extension-enabled manga reader that complements the standalone Komiyomi CBZ/CBR reader. The shared mark combines stylized hiragana **こ** (ko) and **よ** (yo); the `Ext` wordmark distinguishes this app.

- Mark coral: `#F63E50`
- Mark background: `#F0D5C0`

The current brand artwork comes from the supplied Koyo unified asset pack. `android/komiyomi-ext-playstore-512.png` is the exact 512 px light-background export and is used in the root README. Android's launcher and in-app logo use the matching solid coral transparent mark from the pack in `app/src/main/res/drawable-nodpi/`. The adaptive monochrome and notification icons use the pack's solid black transparent mark. `komiyomi-reader-icon-reference.png` is retained only as a reference to the sibling reader's earlier mark.

Do not use the removed hand-drawn SVG drafts; they do not match the supplied logo artwork.

The Android application ID is `com.wongpinter.komiyomi.ext` (`com.wongpinter.komiyomi.ext.dev` for debug). This is a new Android app identity; legacy `app.komikku` installations cannot update into it or share their private app data automatically. The visible name is Komiyomi Ext in every locale through an app-level resource alias. Upstream strings and translations are preserved.

Komikku, Mihon, and TachiyomiSY remain credited for inherited code and applicable open-source notices. Those credits describe source history, not product affiliation.
