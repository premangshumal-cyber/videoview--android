# KinePulse — VLC-style Android reconstruction status

**Branch:** `vlc-style-reconstruction`  
**Status:** Compilable functional prototype; **not** VLC 3.7.2 or a replacement for official VLC for Android.  
**Application ID:** `com.kinepulse.player` (unchanged to avoid impersonating official VLC).  
**Build:** GitHub Actions `build-vlc-preview.yml` builds a debug APK using JDK 17 / Gradle 8.9.

## Implemented in this branch

- Existing functional five-tab KinePulse application (Video, Audio, Browse, Playlists, More).
- Existing VideoView player, Picture-in-Picture, gesture controls, bookmarks, subtitles and playback history preserved.
- Four distinct audio catalog tabs: Artists, Albums, Tracks and Genres. Artist/album selection opens a track chooser.
- Audio queue playback with play-all/shuffle ordering, next/pause notification controls and audio focus loss/ducking.
- Orange UI primary `#FF8800`, secondary `#FFA11A`, dark surface `#212121`; existing light/dark/black setting preserved.
- Optional local Wi-Fi catalog using embedded Ktor CIO on port 4242.
- Device-displayed random six-digit pairing PIN and 15-minute session cookie; no session tokens returned in catalog URLs.
- Read-only paired endpoints: `GET /video-list`, `GET /track-list`; publicly accessible `GET /` (portal), `GET /health`, `POST /code`, `POST /verify-code`.
- Login rate-limit after ten invalid PIN attempts until service is restarted.
- Explicit enable/disable UI under More → Local Wi-Fi remote access. Foreground notification indicates active server.

## Security boundaries

This is a local-network **HTTP** prototype; pairing occurs in plaintext and should only be used on trusted Wi-Fi. There is no TLS, no WebSocket endpoint, no file upload/download, no unrestricted storage browsing, no remote delete and no remote playback commands. Do not open port 4242 on the public internet. The service requires an explicit user interaction and is subject to Android 15 foreground `dataSync` time limits.

Before adding file operations: implement HTTPS, origin/CSRF defenses, per-IP throttling and lockouts, restricted Storage Access Framework roots, request-size quotas, explicit per-operation user grants and audit logging. A four-digit PIN by itself is not sufficient security for remote file access.

## Planned milestones — not yet implemented

| Subsystem | Status | Remaining work |
| --- | --- | --- |
| UI: 5 tabs | Existing | Mini-player bottom sheet, expanded player, polished media grids |
| Video engine | Existing VideoView | Replace with LibVLC or Media3, validate decoding and external track support |
| Audio engine | MediaPlayer | MediaSession playback state, full mini-player, seek, repeat, 10-band EQ and widgets |
| Scanner | Existing MediaStore | Full SD/USB crawler, album artwork/genre indexing, deduplication |
| Remote access | Read-only prototype | HTTPS, WebSocket, remote commands, uploads, downloadable media and ACL |
| Network browsing | Not implemented | SMB/NFS/FTP/SFTP/DLNA/UPnP clients and discovery |
| TV/Auto | Not implemented | Leanback, TV search, Android Auto |
| Subtitle services | Existing local parsing | OpenSubtitles integration, subtitle download & delay UI |
| Persistence | Existing Room | Equalizer, widget, detailed subtitle and playlist schema migrations |
| Platform | minSdk 26 / target 35 | Assess requirements before raising minSdk to 32 and targetSdk to 36 |
| Tests | Existing unit suite | Connected instrumentation, media codec matrix, Wi-Fi server security tests |

## UI flow summary

`MainActivity` → Video / Audio / Browse / Playlists / More.  
Audio → Artists / Albums / Tracks / Genres → queue playback via `AudioPlaybackService`.  
More → `RemoteAccessActivity` → explicitly started `RemoteAccessService` → paired read-only Ktor catalog on port 4242.

The three SVG diagrams supplied alongside the specification are architectural references, not imported executable assets.

## Licensing and project identity

This work retains KinePulse branding and its existing application ID. The official VLC Android application has an independent source repository and GPL licensing obligations: https://github.com/videolan/vlc-android. Directly reusing official VLC source/assets requires compliance with their licenses and trademarks.

## Validation

A successful `assembleDebug` checks compilation and packaging only. Installation, playback, streaming, background runtime behavior and security require testing on Android devices. Do not label this build production-ready until those tests pass.
