# NetEase online mode

Muse keeps local media and remote media in separate state paths. `MusicMode.ONLINE`
selects the NetEase recommendation home and remote playlist library; switching back
to `LOCAL` does not alter the device media database or local playlists.

## Reference implementations and licenses

The authenticated NetEase request shape and playback flow were researched against:

- [lladlam/MeloX-Android](https://github.com/lladlam/MeloX-Android), GPL-3.0.
- [NEORUAA/Mei_MeloX_Android](https://github.com/NEORUAA/Mei_MeloX_Android), GPL-3.0.
- [lyswhut/lx-music-desktop](https://github.com/lyswhut/lx-music-desktop), GPL-3.0.
- [qier222/YesPlayMusic](https://github.com/qier222/YesPlayMusic), MIT.

Muse is distributed as open source. The transport is isolated in
`online/NeteaseOnlineClient.kt`; future changes must retain the upstream notices
and the applicable GPL-3.0/MIT license text when code is copied rather than merely
used as a protocol reference.

## Credentials

The WebView login imports the complete `Cookie` header and verifies it with the
account endpoint before saving. It is stored in encrypted preferences when Android
Keystore is available. NetEase credentials are intentionally excluded from Muse
portable backups and settings sync.

## Endpoint scope

The current vertical slice covers recommendations, user playlists, playlist detail,
search, lyrics, and quality-aware playback URL resolution. NetEase can change or
restrict these private endpoints; errors are surfaced to the online screen and do
not prevent local playback.
