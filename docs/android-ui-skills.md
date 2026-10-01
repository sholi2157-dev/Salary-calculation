# Android UI Skills — RC11 owner review

Scope: Android only. Requested: apply ui-skills.com design guidance and preserve a
complete pre-change recovery version. No stable/public release.

## Baseline and recovery

Actual clean starting HEAD: `25f7066cbe058af226ba46befc990f2d8b30b929` on
`codex/android-local-distribution`. Latest delivered RC10/code18, signed source
`068c7fada2dad09c59c0b240c4ddb4e5bad18d20`. Prior CI run36786550537/job110129210875
and actual logs inspected: builds, unit tests, signed upgrade and UI gates passed.

Remote backup branch `backup/android-rc10-before-ui-skills-20261001` was created
before any edits and read back with exact SHA25f7066. A verified full git bundle
and byte-verified RC10 APK are also retained locally. The supplied RC7 APK embeds
source e7b305c and is historical visual context, not the current release baseline.
The complete465.47s recording was decoded and inspected across the timeline in
five contact sheets sampled every3s, plus a15s overview; this is not real-time
interaction review and does not establish motion/frame-level correctness.

To restore: restore only the UI files changed by this task from the backup branch,
then build/sign a new update with versionCode ABOVE every delivered version.
Keep current package, permanent signing and release tooling; never reset/force
push the release branch, downgrade/install RC10 over RC11, uninstall or clear data.
A partial revert can restore just the disliked visual change. The checkpoint
preserves source and original APK; it is not a backup of the owner's phone data.

## Guidance read and adaptation

Read UI Skills Root, Interface Design (full instructions), and Better UI at:
- https://www.ui-skills.com/skills/ibelick/ui-skills-root
- https://www.ui-skills.com/skills/dammyjay93/interface-design
- https://www.ui-skills.com/skills/jakubkrehel/better-ui

These are mainly web guidance. Apply hierarchy, restraint, surfaces, typography
and component reuse in native Compose; do not introduce CSS/web controls or new
libraries. Existing0.96 critically damped press feedback, accessible48dp History
actions, tabular timer, native navigation and interruptible form transition remain.

Working brief: a Hebrew speaker logging shifts on a phone, checking hours, owed
pay and payment status. Vocabulary: work ledger, shift, hourly rate, receipt,
settlement, employer, worker. Material palette: navy ink, charcoal desk, pale
paper text, blue pen, muted steel. Signature: each shift retains category/date,
stored amount/currency and explicit paid/pending status in one readable RTL row.
Reject decorative moving purple canvas, multicolored glass panels and competing
accents in favor of a still navy canvas, opaque tonal layers and one action hue.

Intent for shared theme/cards/forms/summary: fast readable accounting at night.
Hierarchy: saved money/hours are primary, labels secondary, metadata quieter.
Palette: WorkPalette semantic tokens; accent fill differs from brighter accent
text for contrast. Depth: tonal surface layers with quiet structural borders.
Typography: bundled Heebo in every Material role, zero Hebrew letter spacing,
regular metadata and tabular form digits. Spacing: retain existing4dp-based
layout, native touch targets and compact forms; no layout/persistence rewrite.

## Implemented

- Still navy canvas replaces the constantly moving purple gradient, with no
  perpetual background animation/recomposition.
- Shared opaque card/control/overlay/selected tokens; consistent blue action
  colors across Home, History, settings, forms and AI. Paid/unpaid/destructive
  colors and explicit labels retain their meaning.
- Save uses native Button fill/disabled rendering instead of a gradient painted
  outside the Button. Its existing interaction source, touch size and callback
  are unchanged.
- Every Material typography role uses offline Heebo, including bodySmall captions,
  dialog headlines and small labels. Remove artificial Hebrew letter spacing;
  replace light metadata with regular weight and improve secondary contrast.
- Summary gives total earnings (currency pages) and work hours (general page)
  stronger type hierarchy without changing any calculations or currency grouping.
- Numeric form fields use tabular digits and inset control surfaces. AI processing
  uses a quiet state surface, preserving visible progress, transcript and review.

No database, payroll, timer, drafts, import/export, providers, credentials, account,
cloud or website changes. Version is1.5-rc11/code19. Upgrade gate now pins the exact
previous delivered RC10 binary/hash and verifies RC10/code18→RC11/code19.

## Validation status

Implementation complete; CI/signed artifact, screenshots and real update gate
are pending. Do not describe this candidate as verified until actual results are
recorded. Physical-phone appearance, animation and real speech/live AI remain
owner checks; automated emulator motion is disabled by the existing workflow.
