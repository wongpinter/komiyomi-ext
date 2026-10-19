Looking to report an issue or request a feature? Use [Komiyomi Ext's issue tracker](https://github.com/wongpinter/komiyomi-ext/issues).

---

Thanks for your interest in contributing to Komiyomi Ext!


# Code contributions

Pull requests are welcome!

If you're interested in taking on [an open issue](https://github.com/wongpinter/komiyomi-ext/issues), please comment on it so others are aware.
You do not need to ask for permission nor an assignment.

## Prerequisites

Before you start, please note that the ability to use following technologies is **required** and that existing contributors will not actively teach them to you.

- Basic [Android development](https://developer.android.com/)
- [Kotlin](https://kotlinlang.org/)

### Tools

- [Android Studio](https://developer.android.com/studio)
- Emulator or phone with developer options enabled to test changes.

## Getting help

- For Komiyomi Ext questions, use the [project issue tracker](https://github.com/wongpinter/komiyomi-ext/issues).

# Translations

Komiyomi Ext-only strings are maintained in `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`. Some translations originated with Komikku and retain their attribution; translation changes are maintained in this repository and are not synced from Komikku.


# Forks

Komiyomi Ext is independently maintained and is not synchronized with the Komikku repository. Its code history includes work from Komikku, Mihon, and TachiyomiSY. Contributions must follow this repository's Apache-2.0 license and retain relevant notices.

When creating a fork, remember to:

- The app is branded **Komiyomi Ext** to distinguish it from the standalone **Komiyomi** CBZ/CBR reader.
- The application ID is `com.wongpinter.komiyomi.ext`. It is separate from the legacy `app.komikku` identity; Android treats it as a separate app, so existing installs and app data do not transfer automatically. Google OAuth credentials must also be configured for the new app identity.
- The update checker targets the Komiyomi Ext repository. Keep it pointed at this project when changing release workflows.
- Keep telemetry and crash-reporting endpoints project-owned; do not send Komiyomi Ext data to upstream services.


### Google Drive sync

Google Drive sync is temporarily disabled. The current sign-in uses an unsupported custom URI callback, so the app hides the provider, disables saved selections, and does not register the callback activity. Re-enable it only after migrating sign-in to a supported Android OAuth flow and validating release credentials. See [Google's native app OAuth guidance](https://developers.google.com/identity/protocols/oauth2/native-app).
