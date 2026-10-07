# Privacy

Sonder stores audiobook metadata, playback progress, bookmarks, reading history, and settings on your device. It has no account, advertising, or analytics. Media files remain in the locations you select. File/folder access and optional shared-media scan permissions are used to import and play your files.

Update checks contact GitHub's public release API. Downloads contact GitHub's release hosting. GitHub receives normal connection information, including your IP address and the app version in the request's User-Agent. Sonder does not send your library, reading history, audio, bookmarks, or listening statistics. You can turn off automatic checks in Settings; manual checks and downloads contact GitHub when selected.

AudioBookBay downloads are off until you add a Real-Debrid API token and a download folder in Settings. When you search, Sonder sends your search words to the AudioBookBay address in Settings, and opening a result loads that page. Cover images load from the hosts the pages link to. Downloading sends the book's magnet link to Real-Debrid with your API token, and the files download from Real-Debrid's servers. These services receive normal connection information, including your IP address. Your API token and download history are stored only on this device, and the token is never included in backups. Removing the token in Settings deletes it from Sonder.

Library backup exports contain metadata, bookmarks, progress, and reading history. They do not contain audio or cover images. Save and share those files only where you intend. Android's automatic system backup is disabled. Removing a library entry does not delete its original media; removing a reading-history entry deletes that saved record from the app.

Sonder asks Android to install an update only when you select Install update. Android controls installation permission and confirmation.
