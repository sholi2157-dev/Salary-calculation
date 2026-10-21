## Active combined shift-entry/sharing release — 2026-10-21

Owner authorized implementation, merge and public update on both Android and Web.
Scope/evidence: `docs/shift-entry-20261021.md`, plus `docs/sharing-20261021.md`.
Work in isolated sharing branches; preserve all previous sharing changes. Pending final CI,
Android 2.0.4 (37) signed upgrade gate from delivered 2.0.3 (36), Web browser verification
and publication. No public deployment claimed at this point.

# Current project state

## October 21 — copy/share parity prepared for owner wording review

Separate review branches: `codex/sharing-android-20261021` and `codex/sharing-web-20261021`. Owner explicitly requested changes to both platforms. Contract: `docs/v2-sharing.md`; execution notes: `docs/sharing-20261021.md`. Technical copy and recipient-ready sharing are separate; multiple workplaces use technical blocks; worker search is wired consistently. TSV/CSV use one 14-column contract, unknown pay empty, future rows excluded from done-work exports. Monthly/yearly cards include money and the Web image action requires native file sharing, with no download/text fallback. JSON backups remain full-fidelity.

Draft review PRs: #8 Android, #9 Web. No merge, production promotion, public APK release, signing change or data/schema mutation. Verification complete: Android run 39609123653 SUCCESS (136 Debug + 106 Release tests, unsigned Release/instrumentation builds and identity gates); Web run 39609131883 SUCCESS (build, 89 tests, shared fixtures and browser flows). Full evidence and remaining native WhatsApp/iPhone/signed-update gates: `docs/sharing-20261021.md`. Screenshots and the generated PNG were visually reviewed. No public rollout or APK delivery has taken place.


## October 19 — 2.0.3: live money, both times on a new shift, digit-by-digit times, AI currency

Owner requests after 2.0.2:
- **Live shift (Home):** with a rate, the bar counts the money earned so far to the agora, every second. The clock and the place move to the line below. Without a rate, the bar keeps the clock.
- **New shift:** always shows both times. End is now, rounded; start is 4 hours earlier (`EntryDraft.DEFAULT_MINUTES`). Changing the workplace keeps the times.
- **Editing times:**
  - Hours and minutes are separate taps.
  - The keypad shows four digits with a cursor. Tapping a digit moves the cursor there, and typing replaces only that digit (`typeTime`), so 05:30 → 05:35 takes one digit.
  - Each time has its own −15/+15, replacing the shared nudge row. The ruler is unchanged.
- **AI currency:** the model returns no currency unless the text names one. The app then uses the workplace's currency, else the user's default (`GeminiParser.parseResponse`, `GeminiCurrencyTest`). Before this, AI shifts were always saved in ₪.

**2.0.3** (code 36), source `f7d3688`:
- Run 39173912680: 135 Debug + 105 Release tests; real RC16 → 2.0.2 → 2.0.3 update with data kept; fresh-install launch; crash logs clean.
- APK SHA-256 `10baa2e4b1bf70ce75e61d48ef0ae57ba6dd69b1e2609eef825f117674a8a283`.
- Published: update channel run 39174715276, release `android-v2.0.3`. The live RC16 → 2.0.3 check passed.

**Website** (owner approved: "מאשר לפרסום האתר"):
- The same live money, both times, digit-by-digit editing and ±15 per time, in commit `70fd200` on `codex/web-rc15-live`.
- Production deployment `dpl_EzPUFNqwsoPXhWSffuWBWA19SPjj`. `build-info.json` on the public site shows 70fd200. The public-site browser check (run 39176075361) passed.
- Rollback: `dpl_BiKB488BfxrXNK1VoTy2fztbeqEX`.
- The website has no AI shift entry, so the currency fix is Android only.

The next Android build must use a code higher than 36.

## October 19 — 2.0.2: new versions show on Home's bell

Owner: every new version should show a dot on the notifications bell, with the update inside, in addition to Settings.

**Change** (commit `3c1c5f0`):
- On every return to the app (at most hourly), the app checks the existing update channel.
- A newer version puts a dot on the bell. The sheet shows "גרסה X זמינה" with an "עדכון" button that downloads, verifies the hash and certificate, then opens Android's installer (never silent).
- The notice is remembered offline until that version is installed. Settings keeps its manual check, and both use one shared update path.

**2.0.2** (code 35):
- Run 39171295085: 133 Debug + 105 Release tests; real RC16 → 2.0.1 → 2.0.2 update with data kept; fresh-install launch; crash logs clean.
- APK SHA-256 `c3c743d3dd7f1244ba8249e2d8339a66bb7db9f86618ac01e04c94aa6f943db0`.
- Published through the update channel: run 39172111844, release `android-v2.0.2`. The live RC16 → 2.0.2 check through the actual button passed.

Users on 2.0.1 or older get 2.0.2 through the existing popup/Settings check; from 2.0.2 on, the bell shows new versions. The website needs no change (it already updates itself). The next build must use a code higher than 35.

## October 19 — 2.0.1 hotfix published (launch screen taps)

Owner and users reported that "הבא" on the launch screen needed several taps.

**Cause:** the launch background consumed every pointer event, including the slight moves of a real finger. The buttons' tap detectors saw those moves as consumed and cancelled. Robolectric clicks have no movement, so the tests had passed.

**Fix:** the background uses a plain tap detector (commit `143564e`). A new test taps with small movements.

**2.0.1** (code 34):
- Run 39168415624: 132 Debug + 105 Release tests; real RC16 → 2.0 → 2.0.1 update with data kept; crash logs clean.
- APK SHA-256 `5dec6167314581c9d1e393fe987d478720b839bf4236182feea9da9f2ae62005`.
- Published through the update channel: run 39169154826, release `android-v2.0.1`. The live RC16 → 2.0.1 check through the actual button passed.

**Drive:** the owner uploads `Salary2.0.1.apk` as a new version of the existing file.

The website is not affected: its launch buttons are plain buttons. The next build must use a code higher than 34.

## October 18 — website fix after launch; Drive updated by the owner

- **Website:** a planned shift's letter was invisible in light mode. The inline white text colour overrode the place colour; Android was not affected.
  - Fixed in `4dacd57`, with a browser assertion added.
  - Live as production `dpl_BiKB488BfxrXNK1VoTy2fztbeqEX`; the public-site browser flows passed.
  - Rollback: `dpl_68wLnANYRU9XXVqYfazV3ABsM4SE`.
- **Android:** no change is needed. 2.0 (code 33) stays the published build.
- **Drive:** the owner replaced the existing file in place. It is now `Salary2.0.apk` (id `1jDiOB-ByrQFs3CBzHehJEZSgl9ZKvCY_`), and its size of 19,452,360 bytes matches the release asset.

## October 18 — 2.0 launched (Android and website), owner-approved

**Android 2.0** (code 33):
- Exact tested artifact `salary-2.0-candidate` from run 39151970317 (source `83a889e`). APK SHA-256 `55953e60457ae87ee664e1bdc327009d15fb8f2e3f97dddcc69a885b41e2d849`; same package and permanent certificate.
- Candidate gates: 132 Debug + 105 Release tests; real RC16 → rc8 → 2.0 update with data kept; fresh install with the launch and the tour; crash logs clean.
- Published through the existing update channel: run 39157220086, release `android-v2.0`, asset `Salary2.0.apk`. The anonymous manifest and APK match.
- Live check passed: the original RC16 install found 2.0 through the actual button, downloaded the exact APK, and installed it through the Android installer. The synthetic data came through identical, and 2.0 is not offered again.
- `codex/android-local-distribution` now holds the 2.0 source (fast-forward from `claude/v2-android`).
- The next delivered build must use a code higher than 33.

**Website 2.0:**
- Production deployment `dpl_68wLnANYRU9XXVqYfazV3ABsM4SE` from `3f29f5d`, at the existing origin `salary-calculation-eta.vercel.app`. `build-info.json` reports that commit.
- Rollback deployment: `dpl_A7zWpmd7PTFsaorrZKEQzDZhvGWn` (RC15 web).
- The public-site browser flows passed (run 39157846570).
- `codex/web-rc15-live` now holds the 2.0 site; the RC15 parity suites are retired from that branch.

**Drive:** the existing file `SalaryRC16.apk` (id `1jDiOB-ByrQFs3CBzHehJEZSgl9ZKvCY_`) is not replaced yet. The available Drive tool can change only names and descriptions, not file content. The owner uploads `Salary2.0.apk` as a new version of that file ("ניהול גרסאות"), which keeps its link and permissions.

**Remaining manual checks:** physical phone review of 2.0 and the Drive upload.

## October 18 — stage D (summaries) on both platforms: 2.0-rc8 and website preview (not published)

Owner approved the next stage but not the launch: stage D is done; publishing (stage F) waits for explicit approval. When it comes, the owner chose a clean "2.0" build.

What stage D adds: "החודש שלך" (hourly average, unpaid, insights, busy days) and the story recap "החודש/השנה שלך בכיס" with a shareable card without amounts. Same rules on both platforms (shared `v2-summary.json`). Details: `docs/v2-stage-d.md`.

**Android:** signed `2.0-rc8` / code 32 from `bdbce9e`, run 39147647288 SUCCESS.
- Tests: 132 Debug + 105 Release.
- Identity: 32 > 31 > 24.
- Update gate: real RC16 → delivered rc7 → rc8, data kept; the launch screen was shown and completed on the updated install.
- Fresh-install launch and tour, device tests, crash logs.
- APK SHA-256 `1522b54699fd06b931caf6cdbb4dee2668398ec7103a1d5fe663b46f14d5c1f5`.
- Private artifact `salary-2.0-rc8-candidate`.
- The device screenshot of the launch slogan was still taken before the text rose. The cause is the test's timing, not the app; the test is fixed after rc8.
- The next delivered build must use a code higher than 32.

**Website:** `claude/v2-web` at `d820fa9`, CI web-v2 run 39145054859 SUCCESS (75 unit tests, browser flows incl. the story). Vercel Preview `dpl_9dckoWy7wNoHv84hmrzEu8inbzgs` READY on that commit. Production unchanged.

## October 18 — 2.0-rc7 and website preview: a one-time 2.0 launch, name, tour; settings back in Settings (not published)

Owner request (app and site alike):
- Notification switches are back in Settings. Home's bell only lists what arrived, with a link to those settings.
- The profile (first and last name) is the last group in Settings. Only the first name is ever shown.
- Once per installation, new or updated, a launch screen in the new look appears: "עיצוב חדש. התחלה חדשה." → "הבא" → first and last name ("אחר כך" allowed) → the short tour from Home. On the site the same flow runs once per browser, with a new short tour (`web/kis/launch.js`).

**Android:** signed `2.0-rc7` / code 31 from `6ca5079`, run 39133033538 SUCCESS.
- Tests: 129 Debug + 103 Release tests.
- Identity: 31 > 30 > 24.
- Real RC16 → delivered rc6 → rc7 update: data kept, and the launch is shown and completed on the updated install.
- Fresh-install launch, then the tour.
- Device tests, crash logs.
- APK SHA-256 `6d448664af2364df37db25b32f037926e8acc260eb3b74e77c1f671c0a3253f1`.
- Private artifact `salary-2.0-rc7-candidate`.
- The device screenshot of the launch was taken before the slogan finished rising: the test clock holds the animation. The test now advances the clock (commit after rc7).
- The next delivered build must use a code higher than 31.

**Website:** `claude/v2-web` at `c6f8d74`, CI web-v2 run 39132835096 SUCCESS, Vercel Preview `dpl_7fE5SWUpsbsRRyvH5W8mY83SMD4h` READY on that commit. Production unchanged.

## October 18 — 2.0-rc6 and website preview: explanations behind ⓘ, bell and profile on Home (not published)

Owner request, same change in the app and the site: screens show titles only, and every explanation sits behind an ⓘ next to its title. Home has a bell (recent notifications with a dot for new ones, plus their switches) and a profile icon at the top. The name is typed once, then shown with an edit option. Settings no longer has profile or notifications, so the immediate settings come first. Bottom hints are removed.
- **Android:** signed `2.0-rc6` / code 30 from `c0607fe`, run 39101786881 SUCCESS.
  - Tests: 129 Debug + 103 Release.
  - Identity: same package and permanent certificate, versionCode 30 > 29 > 24.
  - Update gate: real RC16 → delivered rc5 → rc6 update, device tests and crash log checks passed.
  - APK SHA-256 `7c3f8fb4fcda6c3b106bf500ed727879bb3758926f044faca6c4bbf4c37620cc`.
  - Private artifact `salary-2.0-rc6-candidate`. Includes the History panel change (`81790f2`). The next delivered build must use a code higher than 30.
- **Website:** `claude/v2-web` at `1637e2e`, CI web-v2 run 39101248180 SUCCESS. Vercel Preview `dpl_FoeCNd5Ud2DG6Sj2iaV4TpWmNVEr` READY on that exact commit; production is unchanged.
  - Also fixed: on the site, a right click no longer re-selects a card after the selection is cancelled.

## October 18 — website 2.0 (stage E) as a Preview on `claude/v2-web` (not published)

Separate branch `claude/v2-web` from `codex/web-rc15-live`. Same design, rules (shared fixtures checked byte-for-byte), data and backup format (v2/v3 both ways) as Android 2.0. Source `4a0c23d`, CI run 39094056873 SUCCESS, Vercel Preview `dpl_HJNexNKY2UXN8xfQtjEbUynPSmzf` READY on that exact commit; production unchanged (`562a7a5`). Details: `docs/v2-stage-e.md` on that branch. Publishing (stage F) only with explicit approval.

## October 18 — 2.0-rc5: one look, done vs planned, profile, recent notifications (not published)

Branch `claude/v2-android` only. Signed `2.0-rc5` / code 29 from `8f77951`, run 39082570832 SUCCESS: 127 Debug + 103 Release tests, real RC16 → delivered 2.0-rc4 → rc5 update with exact data kept, device tests incl. the weekly-plan auto-record check, fresh install, empty crash logs. APK SHA-256 `c68a00a8b000ab6c12dba688780ffccd83266dfe9397c80a8c20ce17a4ff7225`, same package and permanent certificate. Private artifact `salary-2.0-rc5-candidate` only. Next delivered build must use a code higher than 29. Details in `docs/v2-stage-c.md` (rc5 section). After rc5 the owner asked for a simpler History (all dates by default, one filter/sort panel); done in `81790f2` (dev CI green), not delivered as an RC on request. Owner said to move on: next is the website (stage E) on a separate branch, no publishing or merging without approval; the phone review of rc5 is still open. PR #6 not merged.

## October 16 — 2.0-rc4: stage C weekly plans (not published)

Branch `claude/v2-android` only. Signed `2.0-rc4` / code 28 from `ec0aca5`, run 38955233764 SUCCESS: 123 Debug + 103 Release tests, real RC16 → delivered 2.0-rc3 → rc4 update with exact data kept, device UI/keyboard/currency tests plus the new weekly-plan auto-record check, fresh install, empty crash logs. APK SHA-256 `bc140859fa345851966333ea0c21bd81d04c790b4f24fdb19981a4817e25f71d`, same package and permanent certificate. Private artifact `salary-2.0-rc4-candidate` only. Next delivered build must use a code higher than 28. Stage C (weekly plans, auto-record with undo, reminders, quiet hours) inside the existing screens; details and review fixes in `docs/v2-stage-c.md`. Waiting for the owner's phone review of rc4; then website (stage E) on a separate branch, no publishing without approval. PR #6 not merged.

## October 16 — 2.0-rc3 after the second phone review (not published)

Branch `claude/v2-android` only. Signed `2.0-rc3` / code 27 from `31e4de7`, run 38943035549 SUCCESS: 113 Debug + 95 Release tests, real RC16 → delivered 2.0-rc2 → rc3 update with exact data kept, device UI/keyboard/keypad/currency tests, fresh install, empty crash logs. APK SHA-256 `26352b7875ba7aaac80f1c89799fc1560dba4bbbcdabbc0aeea64901bdace890`, same package and permanent certificate. Private artifact `salary-2.0-rc3-candidate` only. Next delivered build must use a code higher than 27. Owner approved the order: rc3 → stage C (Android) → website (stage E, separate branch, no publishing without approval). Details: `docs/v2-stage-b.md`. PR #6 (claude/v2-android → main) exists; not merged.

## October 14 redistribution — Android RC16 and Web production delivered

Owner authorized merge and redistribution of the audit fixes. Android PR #4 and Web PR #3 are merged on their separate active branches.

Delivered Android `1.5-rc16` / code `24`, exact signed/tested source `c0d655365cfd35e2bba11807cb4fe3c0ff27af93`. Candidate run38591694409/job115947937994 SUCCESS: 91 Debug + 73 Release tests, nine native stages, exact original RC15-to-RC16 retained data, clean crash logs. Public update run38593112482 SUCCESS: original RC15 actual update button/download/Android installer, code24 installed, exact data retained and same-version update not offered again. APK SHA256 `400e11bccd9a46e7911c498137da9ba5d23b3b86cee50f0f1ea6400f40e7cc30`, size19231375. Permanent package/signing identity unchanged. Public anonymous download and Drive roundtrip match the tested bytes. Existing Drive file/link/parent/all eight permissions retained. See `docs/rc16-release.md` and `docs/release-evidence/rc16/`. Next delivered Android build must use a code higher than24; never rebuild/mutate RC16 in place.

Website audit fixes are now published after Vercel access was restored. Existing public origin https://salary-calculation-eta.vercel.app serves source `562a7a568558d6fba22bd6aac2fe173ed85ff971`, production deployment `dpl_A7zWpmd7PTFsaorrZKEQzDZhvGWn` READY. Public build-info verifies this exact source; prior 46-test/build/five-suite evidence remains run38117622906. Actual public browser verification run38595391853/job115960660993 SUCCESS at 360/390/768/1440, including category/timer/currency flows and cross-tab edit/delete draft preservation. Artifact11908872883. No user data or protection settings changed. Website release evidence is on codex/web-rc15-live/docs/rc16-web-release.md. All existing distribution targets are updated.

Manual physical-phone/native-sharing/live-AI review remains. General simultaneous full-snapshot Web localStorage writes are not solved by the scoped open-edit conflict fix. Earlier notes below are historical; this section is authoritative.

## October 11 audit review branch

Audit fixes are isolated on `codex/android-audit-20261011`. Read `docs/audit-20261011.md` for findings, exact verification and remaining gates. These are review changes, not a new delivered APK or production deployment. RC15 remains the public baseline. Earlier scope sections below are historical and must be read together with the latest completion notes.


## Latest completed owner request — RC15, 2026-10-09

Delivered Android1.5-rc15/code23: exact signed/tested sourcebc876374cb7486351fcd7bd9c69c229215241021, candidate run37993791019, public channel+real RC14button/download/Androidinstaller/data gate run37994406008. All passed. APK SHA2569935eb6d49c971832653a207186e64392c93ba65da271d839684c5b624ff0bcc and permanent package/certificate are unchanged. See docs/rc15-layout.md and docs/release-evidence/rc15. Do not repeat completed work or publish a different rebuilt binary underRC15.

Owner explicitly requested both platforms. Web production at the existing eta origin is d4feadd0cdf053906c70d6b76e6b9651c57e455e, with live public browser run37993523248 passed. Preserve that branch/origin and existing design; detailed Web evidence is on codex/web-rc15-live/docs/rc15-feature-parity.md. Android and Web now have on-demand default category/add controls and compact currency/rate layouts; Web also retains RC14currency fallback/editable active currency/confirmed cancellation. Existing Drive APK identity/link/ACL were retained and updated in place toRC15. Future delivery must use a higher code than23 and retain signer/data.


Last updated: 2026-10-09

## Repository and active track
Repository: `sholi2157-dev/Salary-calculation`

Current active track: Android local distribution only.

Branch: `codex/android-local-distribution`

The website/sync work is intentionally paused on:
`codex/preserve-app-sync` / PR #1

Do not resume or modify website, Firebase, accounts or cloud sync until the user explicitly switches the project back to that phase.

## Active task: activate the installed-app update channel

Owner explicitly requested activation on 2026-10-09 after reporting that friends
could not find updates. The old channel was absent (HTTP404); changing Drive did
not publish release.json. Exact tested RC14/code22 is now published at:
https://github.com/sholi2157-dev/Salary-calculation/releases/latest/download/release.json
APK artifact/source/signature are unchanged. Publication job113713359685 in
run37897894084 succeeded. Independent anonymous download, APK v2 signature,
content digest, package, version and SHA256 all verified. Live original RC13
manual-button/Android-installer/data-preservation verification PASSED in
run37963754070/job113932814425, artifact11631484547. Actual installer success
screen confirmed; exact before/after snapshots match, 3 synthetic entries
retained, same-code check returns no update and crash log is empty.
Scope: docs/android-update-channel.md. No website/cloud changes.

## Previous completed task: RC14 currency, timer cancellation and multiline feedback

Owner requested currency editing for stored and active shifts, correct timer
currency defaults, confirmed discard without creating a saved shift, and Enter
in feedback without collapsing Settings. Baseline: 51ca2908349681141f17461399615ea00f88b870.
Scope/evidence: `docs/android-currency-feedback.md`. Verified owner-review candidate
RC14/code22, signed source4267f5e0083f1b945e7d3642d803d15d222f0bdf.
Run37888789674/job113684763786 SUCCESS; artifact11598265150.
SHA256588e058c21f3a1a42de88d7a33028ebce2c2ec302ce3deb5d8b6d6874854f627.
Permanent signature/content digest/package/version independently verified after
download. Original RC13-to-RC14 update preserved exact synthetic data; all nine
native stages passed, including currency defaults/editing/persistence, confirmed
cancel and real Enter in feedback. Crash logs empty. Consolidated84/67 tests passed
before final header fix; exact final focused9/4 plus native gates passed.
Owner file SalaryRC14.apk; existing restricted Drive file replaced in place with
permissions retained. Owner physical-phone review remains.
The original fixes task did not request feed publication; the owner has now
explicitly requested it in the active task above. No website/cloud changes.

## Previous completed task: first-launch Android onboarding — RC13

Owner requested an interactive first-launch tutorial and Settings replay.
Exact pre-change clean HEAD: 6207796903515b97dcb147ae029d4b12e8f635fe.
Rollback branch: `backup/android-rc12-before-onboarding-20261006`.
Latest verified owner-review APK: `1.5-rc13` / code `21`, signed source
`ae489c9655b1684f38be74ef0b12ff2c19e4e68e`.
Final run37415910804/job112114352377 SUCCESS; artifact11390889607.
APK SHA256 `74da79a77d99e1e6d98ddca0c4b5b9b6079032cd191e57353ac2344d4c385da1`.
Consolidated Debug80/Release64 passed; final focused Debug9/Release4 passed.
Original RC12 signed upgrade: all six stages passed with exact synthetic data
preservation, normal UI/draft replay/real IME. Separate signed fresh emulator:
nine steps, completion/skip/replay/recreate/cold process launch passed. Crash logs
empty; downloaded signature/digest/package/version independently verified.
Version1 local state suppresses forced onboarding for established users. Replay
is in Settings. Scope/evidence: `docs/android-onboarding.md`, `docs/release-evidence/rc13/`.
Owner file `SalaryRC13.apk`; physical phone/TalkBack/live AI review remains.
Owner authorized a friend download link on 2026-10-06. Exact verified RC13 APK
published as prerelease `android-v1.5-rc13` (run37487018264); anonymous download
and exact SHA256 verified. Direct permanent link:
https://github.com/sholi2157-dev/Salary-calculation/releases/download/android-v1.5-rc13/SalaryRC13.apk
Stable update feed/Latest remained unchanged at RC13 publication; superseded by
the explicitly requested RC14 channel activation above. No APK rebuild.
Public metadata: `docs/release-evidence/rc13/public-download.json`.
No website/cloud/storage schema/provider/signing identity changes.

## Previous completed task: restore RC10 interface as RC12 — 2026-10-01 UTC

Owner requested the previous version. All 8 UI files restored byte-for-byte from
RC10 backup `25f7066cbe058af226ba46befc990f2d8b30b929`; old animated purple/navy
background, translucent cards, original typography/palette/Save are restored.
Latest verified candidate: `1.5-rc12` / code `20`, signed source
`6a46483278d74439a5ec535d78dceaae196fe055`.
Run `36808449941` / job `110197933791`: SUCCESS; artifact `11138556573`.
APK SHA256 `23d5dd54f256ba865c6b5d8e976ae7c2d3226213fbc4a64d2aa608240f64f811`.
Permanent package/certificate unchanged. Debug 72/72, Release 60/60; original
signed RC11/code19 to RC12/code20 update preserves exact synthetic data. All six
instrumentation stages, offline/updater/real keyboard/Save checks passed; crash
log empty. Downloaded APK independently verified; all 29 rendered and 13 emulator
PNGs inspected with coverage limits recorded. Owner file `SalaryRC12.apk`.
Scope/evidence: `docs/android-ui-skills-rollback.md`, `docs/release-evidence/rc12/`.
Physical owner review remains. No public/stable release or cloud/website changes.

## Previous UI Skills task — RC11

RC11/code19 is completed and signed from the exact RC10 HEAD25f7066.
Pre-change remote backup: `backup/android-rc10-before-ui-skills-20261001` at
`25f7066cbe058af226ba46befc990f2d8b30b929`, verified by read-back.
Scope/evidence/recovery: `docs/android-ui-skills.md` and `docs/release-evidence/rc11/`.

Latest candidate: 1.5-rc11/code19, source b8818c5da483ed9819f7f2abd576b8e61c07f0b0.
Run36806582196/job110192154238 SUCCESS; candidate artifact11137173981.
APK SHA25668d0d70af713e69f88ede0e573dc00113493f095851a7d44ce39296fd754d506.
Package/permanent certificate unchanged. Debug72 and Release60 passed; real signed
original RC10 to RC11 upgrade preserved exact synthetic data, updater/UI/real IME
and duplicate-save checks passed; crash log empty. All29 rendered/13 emulator
PNGs inspected with the disclosed coverage limits. Downloaded APK signature,
content digest and metadata independently verified. Owner APK SalaryRC11.apk.
Physical owner review remains. No stable/public distribution or cloud/website work.

## Previous verified Android owner-review candidate — RC10

- Version: `1.5-rc10`, versionCode `18`
- Tested/signed source: `068c7fada2dad09c59c0b240c4ddb4e5bad18d20`
- Package: `com.aistudio.worktracker.qztvdw.distribution`
- APK SHA-256: `93d696b1324a8460e41a998afb52f01d174a23483ae33263ab03c5d4ebc788ee`
- Permanent certificate SHA-256: `ebaacacf243e5f5411033654fa07e74aefb503e8173fdd87fc62986552e78220`
- Final run `36786550537` / job `110129210875`: SUCCESS
- Signed artifact `11130291390`; owner APK `SalaryRC10.apk`

All eight reviewed interface refinements implemented with a recovery checkpoint.
Consolidated Debug72/72 and Release60/60; signed real original RC9/code17 to
RC10/code18 update, exact synthetic-state preservation, updater, real IME/Save,
duplicate-save and UI checks passed. Crash log empty. Independently verified
downloaded APK bytes/signature/package/version; inspected rendered/emulator PNGs.
Owner physical review remains, including animation timing and real speech/live AI.

Recovery branch: `backup/android-rc9-before-interface-polish-20260930`, exact
pre-change HEAD `3910e9689dcafa0a2dcf2fb90dff3eb46f6a076f`. Revert individual
UI changes or restore the RC9 UI through a new higher-code signed update.
Details: `docs/android-interface-polish.md`, `docs/android-signed-rc-evidence.md`
and `docs/release-evidence/rc10/`. No Stable/public release or cloud work.

Previous delivered baseline: RC9/code17, signed source
`87c288cd3f18c44669a550a0d9b5a944dd5902e3`, run36779054455, artifact11126034909,
APK SHA256 `c63dd258bc328e25204498f593b9704399723e9166c5169839e5bc4e9d751734`.

## Stage A History checkpoint — 2026-09-30

The owner requested a small Android-only checkpoint covering sections 2 and 4 of
`docs/android-final-uiux-task.md`, with no release build or version changes.

Implemented:
- Removed `יומן עבודה (N)` and combined the displayed total, filter badge,
  copy/export menu, sort menu and existing currency cycle into a compact RTL toolbar.
- Kept the selection count and all selection actions; narrow widths wrap the
  controls rather than crowding them.
- Selection rows show `WorkMoney.format(entry.totalEarnings, entry.currency)`.
  Group shifts use the same stored amount as the normal History card. No historical
  amount recalculation or financial/persistence changes were made.
- Removed two unused History menu-state variables and reused the existing money formatter.

Targeted verification:
- `:app:testDebugUnitTest --tests com.example.HistoryCompactUiTest`: 2 tests passed,
  covering 360dp/390dp RTL layout, toolbar bounds/height, ILS/USD totals, stored
  selection amounts (including a group shift with deliberately different rates),
  row selection, filters, currency cycling, all sort options and both copy actions.
- Debug app/test compilation passed as prerequisites of those two tests.
- Rendered both widths with Roborazzi and visually inspected contrast, RTL and amount readability.
- `git diff --check` passed.

RC8/code16 remains the delivered baseline. No new APK was built or signed, no
release gates were run, and release identity/configuration was left unchanged.
The checkpoint commit uses `[skip ci]` to avoid triggering the existing broad
build/signing workflow for this small UI chunk.

History checkpoint: `86268d40568d8b00c109fc5232f860a9402eabec`.
Do not redo these changes. The remaining Stage A work is recorded below.
Physical-device approval remains part of the later consolidated RC review.

## Stage A checkpoint A — 2026-09-30

Commit: `0aa82083ad3848f07a6787603c997e1169e74a5d` (pushed).

Home live-shift action now scrolls with the content and is hidden while the report
is expanded. Save retains its own measured space below the scroll viewport.
Clock date/start/end share one row; break/rate/currency share another. Manual and
group reuse the compact fields without empty clock placeholders. Notes remain
full width and group worker handling/calculations remain unchanged.

History navigation observes actual list position changes, remains visible on top
overscroll and returns at rest/end. Its measured height reserves list space without
resizing the viewport; selection actions also reserve their measured space.
The previous History toolbar and stored multi-select amounts are preserved.

Verification: `:app:testDebugUnitTest --tests com.example.StageALayoutTest`
passed 3 tests (360dp/390dp forms, draft mode switching, reachable Save/last recent
card, genuine History movement/top overscroll/final-card long press). Debug app
and test compilation passed. Six Roborazzi renders were inspected. `git diff --check`
passed. At checkpoint A, AI redesign and final polish remained pending checkpoint B.
No release gates, signed build or version changes were performed.

## Stage A completed — 2026-09-30

The remaining Android UI/UX implementation is complete. Checkpoint B includes:
- Deliberate AI typing/voice → editable transcript → explicit parse → processing →
  review → explicit save. Speech callbacks cannot parse/save. Cancel restores the
  pre-recording draft; interruptions preserve received text and ignore late results.
- Inline AI errors retain the text and mode, with correction/retry. The existing
  provider/model/personal-key mechanism and `addShifts` financial behavior are unchanged.
- Hebrew RTL polish, readable numeric fields/currency isolation, comfortable mode
  targets, and focused cleanup of duplicated form/AI UI and dead group state/unused stop callback.
- No change to the delivered RC8 version, signing/release configuration, storage,
  backup/import/export, updater, backend or website.

Final focused validation: 8/8 tests passed across `StageALayoutTest`,
`AiShiftExperienceTest`, `StageAInsetsTest`. This includes real Compose pointer/list
movement and platform-dispatched IME/gesture insets at 360dp/390dp, preserved drafts,
reachable Save/AI parse, speech callback simulation, errors/retry and explicit
review/save. App and test Debug compilation passed; generated form, AI and inset
renders were visually inspected. `git diff --check` passed. Details and limits:
`docs/android-stage-a-uiux.md`.

Both Stage A checkpoint messages use `[skip ci]`; no heavy release gate was run.
Checkpoint A was verified to have no Actions runs. Checkpoint B is the commit
containing this completion note (do not embed its own hash into its tree).

## Stage B complete — 2026-09-30

Owner authorized Stage B. Exact starting HEAD/clean tree and Stage A completion
verified. Only distribution metadata, validation tooling/test fixtures and verified
evidence changed. Stage A app/UI and all business/persistence semantics preserved.

Signed RC9 is delivered for physical-phone review. All required release/update
and synthetic-data gates passed; see the latest candidate section above and the
RC9 signed evidence. Stop here: physical OEM keyboard/gesture/speech/live AI and
actual owner installation/data review remain. No Stable/feed/friend distribution,
website/account/cloud work or launch video started.

## After the new RC is physically approved
Only after the owner explicitly says the new RC is satisfactory:
- promote the approved code to stable 1.5,
- keep the same permanent package and certificate,
- use a higher versionCode,
- run the minimum required stable release/update gates,
- publish through the existing GitHub Release/update path,
- verify the download/updater/Android installer round trip,
- produce a permanent link suitable for the owner and friends.

## Launch sequence
After stable release:
1. Create the launch/demo video.
2. Share the permanent app download link together with the video.
3. Then return to the website project.

## Relevant documents
- `AGENTS.md` — durable operating rules and lean testing discipline
- `docs/android-final-uiux-task.md` — completed Stage A scope; deferred Stage B delivery
- `docs/android-stage-a-uiux.md` — focused Stage A implementation/evidence and limits
- `docs/android-rc8-layout.md` — RC8 implementation/evidence
- `docs/android-local-distribution.md` — signing, update and distribution architecture
- `docs/android-signed-rc-evidence.md` — signed RC history/evidence
- `docs/preservation-and-sync.md` — full historical product/data contract
- `docs/website-android-handoff.md` — use only when website work resumes

## Codex handoff rule
A new Codex task should not rely on chat memory. It should derive project context from:
`AGENTS.md` → `docs/current-state.md` → `docs/android-final-uiux-task.md` → relevant evidence docs.

Before edits, always verify that those documents still match the actual branch HEAD, current code and relevant CI.

