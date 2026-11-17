# Android review fixes — 2026-11-16

Scope: the six owner-requested review items on `codex/2.5-review-fixes-android`,
based on `release/2.4-android` at `62cbf28e52add7464309167167b57d85d5de7e52`.
One PR targets `release/2.4-android`; no merge, deployment, signing, tag or release.
Modules: Hebrew/RTL UI, money, phone UI, native distribution, public repository,
user data, and Web/Android parity. The Web worker owns its separate worktree.

## Execution plan

1. Inspect each equivalent flow, existing coverage and baseline CI; distinguish
   native differences from reproduced defects. Baseline development CI
   [43056393542](https://github.com/sholi2157-dev/Salary-calculation/actions/runs/43056393542)
   succeeded on the starting commit. GitHub confirms public Android 2.4.0/code 55;
   older current-state notes predate that publication.
2. Reproduce persistence failures with synthetic Room/preferences/file failures.
   Surface short Hebrew feedback, keep unsuccessful edits available, protect
   startup and retain migration snapshots. Preserve schema, wire format and money.
3. Verify focus isolation and initial focus for sign-in, launch and tutorial;
   fix only missing native safeguards. Add intro surface edges and derive the
   displayed version from the existing build source.
4. Verify known-zero rate semantics. Expand the requested controls to 48dp,
   retaining direct time editing, and verify delete text contrast in both themes.
5. Run targeted regression while editing, then one consolidated Debug unit test
   and assembly gate, relevant Roborazzi screenshots, shared fixture parity and
   `git diff --check`. Store synthetic screenshots outside Git.
6. Record per-item evidence and new copy, commit and push the task branch, open
   one PR, watch CI and inspect actual logs. Write the requested English report to
   `/workspace/fable-review/cx/report-android.md`.

## Evidence and results

| Item | Android result and evidence | Regression evidence |
| --- | --- | --- |
| 1. Persistence | Fixed. `WorkStorage.kt` acknowledges preference commits and restores the affected in-memory keys on failure. `WorkViewModel.kt` guards SQLite/I/O failures, atomically saves entry + worker suggestions and reports automatic-backup failures separately. `WorkOnboarding.kt` no longer throws on failed startup/completion commits. `MainActivity.modelFor` stops safely and permits retry if account binding cannot be persisted, without choosing another database. | `WorkStorageFailureTest` exercises Room paid/edit/batch/worker failures, file-backup failure, preference rollback, startup classification, account binding and DataStore read/write failures. `KisUiTest.storageFailureIsVisibleBeforeTheAppShellExists` verifies visible feedback before sign-in. |
| 2. Focus | Fixed where applicable. `MainActivity` returns immediately after `SignInGate`, so there is no app behind sign-in on either new or returning installs. The older `KisLaunch` overlay now uses a native full-screen Dialog. Sign-in, launch and the existing native tutorial dialog explicitly focus an inside control. | `KisUiTest.launchIsANativeModalAndKeyboardFocusStaysInside`, intro initial-focus assertions and existing `OnboardingExperienceTest` flows. |
| 3. Intro contrast | Fixed. `KisAccount.kt` adds 1dp `lineStrong` outlines to chat bubbles and assistant chips in both themes. The existing palette tests cover outline/background AA non-text contrast. | Actual Roborazzi pixel checks in `reviewIntroEdgesHaveActualHighContrastPixelsInBothThemes`; light/dark intro and chat renders. |
| 4. Known zero | Not a bug. `KisDetail.kt` and `KisHistory.kt` offer completion only for `!payKnown`; `WorkDao.completeMissingRates` independently guards the stored flag. A known zero remains a known amount and can be changed with Edit. No pay-rule change. | Explicit zero row in existing `V2PayTest.bulkRateCompletionWritesOnlyMissingShiftRates`; `KisUiTest.knownZeroRateIsShownAndIsNotOfferedAsMissing`. |
| 5. Version | Fixed. The intro's old hard-coded 2.3.1 is now `BuildConfig.VERSION_NAME`, so it follows the installed variant/version. Debug renders correctly show its existing 1.4 identity; distribution version configuration is untouched. | `KisUiTest.introVersionComesFromInstalledBuild`. |
| 6. Touch/contrast | Fixed the undersized targets. `KisMinTouch` is 48dp, switches explicitly reserve it, segments/chips/currencies use it and each clock/duration digit has a separate nonoverlapping 48dp target. Portrait clocks stack to fit; wider screens retain the two-column layout. The tutorial moves its card within available space when the larger field would overlap it. Native delete contrast was already sufficient: filled light 6.56:1 and dark 8.23:1; no color change needed. | `reviewControlsReach48dpAndStillRespond`, `reviewClockDigitTargetsAre48dpWithoutOverlap`, existing `WorkPaletteTest`/`WorkComponentsTest`, editor/tour regressions and screenshots. |

Room migrations remain additive and transactional; Android does not create the Web's
`work_pre_v2` / v23 / rc13 migration snapshots. No snapshot cleanup is introduced.
The existing routine automatic-backup retention policy is unchanged. A failed
backup write does not reach that cleanup. Default currency writes use the existing
canonical Room preference, retaining the old DataStore key as a read fallback;
this avoids an unnecessary non-atomic write of one setting to two stores.
No payroll schema, sync record, backup format, fixture or financial calculation changes.

New Hebrew copy proposed for owner approval:

- `השמירה נכשלה. מומלץ להוריד גיבוי.`
- `הגיבוי האוטומטי נכשל. מומלץ להוריד גיבוי.`

The retry action reuses `לנסות שוב`; acknowledgment reuses `אישור`. The backup
recommendation matches Web. Android's generic failure deliberately does not claim
that every cross-store operation made no change; an automatic backup can fail after
the primary Room save has succeeded. Backup failure therefore has its own message.
The intro's connection wording, mixed address forms and example names are unchanged,
pending owner decision. Physical-phone review, real TalkBack/hardware keyboard and
signed retained-data update testing remain outside this unsigned review task.

## Validation

- Baseline development CI on `62cbf28` succeeded; its actual logs were inspected.
- 31 targeted tests passed before the final account-binding/tour safeguards.
- The three new intro/clock regressions fail against the original UI: initial focus
  is absent and an individual clock digit is only 24dp wide. Baseline renders were
  saved, then the task sources restored before further work.
- The first consolidated run executed 336 tests; four UI checks exposed a tutorial
  card overlap and a whole-duration test tap now landing on an enlarged digit.
  The card placement was corrected and the whole-duration test now taps its label;
  independent digit-editing assertions remain unchanged. Follow-up validation is
  recorded below after execution.
- All 9 shared fixtures match `origin/release/2.4-web` byte-for-byte.
- Screenshot evidence is saved outside Git under
  `/workspace/fable-review/cx/shots-android/{before,after}/`. Intro/chat renders show
  both themes; the clock comparison shows the 24dp baseline digits and the new 48dp
  layout. Final editor/settings/tutorial renders are also generated by the existing
  Debug test suite and uploaded by the existing development CI evidence artifact.
- This checkout has no Gradle wrapper; local commands use installed Gradle 9.3.1
  with JDK 21 and the supplied Android SDK environment.


Final local validation (JDK 21 / Gradle 9.3.1):

- `gradle :app:testDebugUnitTest :app:assembleDebug -Proborazzi.test.record=true`:
  **336 tests, 0 failures/errors/skips, 61 classes; build successful**. Includes
  11 storage failure tests, all 54 `KisUiTest` cases and all 6 tutorial cases.
- Focused follow-up after the layout correction: 18/18 passed.
- `git diff --check`: passed. Shared fixtures: 9/9 identical.
- Final local reports: `app/build/reports/tests/testDebugUnitTest/`.
- Validation-only Debug APK: `app/build/outputs/apk/debug/app-debug.apk`, package
  `com.aistudio.worktracker.qztvdw`, version 1.4 / code 5 (existing Debug configuration).
  SHA-256 `21674e14727b04687b73cecb0f81a6098223e89e984d1668f1aa327b141e9027`.
  This is not the distribution identity and is not delivered for installation.
- PR CI/watch/log results and exact review commit are recorded in the PR and the
  requested English handoff report (`/workspace/fable-review/cx/report-android.md`).
  No CI outcome is inferred from a push. No signed-update or physical-device gate
  is claimed by the local tests above.

Source anchors for the six-item review (line numbers on the review branch):

1. `app/src/main/java/com/example/data/WorkStorage.kt:13`,
   `app/src/main/java/com/example/ui/WorkViewModel.kt:55`, `:195`, `:847`, `:875`,
   `app/src/main/java/com/example/ui/WorkOnboarding.kt:62`,
   `app/src/main/java/com/example/MainActivity.kt:124`.
2. `app/src/main/java/com/example/MainActivity.kt:297`,
   `app/src/main/java/com/example/ui/kis/KisLaunch.kt:42`,
   `app/src/main/java/com/example/ui/kis/KisAccount.kt:59`,
   `app/src/main/java/com/example/ui/WorkOnboarding.kt:269`.
3. `app/src/main/java/com/example/ui/kis/KisAccount.kt:146` and `:162`.
4. `app/src/main/java/com/example/ui/kis/KisDetail.kt:137` and `:264`,
   `app/src/main/java/com/example/ui/kis/KisHistory.kt:338`,
   `app/src/main/java/com/example/data/WorkDao.kt:295`.
5. `app/src/main/java/com/example/ui/kis/KisAccount.kt:73`.
6. `app/src/main/java/com/example/ui/kis/KisKit.kt:93`, `:245`, `:258`, `:276`,
   `app/src/main/java/com/example/ui/kis/KisEditor.kt:583` and `:611`;
   `app/src/test/java/com/example/WorkPaletteTest.kt:32`,
   `app/src/test/java/com/example/WorkComponentsTest.kt:45`.

CI follow-up (initial source `292c9e6`): Debug coverage passed; Release ran 270
cases and failed only `NotificationJobsTest.quietHoursHoldTheNotesBack` at exactly
23:59 UTC. That existing test used the host clock with a quiet interval ending
exclusively at 23:59. It now uses a fixed synthetic noon and explicitly asserts
that the end is excluded. Notification behavior is unchanged. The startup error
host also explicitly provides RTL before the main shell exists, with an assertion
from an LTR test host. After these follow-ups: 7 targeted Debug cases, 6 Release
notification cases and refreshed Debug assembly passed. The 336-test consolidated
local run above was on `292c9e6`; the PR reruns full CI on the follow-up commit.
