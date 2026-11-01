"""Current tokens, transcribed from KisTheme.kt (Android) and kis.css / ui.js (web). Android ARGB alpha converted to CSS #RRGGBBAA."""
LIGHT = dict(bg='#F1F3F8', surface='#FFFFFF', sunk='#E4E8F0', ink='#1B2335', muted='#5A6478', line='#D2D8E3',
             pending='#9A5000', pendingSoft='#FFEBC7', paid='#0B7F60', danger='#C2362B',
             edit='#1B2335', editInk='#FFFFFF', nav='#FFFFFFE6', navInk='#465066', navOn='#1B2335', navOnInk='#FFFFFF',
             bar='#FFFFFFF2', onBar='#1B2335', onInk='#F1F3F8', glow='#5145E8')
DARK = dict(bg='#121212', surface='#1E1E1E', sunk='#2A2A2A', ink='#FFFFFF', muted='#B3B3B3', line='#303030',
            pending='#FFC266', pendingSoft='#3A2D12', paid='#34C79A', danger='#FF8A7A',
            edit='#2A2A2A', editInk='#F1F3F7', nav='#181818', navInk='#B3B3B3', navOn='#FFFFFF', navOnInk='#121212',
            bar='#2C2E33', onBar='#F1F3F7', onInk='#121212', glow='#8F87FF')
GLOW_A = {'light': .22, 'dark': .40}
# name, from, to, on, accentDark, accentDarkInk  (identical in KisTheme.Workplaces and ui.js PLACES)
WORKPLACES = [
    ('indigo', '#5145E8', '#1E1873', '#FFFFFF', '#8F87FF', '#120F3A'),
    ('green', '#0E8059', '#073D2C', '#FFFFFF', '#3DDC97', '#03140E'),
    ('rose', '#D6336C', '#5C1230', '#FFFFFF', '#FF6B9A', '#2E0A18'),
    ('mustard', '#F5B82E', '#C98A10', '#1A1200', '#FFC94D', '#2A1C00'),
    ('purple', '#7D3CF0', '#2E1466', '#FFFFFF', '#B48CFF', '#1E0B3D'),
    ('blue', '#1F72E0', '#0C2F66', '#FFFFFF', '#5AA9FF', '#06203F'),
    ('teal', '#0E8A86', '#063B3A', '#FFFFFF', '#3CD3CB', '#04201F'),
    ('burgundy', '#B5294E', '#4A0F21', '#FFFFFF', '#FF6F8E', '#2E0812'),
]
