# RC16 audit-fix redistribution — 2026-10-14

Owner authorized merging and redistributing all known platforms: Android GitHub Release/update feed, existing Drive APK, existing Vercel website origin.

Android: merge PR #4, increment distribution version to 1.5-rc16 / 24 only. Preserve application ID, permanent certificate, storage schema and existing feature flags. Previous installed binary is exact RC15 from run37993791019, SHA2569935eb6d49c971832653a207186e64392c93ba65da271d839684c5b624ff0bcc.

Release gates: consolidated regression/build; signed original RC15 to RC16 upgrade with exact synthetic data comparison and empty crash log; fresh onboarding; verify identity/hash/certificate; publish the exact tested artifact without rebuilding; exercise the real live update button/download/Android installer; replace existing Drive file in place preserving permissions and parent.

Website: merge tested PR #3 on separate web branch, retain existing origin/local storage and feature flags; verify deployed commit and public browser flow. Vercel connector currently reports scope authorization failure, pending resolution.

Status: signed candidate verified. Source `c0d655365cfd35e2bba11807cb4fe3c0ff27af93`; run38591694409/job115947937994 SUCCESS; artifact11908187232. 91 Debug + 73 Release tests passed with zero failures/errors/skips. Nine native stages passed, exact RC15 snapshots match after upgrade, crash logs clean. APK SHA256 `400e11bccd9a46e7911c498137da9ba5d23b3b86cee50f0f1ea6400f40e7cc30`, size19231375. Public update publication requested with this exact artifact; public/Drive verification pending. Physical phone, native sharing, live AI with owner key remain manual gates. General simultaneous full-snapshot web localStorage writes remain a known limitation described in the audit report.
