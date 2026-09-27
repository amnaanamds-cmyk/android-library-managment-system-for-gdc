"""Render the research paper to a print-ready PDF.

Same approach as build_book.py: fonts embedded as data URIs rather than
linked, so the PDF renders the intended faces on a machine with no internet
and none of them installed.
"""
import re
from pathlib import Path
from playwright.sync_api import sync_playwright

PRINT_CSS = """
<style>
  @page { size: A4; margin: 22mm 22mm 18mm; }
  html { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
  body { background:#fff; padding-inline:0; font-size:10.4pt; line-height:1.55; text-align:justify; hyphens:auto; }
  .paper { max-width:none; padding-block:0; }
  p, li { orphans:3; widows:3; }
  h1.title { font-size:22pt; }
  .subtitle { font-size:12pt; }
  h2 { font-size:14pt; break-after:avoid; }
  h3 { font-size:10.5pt; break-after:avoid; margin-top:16pt; }
  .masthead { break-after:avoid; }
  .abstract, .finding, .callout, pre, blockquote, table, .figcap { break-inside:avoid; }
  pre { font-size:8pt; }
  .figcap { break-before:avoid; }
  h2:has(+ *) { break-after:avoid; }
</style>
"""

src = Path("nexlib-paper.html").read_text(encoding="utf-8")
fonts = Path("fonts-inline.css").read_text(encoding="utf-8")
head, body = src.split('<div class="paper">', 1)
head = re.sub(r'<link rel="stylesheet" href="https://fonts\.googleapis\.com[^>]*>',
              f"<style>\n{fonts}\n</style>", head, count=1)
assert "fonts.googleapis.com" not in head

Path("paper-print.html").write_text(
    '<!doctype html>\n<html lang="en" data-theme="light">\n<head>\n<meta charset="utf-8">\n'
    '<style>*{box-sizing:border-box}img{max-width:100%}</style>\n'
    + head + PRINT_CSS + '</head>\n<body>\n<div class="paper">' + body + "\n</body>\n</html>\n",
    encoding="utf-8")

FOOT = ('<div style="font-family:Georgia,serif;font-size:8pt;color:#777;width:100%;'
        'padding:0 22mm;display:flex;justify-content:space-between;">'
        '<span>NEXLIB: Aggregation Without Access</span>'
        '<span class="pageNumber"></span></div>')

with sync_playwright() as p:
    b = p.chromium.launch(executable_path="/opt/pw-browsers/chromium-1194/chrome-linux/chrome")
    pg = b.new_page()
    pg.goto(Path("paper-print.html").resolve().as_uri(), wait_until="load")
    pg.wait_for_function("document.fonts.status === 'loaded'", timeout=30000)
    pg.pdf(path="NEXLIB-Research-Paper.pdf", format="A4", print_background=True,
           display_header_footer=True, header_template="<div></div>", footer_template=FOOT,
           margin={"top": "22mm", "bottom": "18mm", "left": "22mm", "right": "22mm"})
    b.close()
print("built NEXLIB-Research-Paper.pdf")
