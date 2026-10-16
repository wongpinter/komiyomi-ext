<div align="center">

<a href="https://github.com/wongpinter/komiyomi-ext">
  <img width="200" height="200" src="./branding/komiyomi-ext-icon.svg" alt="Komiyomi Ext icon"/>
</a>

<h1>Komiyomi Ext</h1>

<p>Browse manga from source extensions, read local CBZ and CBR archives, and manage your library on Android.</p>

<p>
  <a href="https://github.com/wongpinter/komiyomi-ext/releases/latest">Download latest release</a> ·
  <a href="https://github.com/wongpinter/komiyomi-ext/releases">All releases</a> ·
  <a href="https://github.com/wongpinter/komiyomi-ext/issues">Issues</a>
</p>

<p>Android 8.0 or newer</p>

<a href="./LICENSE">
  <img alt="Apache-2.0 license" src="https://img.shields.io/github/license/wongpinter/komiyomi-ext?label=License&labelColor=27303D&color=0877d2"/>
</a>

</div>

![Komiyomi Ext screenshots showing recommendations, cover-based colors, bulk library actions, and custom themes](./.github/readme-images/screens.png)

## About

Komiyomi Ext is an independently maintained Android manga reader for compatible source extensions, local CBZ/CBR archives, downloads, trackers, and a configurable reader. It has its own product identity, roadmap, releases, and services.

Komiyomi Ext is the extension-enabled app in the Komiyomi family. For a dedicated offline comic reader, see [Komiyomi](https://github.com/wongpinter/komiyomi).

## Features

- **Browse and discover manga:** Use compatible source extensions, search across sources, save searches, and explore feeds and recommendations.
- **Read online or offline:** Configure reading direction, viewer modes, and page loading; bookmark pages and navigate by chapter, section, or page. Smart Resume can continue to the next chapter after your chosen completion threshold, and separate Android tasks let you keep multiple readers open.
- **Manage a local CBZ/CBR library:** Scan a chosen folder and its subfolders without moving your archives. Group multi-part files into series, track progress and favorites, and browse with grid or list views, search, filters, and sorting.
- **Open large archives efficiently:** Komiyomi Ext caches archive indexes and loads pages as needed, which can make later opens of large CBZ files faster.
- **Download chapters:** Download in the background, set concurrent download limits, or queue all chapters. Optionally save downloaded chapters as CBZ files.
- **Organize and back up your library:** Use categories, tags, saved searches, batch actions, source migration, and library/settings backups.
- **Sync reading progress:** Connect trackers including AniList, Bangumi, Kitsu, Kavita, MangaDex, MangaUpdates, MyAnimeList, Shikimori, Suwayomi, and Komga.
- **Personalize the app:** Choose light, dark, or custom themes, including colors generated from a manga's cover.

Online source extensions are managed separately from this repository. Komiyomi Ext does not host or bundle manga content.

## Install

1. Download the latest Android APK from [Releases](https://github.com/wongpinter/komiyomi-ext/releases/latest).
2. Install it on a device running Android 8.0 or newer.
3. Open **Extensions** to add and manage compatible sources, or use the **Local** tab for your own archives.

## Set up a local CBZ/CBR library

1. Open **More → Settings → Data → Local manga folder**.
2. Choose the folder containing your CBZ or CBR files and grant access when Android asks.
3. Open the **Local** tab. Use **Rescan folder** after adding or changing files.

The selected folder may contain nested folders. Komiyomi Ext scans the archives where they are and does not move them. To save downloaded chapters as CBZ files under this folder, configure the option in **More → Settings → Downloads**.

## Build from source

The project uses JDK 21 and the Android SDK. To build a debug APK:

~~~bash
./gradlew assembleDebug
~~~

For development setup and contribution guidelines, see [CONTRIBUTING.md](./CONTRIBUTING.md).

## Issues and contributing

Report bugs and request features through the [issue tracker](https://github.com/wongpinter/komiyomi-ext/issues). Before opening an issue, check the [release notes](https://github.com/wongpinter/komiyomi-ext/releases) and existing issues.

Contributions are welcome. Please read [CONTRIBUTING.md](./CONTRIBUTING.md) before opening a pull request, and follow the [Code of Conduct](./CODE_OF_CONDUCT.md).

## Project lineage and credits

Komiyomi Ext is a separate, independently maintained project. Its codebase includes work from [Komikku](https://github.com/komikku-app/komikku), [Mihon](https://github.com/mihonapp/mihon), and [TachiyomiSY](https://github.com/jobobby04/TachiyomiSY). These links record source history and contributor attribution; they do not imply affiliation, endorsement, or ongoing synchronization.

See the [Komikku contributor history](https://github.com/komikku-app/komikku/graphs/contributors), [Mihon contributors](https://github.com/mihonapp/mihon/graphs/contributors), and [TachiyomiSY contributors](https://github.com/jobobby04/TachiyomiSY/graphs/contributors). Additional credits are available in the app's **About** section.

## Disclaimer

Komiyomi Ext is not affiliated with content providers. The app hosts no manga or other content; users access sources and files they choose.

## License

Komiyomi Ext is licensed under the [Apache License 2.0](./LICENSE). See the license file for terms and copyright notices.
