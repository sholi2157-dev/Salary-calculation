# RC15 website and Android feature parity

Owner's October 9 request supersedes old Android-only/Web-only scope notes.
Web baseline is the actual public eta deployment source 42af2c3527fbbc9af1860806f56f2793f90e0ce5, retaining its analytics loader, styles, data contracts and origin.
Android baseline is e18853da89be656b72a290ba76e0dfd5e1c7adac; delivered RC14/code22 remains the previous installation.

Web adds RC14's unconfigured-category currency fallback to the main default, editable active-shift currency without restarting elapsed time, and confirmed cancellation without saving a history entry. Finish reads the current timer after confirmation. Cancellation checks owner and timer ID against stale confirmations.
Both platforms show the current default category in one control, opening choices and an add-category path on demand. Web changes remain in the settings draft until Save; Cancel discards them. Currency/rate fields share a compact row; standalone category controls are centered. Website history/search/navigation and existing navy/violet styling remain the supplied screenshot references.
Saved entry currency editing and multiline feedback are retained. Web AI/account flags and Android personal-key behavior are unchanged. No financial schemas, historical amounts, user records or permissions are reset.

Validation: 43 local Web unit tests and build pass. Existing browser preservation suites and the new RC15 browser suite run in CI at 360/390/768/1440 widths. Native tests include the existing currency/feedback gate plus default picker/add assertions. Signed RC14-to-RC15 and actual public-channel download/installer/data gates must pass before completion is claimed. Publication and deployment evidence will be recorded after verification.
