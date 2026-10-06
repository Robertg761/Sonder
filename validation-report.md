# Sonder 1.6.0 validation

Package `app.sonder.audiobooks`, versionCode 7, Android 9 and later. Tests ran on an isolated Android 16/API 36 x86_64 emulator. No physical phone was attached.

## Playback changes

Resuming after a pause rewinds five seconds once. Temporary audio-focus loss pauses speech and uses the same rewind when focus returns. A manual pause during an interruption stays paused. The rewind can cross track files and clamps at the beginning of a book. An explicit seek while paused, a replacement queue, normal buffering, and the initial start of a book do not add a resume rewind.

The existing speech audio attributes let Media3 pause for Android notification requests to duck. These tests exercise real AudioManager focus requests and MediaController commands, rather than calling the service's listeners directly. Sounds that do not request Android audio focus do not trigger the interruption behavior.

## Completed checks

- Debug APK, test APK, optimized release APK, and release AAB built with Java 17 and Gradle 8.14.3.
- Seven existing JVM parser tests passed.
- All eight new Android playback tests passed. They cover app pause/play and saved progress, media commands and repeated Play, background notification focus loss with automatic resume, transient focus loss with manual pause, permanent focus loss, rewind across files and at the beginning, explicit seeks while paused, and replacement queues.
- All 24 existing Android tests passed, covering imports, library persistence and status, background playback and sleep timers, reading history, device discovery, layouts, and update verification. The full regression run passed 31 of 32 tests; the remaining test's assertion was corrected to account for MediaController reporting both a masked seek and its acknowledgement. The final focused run passed all eight new tests with the same production APK.
- Release lint passed with zero errors and 29 warnings. The warnings concern dependency/API modernization, existing Compose icon deprecations, and existing manifest/style advice.
- The release APK and AAB were signed with the existing Sonder release key. APK Signature Scheme v3 and the AAB JAR signature verified successfully. Certificate SHA-256: `371bd03c279be465481ddb8504576030a206812026367654ec68ab8de77ceb12`.
- The signed 1.5.0 release imported an actual MP4 through Android's document picker, completed playback, and saved a 100% Reading History entry. Installing the signed 1.6.0 APK over it retained the imported book, finished status, and reading record. Android reported versionCode 7 / versionName 1.6.0 after the update.
- The optimized signed 1.6.0 APK replayed that imported book. Android media commands paused it at 10,870 ms, then resumed and paused it at 6,187 ms after roughly 0.35 seconds of playback, confirming the five-second rewind in the release build.

Build, test, lint, signing, and release smoke-test evidence are in the accompanying validation files. The private signing key is outside the source tree and source ZIP.
