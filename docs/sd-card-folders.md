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

The browser queries immediate children on a background thread and only shows
folders and audio/video documents. It does not recursively scan the whole card.
MIME types identify media, with an extension fallback for providers reporting no
type or application/octet-stream. Virtual documents are excluded. Playback uses
content URIs directly in the existing Media3 service. Codec support remains
that of the device/player. MediaStore scanning and library folders are unchanged.
SAF-only files are not added to the MediaStore library or its history/locker
metadata; audio falls back to a single-item queue if absent from the library.

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
