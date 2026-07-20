# Muse open-source UI port

## Primary source

- Project: Symphony
- Repository: https://github.com/zyrouge/symphony
- Commit: `dd04b872b8b4e6dd56172c053a5776c4d56ad080`
- License: GNU Affero General Public License v3.0 only (AGPL-3.0-only)
- License copy: `licenses/SYMPHONY-AGPL-3.0.txt`

GitHub cloning repeatedly failed because the connection was reset, so the exact commit archive was downloaded from GitHub codeload and inspected locally under `.reference-ui/symphony-full`.

## Ported UI patterns/code

- `NowPlayingBottomBar.kt` → Muse `MiniPlayerBar.kt`: animated song changes, edge-to-edge elevated surface, artwork/title/artist/control row, top progress strip.
- `SongCard.kt` → Muse `SongListItem.kt`: transparent Material card, 48dp rounded artwork, two-line metadata hierarchy, trailing overflow action, current-song highlight.
- `Home.kt`, `home/Songs.kt`, `home/Playlists.kt` → Muse `HomeScreen.kt`: Material scaffold, top app bar, Songs/Library navigation bar, play/shuffle actions, list-first media library destinations.
- `nowPlaying/BodyContent.kt` and `BottomBar.kt` informed the full-player control hierarchy retained in Muse's adapted player implementation.

Muse domain models and callbacks replace Symphony's services/navigation. Network, account, database, radio, and other unrelated business code was not copied.

## Existing Muse functionality retained

Playback, scanning, playlists, lyrics, WebDAV, wallpapers/video backgrounds, equalizer and player callbacks remain wired through the existing `MusicViewModel` and player implementation. No signing key or core repository/player implementation was changed.

## Copyleft notice

Files derived from Symphony are marked in source headers. The resulting combined application includes AGPL-covered adapted code. If the app is conveyed/distributed, AGPL source-availability and notice obligations must be satisfied.
