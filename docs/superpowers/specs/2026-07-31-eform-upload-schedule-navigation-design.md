# Preserve Schedule Navigation Through eForm Uploads

## Context

PR 3286 preserves `scheduleNav=1` when the Administration Panel home link is used from the focused schedule shell. The Manage eForms workflow still loses that context after a successful HTML upload or ZIP import because both embedded success pages unconditionally navigate the top window to `/administration?show=Forms`.

## Scope

Preserve the focused schedule navigation through both Manage eForms completion paths:

- HTML eForm upload
- ZIP eForm import

Direct access without schedule navigation must retain the existing clean Administration URL. Upload/import processing, authorization, validation, and persistence are unchanged.

## Design

Treat `scheduleNav=1` as explicit navigation context and carry it through every request in the workflow:

1. Both Administration Manage eForms entry links conditionally append `scheduleNav=1` when the Administration request has that exact value.
2. The eForm manager conditionally appends the same value to its HTML Upload and ZIP Import iframe URLs.
3. Each iframe form includes `scheduleNav=1` as a hidden multipart form field only when its request received that exact value.
4. Each successful action renders its existing partial with the submitted parameter available.
5. Each success partial redirects the top window to:
   - `/administration?show=Forms&scheduleNav=1` when the submitted value is exactly `1`.
   - `/administration?show=Forms` otherwise.

No arbitrary return URL or arbitrary query value is accepted or propagated.

## Error Handling

Failed uploads and imports remain in their iframe and retain the hidden navigation context for a retry. Existing validation and error messages remain unchanged. Missing or invalid `scheduleNav` values fall back to the existing clean Administration URL.

## Automated Verification

Add focused regression assertions covering every propagation boundary:

- Administration quick link and left-nav link conditionally preserve `scheduleNav=1`.
- The eForm manager conditionally passes `scheduleNav=1` to both iframe URLs.
- Both multipart forms conditionally submit the hidden navigation parameter.
- Both success partials conditionally preserve the parameter in the top-window redirect and retain the clean direct-access destination.

Run the focused Java regression tests and the relevant build checks.

## Browser Verification

Use Playwright against the locally running PR build to:

1. Log in and open Administration from the focused schedule shell.
2. Open Manage eForms.
3. Upload a deterministic temporary HTML eForm.
4. Confirm the resulting top-level URL contains both `show=Forms` and `scheduleNav=1`.
5. Confirm the schedule navigation remains visible.
6. Capture screenshots before upload and after the successful redirect.
7. Exercise the ZIP Import propagation path when a deterministic fixture is available; otherwise verify its equivalent request and redirect boundaries through the focused regression test.

Temporary browser fixtures and screenshots live outside the repository. The uploaded test eForm may remain in the disposable local development database unless the existing test workflow provides safe cleanup.
