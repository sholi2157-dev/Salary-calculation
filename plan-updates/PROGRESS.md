# UX audit progress (checkpoint — resume from here)
Started 2026-11-01. Workspace: /home/user/Salary-calculation/ux-audit (git-ignored via .git/info/exclude). No app code changes.

## Sources audited
- Android newest: origin/claude/project-thread-zs8886 @ fd5a2b6 (2.2.3 code 48, unreleased) -> src/android
- Android published: origin/codex/android-local-distribution @ 26af6aa (2.2.1 code 46)
- Web newest: origin/claude/project-thread-c50fyf-web @ 69e430e -> src/web
- Web live: da7a7c3 (diff to newest: 5 small files, share.js/account.js wording)

## Steps
- [x] 1 Android theme/colors/typography code read
- [x] 2 Web CSS tokens read
- [x] 3 Web Playwright inventory (light/dark, 3 viewports, synthetic data)
- [x] 4 axe runs
- [~] 5 Android CI screenshots: blocked (blob host)
- [x] 6 Second pass (subagents)
- [x] 7 Report (Hebrew)

## Status 2026-11-01
- Web pass 1 done: notes/findings-web-pass1.md, explore JSON, shots/web, shots/web2, grids shots/g*.png d*.png
- Live site blocked again in this container (ERR_TUNNEL 403); local build used (diff vs live = 5 small files)
- Android CI screenshots: artifact + logs on blob.core.windows.net -> blocked. Only design/launch-2.0 pngs (2.0.8) available.
- Subagents launched (background): agent-android.md, agent-color.md, agent-typography.md, agent-web-ux.md (notes/)
- Next: UI UX Pro Max references, merge, report in Hebrew (report/)
- Report published (private): https://claude.ai/artifact/PJ1kCMdQL71FXWcjgw7zFX ; source ux-audit/report/index.html, generator tools/build_report.py. Waiting for owner approval before any implementation.

## Cross-analysis 2026-11-01
- Codex audit extracted to ux-audit/codex-audit/ (same SHAs fd5a2b6 / 69e430e). 37 findings + AND-C06 + QA-01..03.
- Unified plan: ux-audit/plan/index.html (published privately https://claude.ai/artifact/BRPfMVZedueLSGSY46VD25), register plan/unified-register.json (48 items), Codex prompts plan/codex-prompts.md (C-1..C-6).
- WAITING for owner decisions D-1..D-17 before creating branches/any code change.

## Stage 0 done 2026-11-01
- Pushed: audit/2.3-unified-plan @974cf2a (orphan docs, vercel deploy disabled; verified 0 Vercel deployments).
- Tags created locally (push blocked 403) → verified git bundle in audit branch backup/ and ZIP.
- Local only: release/2.3-android(fd5a2b6), release/2.3-web(69e430e), work/2.3-stage0-android(200a18a), work/2.3-stage0-web(4188607); worktrees /home/user/wt/{android,web,audit}.
- Web stage0: 122/122 node tests. Android RichUpgradeTest NOT yet compiled/run (needs signed Actions run approval).
- Waiting: owner decisions (DECISIONS.md personal items) + approval to start implementation / push release branches / run signed gate.
- M1a committed e85bcf6 on work/2.3-m1-data-web: D01 D02 D03 D04(store) CW-09(web) CW-11; 133/133
- M1b 53c51d4: CW-13 S-CUR-01 CW-15 D04-UI D05; node 137/137; browser 5 CI suites + m1-data pass; v2.cjs date flake fixed (pre-existing)
- M2 web 63dfb5a: money contract + input validation; pushed work/2.3-m1-data-web

## Implementation status (resume point) 2026-11-01
- Vercel: release/** and work/** excluded via vercel.json (verified 0 deployments). release/2.3-web @a15db10, release/2.3-android @e2c5ae3 pushed.
- Web work branch work/2.3-m1-data-web @27e0527 (pushed): M1 (D01-D04, CW-09, CW-11, CW-13, S-CUR-01, CW-15, D05), M2 (money contract CAL-02/05, S-CUR-06/07, input validation M-06/M-09), M3 (CW-06, CW-07). Node 145/145; browser CI suites + m1-data + m2-input pass.
- Android work branch work/2.3-m2-android @2661a65 (pushed): money contract, elapsedSeconds, live rate, refused digit, endUntil/overlapsAt, history now-ticker, timer follows place. Dev CI run 41282780676 SUCCESS.
- Signed gate: run 41281551484 on release (old RC16 path PASS; rich step FAIL = test design: 2.2.1 auto-recorded ~20 plan shifts after seed). Fixed on work/2.3-gate-debug @138c30e (snapshot after 2.2.1 open, past-only series); rerun queued.
- Design decisions recorded: plan/DESIGN-DECISIONS.md (keep Rubik+Secular One; soften live card & recap banner; editor scroll/IME; settings categories; palette conditional; pending: workplace palette, dark identity, dark save button, components, group detail, feedback).
- Pending owner: ERASE-WORDING.md approval (M-13 not implemented yet). CAL-03 deferred (commission semantics).
- TODO next: M-15 review flow (both), merge work→release, Codex prompts final (branch names work/codex-*), previews (web+Roborazzi), M-31 settings web, a11y.
