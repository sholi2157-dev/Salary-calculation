# Automatic website updates — 2026-10-21

- Online navigation requests fresh HTML at the unchanged public origin.
- Each built document pins its scripts and stylesheet to its build identity.
  An unavailable asset cannot silently fall back to another build's cached asset.
- Complete offline shells install atomically and activate automatically. Activation
  never navigates an existing tab. Open forms and timers continue uninterrupted.
- Offline navigation and server errors fall back to the complete installed shell;
  failed partial installs cannot replace it. Only obsolete code caches are pruned.
  The previous complete code cache is retained for older open documents.
- Removed update banner/buttons, settings check action, version/commit footer,
  and the obsolete explicit-update manager. Help remains simply "עזרה".
- No financial storage, accounts, Android code, version or distribution changes.
- One-time migration: the old cache-first worker may serve its last cached page
  while the automatic replacement installs. No update button is required; once
  installed, the next navigation uses the new behavior. We deliberately do not
  force-reload an already open legacy form.

Validation: local build, 90 unit tests and diff whitespace checks pass. CI real
browser suite includes the exact previously deployed worker; A/B/C on one origin,
no automatic page reload, new draft and timer retention, unchanged synthetic 22
shifts/8 categories and dormant account data, offline reload, failed installation,
and removal of settings update/version UI. CI/deployment results recorded after
completion; not claimed from local unit tests.
