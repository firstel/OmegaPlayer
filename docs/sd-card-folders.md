# SD card folders

From the library, tap **SD card folders**, then **Select folder**. Open the
SD card in Android's picker and choose a media folder. Browse its subfolders,
tap a video or audio file to play it, and use **Up** or Android Back to return.
The browser remains available without the MediaStore media permissions.

Android 11+ may prohibit selecting a storage root; select a subfolder such as
Music or Movies instead. Access is read-only and persists across app/device
restarts. Changing folders keeps previous grants so earlier playback URIs remain
usable. Android Settings can revoke access. If access expires, or a card is
removed, the browser shows a recovery message; reinsert it and Refresh, or
select the folder again. Cancelling the picker preserves the old selection.

The browser queries immediate children on a background thread and shows folders
and media documents. Selecting a folder also recursively adds its videos to the
main Videos library, under labels such as SD card · Movies. It only scans the
selected tree, not the rest of the card. A toolbar refresh and app resume rescan
the library. Changing the selected folder replaces its contribution to the library.
MIME types identify media, with an extension fallback for providers reporting no
type or application container types such as application/x-matroska. Virtual documents are excluded. Playback uses
content URIs directly in the existing Media3 service. Codec support remains
that of the device/player. MediaStore scanning and library folders are unchanged.
SD videos participate in the app library, thumbnails, playlists and playback
history without being inserted into Android MediaStore. Read-only SAF items
do not expose the library’s destructive delete/locker actions. Video duration is
discovered during playback. Audio still falls back to a single-item queue when
absent from MediaStore. Compact screens hide the fixed storage panel to leave
room for the scrollable library, with search and folder controls in the toolbar.

## Verification

Automated: DocumentMediaTypeTest covers provider MIME precedence, generic MIME
fallback, case-insensitive extensions, directories and non-media exclusion.
Android verification workflow runs debug unit tests, APK assembly and lint.

Device checks (require Android hardware/emulator with a documents provider):

- Deny library permissions, select an SD-card folder, browse nested folders,
  and play an unindexed video and audio file.
- With an unrelated audio library folder selected, open an SD audio file;
  verify it plays the selected file rather than the library's first track.
- Use names containing spaces, plus, percent, #, and non-ASCII characters;
  verify navigation preserves the complete document URI.
- Back/Up at nested and root levels; return from playback; rotate/recreate the
  activity and verify browser location is restored.
- Force-stop/relaunch and reboot: reopen the browser without another grant.
- Cancel Change folder; confirm previous selection remains usable.
- Remove card, Refresh, reinsert card, Refresh; revoke permission and reselect.
- Empty directories, non-media-only directories, large folders and unavailable
  documents should remain responsive and show an empty/recovery state.
- Verify original MediaStore video/audio lists and playback still work.

### Verified build

Build commit `ee6443133a5c0f07ffc2b9a28bfdf45813823a2e` passed GitHub Actions:
https://github.com/firstel/OmegaPlayer/actions/runs/38795981656

- Android compile/target SDK 36, minimum SDK 25.
- AndroidX Core 1.18.0 and Hilt navigation Compose 1.3.0 avoid SDK 37-only dependencies.
- KSP 2.3.6 aligns with the Android Gradle Plugin 9 build.
- Debug APK assembly passed; all four unit tests passed.
- Lint completed with 0 errors, 63 warnings and 3 hints.
- Downloaded APK passed Android apksigner verification (v2 signature) and ZIP integrity checks.

The installable preview is a debug-signed build named **OmegaPlayer SD**, version
1.2.1-sdcard, with application ID `com.arslandaim.omegaplayer.sdcard`. It installs
alongside the original app without replacing its data. It is a universal APK.
Copy/download the APK to the Android device, open it, and permit installation
from that browser/file manager when Android asks. Open OmegaPlayer SD, tap
SD card folders, and choose the desired media folder on the SD card.
Physical AYN Thor playback and SD-card reboot/removal checks still require the device.


### Main-library preview 1.3.0

Verified source: `7fbc1a9aae965c1be1ffeaed17a4cae56f020169`.
Build: https://github.com/firstel/OmegaPlayer/actions/runs/38800919059
Release: https://github.com/firstel/OmegaPlayer/releases/tag/sdcard-preview-2

Assembly and all 9 tests passed. Lint: 0 errors, 65 warnings, 3 hints.
Tests cover recursive scanning, duplicate/cyclic entries, non-video exclusion,
root revocation, inaccessible children, cancellation and MIME classification.
The distributed APK passed v2/v3 signature verification and 16 KB ZIP alignment.
Thor hardware playback and compact layout still need device testing.

This preview introduced a retained local signing key. The earlier 1.2.1 preview
used another certificate, so Android cannot update it in place. Before removing
that earlier preview, export any vault videos and back them up: uninstalling
removes app-private vault files as well as settings and history. Shared SD-card
media and the separate original OmegaPlayer app are unaffected. Versions 1.3.0
and newer share the retained key and must be installed as updates without
uninstalling. On Home, open Videos, then SD card · Movies.

### Landscape UI preview 1.3.1

Source: `519a051d96c723aadf4ef34b71a61f7ac2183373`.
Build: https://github.com/firstel/OmegaPlayer/actions/runs/38805579338
Release: https://github.com/firstel/OmegaPlayer/releases/tag/sdcard-preview-3

The library now uses the explicit Folders heading and plural folder counts.
Wide, short windows use an 80 dp navigation rail; portrait retains bottom
navigation. PIN authentication uses a two-column prompt/keypad in landscape
with bounded 48–64 dp keys and scroll fallback for exceptionally short windows.
Setup/settings/security forms are bounded and scrollable with keyboard insets.
Media grids adapt their column count and the audio player places artwork beside
scrollable controls in landscape. Existing PIN verification and biometric
settings remain in use.

Assembly, all 9 unit tests and all 6 Android tests passed. Five Android tests
check PIN key visibility in 800×360 dp landscape, larger text and portrait,
key action order, populated-library labels/counts, and navigation to settings
and locker setup. Screenshots for those states were inspected. Lint completed
with 0 errors, 67 warnings and 3 hints. The signed APK passed v2/v3 verification
and 16 KB ZIP alignment. Physical Thor validation remains a device check.

Install 1.3.1 over the 1.3.0 preview without uninstalling: the retained signing
key and application ID match, preserving settings, PIN and selected-folder
access. Screenshots use emulator colors; device wallpaper colors can differ.

### Dual-screen controls and vault setup preview 1.4.0

Start OmegaPlayer SD on the Thor's top screen, open a video, tap the video to
show its controls, then choose **Bottom-screen controls**. A control-only Android
Presentation uses the existing player/session on a presentation-category second
display; it never creates another decoder or audio stream. It provides play/pause,
rewind/fast-forward and a seek bar. Use **Turn off bottom controls** on the video
to return the second display to the launcher. If Android reports no suitable
display, the app explains this and continues normal playback. Display IDs are
not hard-coded. Panel resources detach when dismissed, playback closes, the
activity stops, a display changes/disconnects, or the on-screen player lock is
engaged. Displaying it on a physical Thor, including touch routing, remains a
hardware validation step.

Vault creation now uses two shorter steps with PIN confirmation, recovery
question/answer, disabled actions until fields are valid, and a landscape
side-by-side introduction/form. It explains that app-private vault files are
removed by uninstalling or clearing app data. Original OmegaPlayer and OmegaPlayer
SD have different application IDs and independent vaults. Uninstalling normally
already removes the local private files; do not delete shared originals as a
cleanup measure. This update does not delete, migrate or recover any vault files.

Verified source: `53281258b71dcc8ecc2bb744a326fc148d9cc165`.
Build: https://github.com/firstel/OmegaPlayer/actions/runs/38812826290
Release: https://github.com/firstel/OmegaPlayer/releases/tag/sdcard-preview-4

APK assembly, all 9 unit tests and all 7 Android tests passed. The added virtual
display test shows a second-display panel, verifies it shares the player,
activates playback and detaches the player on dismissal. Setup tests enter and
confirm a PIN, advance to recovery, and check disabled incomplete actions.
Both landscape setup steps were captured and inspected; the complete recovery
form and action fit before scrolling. Lint: 0 errors, 67 warnings, 3 hints.
The release APK passed v2/v3 signature verification and 16 KB ZIP alignment.
SHA-256: `989c6bf41ec3c55492e30cef6a5c7f2cd4a660fcf3fe494a50becfbf7923da7e`.
Install over 1.3.0 or 1.3.1 without uninstalling.

### Complete transport controls preview 1.4.1

The bottom presentation now uses explicit transport buttons rather than the
standard controller's responsive button layout: −10 sec, Play/Pause, +10 sec,
Stop, Mute/Unmute, speed cycling from 0.5× to 2×, and Close. It also shows
elapsed/total time and a draggable seek bar. Seeking clamps to the media bounds;
Stop pauses, rewinds and stops, retaining the selected item for Play to restart.
The presentation removes its player listener and progress callbacks on stop.
Close cancels the panel and updates the top-screen toggle state. Short displays
have a scroll fallback.

Video items now carry a title, explicit movie media type and request URI across
the MediaSession boundary. Previously, numeric content URIs lacked extensions
and the mini-player guessed audio, displaying Unknown Track/Unknown Artist.
The video mini-player now receives correct classification, title and URI;
resume/history lookup also uses the preserved URI. No media files are created,
moved or deleted by this fix.

Verified source: `d0b5491cf926712b838ea909a504565d7ae63634`.
Build: https://github.com/firstel/OmegaPlayer/actions/runs/38815659615
Release: https://github.com/firstel/OmegaPlayer/releases/tag/sdcard-preview-5

APK assembly, 9 unit tests and 8 Android tests passed. Tests verify video title,
type and URI survive MediaItem serialization, while audio remains classified
as audio. The virtual-display test activates the explicit controls and checks
10-second seek targets and boundary clamping, mute/restoration, speed change,
Stop/reset and Close/detachment. Existing layout and setup checks also passed.
Lint: 0 errors, 68 warnings, 3 hints. Signed APK v2/v3 signatures and 16 KB ZIP
alignment verified with the retained preview signing key.
SHA-256: `43be2fd4b8782a2e515e1ecec86646e23579f4f01d0ea8b71b9feb2f863ebb83`.

### Automatic controls and thumbnail timeline preview 1.4.2

Second-display controls are enabled by default for each video URI. An available
presentation display is used automatically; no display IDs are hard-coded.
The manual top-screen toggle remains available. A different video re-enables
the automatic panel and resets the player and top-screen speed state to 1.0×.
**Close video** saves progress before stopping/clearing the session, dismisses
the panel and navigates back, removing the mini-player. Stop retains its existing
rewind-and-stop behavior.

The bottom panel adds volume down/up in 10% steps (bounded to 0–100%) and a level
indicator. Six 160×90 previews are extracted on one background worker using the
video URI and shown above the seek slider. Each cell seeks to its approximate
sample position. Preview generation is cancelled/invalidated on media changes
and panel disposal; no thumbnails or media copies are written to storage.
Unavailable previews leave playback usable. The panel scrolls in short windows.

Verified source: `da4098999168595fa13afab9a47b91ccdba43bf0`.
Build: https://github.com/firstel/OmegaPlayer/actions/runs/38818645609
Release: https://github.com/firstel/OmegaPlayer/releases/tag/sdcard-preview-6

APK assembly, all 9 unit tests and all 10 Android tests passed. Added checks cover
automatic presentation opening and re-enabling for a different video URI,
session clearing/close callback, reset from 2× to 1× when preparing a new item,
volume increments and upper clamping, and loading six real previews through a
MediaStore URI with a thumbnail tap mapped to its sample timestamp. The preview
test uses a generated 2-second test-pattern MP4 included only in test assets.
Existing PIN/library/metadata/transport checks also passed. Lint: 0 errors,
70 warnings, 3 hints. APK v2/v3 signature and 16 KB ZIP alignment verified.
SHA-256: `bd0435bd97ef7e31984280af7f27f2df07fd274560f9b95a02c723eb9573ba5b`.
Actual Thor thumbnail performance and final screen layout remain device checks.

### Media-volume accuracy and audio checks preview 1.4.3

The old 100% represented only Player.volume, which could disagree with Android's
media stream. The bottom controls now adjust AudioManager.STREAM_MUSIC and show
its actual rounded percentage/mute state, including hardware-button changes.
The primary video volume gesture uses the same stream. The bottom window directs
volume keys to media. MODIFY_AUDIO_SETTINGS is a normal manifest permission.
Device volume is never raised at startup. Video preparation resets residual
player attenuation, clears audio track overrides and re-enables audio selection.

The shared service player configuration enables decoder fallback. Ready videos
with missing, unsupported or unselected audio show an explanatory bottom-panel
message. This is diagnostic and uses the current platform decoder capabilities;
it does not add a software codec bundle. Sound returned on the installed 1.4.2
without an update; the intermittent silence has no confirmed root cause. Actual
Thor audio behavior still requires device validation.

PIN deletion now remains available during asynchronous verification. Deleting a
digit cancels the attempt and invalidates late results, so an edited PIN cannot
unlock from an earlier submission. Dots reflect the current input immediately.
PIN hashing runs off the UI thread. Vault creation shows saving progress and
prevents repeated submissions; failure leaves the form available for retry.
Setup PIN fields normalize numeric input to four digits instead of rejecting an
entire keyboard edit. Existing vault contents and PIN hashes remain compatible.

Verified source: `9187a01a7a41f2fa494ebc3b4f200c67ce3fd500`.
Build: https://github.com/firstel/OmegaPlayer/actions/runs/38826217628
Release: https://github.com/firstel/OmegaPlayer/releases/tag/sdcard-preview-7

APK assembly, all 9 unit tests and all 12 Android tests passed, including a delayed
verification/delete regression, AAC audio decoding/rendering, device media-volume
controls and existing landscape, presentation and thumbnail checks. Lint: 0 errors,
70 warnings, 3 hints. Emulator audio rendering does not verify audible sound on a
physical Thor. APK v2/v3 signatures and 16 KB ZIP alignment verified with the retained
preview key. SHA-256: `717f9a9e8006bd1f3e1c3dc0673c4a6c4d22adf471ae43aeeced8a3683cb83b7`.

### Watch-history availability preview 1.4.4

The home-screen recent row and full history list probe each saved URI on an IO
worker before displaying a playback card. Content URIs are opened read-only and
closed immediately, while local vault paths require an existing readable file.
Unavailable media, revoked permissions and missing SD cards are hidden without
deleting the history record. Returning to the screen refreshes availability, and
tapping a card checks again before navigation to handle removal since the scan.
Tests cover readable/deleted MediaStore and vault paths, unavailable SAF access,
filtering missing entries, removing a stale card on tap, and restoring access.

Verified source: `7dbd6648f0de379c63afc0eab54b09b8877ff8e4`.
Build: https://github.com/firstel/OmegaPlayer/actions/runs/38914634126
Release: https://github.com/firstel/OmegaPlayer/releases/tag/sdcard-preview-8

APK assembly, all 9 unit tests and all 14 Android tests passed. The initial test
run found a double-delete in fixture cleanup; cleanup was corrected and the full
suite passed. Lint: 0 errors, 71 warnings, 3 hints (the additional warning suggests
a KTX URI parsing convenience function). APK v2/v3 signatures and 16 KB ZIP
alignment verified using the retained preview key. SHA-256:
`a56aeffc2c741d49dd220397c1ed390169fa71292eda3eec5e3b846704e51ad0`.
Physical Thor removable-card behavior remains a device check.

### VR180 side-by-side preview 1.5.0

Manual VR180 SBS mode maps one half of an equirectangular side-by-side source
onto a 180-degree hemisphere. A GLES external-texture surface shares the current
MediaController/player; it does not start a second decoder or copy the movie.
The source eye can be changed without restarting playback. Source texture
transforms are applied and the rear hemisphere is black rather than repeating.
Touch dragging and lower-screen directional controls adjust a bounded view;
Recenter resets the offsets. Optional game/regular rotation-vector input uses
screen-orientation remapping and a fresh relative reference on recenter. Motion
is off by default, with an unavailable message when no rotation sensor exists.
Sensors pause with the activity; surface and sensor listeners detach on disposal.

The lower display retains existing transport, timeline, volume and close controls
and adds VR mode, eye selection, look direction, recenter and motion controls.
Normal playback remains the default for each video. This is a single-eye handheld
view, not stereoscopic headset output. Fisheye VR180, VR360 and automatic format
detection are outside this preview. High-resolution decode performance and Thor
sensor axes/calibration require a physical-device check.

Verified source: `1ae94157b84e62c355ac8e9823804684517fc594`.
Build: https://github.com/firstel/OmegaPlayer/actions/runs/38924165009 (attempt 2).
Release: https://github.com/firstel/OmegaPlayer/releases/tag/sdcard-preview-9

APK assembly, 9 unit tests and 16 Android tests passed. VR tests decode a synthetic
SBS video and use PixelCopy to verify left/right eye selection, upright orientation
and pause/resume rendering, plus mapping bounds/recenter. An initial run had a
transient missing Compose host in an existing PIN-layout test; the full rerun passed.
Lint: 0 errors, 72 warnings, 3 hints. APK v2/v3 signatures and 16 KB ZIP alignment
verified with the retained preview key. SHA-256:
`ab4a06772b6803bad9c5f891b1d10f5ca48437f4822f5b5084bbb9b686387687`.
Actual Thor sensor axes and high-resolution video performance remain device checks.

### Thumbstick/trigger preview 1.5.1

A shared main-thread controller handler receives joystick and L2/R2 key events
from both the player activity and the secondary Presentation. Left X/Y controls
VR look; right RZ (or RY on devices exposing that range) controls 1×–3× zoom.
A minimum 18% dead zone respects larger device flat ranges. Movement is integrated
with elapsed time rather than event count and stops on neutral input, player lock,
activity stop, Presentation stop or disposal. Recenter resets zoom to 1×.
L2/R2 analog axes (including BRAKE/GAS aliases) and digital button events share
press state, preventing double seeks or repeated skips while held. Trigger seeks
are clamped to duration and are also available during ordinary video playback.

Verified source: `6b0da3624cc3e0c00cd21ae436839b55e8533454`.
Build: https://github.com/firstel/OmegaPlayer/actions/runs/38934626799
Release: https://github.com/firstel/OmegaPlayer/releases/tag/sdcard-preview-10

APK assembly, all 9 unit tests and all 17 Android tests passed. Controller checks
cover real MotionEvent axes, dead zone, yaw/pitch/zoom, analog/digital trigger
coalescing, hold behavior, seek limits and player lock. Prior VR eye/orientation,
playback, history and layout checks passed. The library test fixture now publishes
a valid MP4 and tolerates an already-unavailable cleanup row. Lint: 0 errors,
73 warnings, 3 hints. APK v2/v3 signatures and 16 KB ZIP alignment verified using
the retained preview key. SHA-256:
`80920e5fcf20e88ac94c464f77fe8a0ae8370d720ace9b1edb8beca3e59fb2f2`.
Actual Thor built-in controller mapping remains a physical-device check.
