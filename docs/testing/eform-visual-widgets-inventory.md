# Visual eForm non-text controls

Target: `release/2026.08`; coverage request [#5801](https://github.com/carlos-emr/carlos/issues/5801).
Run `npm run test:eform-visual-widgets-playwright` on an installed disposable
Ubuntu deployment with `CARLOS_DISPOSABLE_HOST=true`, `EXCLUSIVE=1`, the installed
`EFORM_IMAGE_DIR` and `EFORM_RENDER_TEMP_DIR`. Poppler is required. This check
must not run concurrently with other checks (live wrapper: EXCLUSIVE=1).

The actual editor offers Checkbox, Text Box, Label, Shapes, Images and Signature
tabs. Its ordinary checkbox template is hidden/deprecated in favor of text
xBoxes; the check uses the offered xBox and radio-style templates. It creates two
independent xBoxes and two grouped radios, with distinct generated names/IDs and
explicit selected/empty defaults. Native Pre-Check, placement, double-click,
Group Yellow Radios, descriptive-title prompt and Group Completed controls are
used. No generated template HTML or handlers are substituted. Native Page Setup
sets 600x800 CSS content within the native one-page Letter PDF. Actual PDF
geometry is pinned independently at 612x792 points, with CSS-to-point positions
converted at 0.75.

An owned 100x60 red/black PNG is isolated fixture setup in the real image library;
the native Images selector binds and places it. The supported wet signature is
placed natively, drawn with mouse strokes and cleared with its actual control.
The editor's signature stamp remains separately available; this check does not
invent or convert signature mechanisms. Untouched empty signature fields and
cleared transparent PNG canvases are distinct supported representations. Drawn
PNGs must have complete dimensions and real opaque ink, with no external URL or
HTML-data substitute.

Exactly one template is created through Save As New Eform. Chart launch verifies
patient/template attribution and initial defaults. Both radio-selection directions are exercised with both independent checkboxes
selected; only the radio peer may clear. The patient then clears selected
defaults, changes every checkbox/radio, draws a signature and uses native
Save/Download. Independent SQL and inert DOM parsing compare exact nonempty
`eform_values`, absence of blank value rows and every saved patient-markup field.
An absent blank row alone never proves that a template default was cleared.
Native saved-instance reopening verifies the exact state and signature bytes.
The subsequent clear/Save-Download follows the existing new-revision contract, rather
than requiring an in-place overwrite; the earlier saved revision stays exact.
Native list selection/reopen names the intended revision by its actual ID.

Each actual downloaded PDF must contain the intended selected/cleared controls
in their independently measured print-media placement rectangles, preserving every field identity as well
as total selection count,
the red image region and signature ink in its own placement region. The cleared
revision requires zero ink in that same signature region, with no
editor, toolbar or clear-button UI. Selection and image/signature concerns are
judged independently, so a selection defect cannot prevent image assertions.
The library's visual-editor pencil reopens the same template; native dragging
changes a checkbox's layout and Update must retain its template ID and field
identities. An unsaved new patient instance must use the revised layout/defaults,
and neither Update nor opening it may change saved patient histories.

Every clinical assertion remains fatal in the final ledger while independent
concerns continue. [#5826](https://github.com/carlos-emr/carlos/issues/5826) is a
reproduced product defect: AddEForm drops explicit blank parameters, retaining
the template's X in saved markup, reopen and PDF. There is no failure pin or
production workaround. Tests, entry actions and cleanup can be reached while
this clinical verdict correctly fails.

Unrelated templates, image files and a separate owned control patient's complete
clinical state are snapshotted and checked, including failure cleanup. Native
create/update/instance writes are forwarded unchanged once under durable request
supervision; unknown outcomes retain all fixtures and a private journal. Cleanup
runs after browser teardown/drain, removes exact owned SQL rows and verified
image/PDF bytes, and preserves preexisting renderer files. Existing text/database-
tag and editor-routing checks remain registered unchanged.
