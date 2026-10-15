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

- Join [the Discord server](https://discord.gg/85jB7V5AJR) for online help and to ask questions while developing.

# Translations

Komiyomi Ext-only strings are maintained in `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`. Upstream translations remain credited to Komikku and are not edited in this repository.


# Forks

Komiyomi Ext is an independent fork of Komikku, Mihon, and TachiyomiSY. Contributions must follow this repository's Apache-2.0 license and retain relevant upstream notices.

When creating a fork, remember to:

- The app is branded **Komiyomi Ext** to distinguish it from the standalone **Komiyomi** CBZ/CBR reader.
- The application ID remains `app.komikku` so compatible existing installs can upgrade and retain app data. Changing it creates a separate Android app identity.
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
10. Click Create credentials -> Oauth client ID
11. Select Android, give it a name, and set `eu.kanade.google.oauth` as the package name
12. To get the SHA-1 key, run `keytool -printcert -jarfile app-standard-universal-release.apk` on your apk, and copy the listed SHA-1
13. Expand advanced settings, and enable Custom URL scheme
14. After that just download the json, name it to `client_secrets.json` and put it in `app/src/main/assets/`
