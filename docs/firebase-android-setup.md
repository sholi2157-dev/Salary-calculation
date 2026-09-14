# Android Firebase handoff

Project: `workshiftsapp`. Do not create a replacement project, remove registrations,
or modify live shift records. Auth and sync gates remain false until validation.

## User-reported console configuration, 2026-09-14

- Android registration added for `com.aistudio.worktracker.qztvdw`.
- Google and Email/Password providers enabled (screenshots).
- User reported publishing the owner-only rules now recorded in `firestore.rules`.
  Live deployment and permission tests have NOT been independently verified.
- Uploaded google-services.json could not be recovered: original scratch copy is
  absent; attachment materialization returned HTTP 502 twice. Its contents have
  NOT been read, checked, installed or committed. Obtain another accessible copy.

## Configuring a build

The official Google Services Gradle plugin is configured conditionally. Without a
client config, debug/preview still build offline. With a config, the plugin must
find an exact client package match; do not edit the JSON to counterfeit a match.

- Debug/release: `com.aistudio.worktracker.qztvdw`.
- Preview: `com.aistudio.worktracker.qztvdw.preview` (separate Firebase registration
  still unverified). Use `app/src/preview/google-services.json` for this variant,
  and `app/src/debug/google-services.json` for debug, or a module-level file
  containing genuine registrations for both packages.
- Configuration files are ignored by git pending inspection. Never upload a service
  account private key, signing material, passwords or personal Gemini credentials.
- Google sign-in additionally needs the matching signing certificate fingerprints
  and OAuth configuration. Preview signing currently changes between CI builds.
- `ACCOUNTS_ENABLED` is independent of `CLOUD_SYNC_ENABLED`; neither is enabled by
  merely adding the config. Do not enable the old Int-ID Firestore writer.

The Android account UI now offers explicit email sign-in/registration, immutable
account-scoped local stores and reviewed guest copying. Passwords are not saved
or logged. This is not a claim of successful provider authentication or cloud sync.

`firebase.json` records only Firestore rules; it does not deploy automatically.
No Firebase deployment was performed in this continuation. Existing rules allow
all document types within each owner's subtree; schema validation and full
permission-denial/emulator tests remain required for the versioned sync protocol.

References: [Android setup](https://firebase.google.com/docs/android/setup),
[email/password](https://firebase.google.com/docs/auth/android/password-auth).
