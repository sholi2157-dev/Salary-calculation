# Current delivery target: separate trial application

User clarified on 2026-09-20 that the original AI Studio app must coexist with
the maintained trial app. Deliver ONLY the preview variant with package
com.aistudio.worktracker.qztvdw.preview and Hebrew trial label.
The base-package debug artifact is NOT a trial update and must not be delivered
as such. The previous version 1.3 download targeted the wrong installed app.

The newly supplied genuine Firebase client registers this preview package in
workshiftsapp. Its filtered config is in app/src/preview/google-services.json.
User authorization to integrate/publish Firebase client configuration continues
from the session. No administrative credential or signing key is included.

Version 1.4 / code 5 enables email accounts and versioned sync for configured
preview builds. Google sign-in and the retired integer-ID cloud writer remain
disabled. Existing account-isolation and sync code is retained.
CI inspects the built preview APK for its exact Firebase app ID to avoid shipping the
original-package registration accidentally.

Signing remains runner-generated debug signing. The package now targets the
correct separate trial app, but updating an older trial APK still requires
matching certificates. Do not claim this fixes in-place updates or remove the
original app. User allows a fresh trial installation after an external backup.
Live account and physical-device installation testing remain outstanding.
See preservation-and-sync.md for the last completed checks.
