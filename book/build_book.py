"""Render the casebook to a print-ready PDF.

Fonts are embedded as data URIs rather than linked, so the PDF renders the
intended faces on a machine with no internet and none of them installed —
which is the situation on most college PCs.
"""
import re
from pathlib import Path
from playwright.sync_api import sync_playwright

PRINT_CSS = """
<style>
  @page { size: A4; margin: 20mm 19mm 18mm; }
  html { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
  body { background:#fff; padding-inline:0; font-size:10.6pt; line-height:1.6; }
  .book { max-width:none; padding-block:0; }
  p, li { orphans:3; widows:3; }
  h1 { font-size:30pt; }
  .subtitle { font-size:13pt; }
  h2 { font-size:16pt; break-after:avoid; }
  .part-title { font-size:19pt; }
  h3 { font-size:11pt; break-after:avoid; margin-top:18pt; }
  /* A chapter or a part page starts clean; a case study read across a break
     loses the thread between symptom and mechanism. */
  .chapter, .part { break-before:page; }
  .title-page, .toc { break-after:page; }
  pre, blockquote, .lesson, table, .caption { break-inside:avoid; }
  pre { font-size:8.2pt; }
  .caption { break-before:avoid; }
  .lesson { margin:16pt 0; }
</style>
"""

src = Path("nexlib-book.html").read_text(encoding="utf-8")
fonts = Path("fonts-inline.css").read_text(encoding="utf-8")
head, body = src.split('<div class="book">', 1)
head = re.sub(r'<link rel="stylesheet" href="https://fonts\.googleapis\.com[^>]*>',
              f"<style>\n{fonts}\n</style>", head, count=1)
assert "fonts.googleapis.com" not in head

Path("book-print.html").write_text(
    '<!doctype html>\n<html lang="en" data-theme="light">\n<head>\n<meta charset="utf-8">\n'
    '<style>*{box-sizing:border-box}img{max-width:100%}</style>\n'
    + head + PRINT_CSS + '</head>\n<body>\n<div class="book">' + body + "\n</body>\n</html>\n",
    encoding="utf-8")

FOOT = ('<div style="font-family:Georgia,serif;font-size:8pt;color:#777;width:100%;'
        'padding:0 19mm;display:flex;justify-content:space-between;">'
        '<span>Field Notes from a Provincial System</span>'
        '<span class="pageNumber"></span></div>')

with sync_playwright() as p:
    b = p.chromium.launch(executable_path="/opt/pw-browsers/chromium-1194/chrome-linux/chrome")
    pg = b.new_page()
    pg.goto(Path("book-print.html").resolve().as_uri(), wait_until="load")
    pg.wait_for_function("document.fonts.status === 'loaded'", timeout=30000)
    pg.pdf(path="NEXLIB-Casebook.pdf", format="A4", print_background=True,
           display_header_footer=True, header_template="<div></div>", footer_template=FOOT,
           margin={"top": "20mm", "bottom": "18mm", "left": "19mm", "right": "19mm"})
    b.close()
print("built NEXLIB-Casebook.pdf")
