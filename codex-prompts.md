# Codex task prompts (2.3.0)

Send only after release/2.3-web and release/2.3-android exist.

## C-1 — QA-03 — שער הבדיקה של הווב
Base: release/2.3-web

```
Repository: sholi2157-dev/Salary-calculation. Read AGENTS.md first.
Base branch: release/2.3-web (do not use main). Create branch: codex/qa03-web-fixture-gate.

Problem (audit finding QA-03): the "Web 2.0 (כיס) checks" workflow (.github/workflows/web-v2.yml) compares the website's shared expected-result fixtures against an older public Android target, so on the newest web commit the fixture step fails before the browser test stage runs (run 40990885116 failed on 69e430e).

Task:
1. Make the fixture comparison target the Android release branch paired with this web branch: release/2.3-android. Keep a separate, clearly named step for backward compatibility against the published tag android-v2.2.1-code46 that reports the difference without blocking the browser stage.
2. The browser stage must run even when the backward-compatibility comparison reports a difference. Do not delete, skip or weaken any existing test.
3. Touch only .github/workflows/web-v2.yml (and a helper script under scripts/ only if strictly needed).

Acceptance: run the workflow with workflow_dispatch on your branch. Fixture comparison against release/2.3-android passes, the browser stage runs, and 119/119 node tests pass. Report the run URL and result.
Constraints: no application code changes, no version bump, no deploy, no merge. Open a PR into release/2.3-web and stop. Reply in Hebrew with the commit SHA, the PR link and the CI result.
```

## C-2 — QA-01 — בדיקות אנדרואיד שלא רצות
Base: release/2.3-android

```
Repository: sholi2157-dev/Salary-calculation. Read AGENTS.md first (permanent package com.aistudio.worktracker.qztvdw.distribution; never touch signing material).
Base branch: release/2.3-android, AFTER the Stage 0 commit (rich upgrade gate) has been merged into it — Claude will confirm. Create branch: codex/qa01-android-test-filters.

Problem (audit finding QA-01): seven existing unit tests (NotificationJobs* and GeminiCurrency* test classes under app/src/test) are never selected by the --tests filters in .github/workflows/android-v2.yml and the "Consolidated regression" step of .github/workflows/android-v2-rc.yml.

Task:
1. List the exact test classes that exist but are not matched by any filter in either workflow.
2. Add them to both workflows' filters (Debug and Release logic tests, the same way the other logic tests are listed).
3. If any of them fail, do NOT change the tests or the app. Report the failure output instead.
4. Touch only those two workflow files.

Acceptance: workflow_dispatch of android-v2.yml on your branch succeeds, and scripts/summarize-android-tests.py shows the higher test count. Report the before/after counts.
Constraints: no app code changes, no signing, no RC, no publishing, no merge. Open a PR into release/2.3-android and stop. Reply in Hebrew with the SHA, PR, run URL and test counts.
```

## C-3 — S-CUR-03 + R-22 — גרסת ההורדה
Base: release/2.3-web

```
Repository: sholi2157-dev/Salary-calculation. Read AGENTS.md first.
Base branch: release/2.3-web. Create branch: codex/download-version-label.

Problem (audit findings S-CUR-03 and R-22):
- web/kis/download.js shows the hardcoded "גרסה 2.0.3" and falls back to the 2.0.3 APK (lines 4, 12, 30).
- lib/server/android-release.cjs falls back to 2.0.5 and returns the version from the tag by stripping only "android-v", so the current tag "android-v2.2.1-code46" becomes "2.2.1-code46". download.js:28 then rejects it (it accepts only digits and dots), so the label never updates.

Task:
1. In android-release.cjs, parse tags of the form android-v<semver>[-code<N>] into {version:'2.2.1', code:46}. Keep the response shape backward compatible (version stays a plain semver string; code is an optional new field).
2. Remove the hardcoded old version from download.js. While loading, show a neutral label ("בודק גרסה…"). On failure, show "הגרסה האחרונה" and link to the stable releases/latest page, never to an old APK. Keep existing Hebrew wording style and existing CSS classes (no visual redesign; another task owns styling).
3. Add node tests in web-tests/ for tag parsing (with and without -code, malformed tags) and for the failure label.

Acceptance: all existing node tests pass, the new tests pass, and the web-v2 workflow succeeds.
Constraints: touch only web/kis/download.js, lib/server/android-release.cjs and new or updated tests. No deploy, no merge. Open a PR into release/2.3-web. Reply in Hebrew with the SHA, PR, tests and CI result.
```

## C-4 — S-CUR-02 — שדות זמן בגיבוי וב-CSV
Base: release/2.3-web

```
Repository: sholi2157-dev/Salary-calculation. Read AGENTS.md first. Use synthetic data only.
Base branch: release/2.3-web. Create branch: codex/import-time-fields.

Problem (audit finding S-CUR-02):
- web/transfer.js (around lines 98–105) validates money fields but passes startTime/endTime through without checking type or format.
- web/v2/share.js (around lines 135–145) neutralizes spreadsheet formulas in name, note and currency but not in time cells, and assumes every cell is a string.
- A backup with startTime:'=1+1' exports a live formula. A backup with startTime:123 makes the export throw "s.includes is not a function".

Task:
1. On import, accept startTime/endTime only as null or a valid "HH:mm" string (00:00–23:59). Reject an invalid row with a clear Hebrew message in the existing import check summary. Never silently change the stored amount or hours.
2. In CSV/TSV export, coerce every cell to a string and apply the existing formula neutralization to every text cell, including times.
3. Keep share.js output for valid data byte-identical (the Android-shared fixtures in web-tests/fixtures/v2-share-fixtures.json must still match).
4. Add node tests: formula in a time field, numeric time, valid times unchanged, round trip of a valid backup unchanged.

Acceptance: 119 existing node tests plus the new tests pass, and the fixture comparison is unchanged.
Constraints: touch only web/transfer.js, web/v2/share.js and tests. No UI redesign, no deploy, no merge. Open a PR into release/2.3-web. Reply in Hebrew with the SHA, PR and test results.
```

## C-5 — CW-16 + AND-C01 — גבול טווח ביום שעון קיץ
Base: שני הענפים (שני PR נפרדים)

```
Repository: sholi2157-dev/Salary-calculation. Read AGENTS.md first. Synthetic data only.

Problem (audit finding CW-16/AND-C01): the history date-range filter computes the end of the range by adding a fixed 24h in milliseconds. On the day clocks change (e.g. 27/03/2026 in Asia/Jerusalem), filtering only 27/03 also includes 28/03.
- Web: web/kis/history.js around line 33.
- Android: the equivalent range computation used by the history filter (search for the code path that builds the range end from the selected end date in app/src/main/java/com/example/ui/kis/KisHistoryLogic.kt or KisHistory.kt).

Task (two separate branches and PRs):
A) Base release/2.3-web, branch codex/dst-range-web: compute "the next calendar day at 00:00 local" with setDate(+1) instead of +86400000. Add node tests run with TZ=Asia/Jerusalem covering both DST transitions, month/year ends, and the last day included / next day excluded.
B) Base release/2.3-android, branch codex/dst-range-android: same fix with java.time (LocalDate.plusDays(1).atStartOfDay(zone)), and a JVM unit test with zone Asia/Jerusalem for the same cases. Add the new test class to the workflow test filters only if it isn't matched by an existing pattern.

Constraints: change only the range-boundary computation and tests. Do not touch UI layout or styling (another task owns it). No version bump, no RC, no merge. Open both PRs. Reply in Hebrew with both SHAs, PRs and test results.
```

## C-6 — D10 — מונה הייבוא באנדרואיד
Base: release/2.3-android

```
Repository: sholi2157-dev/Salary-calculation. Read AGENTS.md first. Synthetic data only.
Base branch: release/2.3-android. Create branch: codex/import-count.

Problem (audit finding D10): app/src/main/java/com/example/data/WorkDao.kt (around lines 183–216) can report more imported records than were actually inserted. For example, a row with an existing seriesKey and different hours is correctly not inserted, but it is still counted.

Task:
1. Count successful inserts separately from skipped or conflicting rows and return both.
2. Show the user "נוספו X · דולגו Y" (keep the existing message style; if Y=0 show only the added count).
3. Never change existing records or amounts.
4. Add a JVM/Robolectric unit test: repeated import of the same backup (adds 0), same seriesKey with different hours (skipped), and existing amounts unchanged.

Acceptance: android-v2.yml (workflow_dispatch) is green with the new test included.
Constraints: touch only WorkDao.kt, the minimal caller that shows the message, and tests. No Room schema change, no version bump, no signing, no merge. Open a PR into release/2.3-android. Reply in Hebrew with the SHA, PR and results.
```