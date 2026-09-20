# Android Firebase handoff — version 1.3

Project: workshiftsapp. Preserve registrations and all real shift records.

The user explicitly approved publication of the supplied Firebase client config
after the prior approval block. app/src/debug/google-services.json contains the
genuine com.aistudio.worktracker.qztvdw client. This is public client configuration,
not an administrative credential. Other configuration paths remain ignored. Never
upload service-account private keys, signing keys, passwords or personal AI keys.

Debug version 1.3 (code 4) enables ACCOUNTS_ENABLED and VERSIONED_SYNC_ENABLED.
Email/password registration, login and reset are available. Google sign-in remains
disabled pending matching Android OAuth/signing fingerprints. Preview has the
separate .preview package and remains offline without genuine registration;
release is gated. CLOUD_SYNC_ENABLED stays false: the old integer-ID writer must
never be re-enabled. The new transport uses users/{uid}/records_v1/{syncId}.

The official Google Services plugin finds exact package matches. The preview
processing task is disabled at configuration time when its config is absent; this
avoids capturing Gradle script objects in configuration-cache execution closures.

Owner-only firestore.rules are recorded locally. The user reported publishing
them and enabling Email/Password and Google providers. No administrative Firebase
deployment or inspection of real records was performed. firebase.test.json uses
only demo-salary-sync emulators. CI tests verified permissions across separate
accounts, edits, deletion, offline retry, lost acknowledgements and conflicts.
Android unit tests verify journal/transport orchestration and packaged options;
this does not replace native-device/live-provider end-to-end verification.

Verified source 205a84e958ae55d429260299a63e737244b15ead, Actions run 35032047175:
51 Android tests, 26 web tests and one multi-scenario Firebase emulator integration
test passed; both APKs built successfully. See preservation-and-sync.md for
artifact URLs and public package/version/certificate diagnostics.

Standard runner debug signing is used, and differs between CI builds. Existing
phone update compatibility remains unverified. User permits fresh installation
and reports backed-up data; preserve that external backup before uninstalling.
Neither live sign-in nor installation on the user's phone has been performed.

References: [Android setup](https://firebase.google.com/docs/android/setup),
[email/password](https://firebase.google.com/docs/auth/android/password-auth).
