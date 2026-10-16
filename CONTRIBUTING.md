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


### Supporting Cloud Sync - Google Drive Implementation
1. Go to [Google Cloud Console](https://console.cloud.google.com)
2. Create a new project
3. Go to API & Services -> Library -> Google Drive API and click enable
4. Go to API & Services -> Oauth consent screen
5. Create it, fill in the app name, user support email, and developer contact information
6. In the next screen, click add or remove scopes, and add the `.../auth/drive.appdata` and `.../auth/drive.file` scopes
7. Don't add any test users and go back to the dashboard
8. Click publish
9. Go to API & Services -> Credentials
10. The app currently reads `client_secrets.json` from `app/src/main/assets/` and uses the callback `com.wongpinter.komiyomi.ext:/oauth2redirect`.
11. The current implementation uses a custom URI scheme for Google OAuth. Google no longer supports custom-scheme redirects on Android; migrate the Google Drive sign-in flow to a supported Android OAuth method before public release. See [Google's native app OAuth guidance](https://developers.google.com/identity/protocols/oauth2/native-app).
