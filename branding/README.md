# Komiyomi Ext

Komiyomi Ext is the extension-enabled manga reader that complements the standalone Komiyomi CBZ/CBR reader. The shared mark combines stylized hiragana **こ** (ko) and **よ** (yo); the `Ext` wordmark distinguishes this app.

- Vermilion: `#B63736`
- Warm cream: `#FFF5E6`
- Wordmark ink: `#282321`

`komiyomi-ext-icon.svg` and `komiyomi-ext-logo.svg` are the scalable source assets. Android uses matching vector assets for its adaptive launcher icon, splash screen, notification icon, and monochrome themed icon. `komiyomi-reader-icon-reference.png` is retained only as a reference to the sibling reader's earlier mark.

The application ID remains `app.komikku` (`app.komikku.dev` for debug) so compatible existing installations retain folder grants, library, preferences, and reading progress. Its visible name is Komiyomi Ext in every locale through an app-level resource alias. Upstream strings and translations are preserved.

Komikku, Mihon, and TachiyomiSY credits and open-source licenses remain available in About. The app package ID and upstream protocol schemes remain unchanged for compatibility.
