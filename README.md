![MusicMonster](public/preview/favicon.png)

# MusicMonster

MusicMonster is a local-first browser music player built as a personal hobby project.

It is designed for people who want to browse and play their own music library in a clean dark interface, keep everything local, and avoid external services or cloud dependencies. The application focuses on local folder imports, offline JSON database storage, queue management, playlists, metadata-based browsing, and a compact music-centered workflow.

## Project status

MusicMonster is **work in progress**.

This project is not finished. Some areas are already usable and work well, while others are still being refined. You should expect rough edges, missing features, incomplete workflows, and occasional bugs. Behavior may change between versions as the project continues to evolve.

This software is provided mainly as an experimental personal project and proof of concept, not as a polished production release.


## Key features

- Local music library import from folders and subfolders
- Offline JSON database export and import
- Album, artist, genre, folder, title, history, favorites, playlists, and top views
- Queue-based playback with bottom player controls
- ID3 metadata parsing for supported files
- Album cover detection from tags and folder artwork
- Playlist creation and M3U import/export
- Local asset loading with no required external CDNs
- Accent color customization
- Search across the local library
- Custom library sections

## Previews

![Preview 1](preview/preview1.jpg)
![Preview 2](preview/preview2.jpg)
![Preview 3](preview/preview3.jpg)
![Preview 4](preview/preview4.jpg)
![Preview 5](preview/preview5.jpg)
![Preview 1](preview/preview6.jpg)
![Preview 2](preview/preview7.jpg)
![Preview 3](preview/preview8.jpg)
![Preview 4](preview/preview9.jpg)
![Preview 5](preview/preview10.jpg)
![Preview 5](preview/preview11.jpg)


## Design goals

MusicMonster is built around a few simple ideas:

- **Local first**: your files stay on your system
- **Browser based**: no heavyweight desktop install required for the core app structure
- **Media focused**: quick access to albums, tracks, queue, and playlists
- **Visually clean**: dark UI, compact controls, and a music-centered layout
- **Practical over perfect**: features are added and refined iteratively

## Intended use

MusicMonster is intended for:

- private local music collections
- personal music library browsing and playback
- testing UI and workflow ideas for a browser-based music application
- hobby and learning purposes

It is **not** presented as enterprise software, a commercial media platform, or a guaranteed long-term stable release.

## Installation and setup

1. Download or extract the project files.
2. Place your local font files in `assets/fonts/` if needed.
3. Place your own logo and favicon in `assets/media/` if you want to customize branding.
4. Open `index.html` in a modern browser.
5. Import a music folder from within the app. (works bes with single Library Folder like "MUSIC" which contains additional subfolders)

Depending on browser behavior and local file access limitations, some features may work better in Chromium-based browsers than in others.

## Folder structure

Typical important folders in the project:

- `assets/css/` – styling
- `assets/js/` – application logic
- `assets/fonts/` – local font files
- `assets/media/` – logo, favicon, and visual assets

## Recommended local fonts

The project is prepared for local fonts. The intended setup is:

- `assets/fonts/googlesansregular.woff2`
- `assets/fonts/googlesansbold.woff2`

If those files are missing, the browser will fall back to available system fonts.

## Current limitations

A few important limitations are worth stating clearly:

- Large libraries can still take time to process depending on browser performance and file count.
- Some metadata and cover behavior depends on file format and how tags were written.
- Not every feature is fully optimized yet.
- Some workflows may still behave inconsistently in edge cases.
- UI layout and performance tuning are still in progress.

## Disclaimer

MusicMonster is a **private hobby project**.

It is provided **as is**, without any warranty, guarantee, or promise of fitness for a particular purpose. The author does not guarantee that the project is complete, bug-free, secure, stable, or suitable for any specific workflow.

You use this project entirely at your own risk.

The author is not liable for:

- data loss
- playback issues
- corrupted libraries or metadata
- compatibility problems
- browser-specific issues
- interruptions, bugs, or unexpected behavior
- any direct or indirect damages resulting from the use or inability to use the project

## Non-commercial project notice

This project is currently released for **private, personal, and non-commercial use only**, under the terms described in the license file.

Commercial use is **not allowed** unless separate written permission is granted by the copyright holder.

## Contributing

There is currently no formal public contribution workflow.

If you adapt or test the project privately, keep in mind that the codebase is still moving and parts of it may change significantly.

## Credits

MusicMonster is a personal hobby project created and maintained by the complicatiion aka sksdesign aka sven404.

If you redistribute or adapt it where the license permits, proper attribution is required.

## Final note

This project exists because building and refining local media tools is fun.

It is intentionally practical, experimental, and still growing.


