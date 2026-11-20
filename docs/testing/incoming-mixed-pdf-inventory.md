# Scanned and mixed-geometry incoming PDFs

Ticket: [#5731](https://github.com/carlos-emr/carlos/issues/5731).
Check: `incoming-mixed-pdf`, extended tier, 600-second limit. Requires Poppler,
locked `pngjs`, `CARLOS_DISPOSABLE_HOST=true`, `EXCLUSIVE=1` and the installed
`INCOMINGDOCUMENT_DIR` queue `1/File`. Recycling follows the configured
`INCOMINGDOCUMENT_RECYCLEBIN` contract. No OCR or new document feature is added.

The independent fixture contains two image-only scans and one image/text page,
asymmetric colour identities, a visible link annotation and distinct page boxes:

| Identity | MediaBox | CropBox | Initial rotation |
| --- | --- | --- | --- |
| 1 | 0 0 360 480 | 12 18 348 462 | 0 |
| 2 | 0 0 540 360 | 20 10 520 350 | 90 |
| 3 | 0 0 420 540 | 10 20 410 520 | 180 |

Page 2's annotation points to a synthetic `example.invalid` marker URL; it is
never followed. Page 3 has an independently checked marker caption. Pages 1 and
2 genuinely have no extractable text. Scanned content is checked by pixels.

| Native operation | Exact intended output |
| --- | --- |
| Open/image preview | All three identities and existing rotations display; actual iframe document/page and decoded image dimensions match. |
| Extract page 2 | Source contains 1,3; extracted document contains 2 with its annotation. Both reopen through the queue. |
| Rotate source page 1 by +90 | Source rotations are 90,180; page 3 and the extracted document remain intact. |
| Rotate all source pages by 180 | Source rotations are 270,0; relative orientations and cropped content survive. |
| Delete source page 1 | Source contains identity 3 at rotation 0; extracted identity 2 is unchanged. With recycling enabled, the exact deleted identity 1 at rotation 270 survives in the owned recycle output. Both active documents reopen correctly. |

Every output is inspected independently with Poppler: page count/order, effective
rotation, MediaBox/CropBox within 0.02 PDF points, complete mixed-page text,
annotation URI and cropped raster content. At 72 dpi, raster comparison permits
one pixel of edge quantization and eight RGBA levels, with at most 0.1% unmatched
pixels in either direction. Independently generated copies of the same fixture
at opposite PDF orientations demonstrate this renderer quantization without
using the application's edit implementation. Native preview colour bounds and
area fractions agree within 0.025 in normalized image coordinates; aspect ratio
agrees within 0.01. Full PDF output receives the stricter raster comparison.

Sensitivity controls reject omitted/reordered pages, changed crops/rotation/text,
lost annotation actions, absent/invisible scan identities, excessive shifts and
lost thin content. A one-pixel boundary control proves the documented tolerance.

The shared preview fixture captures the exact UI request's real upstream bytes
and forwards that same buffer. Chromium's debugging protocol text-decodes some
PNG responses declaring UTF-8; those debugger bytes are not substituted for the
native image. The browser must also display the actual image. No successful
preview, edit, extraction or filing response is stubbed.

Image GETs create server caches. Their operations are drained before cleanup;
unacknowledged responses retain fixtures. Cache pixels must match the captured
native response. Teardown atomically retires only byte-matching owned cache/PDF/
recycle files, restores exact provider preferences, and preserves all original
queue/recycle/cache hashes and an untouched control document. Request dispatch
and unknown edit outcomes have durable recovery journals.

The original `incoming-pdf-extraction` regression retains its collision,
cancellation, permissions, stale edits, scratch cleanup, capacity refusals and
actual filing checks. Its preview capture is now lossless and its cache/deleted-
page teardown is verified too. Earlier attempts with invalid selectors, debugger
PNG decoding, overly strict raster phase comparison or incomplete independent
cleanup audits remain failed evidence.

On snapshot31 Ubuntu 26.04.1 packages, both checks pass together in ON and BC.
Final independent audits report no owned incoming, recycled or cached files,
unchanged original configuration, verified/healthy packages, NRestarts=0 and
unchanged legacy email-log digests. Exact qualification and remaining release
limitations are in the [acceptance register](consolidated-test-ticket-acceptance.md).
