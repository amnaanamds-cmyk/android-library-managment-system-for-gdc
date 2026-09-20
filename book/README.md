# Field Notes from a Provincial System

An engineering casebook drawn from NEXLIB's own defect history. Every case is a
real failure from this repository, cross-referenced to the commit that fixed it.

## Building the PDF

```bash
cd book
python build_book.py      # writes NEXLIB-Casebook.pdf
```

Requires `playwright` and `pymupdf`. Fonts are embedded from `fonts-inline.css`
rather than linked, so the PDF renders correctly on a machine with no internet
and none of the typefaces installed.

## Adding a chapter

Chapters live in `nexlib-book.html` as `<article class="chapter">` blocks and
follow one structure throughout:

1. **Symptom** — what the user actually saw, before anyone knew the cause
2. **False trails** — the hypotheses that were wrong, and why they were tempting
3. **Mechanism** — what was really happening
4. **Fix** — the change, with the code
5. **Lesson** — the generalisation, in a `.lesson` block

The false-trails section is the part most debugging writing omits and the part
that carries the most teaching. Keep it.

Source material is `git log` on this repository: each fix commit records the
failure and the reasoning at the time it was understood, which is more accurate
than reconstructing it later.
