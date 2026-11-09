# Deployed eForm page exclusion

`eform-deployed-page-exclusion` implements #5809 alongside the retained standalone
`eform-page-exclusion` geometry check. It uses the installed application's native
E-Chart → Add eForm, patient eForm list and toolbar PDF controls. No local
`page.pdf()`, extracted production geometry script or fabricated server output is
accepted as the deployed PDF.

Three owned templates exercise a clean control, form-wrapped pages with an
interstitial, and pages directly under the body with an interstitial. Native new
and saved views must retain the requested topology. A visible full-page outline
defines the renderer's content extent without external image fixtures. Each page
has distinct top, right, lower, lower-right and central clinical tokens.

The release deliberately requires informed approval for **interstitial** content,
even when the author marks it as decoration. Only decorations before/after all
pages are advisory. The workflow verifies the native approval's exact saved
revision/patient/token binding and one excluded visible element, with every other
blocking component absent. Approval downloads the already saved revision without
another save. Submit & PDF itself creates one new revision per click; the original
revision must remain unchanged when the saved view is submitted again.

Poppler independently checks exactly two ordered pages, every unique clinical
token and no interstitial/extra content, 540 × 705 point authored extent (under
one point paper quantization), absolute text positions (one point horizontally,
three vertically for font metrics), unclipped bounds and actual raster ink.
Every variant/revision must also match the clean control within 0.3 point. Ten
negative controls reject missing/reversed/duplicated pages/content, extra blank
pages/interstitials, shifts, clipping, wrong scale and invisible extracted text.

Requires `EXCLUSIVE=1`, Poppler, the saved-eForm browser renderer, and a readable
`LAB_UPLOAD_JOURNAL_UNIT` **or** `LAB_UPLOAD_SERVER_LOG`. Enable INFO receipts for
`EFormBrowserPdfService` during the disposable test window (for example
`LOG_VERBOSITY=info`) and restore the original setting afterward. Each downloaded
PDF requires a new completion receipt naming its exact saved revision and both
authored pages. Default WARN logging cannot satisfy that evidence requirement.

`EFORM_RENDER_TEMP_DIR` defaults to the Debian Catalina managed renderer store
`/var/lib/carlos-emr/catalina/work/carlos/eform-browser-pdf-temp`; configure it
explicitly for other Catalina layouts. The directory must exist, be readable and
be a real directory. Cleanup inventories it before writes, recovers new artifacts
by their exact marker-owned PDF title (including a lost download acknowledgment),
and uses verified atomic retirement. Preexisting entries are preserved;
unrecognized/replaced files or directories fail cleanup and remain for recovery.
Separate negative controls exercise those refusal paths. Library/instance/value
rows and browser-download scratch files are also verified removed. No external
image, clinical document attachment or shared demo-patient fixture is created.
