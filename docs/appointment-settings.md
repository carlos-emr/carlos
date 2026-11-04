# Appointment Settings: statuses and the Location List

Issue #3982, ported from Open-O PR #2518 and adapted to CARLOS. Administration > Schedule
Management opens two tabs that share one navigation bar (`appointmentSettingsNav.jspf`):

| Tab | Route | Action | View |
|---|---|---|---|
| Statuses | `appointment/apptStatusSetting` | `appt.status.web.AppointmentStatus2Action` | `WEB-INF/jsp/appointment/appointmentstatussetting.jsp` |
| Locations | `appointment/apptLocationSetting` | `appt.web.AppointmentLocationSettings2Action` | `WEB-INF/jsp/appointment/appointmentLocationSetting.jsp` |

The Statuses tab (and its menu link) shows only while status editing is enabled
(`CarlosProperties.isAppointmentStatusEditingEnabled()`, on unless `ENABLE_EDIT_APPT_STATUS` is
set to a non-active value), because otherwise the schedule ignores the statuses it edits.

## Statuses: inline styling

- Description, colour and icon are edited inline, each from its own small POST form
  (`dispatch=updateDescription|updateColour|updateIcon`, `id`, `apptDesc|apptColor|apptIcon`).
  Saving one field never rewrites another, so a legacy non-hexadecimal colour (#3381) is only
  replaced by an explicit colour save; the page says so next to such a value. The separate edit
  page (`appointmentstatusedit.jsp`, `dispatch=modify|update`) is retired; `dispatch=modify`
  renders the list so bookmarked links still open.
- **`editable` gates only enable/disable** (#3888). Every status, including the core ones seeded
  with `editable=0` (t, T, N, C, B), can be renamed, recoloured and given another icon; only
  `changestatus` refuses an `editable=0` status. This deliberately differs from the fork, whose
  manager refuses style edits to `editable=0` statuses.
- Validation lives in `AppointmentStatusMgrImpl` and is reported through
  `AppointmentStatusStyleException` (an `oscarResources` key): description 1–30 characters
  (`appointment_status.description` width), colour exactly `#rrggbb`, icon one of
  `AppointmentStatusMgr.ICON_SET`. The icon set lists only the images that also have the `S`/`V`
  copies the day sheet uses for signed and verified appointments (`Sthumb.png`/`Vthumb.png` were
  added for Confirmed's `thumb.png`); `AppointmentStatusMgrImplUnitTest` checks the files exist.
- Disabling a status, and Reset, ask for confirmation first; cancelling sends no request.
- Reset restores description, colour **and icon** of every seeded status by its exact status code,
  not row id (a custom `tx` never stands in for `t`), in one transaction.
- Privileges: read to view and write for every change, on `_admin.schedule` or `_admin` (the same
  rule as the Locations tab). Every change must be posted (GET/HEAD answer 405 first). Each saved
  change writes a `LogAction` row naming the status id and the field, never patient data. The
  route is not in `HttpMethodGuardFilter.MUTATION_INTENT_PARAMS_BY_ROUTE`, because its menu link
  and old bookmarks open it with `dispatch=view|modify`; the action refuses GET by value.

## Locations: the appointment Location List

Clinics without a site setup could only type a free-text location. The Location List lets an
administrator keep the bookable places (rooms, Virtual, a satellite office), each with a colour and
an icon.

### Storage (Flyway `common/V1.0.81`)

- A Look-Up List named `appointmentLocationCode` (`AppointmentLocationResolver.LIST_NAME`), seeded
  empty. Items gain `icon` (a whitelisted Font Awesome 6 class, `AppointmentLocationStyle.ICONS`,
  checked against `css/fontawesome-all.min.css` by a unit test) and `colour` (`#rrggbb`).
- `appointment.locationCode` and `appointmentArchive.locationCode` hold the chosen item's id.
  `appointment.location` keeps a snapshot of the item's label and stays `varchar(255)` (V1.0.8);
  location names are limited to 255 characters so the snapshot is never cut. The archive copy
  (`AppointmentArchiveDaoImpl.archiveAppointment`) carries the code.

### When the booking screens use it

`AppointmentLocationList.isLocationMode()` is true only when the list has an active item **and** no
site setup is configured (`AppointmentLocationResolver.isSiteSetupConfigured()`): multisite sites,
`scheduleSiteID`, or CAISI program locations (`ModuleNames` containing Caisi with
`useProgramLocation=true`). Site setups keep their own controls. There is no separate switch:
disabling the last active location turns the dropdown off again (the page warns first).

### The one write rule

A code is only ever saved together with its item's label, and is cleared whenever the text
changes for any other reason (`AppointmentLocationResolver`):

The writer first loads the appointment as saved (a new booking has none), so each rule below is an
edit of what is stored:

| Posted by the booking form | Saved |
|---|---|
| no `locationCode` (free text, site or program control) | the text; the code is kept only while the text is unchanged (`setLocationText`) |
| `locationCode=legacy` | the legacy text the edit screen offered; code cleared |
| the appointment's own code | nothing changes: the saved label stays even if the item was renamed or disabled since |
| another active item, in location mode | the item id and its label |
| blank ("Not specified") | location mode: no location, no code. Otherwise the text, through `setLocationText` (the patient-search pages pass a blank code through) |
| a disabled item, an id of another list, or any item outside location mode | refused: the saved location stays as it was |

Only a hand-made request, or a form opened before an item was disabled, can post the last row, so
a disabled location is refused on the server as well as hidden from the dropdown. The Repeat page
seeds its template from the saved appointment the same way, and treats typed text linked to the item
with that name (see below) as unchanged, so "Save changes first" is not asked for a form nobody
changed. Group Update (`appointmentgrouprecords.jsp`) recreates each appointment, so it copies the
location of the one it replaces before applying the form.

Every booking-form writer goes through `AppointmentLocationResolver.applyPostedLocation`:
`AppointmentAddRecord2Action`, `AppointmentUpdateRecord2Action`, `RecurringAppointmentService`,
and the legacy record JSPs (`appointmentgrouprecords.jsp`, `appointmentaddrecordcard.jsp`,
`appointmentaddrecordprint.jsp`). Integrations only send text and go through `setLocationText`, so
a code never outlives its label: SOAP (`AppointmentTransfer.copyTo`, where the code is read-only,
and `BookingWs`), REST (`AppointmentConverter.applyEditableProperties`, `NewAppointmentConverter`)
and HL7 (`PhsStarHandler`). A multisite rename (`SiteDaoImpl`) clears the code of the appointments
whose text it rewrites. Renaming a Location List item does **not** rewrite past appointments.

### Booking screens

`WEB-INF/tags/appointment/locationSelect.tag` renders `<select name="locationCode">` in
`addappointment.jsp`/`editappointment.jsp` with "Not specified", the active items in booking
order, on edit the appointment's own item even if deactivated since (marked), and a
"Legacy: <text>" option for location text that names no item. An edit screen links typed text to
the active item with the same name (ignoring case and spaces) so it is coded on the next save.
`js/appointmentLocationSelect.js` picks the option an appointment type names and the one a
pasted appointment carries (`ApptData.locationCode`). The choice survives a patient search and the
add-patient detour (`demographicsearch2apptresults.jsp`, `add-form-personal.jsp`,
`demographicaddarecord.jsp`). `AppointmentEditVersion` includes the code.

### Day schedule

`appointmentprovideradminday.jsp` loads the list once per page and draws a chip
(`WEB-INF/tags/appointment/locationChip.tag`) in the item's colour with its icon, naming on hover
and to screen readers the location the appointment was booked with (its saved label, which a later
rename leaves alone). Site setups draw their own marker and no chip. Colours
and icons go through `AppointmentLocationStyle`, which falls back to the defaults for any stored
value that is not `#rrggbb` or whitelisted.

### Settings page

Add one location or paste a list (one per line, at most 100 lines and 100 × 257 characters, read
before it is split; active names are skipped), rename, set colour (or back to default) and icon
(previewed before saving), move up/down the booking order, disable and enable. Names are unique
among active locations, ignoring case and surrounding spaces, and may not contain control or
format characters (line breaks, tabs, bidi overrides, zero-width marks), which would travel into
every booking's text and out through SOAP, REST and HL7. A refused entry is offered again.

- Privileges, checked by both the action and `AppointmentLocationService`: read, write, and for
  disabling delete, on `_admin.schedule` or `_admin`.
- Every change is POST-only. The page opens without a `dispatch`, so any `dispatch` is a change:
  GET/HEAD answer 405 before anything is read or written, and the route is listed in
  `HttpMethodGuardFilter.MUTATION_INTENT_PARAMS_BY_ROUTE` by it.
- Every change runs in one transaction that first locks the list row
  (`LookupListDao.findForUpdate`), so two at once (a double click, two administrators) cannot both
  pass the name check; it evicts the lookup-list cache after commit and writes a `LogAction` row with
  the item id and the field only.
- Repeats: the add and paste forms carry a `FormSubmissionGuard` token
  (`Scope.APPOINTMENT_LOCATION_ADD`, one per rendering), so a double click or a reload shows what the
  first submission added instead of adding again. Move up/down posts the place the page showed
  (`position`); a repeat finds the location gone from there and is refused with "The list changed".
- Locations are never deleted, so a code on a past appointment always names an item. The list is
  also an ordinary Look-Up List in the generic Look-Up List manager, which applies none of these
  rules; use the Locations tab.

## Tests

- Java: `AppointmentStatus2ActionUnitTest`, `AppointmentStatusMgrImplUnitTest`,
  `AppointmentStatus2ActionStrutsBindingIntegrationTest`, `AppointmentLocationListUnitTest`,
  `AppointmentLocationResolverUnitTest`, `AppointmentLocationStyleUnitTest`,
  `AppointmentLocationServiceUnitTest`, `AppointmentLocationSettings2ActionUnitTest`,
  `AppointmentLocationIntegrationTest` (H2 round trip, archive copy, service through real DAOs),
  `AppointmentLocationServiceCacheIntegrationTest` (the service through a Spring proxy: transaction
  and cache eviction), `AppointmentStatusDaoCacheIntegrationTest` (`modifyStyle` eviction),
  `RecurringAppointmentServiceIntegrationTest`, `SiteDaoImplRenameUnitTest`, and both actions'
  dispatches in `MutatorActionGetRejectionContractUnitTest`.
- Node: `scripts/appointment-location-select.test.js`.
- Browser: `scripts/appointment-settings-playwright-checks.js`
  (`npm run test:appointment-settings-playwright`).
