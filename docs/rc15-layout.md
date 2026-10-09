# RC15 compact controls and category defaults

October 9 owner request authorizes both platform updates and a new installed-app-channel release.
Android starts from the verified RC14 branch e18853d. Permanent package/signing identity is retained; code23/name1.5-rc15 replaces delivered RC14/code22 through the existing update channel.

Default category settings now show a single current choice with an on-demand menu and add-category dialog. Adding validates the name/rate, creates the category with the current default currency and selects it only after insertion. No historical financial records are recalculated.
Quick active-shift setup and saved entry editing place short currency choices beside the hourly rate. Standalone active currency choices and category fields are centered. The bottom navigation's selected pill follows the supplied website reference; existing native navigation, history and search behavior are preserved.
RC14's active currency, confirmed discard, currency-only saved-entry editing and multiline feedback fixes are retained and covered by the native gate, with new default picker/add checks.

Build validation and the signed RC14-to-RC15 data-preservation gate must pass before publication. The same tested binary is then published and checked through the actual current RC14 app button, network download and Android installer. Exact CI/source/hash evidence will be added after completion. User authorization already covers publishing this update; no signing material is copied out of CI.
