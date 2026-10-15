"""Inline the shared engine into each direction -> dist/<name>.html (self-contained artifact pages)."""
import pathlib
root = pathlib.Path(__file__).parent
core_js = (root / 'shared/core.js').read_text()
core_css = (root / 'shared/core.css').read_text()
(root / 'dist').mkdir(exist_ok=True)
for page in sorted(root.glob('*.html')):
    html = page.read_text()
    assert '/*CORE_JS*/' in html and '/*CORE_CSS*/' in html, page
    (root / 'dist' / page.name).write_text(html.replace('/*CORE_CSS*/', core_css).replace('/*CORE_JS*/', core_js))
    print('built', page.name)
