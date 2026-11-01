# Android metrics (fd5a2b6)
- Theme: KisTheme.kt. Light bg F1F3F8 (blue-grey), ink 1B2335 navy; Dark bg 121212 neutral grey, surface 1E1E1E, ink white. Light tinted cool-blue vs dark neutral -> not same identity.
- Hardcoded hex outside theme: 12 (notification 5145E8 x5, KisKit logo icon colors, KisInbox 121212, KisStory, Onboarding FFC94D)
- Inline fontSize: 23 distinct values (10..130sp), 48 TextStyle.copy(fontSize=..)
- KisType usage: caption 82, label 80, body 55, title 50, hero 11, amount 11, page 5, micro 5
- Corner radii: 20 distinct values (2,3,6,8,9,10,12,14,16,18,20,22,24,26,99,50%)
