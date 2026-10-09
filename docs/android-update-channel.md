# Android live update channel

Owner explicitly requested activating the existing installed-app channel on 2026-10-09. Updating a restricted Drive file did not activate the channel: the `latest/download/release.json` endpoint was missing.

Publish the exact already signed and tested RC14 artifact from run 37888789674 (source 4267f5e0083f1b945e7d3642d803d15d222f0bdf); never rebuild it for publication. It uses the existing package and permanent signing identity, version code 22. Publication workflow verifies the artifact, signature, package, version and digest, stages a draft, then activates the release as the latest channel. Anonymous manifest and APK reads must match exactly.

The disposable emulator test installs the original publicly distributed RC13/code21, seeds synthetic local data, uses the actual settings check/update buttons over the public network, validates the downloaded APK, confirms Android's actual installer, checks installed code22, compares local snapshots exactly and checks that code22 is not offered to itself. Test instrumentation is signed by the existing identity; no production APK is rebuilt.

User instructions: Settings → מערכת ומשוב → בדוק עדכונים → עדכון. If Android asks to allow installation from this source, allow it, return and press עדכון again, then approve Android's install dialog. Installation cannot happen silently. RC14 users correctly see no new update until a higher-code release is published. Future updates must publish the signed higher-code APK and matching manifest to this channel; replacing the Drive file alone is insufficient.

Publication status: run37897894084, publication job113713359685 SUCCESS. Independent anonymous manifest/APK download and v2 signature/content digest verification passed. Original RC13 live end-to-end verification PASSED: run37963754070/job113932814425, artifact11631484547. The actual settings button found RC14, the public APK passed validation, Android installer confirmation and success screen passed, exact local snapshots matched, three entries remained, and RC14 did not offer itself again. Crash log empty. Evidence: docs/release-evidence/rc14/live-update.json.

The initial attempt to tag the historical APK source was refused by GitHub Actions permissions. The release tag instead identifies the publication commit; the exact immutable APK source is recorded separately in the selection manifest and release notes.

Post-install test diagnostic: run37962791319 recorded ActivityManager refusing test startup because the package was still frozen during Android dexopt. The app had already downloaded and passed the actual installer confirmation. The harness now waits for the actual installer success/Done screen before starting retained-data instrumentation; it does not change production app code or bypass Android installation.
