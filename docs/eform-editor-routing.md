# eForm Library: which editor the Edit pencil opens

Issue [#3166](https://github.com/carlos-emr/carlos/issues/3166) made the **Visual eForm Editor** the
default editor in **Administration ▸ Forms/eForms ▸ Manage eForms**, while keeping the **HTML source
editor** for every form the visual editor cannot change safely.

## Why it is not "visual for everything"

The visual editor does not edit a template's markup in place. It rebuilds the page from its own
widgets and regenerates every script when it saves. A form it produced round-trips. A hand-written
form, a form imported from another EMR or oscargalaxy.org, or a form with custom JavaScript loses that
code on the first visual save, and nobody can reliably prove arbitrary JavaScript is safe to drop.
So the visual editor is the default only for forms it can be shown to own.

## The rule

`EFormEditorResolver.resolve(formHtml)` classifies each template when the library lists it
(`EFormUtil.listEFormsWithEditor`):

| Kind | How it is recognised | Pencil opens | Also on the row |
|---|---|---|---|
| `VISUAL` | the marker the visual editor stamps on every save, `<meta name='carlos-eform-editor' content='visual' data-fingerprint='…'>`, **and** a fingerprint that still matches the rest of the markup | Visual eForm Editor, pre-loaded | **Edit HTML source (Advanced)** (`fa-code`) |
| `CONVERTIBLE` | a marker whose fingerprint no longer matches (changed outside the editor since), a form the editor saved before the marker existed (`<div id="eform_container">` opening with `<form id="inputForm">`), or the eForm Generator's page structure (`id="page1"` and an `id="BGImage…"` image) | HTML source editor (unchanged) | **Open in Visual eForm Editor** (`fa-object-group`), which warns that scripts are not kept |
| `HTML` | anything else, including blank markup | HTML source editor (unchanged) | nothing |

A user without `_admin.eform` write cannot open the visual editor, so every row stays `HTML` for them.
The HTML editor (`/eform/efmformmanageredit?fid=<id>`) is unchanged and still reachable from every row.

### Why a fingerprint

A marker alone says "the visual editor saved this once". It does not say nobody changed the form
since: a clinic that adds a custom script in the HTML editor would keep the marker, and the next visual
save would silently regenerate the script away. So the editor stamps the marker **last**, over the
finished markup, with a fingerprint of everything else: 32-bit FNV-1a over the UTF-16 code units of the
markup without the marker tag, as eight hex digits (`EFormEditorResolver.fingerprint` in Java,
`eformFingerprint` in the editor). Any later change outside the editor, even line endings, no longer
matches, and the form drops to `CONVERTIBLE`: still one click from the visual editor, but no longer the
default, and the editor warns before converting it. Saving it visually again re-stamps it. The
fingerprint detects change; it is not a security control.

The marker is matched only in the exact form the editor writes; a re-quoted or hand-copied tag is not
proof. Forms the editor saved before #3166 have no marker and are `CONVERTIBLE` until their first visual
save, rather than being trusted on structure alone.

The same check runs in the browser as `isVisualEditorAuthored()` in `visualEformEditor.jsp`, which is
what decides whether the editor shows the "converted" warning. Keep the two in step:
`scripts/eform-visual-editor-routing.test.js` runs the JavaScript against reference values the Java
unit test also asserts, and `VisualEFormEditorDefaultEditRegressionTest` pins the shared constants and
that the marker is stamped after the last rewrite of the source.

## Opening a form in the visual editor

- `/eform/visualEformEditor` opens blank, to create a new eForm (the **Create eForm** menu still opens
  it in a new tab).
- `/eform/visualEformEditor?fid=<id>` opens that form pre-loaded. `ViewVisualEFormEditor2Action`
  requires `_admin.eform` write (and `_eform` write when an fid is given, since the editor saves the
  form back), accepts GET/HEAD only, answers **400** for an fid that is not a positive decimal `int` and
  **404** for a missing or deleted form, and hands the page only the parsed integer. Loading and saving go through `/ws/rs/eform`, which enforces `_eform` read/write itself.
- Saving a pre-loaded form updates it (`PUT /ws/rs/eform/<fid>/json`); it never creates a copy.
- The editor attaches itself to a stored form only after that form converted onto the canvas. A
  refused conversion ("unable to convert … lacks any recognisable background images / page
  structure") changes nothing on the page, so **Update Eform** cannot overwrite the stored form with
  an unrelated canvas. This also fixes the in-page **Load Existing E-form** button, which used to
  attach the id even when the conversion was refused. A conversion that fails part-way detaches the
  editor instead of leaving it attached to the previous form; a load that answers after a newer one
  is ignored; and saving is refused while a stored form is still loading, since it would create a copy.
- An `#inputForm` without the editor's `.page_container` pages is refused like any other form the
  editor cannot convert.
- Once attached, a banner names the form (`#<fid>`) and links to its HTML source. A converted form
  (one the editor did not write) also gets a warning dialog and a banner note that saving replaces its
  scripts.

## Inside the Administration panel

The HTML editor is injected into the panel as an AJAX fragment. The visual editor cannot be: it is a
full page with its own jQuery UI, globals and keyboard handlers. Inside the panel its links are
therefore framed in the panel's `#myFrame`, through the shell's own loader `carlosOpenInAdminFrame()`
(`administration/leftNav.jspf`, also used by the `.xlink` sections). The click handler lives in
`eform/efmFooter.jspf`. Opened standalone, the same link navigates the same tab, as the HTML editor
does.

## Not covered here

- Developer-locked forms (Rourke, Ontario lab requisition, Rich Text Letter), discussed on #3166, are
  a separate change. Today they fall in the `HTML` kind because they have no visual-editor structure.
- Two editors saving the same form at once: the last save wins, as it always has for both editors
  (`PUT /ws/rs/eform/<fid>/json` has no version check).

## Tests

- `EFormEditorResolverUnitTest`, `ViewVisualEFormEditor2ActionTest`, `EFormUtilIntegrationTest`,
  `VisualEFormEditorDefaultEditRegressionTest`
- `scripts/eform-visual-editor-routing.test.js` (`node --test`)
- `scripts/eform-visual-editor-default-edit-playwright-checks.js`
  (`npm run test:eform-visual-editor-default-edit-playwright`)
