# Android Firebase handoff

Project: `workshiftsapp`. Do not create a replacement project, remove registrations,
or modify live shift records. Cloud sync remains gated until end-to-end validation.

## User-reported console configuration, 2026-09-14

- Android registration added for `com.aistudio.worktracker.qztvdw`.
- Google and Email/Password providers enabled (screenshots).
- User reported publishing the owner-only rules now recorded in `firestore.rules`.
  Live deployment and permission tests have NOT been independently verified.
- Replacement google-services.json was successfully read on 2026-09-15. The genuine
  client for the original package was retained in app/src/debug/google-services.json;
  the unrelated AndroidManifest.xml registration was omitted from this build copy.
  This is Firebase client configuration, not an administrative credential. It is
  retained locally only: automatic approval review rejected publishing it to this
  public repository without explicit publication approval. CI remains offline.

## Configuring a build

The official Google Services Gradle plugin is configured conditionally. Without a
client config, debug/preview still build offline. With a config, the plugin must
find an exact client package match; do not edit the JSON to counterfeit a match.

- Debug/release: `com.aistudio.worktracker.qztvdw`.
- Preview: `com.aistudio.worktracker.qztvdw.preview` (separate Firebase registration
  still unverified). Use `app/src/preview/google-services.json` for this variant,
  and `app/src/debug/google-services.json` for debug, or a module-level file
  containing genuine registrations for both packages.
- Configuration files remain ignored. Never upload a service
  account private key, signing material, passwords or personal Gemini credentials.
- Google sign-in additionally needs the matching signing certificate fingerprints
  and OAuth configuration. Preview signing currently changes between CI builds.
- Debug enables `ACCOUNTS_ENABLED` with its reviewed configuration. Preview remains
  offline with its separate package; release remains gated. `CLOUD_SYNC_ENABLED`
  and `GOOGLE_SIGN_IN_ENABLED` remain false. Do not enable the old Int-ID writer.
- Version 1.2 (code 3) adds an original-package debug APK artifact alongside preview,
  email password reset, and packaged-resource configuration checks. Authentication
  with a real provider and installation over the user's signed app remain unverified.

The Android account UI now offers explicit email sign-in/registration, immutable
account-scoped local stores and reviewed guest copying. Passwords are not saved
or logged. This is not a claim of successful provider authentication or cloud sync.

`firebase.json` records only Firestore rules; it does not deploy automatically.
No Firebase deployment was performed in this continuation. Existing rules allow
all document types within each owner's subtree; schema validation and full
permission-denial/emulator tests remain required for the versioned sync protocol.

References: [Android setup](https://firebase.google.com/docs/android/setup),
[email/password](https://firebase.google.com/docs/auth/android/password-auth).
