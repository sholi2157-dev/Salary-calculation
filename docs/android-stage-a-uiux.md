# Android Stage A UI/UX checkpoint evidence — 2026-09-30

Branch: `codex/android-local-distribution`. Delivered baseline remains `1.5-rc8`,
versionCode `16`. Previous completed History work at `86268d40568d8b00c109fc5232f860a9402eabec`
is preserved. Checkpoint A: `0aa82083ad3848f07a6787603c997e1169e74a5d`, pushed.
Checkpoint B is the commit containing this evidence.

## Preparation and scope

Verified branch/HEAD and initially clean tree against the previous checkpoint.
Read AGENTS/current-state/final UI task and inspected current implementation/coverage.
Reviewed all seven newly attached screenshots and the full recording via successive
one-second frames/contact sheets. The two supplied video files were identical
SHA-256 `79c51073b57d6807538e5161a542632ef81f7dab6b66e7ff799bb7b1163785bd`.
The screenshot showing the old History title was not used to undo the completed toolbar.

Android UI only; no version/package/signing/release/backend/website changes. No
uninstall, storage reset, migration, historical amount recalculation or user-record modification.

## Implementation

- Home live-shift action is a compact scroll-content action, hidden while the report
  is open. There is no permanent live-shift footer shelf. Recent cards scroll fully
  above navigation. Normal-form Save retains the RC8 separate viewport/footer model.
- Shared Clock/Manual/Group fields use two compact rows, RTL category controls and
  full-width multiline Notes. Group separate rates share a row; worker behavior is
  retained. The Save validation/calculation/dispatch block is byte-identical to the
  previous History checkpoint. Numeric editing tags/accessibility belong to inputs.
- History observes actual lazy-list position changes during scrolling. Top overscroll
  does not hide navigation; rest/end restores it. A stable viewport plus measured
  navigation/selection-action bottom space keeps the final card usable. Existing
  toolbar/amount work is retained; the selection toolbar uses the actual wrapping FlowRow.
- AI has explicit writing/voice controls, listening/finishing/stop/cancel state,
  editable transcript, explicit parse, restrained progress panel, inline errors and
  RTL review cards with isolated currency strings. Only explicit confirmation calls
  the existing addShifts path. Speech session has no parse/save callbacks. Text
  survives errors, mode changes and interruption. Parsed proposals are consumed
  before save dispatch to prevent repeated confirmation.
- Scoped cleanup removes duplicated picker/form/AI layout, old auto-parse speech
  handling/stale listener closure, unused group autocomplete flag and unused Dashboard
  stop callback. Unrelated monolithic screens/helpers were left unchanged.

## Targeted checks actually run

During checkpoint A: `:app:testDebugUnitTest --tests com.example.StageALayoutTest`
passed 3 tests. During AI implementation, only the new AI tests and Debug compilation
were used; test harness assertions/field tags were corrected when failures exposed them.

Final focused command:

```text
:app:testDebugUnitTest
--tests com.example.StageALayoutTest
--tests com.example.AiShiftExperienceTest
--tests com.example.StageAInsetsTest
```

Result: **8 tests, 0 failures, 0 errors, 0 skipped**.

- Layout (3): 360dp/390dp RTL row bounds/compact footprint, last recent card fully
  visible, drafts retained across modes, focused rate editing, separate reachable Save,
  actual missing-key inline error retaining AI text; History top overscroll, genuine
  movement, final-card visibility and long press into selection.
- AI (3): both widths voice → editable text with no automatic parse/save, explicit
  parse/progress, retained input on error and correction/retry, review with ILS/USD
  and group members, return to edit and explicit save; cancel/late results/interruption.
- Insets (2): API35 platform-dispatched 24dp navigation/gesture and 300dp IME insets at
  both widths. Clock/Manual/Group Notes focus survives scroll; every tested field,
  worker hours, Save and AI parse remains reachable. Navigation returns after IME
  removal; drafts remain intact. These are simulated insets, not a rendered real IME.
- App/test Debug compilation passed. Roborazzi renders of all three modes, both inset
  widths and AI recording/processing/error/review were visually inspected.
- `git diff --check` passed. Final cosmetic group punctuation also compiled in Debug.

No broad suite, release gate, signed APK, upgrade test, public release or updater feed
was run/produced. `[skip ci]` intentionally suppresses the heavy RC workflow;
checkpoint A was checked through Actions API and had zero runs.

## Stage B limits

Stage A UI implementation is complete. Stage B remains deferred: signed RC build,
actual RC8 upgrade/data preservation/crash evidence and physical-device approval.
Robolectric proves Compose layout/state and dispatched insets, not physical keyboard
animation/flicker, OEM speech recognition/microphone quality or a live provider response
with the owner's key. Those must be verified in Stage B. No live AI request or key
change was made for these tests. Version/signing identity remain the delivered RC8 baseline.
