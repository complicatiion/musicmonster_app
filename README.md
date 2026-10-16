![MusicMonster](/preview/favicon.png)

# MusicMonster

**A customizable, local-first music player for Android and Android TV.**

MusicMonster brings your personal music collection together in one place. Browse albums, tracks, artists, genres and folders; manage favorites and playlists; and enjoy music through a compact Mini Player or a full-screen player with artwork, playback controls and optional audio visualization. Local files remain on your device or selected storage location—MusicMonster does not provide or bundle music.

![MusicMonster](/preview/preview-collage.png)

## Highlights

- **Personal music library** — Browse albums, tracks, folders, genres, artists, podcasts, soundtracks (OST) and custom collections.
- **Local and network music** — Scan music stored on your device or access network folders, including NAS/SMB shares exposed through compatible Android document providers.
- **Flexible playback** — Mini Player, Big Player, playback queue, shuffle, repeat, favorites and Android media-notification controls.
- **Android Auto and TV support** — Car-friendly playback integration, landscape layouts, D-pad/remote navigation and keyboard shortcuts.
- **Personalized appearance** — AMOLED and Light modes, accent colors, Glass/Balanced/Solid surfaces, live blur where supported, ambient glow and adjustable borders.
- **Library management** — Automatic or manual scanning, customizable section visibility, saved playlists, favorites, play counts and listening history.
- **Built-in utilities** — Sleep Timer, equalizer, Big Player visualizer, settings backup and contextual music-file actions.

## Navigation

| Section | What you'll find |
| --- | --- |
| **Home** | Discovery, Favorites, History, Top and personalized library shortcuts. |
| **Library** | Albums, Tracks, Folders, Genres, Artists, Podcasts, OST and optional custom or web collections. |
| **Search** | Search across your indexed music library. |
| **Playlists** | Create, manage, import and export playlists. |
| **Settings** | Appearance, library sources, playback, automation, remote controls, imports/exports and app information. |

Home and Library sections can be reordered or individually hidden in Settings without deleting the underlying music data.

## Music Library

### Local collections

MusicMonster works with Android's media library and folders selected by the user. Separate locations can be used for the main music collection, podcasts, soundtracks and custom folders. Manual rescans and configurable automatic scanning help keep the library up to date.

Tracks are organized using available audio metadata, including titles, artists, albums and genres. Album art is displayed where available, with an in-app fallback when artwork is missing.

### Network collections

MusicMonster can browse music from mounted network locations using a compatible Android document provider, such as an SMB-capable file manager. Network music remains on the share; the app retains a **local metadata index** so your library entries remain visible when a NAS or network share is temporarily offline.

**An offline index is not an offline audio copy:** streaming or playing a file still requires access to the original network location. Network sources can be rescanned to discover library changes.

### Favorites, history and playlists

Mark individual tracks or groups of tracks as favorites, revisit listening history, and organize music into playlists. The playback queue supports adding music as you browse. Favorites, playlists and library preferences are saved locally.

## Playback

MusicMonster provides several ways to control playback:

- **Mini Player:** quick access to the current track while browsing.
- **Big Player:** large album artwork, track information, interactive seek bar, shuffle, repeat, favorites and playback controls.
- **Notification Player:** playback controls through Android's system media notification.
- **Android Auto:** media controls and configurable phone-screen behavior when an Android Auto connection is detected.

The Big Player supports **swiping the cover** to skip tracks and **double-tapping** to toggle a favorite, with a short visual confirmation. Smooth transitions and animations can be customized or disabled.

### Audio tools

- **Equalizer:** audio adjustment controls integrated into the player.
- **Audio Visualizer:** optional spectrum, bar and waveform-inspired styles in the Big Player only. Android may request audio-capture permission to enable visualization.
- **Sleep Timer:** choose a preset or custom duration to pause playback automatically, with an optional display-timeout behavior.
- **Always On Display:** keep the screen awake manually or conditionally when Android Auto is connected.

## Track and Folder Actions

Press and hold supported tracks, albums or folders to open a compact action menu. Available actions depend on the selected item and its storage permissions and may include favorites, queue or playlist actions, equalizer access, sharing and deletion.

A lightweight **MP3 ID3 tag editor** supports basic fields such as title, artist, album and genre for compatible, writable local MP3 files. Batch editing is available for supported album and folder selections. Android may ask you to confirm file modifications or deletion. Network sources and read-only document providers may restrict these actions.

## Appearance & Personalization

MusicMonster combines Material-inspired navigation with extensive visual customization:

- **AMOLED MODE** and **LIGHT MODE**.
- Preset and custom **accent colors**.
- **Solid**, **Balanced** and **Glass** card styles with adjustable transparency and background intensity.
- Live background blur on supported devices, including navigation surfaces and supported overlay menus, with a readable fallback where blur is unavailable.
- Optional **card borders**, including opacity and accent-color settings.
- **Ambient Glow**, animations and adjustable transition speed.
- Customizable navigation and player-control appearance.
- Landscape and TV layout adjustments, including UI scale and album-grid density.

The interface is designed for touch interaction while also supporting keyboards, D-pads and compatible TV/IR remotes. Focus highlights help identify the selected control on larger screens.

## Import & Export

MusicMonster offers local backup and transfer options for **settings, favorites and playlists**. Settings and favorites use MusicMonster's `.mm` file extension for their structured backup data; playlist collections can be exported together as a ZIP archive. These exports are for app data and playlist information, **not copies of the original music files**.

## Privacy & Permissions

MusicMonster is built around personal and local music collections. No cloud account is required for local playback. Library information and preferences are kept on the device, while audio files remain in their original storage locations.

Certain features require Android permissions—for example, local media access, selected folders or audio visualization. Optional web-library entries may open third-party services, which follow their own availability, sign-in requirements and privacy policies. MusicMonster is not affiliated with those services.

On first use, MusicMonster displays its legal information for acceptance. The **EULA, Privacy Policy, Legal Notice and Copyright information** remain accessible through the in-app information screen.

## Important Notes

- MusicMonster does not include or distribute music files.
- You are responsible for having the necessary rights to the audio you play or manage.
- Available audio formats, artwork, network connectivity and media access can vary by device, storage provider and file metadata.
- Offline library visibility does not guarantee offline playback of network-hosted files.
- Editing tags and deleting files modify the original audio collection; keep backups of important files.
- Some visual effects and Android Auto/TV features depend on device and system support.

## Project & Copyright

MusicMonster is a personal software project by **Sven Klocke** (*complicatiion / sksdesign*).

**Project repository:** [github.com/complicatiion/musicmonster_app](https://github.com/complicatiion/musicmonster_app)  
**Website:** [sksdesign.de](https://sksdesign.de)

Copyright © 2026 Sven Klocke. All rights reserved. Usage, redistribution and third-party component licenses are governed by their respective license notices and the legal documents supplied with the application.
