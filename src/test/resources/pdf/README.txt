unicode-clinical-print.pdf is a synthetic three-page reader fixture generated with
OpenPDF 3.0.3 and embedded DejaVu Sans, using the CARLOS PdfFonts factory.
Its pages contain ASCII form markers, Vietnamese/Polish/Turkish letters, mathematical
comparisons, and parentheses. The text uses CID glyphs with ToUnicode mappings,
so tests fail if a reader inspects raw Latin-1 content streams instead of rendered text.
No patient records are used. Poppler pdftotext reads the fixture in Node regressions.
