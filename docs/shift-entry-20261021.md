# Shift entry and history update — 2026-10-21

Owner authorized both platforms and combined public release with sharing work.
- UI durations: hours and minutes (H:MM); dot/comma on decimal keyboards separates minutes. 1.40 → 1:40 → 100 minutes. Stored decimal hours and legacy drafts retain their meaning.
- Validate minutes 00–59 and maximum 24 hours; preserve untouched imported hours, money and worker metadata.
- Clock starts with START; mode switch carries current net duration. Seven civil calendar dates centered on today (DST safe). No popular-start chips.
- Worked/planned lives inside filter sheet; cancel discards edits; apply changes view and clears stale selection. Planned view always retains filter access.
- Search/copy controls use filter surface, outline and radius.
- Validate targeted regressions, browser screenshots, signed previous-release update and crash logs before publication.

Additional audited fixes:
- Clock/manual/group transitions carry exact current net minutes, discard stale durations, keep manual groups manual and preserve 24-hour validation.
- New worker defaults carry exact duration; renaming/removing workers retains their own original imported hours and unknown metadata.
- Web preview uses the exact amount that will be stored on an imported-entry edit, matching Android.

Web verification: source 96e3e4bb52a6a693fb728c682295934b724fa7c3; run 39669064791 SUCCESS.
Build + 92 tests + byte-identical shared fixtures + complete v2/download/sharing browser suites.
Inspected 360px dark manual 1.40 → 1:40 / ₪100, centered dates, history tools, planned filter; wider/light layouts included.
PR #9 merged to maintenance branch at 9cfd9c70b3659f2ce8b38eebe279e1f4ffd8c50f.
Staged production deployment dpl_52v5KTd2H9dEggr8oeHsWTzkCrG6 matches source and full shell build digest 8d45814d204db702.

Android final source: 18ae87f9cc1285b5fc2767511a6d950859a8e00d; candidate run 39668563320 SUCCESS.
141 Debug + 110 Release tests; permanent package/certificate gates; signed APK 2.0.4 / code 37.
SHA-256: 54cef6ca017da5ce09bb9a3974990cc951eaf0b38d58bb7ec89e423bf967e1ee.
Real RC16 → delivered 2.0.3 → exact candidate update passed with byte-identical before snapshots;
Release UI, keyboard/resume/save, currency/default-workplace, plan recording and fresh launch passed.
Both crash logs clean. Candidate screenshots inspected. PR #8 merged at ddbb3515401b9320376ca055617aa917914e0d6c.
Earlier run 39667966007 failed the default-workplace test after a passing update gate; the final run passed that test and all subsequent gates. The earlier candidate is not used for publication.

Website promoted: dpl_52v5KTd2H9dEggr8oeHsWTzkCrG6, same tested production build (no rebuild).
Stable origin https://salary-calculation-eta.vercel.app resolves to it. Public browser run 39670920652 SUCCESS: v2 flows, download guide, copy/share recipient boundaries, generated money PNG, unsupported sharing and cancellation, preserved synthetic storage. Post-merge checks 39670920676 SUCCESS.
Android publication completed via docs/android-update-release.json: run 39670906841 SUCCESS (attempt 2).
Public release android-v2.0.4 / Salary2.0.4.apk has the exact verified SHA above, anonymously downloaded and checked.
Live RC16 app manual update button found code 37, downloaded the verified bytes and opened Android's actual installer.
Installer confirmation and completed-install screen passed; installed version 37, unchanged before/after synthetic data,
clean crash log and no same-version update offered again. No published APK rebuild or re-sign.
The first live-verification attempt failed on a corrupt Android Emulator SDK ZIP before running the app; retry only the failed job succeeded.
Native WhatsApp recipient/rendering and Safari file sharing remain physical-device checks; automated share/clipboard tests validate payloads and cancellation, not external app behavior.
