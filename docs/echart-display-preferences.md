# eChart display preferences

Each provider chooses what their own eChart shows, in **Preferences > eChart** (the schedule's
"Edit your personal setting" icon). The settings and the header bar they drive follow OSCAR 19's
eChart Display Settings, so a clinic moving from OSCAR 19 sees the same chart. In OSCAR 19 these
were clinic-wide (Administration > eChart > eChart Display Settings); in CARLOS they are personal.

| Setting | Default | What it changes |
|---|---|---|
| Display eChart Timer | Yes | The timer button in the note panel (click pastes start, end and elapsed time into the note; it turns green at 20 minutes and yellow at 50). |
| File Documents in Folders by Document Type | Yes | The left-column Documents panel lists one collapsed folder per document type ("consult (2)", "lab (1)", ...) instead of a list by date. Documents without a type go in "Other". |
| Display Date of Birth (yyyy-mm-dd) | Yes | The header bar shows the date of birth with the age as its tooltip; No shows the age with the date of birth as its tooltip. |
| Display Cell Phone Number if Available | Yes | The header bar shows the cell number (home number in the tooltip); No, or no cell on file, shows the home number. A number the patient marked preferred (trailing `*`) is always shown instead. |
| Display Health Insurance Number (HIN) | Yes | The HIN, version code and province in the header bar. |
| Display Patient Email Address if Available | Yes | The email address in the header bar; a mailto link when the patient consented to email for care. |
| Display Family Doctor | Yes | A Family Doctor panel at the top of the left column: the patient's "Family Doctor" professional contact (Health Care Team), else the legacy family-doctor field. |
| Display Referring Doctor | No | A Referring Doctor panel at the top of the left column: the master record's Referral Doctor, else a "Referring Doctor" professional contact. |
| Display OCEAN UI | No | The Ocean toolbar placeholder. Ocean must also be enabled for the clinic: `cme_js=ocean` in `carlos.properties`, and Administration > eChart > eChart Display Settings left on. |

## The header bar

One compact line in the viewer's provider colour (Preferences > Set Provider Colour; light blue
when never chosen): MRP (underlined in the MRP's colour), the patient's name (opens the Master
Record), sex, date of birth or age, HIN, phone, email, next appointment (opens the appointment
history), the clinical calculators, configured chart links, and About | License on the right. Date
of birth or age, HIN, phone and email are buttons that copy the value to the clipboard.

## Implementation

- Settings: `io.github.carlos_emr.carlos.encounter.display.EchartDisplayPreference` (names, defaults),
  stored as `property` rows (`provider_no`, `name`, `"true"`/`"false"`); a provider with no row gets
  the default. Saved by `EchartDisplayPreferencePersister` from Save All
  (`ProviderPropertyAction`); read by `EchartDisplayPreferenceResolver`.
- Header bar: `casemgmt/newEncounterHeader.jsp`, built from `EchartHeaderViewModelAssembler`.
- Left-column panels: `encounter/displayFamilyDoctor`, `encounter/displayReferringDoctor`
  (`EctDisplayPatientDoctorAction`); Documents folders: `EctDisplayDocs2Action` +
  `LeftNavBarDisplay.jsp` (`displayFolders`).
- Browser check: `scripts/echart-display-preferences-playwright-checks.js`
  (`npm run test:echart-display-preferences-playwright`).
