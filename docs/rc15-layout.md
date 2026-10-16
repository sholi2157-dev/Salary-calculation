# RC15 compact controls and category defaults

October 9 owner request authorizes both platform updates and a new installed-app-channel release.
Android starts from the verified RC14 branch e18853d. Permanent package/signing identity is retained; code23/name1.5-rc15 replaces delivered RC14/code22 through the existing update channel.

Default category settings now show a single current choice with an on-demand menu and add-category dialog. Adding validates the name/rate, creates the category with the current default currency and selects it only after insertion. No historical financial records are recalculated.
Quick active-shift setup and saved entry editing place short currency choices beside the hourly rate. Standalone active currency choices and category fields are centered. The bottom navigation's selected pill follows the supplied website reference; existing native navigation, history and search behavior are preserved.
RC14's active currency, confirmed discard, currency-only saved-entry editing and multiline feedback fixes are retained and covered by the native gate, with new default picker/add checks.

Build validation and the signed RC14-to-RC15 data-preservation gate must pass before publication. The same tested binary is then published and checked through the actual current RC14 app button, network download and Android installer. Exact CI/source/hash evidence will be added after completion. User authorization already covers publishing this update; no signing material is copied out of CI.

## Verified candidate

Broad source eb3d75b3f6da3a1caff79f338e3913b90703c589 passed run37992932956/job114031495240, including full configured unit/UI suite and signed RC14-to-RC15, currency/feedback/default-picker/add, keyboard and fresh/cold-launch gates. Final source bc876374cb7486351fcd7bd9c69c229215241021 adds only standalone edit-category centering and the focused CI path; run37993791019/job114034476879 compiled it and repeated the signed upgrade, native UI/currency/default/feedback/keyboard and fresh/cold-launch gates. The completed broad suite was retained rather than rerun.
Candidate artifact11645758811 contains code23/name1.5-rc15 with SHA2569935eb6d49c971832653a207186e64392c93ba65da271d839684c5b624ff0bcc, 19231379bytes and the unchanged permanent certificate. Independent local APKv2 RSA signature, complete content digest and binary-manifest checks passed on those bytes. Original RC14/code22 was used, not rebuilt; financial snapshots compared exactly before and after the signed update, crash logs were empty.
The publication job reuses this exact tested artifact. The online UI/installer gate reads expected target code/name/hash from publication configuration and runs on the real previous RC14 installation. It preserves the previously fixed wait for Android installer success and package unfreeze. Live publication proof remains pending until that job finishes.

## Live installed-app channel completed

Run37994406008, publication commit9dd04eee6f99fce7f1792ae7f7eba7acc28539e5, passed both publication job114036622149 and live verification job114036824484. Publication reused exact tested artifact11645758811; anonymous latest manifest and APK bytes were verified. Public release is android-v1.5-rc15/SalaryRC15.apk, code23/name1.5-rc15/hash9935eb6d49c971832653a207186e64392c93ba65da271d839684c5b624ff0bcc.

Live artifact11645804658 proves real previous RC14/code22 UI found23 through its actual manual button, downloaded/validated the published binary, and completed the real Android package installer. The successful-install screen was inspected. Exact before/after financial snapshots match, 3 synthetic entries remain, crash log is0bytes and updatedRC15 does not offer itself. Result is archived at docs/release-evidence/rc15/live-update.json. This is disposable Android evidence, not a claim of remote installation on the owner's physical phone.

Existing Drive file1jDiOB-ByrQFs3CBzHehJEZSgl9ZKvCY_ was replaced in place with the same verifiedRC15bytes and renamedSalaryRC15.apk. Readback confirms19231379bytes, unchanged file/link/parent, and unchanged owner+5 individual reader permissions; no anyone/domain sharing was added. Website production remains the verified same-origin d4feadd build, documented on codex/web-rc15-live. No other Drive files were touched.
