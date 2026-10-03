# Sonder 1.2.0 validation

Native Android application, package `app.sonder.audiobooks`. Android 9+ (min SDK 28), target SDK 36. Tests ran on an isolated Android 16/API 36 x86_64 emulator. No physical phone was attached.

## Completed checks

- Debug APK, optimized release APK, release AAB, and test APK built successfully with Java 17 and Gradle 8.14.3.
- Seven JVM tests passed: Nero MP4 chapters, real QuickTime chapter tracks, ID3 CHAP, multi-file CUE, malformed MP4 data, natural filename sorting, and real length-prefixed FLAC/Vorbis/Opus comments with long chapter names.
- Twenty Android integration tests passed: actual MP4 import and chapter metadata, eight common audio formats, transactional rollback, progress/bookmark remapping after track insertion, mixed-folder album grouping, recursive document-provider scanning/CUE/duplicate detection, service playback and timer behavior, and landscape/200% text layouts.
- New tests covered long presses on Continue Listening and grid/list rows; not-started/in-progress/finished filters; resetting an actively playing book without a late autosave restoring progress; bookmark/listening-statistics retention; status backup and reopening; migration from the version-two database; actual MediaStore audio/video discovery; selective import through the scan sheet; and duplicate identity between the media index and Android document picker.
- Player test confirmed a media notification, playback after the Activity entered the background, persisted progress, bookmark note input, 1.5× speed, chapter-end pause, and Activity recreation.
- Six reading-history tests covered independent manual/partial records, editing and deletion, durable completion prompts and declining them, rereads, idempotent saves/restores, history backup without audio, malformed-data rollback, version-three migration, activity recreation with unsaved input, and completion while the Activity was in the background. Resetting or removing a library book preserves saved reading entries.
- Manual signed-release smoke test imported a video-and-audio MP4 in version 1.1 through Android's real system document picker, then installed version 1.2 over it without uninstalling. The imported book remained available. Holding the book and marking it finished showed the opt-in prompt; accepting and saving created a 100% reading record in History. Earlier release checks also confirmed three embedded chapters and background media-session playback.
- Release lint completed with **0 errors and 20 warnings**. Warnings concern pinned dependency/target versions, optional Kotlin extension style, and backup declaration advice; automatic system backup is disabled in the manifest.
- Release manifest inspection confirmed no Internet permission, debugging, or test document providers.
- APK signature verification succeeded using APK Signature Scheme v3, with an RSA 3072-bit dedicated release key. AAB JAR signature verification also succeeded. Android signing certificates are self-signed; the standard JAR trust/timestamp warnings do not mean the signature failed.

Version 1.2 uses the same release certificate as versions 1.0 and 1.1 and a higher versionCode of 3. Installing over the existing app preserves its library.

Detailed test logs, the parser test XML, lint report, build log, and APK certificate report are in the accompanying `validation/` directory. Screenshots show test media; the shipped library starts empty.

## Before a public store release

Run physical-device checks for Bluetooth/headset hardware, incoming calls and focus interruptions, screen-off playback under manufacturer battery restrictions, SD-card removal, unavailable document providers, low storage, large real libraries, TalkBack, and codecs across intended phones. The automated tests do not qualify every device or publisher's metadata encoding.

Prepare store artwork, privacy/data safety declarations, signing enrollment, and a store account. The supplied AAB has not been submitted to Google Play. Protect the separate private Signing directory and retain the key for future APK updates.

## Device discovery limits

The optional device scan queries Android's shared-media index with runtime audio permission and optional video permission. It cannot search private app directories or guarantee discovery of unindexed files. Results must be reviewed before import; audiobook suggestions can include incorrectly tagged music. Denying permission leaves file/folder import available. SD-card availability, restricted video selections, media permission changes, and library size still require checks on intended physical devices.

## Explicit limits

No DRM removal, transcoding, cloud synchronization, streaming catalog, Android Auto integration, or Chromecast. Protected Audible AA/AAX/AAXC files are excluded. Supported containers depend on device codecs. Backup exports contain library metadata, bookmarks, and reading history, not the audio or covers. Keep selected media available at its original location. Full details are in `Sonder/README.md`.
