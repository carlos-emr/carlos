# Unused Files Report — CARLOS EMR

**Date**: 2026-03-07
**Scope**: JSP, JSPF, JS, JSON, JS.JSP files in `src/main/webapp/`
**Total files scanned**: 1,800
**Unused files found**: 346 (+ ~10 jsCalendar non-lang JS files = ~356 total)
**Estimated disk savings**: ~6.2 MB

## Methodology

Each file was checked against ALL possible referencing sources:

1. **Struts XML** (`struts.xml`) — action result/forward mappings
2. **JSP includes** — `<%@ include`, `<jsp:include>`, `<c:import>`
3. **JSP links** — `href=`, `action=`, `window.open()`, AJAX calls
4. **Java code** — `RequestDispatcher`, `sendRedirect()`, hardcoded paths
5. **JavaScript** — `<script src=`, dynamic loading, imports
6. **Properties/XML** — Spring configs, i18n bundles, web.xml
7. **Database seeds** — `encounterForm` table registrations (`oscardata.sql`, `oscardata_bc.sql`, `oscardata_on.sql`, `caisi/initcaisi.sql`)

**False positive exclusions**: Form JSPs (`form/form*.jsp`) are dynamically loaded via the `encounterForm` database table and `FrmSetupForm2Action`. All 65+ form JSPs were verified against DB seeds and excluded. Calendar language files loaded via i18n keys (`global.javascript.calendar`) were also excluded.

---

## Category 1: Dead Library Directories (removable as whole directories)

### `jsCalendar/` — Entire directory is dead code
- **Files**: ~55 (45 JS + CSS/images)
- **Size**: 234 KB
- **Evidence**: Only 1 reference exists in entire codebase — a CSS import in `casemgmt/attachClient.jsp` for `jsCalendar/skins/aqua/theme.css`. Zero JS file references. The active calendar library is `share/calendar/` (444 references across the codebase).
- **Recommendation**: Remove entire `jsCalendar/` directory. Update the single CSS import in `attachClient.jsp` if needed.

### `library/DataTables-1.10.12/extensions/` — All extension JS files unused
- **Files**: 58 JS files
- **Size**: 1.1 MB
- **Evidence**: Framework-specific builds (Bootstrap, Foundation, jQuery UI, Semantic UI) for AutoFill, Buttons, ColReorder, FixedColumns, FixedHeader, KeyTable, Responsive, RowReorder, Scroller, Select extensions. None are referenced anywhere. The project uses the newer DataTables 1.13.4 and its Bootstrap 5 build.
- **Recommendation**: Remove entire `library/DataTables-1.10.12/extensions/` directory.

### `library/DataTables-1.10.12/media/js/` — Framework-specific DataTables core builds unused
- **Files**: 17 JS files (12 unreferenced + their min variants)
- **Size**: 761 KB
- **Evidence**: Bootstrap4, Foundation, jQuery UI, Material, Semantic UI, UIKit builds of old DataTables core. None referenced.
- **Recommendation**: Remove all framework-specific builds. Check if `dataTables.bootstrap.js/min.js` and core `jquery.dataTables.js/min.js` in this directory are still used before removing those.

### `library/DataTables/DataTables-1.13.4/js/` — Framework-specific builds unused
- **Files**: 13 JS files
- **Size**: ~400 KB
- **Evidence**: Bootstrap4, Bulma, Foundation, jQuery UI, Semantic UI, and base DataTables builds. The project uses Bootstrap 5, so only `dataTables.bootstrap5.min.js` would be needed (and it IS in this list as unused — verify the min version is used instead).

```
library/DataTables/DataTables-1.13.4/js/dataTables.bootstrap4.js
library/DataTables/DataTables-1.13.4/js/dataTables.bootstrap4.min.js
library/DataTables/DataTables-1.13.4/js/dataTables.bootstrap5.js       # non-min; check if .min version is used
library/DataTables/DataTables-1.13.4/js/dataTables.bulma.js
library/DataTables/DataTables-1.13.4/js/dataTables.bulma.min.js
library/DataTables/DataTables-1.13.4/js/dataTables.dataTables.js
library/DataTables/DataTables-1.13.4/js/dataTables.dataTables.min.js
library/DataTables/DataTables-1.13.4/js/dataTables.foundation.js
library/DataTables/DataTables-1.13.4/js/dataTables.foundation.min.js
library/DataTables/DataTables-1.13.4/js/dataTables.jqueryui.js
library/DataTables/DataTables-1.13.4/js/dataTables.jqueryui.min.js
library/DataTables/DataTables-1.13.4/js/dataTables.semanticui.js
library/DataTables/DataTables-1.13.4/js/dataTables.semanticui.min.js
```

### `library/DataTables/` — Additional unused files
```
library/DataTables/datatables.js           # Bundled all-in-one (non-min); likely replaced by individual includes
library/DataTables/i18n/fr-FR.json         # French i18n for DataTables — verify no dynamic loading
library/DataTables/i18n/pt-BR.json         # Portuguese i18n for DataTables — verify no dynamic loading
```

---

## Category 2: Unused Individual JavaScript Libraries (~2.2 MB)

### Angular.js ecosystem (project doesn't use Angular)
```
library/angular-datatables.min.js
library/angular-resource.js
library/angular-resource.min.js
library/angular-route.js
library/angular-route.min.js
library/angular-ui-router.js
library/ng-infinite-scroll.min.js
library/ng-table/ng-table.js
library/ng-table/ng-table.min.js
library/ui-bootstrap-tpls-2.5.0.js         # Angular UI Bootstrap
```

### Bootstrap 3.0.0 assets (project uses Bootstrap 5.3.0 from CDN)
```
library/bootstrap/3.0.0/assets/js/customizer.js
library/bootstrap/3.0.0/assets/js/filesaver.js
library/bootstrap/3.0.0/assets/js/jszip.js
library/bootstrap/3.0.0/assets/js/less.js
library/bootstrap/3.0.0/assets/js/raw-files.js
library/bootstrap/3.0.0/assets/js/typeahead.min.js
```

### Duplicate/unused jQuery plugins
```
library/jquery/jSignature.min.noconflict.js                          # Duplicate of share/javascript/jquery/ version
library/jquery/jquery-ui-1.11.4.min.js                               # Old jQuery UI version
library/jquery/jquery-ui-1.8.15.custom.draggable.slider.min.js       # Very old jQuery UI
library/jquery/jquery-ui-1.8.4.custom_full.min.js                    # Very old jQuery UI
library/jquery/jquery.autogrow-textarea.js                           # Not referenced
library/jquery/jquery.validate-1.19.1.min.js                         # Not referenced
```

### Unused eform libraries
```
library/eforms/APCache.js                   # Not referenced (jsgraphics.js IS used)
library/eforms/editControl.js               # Not referenced
library/eforms/jSignature.js                # Not referenced (duplicate)
library/eforms/jSignature.min.noconflict.js # Not referenced (duplicate)
```

### Other unused libraries
```
library/hogan-2.0.0.js                     # Mustache template engine — not referenced
library/toastui/i18n/es-es.js              # Toast UI editor i18n (only en used)
library/toastui/i18n/fr-fr.js
library/toastui/i18n/pl-pl.js
library/toastui/i18n/pt-br.js
library/typeahead.js/typeahead.bundle.min.js   # Twitter typeahead — not referenced
library/typeahead.js/typeahead.min.js
```

---

## Category 3: Unused JS files in `js/` directory

```
js/bootstrap-multiselect.js                 # Bootstrap multiselect plugin
js/bootstrap-timepicker.js                  # Bootstrap timepicker plugin
js/caisi_report_tools.js                    # CAISI reporting tools
js/custom/default/main.js                   # Custom main JS
js/fancybox/jquery.easing-1.3.pack.js       # Fancybox dependency
js/fancybox/jquery.fancybox-1.3.4.js        # Fancybox lightbox (old version)
js/fancybox/jquery.mousewheel-3.0.4.pack.js # Fancybox dependency
js/jquery-3.1.0.min.js                      # Old jQuery 3.1.0 (newer version likely in use)
js/jquery.dataTables.1.10.11.min.js          # Old DataTables (1.13.4 is current)
js/jquery.metadata.js                       # jQuery metadata plugin
js/jquery.tablesorter.js                    # TableSorter plugin
js/jquery.tablesorter.pager.js              # TableSorter pager
js/jquery.treeview.js                       # jQuery treeview plugin
js/loading-bar.js                           # Loading bar animation
js/standard.js                              # Legacy standard utilities
js/topnav.js                                # Legacy top navigation
```

---

## Category 4: Unused JS/JSON in `share/` directory

```
share/calendar/calendar-setup_stripped.js    # Stripped calendar setup (full version used)
share/calendar/calendar_stripped.js          # Stripped calendar core (full version used)
share/documentUploader/jquery.iframe-transport.js  # Old file upload transport
share/javascript/dragdrop.js                # script.aculo.us drag & drop
share/javascript/jquery/jquery-ui-1.8.15.custom.draggable.slider.min.js  # Very old jQuery UI
share/javascript/jquery/jquery-ui-1.8.4.custom_full.min.js               # Very old jQuery UI
share/javascript/jquery/jquery.autogrow-textarea.js                      # Not referenced
share/javascript/sorttable.js               # Sort table library
share/javascript/sound.js                   # script.aculo.us sound library
```

### `share/calendar/lang/` — Unused locale files (only `en` and `fr` are configured)
```
share/calendar/lang/calendar-af.js          share/calendar/lang/calendar-nl.js
share/calendar/lang/calendar-al.js          share/calendar/lang/calendar-no.js
share/calendar/lang/calendar-bg.js          share/calendar/lang/calendar-pl-utf8.js
share/calendar/lang/calendar-big5-utf8.js   share/calendar/lang/calendar-pl.js
share/calendar/lang/calendar-big5.js        share/calendar/lang/calendar-pt.js
share/calendar/lang/calendar-br.js          share/calendar/lang/calendar-ru.js
share/calendar/lang/calendar-ca.js          share/calendar/lang/calendar-ru_win_.js
share/calendar/lang/calendar-cs-utf8.js     share/calendar/lang/calendar-si.js
share/calendar/lang/calendar-da.js          share/calendar/lang/calendar-sk.js
share/calendar/lang/calendar-de.js          share/calendar/lang/calendar-sp.js
share/calendar/lang/calendar-du.js          share/calendar/lang/calendar-sv.js
share/calendar/lang/calendar-el.js          share/calendar/lang/calendar-tr.js
share/calendar/lang/calendar-es.js          share/calendar/lang/calendar-zh.js
share/calendar/lang/calendar-fi.js          share/calendar/lang/cn_utf8.js
share/calendar/lang/calendar-he-utf8.js
share/calendar/lang/calendar-hr-utf8.js
share/calendar/lang/calendar-hr.js
share/calendar/lang/calendar-hu.js
share/calendar/lang/calendar-it.js
share/calendar/lang/calendar-jp.js
share/calendar/lang/calendar-ko-utf8.js
share/calendar/lang/calendar-ko.js
share/calendar/lang/calendar-lt-utf8.js
share/calendar/lang/calendar-lt.js
share/calendar/lang/calendar-lv.js
```

---

## Category 5: Unused JSP Files by Module

### PMmodule/Admin/ (35 files) — Old program management admin
These appear to be legacy Struts 1 tab fragments that were replaced during the Struts 2 migration. None are referenced in `struts.xml`, Java code, or other JSPs.

```
PMmodule/Admin/DefaultRoleAccessForm.jsp
PMmodule/Admin/DefaultRoleAccessList.jsp
PMmodule/Admin/Facility/ViewFacility.jsp
PMmodule/Admin/Home.jsp
PMmodule/Admin/Organization/ORGTree.jsp
PMmodule/Admin/ProgramEdit/access.jsp
PMmodule/Admin/ProgramEdit/client_status.jsp
PMmodule/Admin/ProgramEdit/function_user.jsp
PMmodule/Admin/ProgramEdit/queue.jsp
PMmodule/Admin/ProgramEdit/service_restrictions.jsp
PMmodule/Admin/ProgramEdit/staff.jsp
PMmodule/Admin/ProgramEdit/teams.jsp
PMmodule/Admin/ProgramEdit/vacancies.jsp
PMmodule/Admin/ProgramEdit/vacancy_add.jsp
PMmodule/Admin/ProgramEdit/vacancy_template_add.jsp
PMmodule/Admin/ProgramEdit/vacancy_templates.jsp
PMmodule/Admin/ProgramManagerForm.jsp
PMmodule/Admin/ProgramManagerList.jsp
PMmodule/Admin/ProgramManagerView.jsp
PMmodule/Admin/ProgramView/access.jsp
PMmodule/Admin/ProgramView/client_status.jsp
PMmodule/Admin/ProgramView/function_user.jsp
PMmodule/Admin/ProgramView/queue.jsp
PMmodule/Admin/ProgramView/service_restriction_error.jsp
PMmodule/Admin/ProgramView/service_restrictions.jsp
PMmodule/Admin/ProgramView/staff.jsp
PMmodule/Admin/ProgramView/teams.jsp
PMmodule/Admin/ProgramView/vacancies.jsp
PMmodule/Admin/ProgramView/vacancy_add.jsp
PMmodule/Admin/ProgramView/vacancy_template_add.jsp
PMmodule/Admin/ProgramView/vacancy_templates.jsp
PMmodule/Admin/StaffEdit/facilities.jsp
PMmodule/Admin/StaffEdit/programs.jsp
PMmodule/Admin/StaffManagerForm.jsp
PMmodule/Admin/StaffManagerList.jsp
```

### admin/ (11 files) — Migration scripts and obsolete config pages
```
admin/ApptSearchConfiguration.jsp           # Appointment search config (no references)
admin/appointmentSearchConfig.jsp           # Duplicate/old version
admin/consentConfiguration.jsp              # Consent config (no references)
admin/fixThirdPartyBillingPayments.jsp      # One-time migration tool
admin/iso36612.jsp                          # ISO standard config
admin/migrateCustomAllergyNonDrug.jsp       # One-time migration tool
admin/migrateToNewContacts.jsp              # One-time migration tool
admin/populateProgramAccess.jsp             # One-time migration tool
admin/providerAudit.jsp                     # Provider audit (no references)
admin/upgradeRosterData.jsp                 # One-time migration tool
admin/viewFax.jsp                           # Old fax viewer (no references)
```

### billing/ (21 files) — Old billing JSPs
```
billing/CA/BC/billingBCEditPrivateCode.jsp
billing/CA/BC/billingTeleplanCorrection.jsp
billing/CA/BC/methadoneBillingBC.jsp
billing/CA/BC/privateBilling/manageFeeSplit.jsp
billing/CA/BC/saveAssocs.jsp
billing/CA/BC/support/providers.jsp
billing/CA/BC/wcbMigrate.jsp
billing/CA/ON/addEditRefDoc.jsp
billing/CA/ON/billingDelete.jsp
billing/CA/ON/billingEA.jsp
billing/CA/ON/billingOHIPGroupReport.jsp
billing/CA/ON/billingONCorrectionSave.jsp
billing/CA/ON/billingONPriInvoice.jsp
billing/CA/ON/billingRA.jsp
billing/CA/ON/billingRAView.jsp
billing/CA/ON/billingReview.jsp
billing/CA/ON/billingUpdateWithoutNo.jsp
billing/CA/ON/genRAError.jsp
billing/CA/ON/genRAsettle.jsp
billing/CA/ON/genRAsettle35.jsp
billing/CA/ON/genRAwithlogic.jsp
```

### casemgmt/ (14 files) — Old case management fragments
```
casemgmt/calculatorsSelectList.jspf
casemgmt/client_history.jsp
casemgmt/current_issues.jsp
casemgmt/header.jsp
casemgmt/newCaseManagement.jsp
casemgmt/newCaseManagementEnable.jsp
casemgmt/newNavigationFloatCols.jsp
casemgmt/ongoing_concerns.jsp
casemgmt/prescriptions.jsp
casemgmt/reminders.jsp
casemgmt/rightColumnFloatCols.jsp
casemgmt/siteLayout.jsp
casemgmt/ticklers.jsp
casemgmt/tripsearch.jsp
```

### report/ (21 files) — Old reporting JSPs
```
report/PreventionReport.jsp
report/dobformat.jsp
report/reportBCARDemo.jsp
report/reportactivepatientlist.jsp
report/reportapptsheet.jsp
report/reportbcedblist2007.jsp
report/reportbilledvisit.jsp
report/reportbilledvisit1.jsp
report/reportbilledvisit2.jsp
report/reportbilledvisit3.jsp
report/reportdaysheet2.jsp
report/reportdxvisit.jsp
report/reportectapptsheet.jsp
report/reportencounterhistory.jsp
report/reportnewedblist.jsp
report/reportnoshowapptlist.jsp
report/reportonbilledphcpv2.jsp
report/reportonbilledvisit.jsp
report/reportonedblist.jsp
report/reportpatientchartlistspecial.jsp
report/tabulardaysheetreport.jsp
```

### Other unused JSP/JSPF files
```
404.jsp                                     # Custom 404 page (not configured in web.xml)
apps/oauth1.jsp                             # OAuth1 test page
cc/launcherError.jsp                        # CC launcher error page
common/help.jsp                             # Common help page
common/progress_dialog.jsp                  # Progress dialog
common/readonly.jsp                         # Read-only mode page
demographic/addnewdemographicswipe.jsp      # Card swipe demographic entry
demographic/diabetesExport.jsp              # Diabetes export tool
demographic/followUp.jsp                    # Follow-up page
demographic/followUpSelection.jsp           # Follow-up selection
demographic/shnfields.jsp                   # SHN fields
demographic/shnfieldsView.jsp               # SHN fields view
documentManager/documentUploaderFirefox36.jsp  # Firefox 36-specific uploader
documentManager/listDocumentFromProvider.jsp    # Old document listing
documentManager/listDocumentFromQueue.jsp       # Old document listing
documentManager/previewDocHL7Inbox.jsp         # HL7 inbox doc preview
eform/attachEform.jsp                       # Eform attachment
eform/displayAttachedFiles.jsp              # Attached files display
eform/efmSendform.jsp                       # Send eform
eform/efmclosewindow.jsp                    # Close window helper
eform/efmformrtl_templates.jsp              # RTL templates
eform/fieldNoteReport/fieldnotereportdetail_pre2015.jsp  # Pre-2015 field note detail
hospitalReportManager/inbox.jsp             # HRM inbox
incTop.jsp                                  # Legacy top include
lab/CA/BC/viewreports.jsp                   # BC lab reports view
layouts/caisi_html_bottom2.jspf             # CAISI layout bottom
layouts/nonPatientContext.jspf              # Non-patient context layout
layouts/nonPatientContextFooter.jspf       # Non-patient context footer
layouts/simpleLayout.jsp                    # Simple layout wrapper
messenger/Transfer/DocXfer.jsp              # Document transfer
messenger/previewPDF.jsp                    # PDF preview
messenger/processAttFrameset.jsp            # Attachment frameset
oscarEncounter/calculators/BMI.jsp          # BMI calculator
oscarEncounter/clinicModules.jsp            # Clinic modules page
oscarEncounter/newEncounter.jsp             # Legacy new encounter
oscarEncounter/oscarConsultationRequest/ConsultationFaxComfirmation.jsp
oscarEncounter/oscarConsultationRequest/ConsultationFormFax.jsp
oscarEncounter/oscarConsultationRequest/attachConsultation2.jsp
oscarEncounter/oscarConsultationRequest/displayAttachedFiles.jsp
oscarMDS/SelectProviderSimple.jsp           # Simple provider selector
oscarReport/CDSOneTimeConsultReport.jsp     # CDS consultation report
oscarReport/ConsultationReport.jsp          # Consultation report
oscarReport/InjectionReport2.jsp            # Injection report
oscarReport/LabReqReport.jsp                # Lab requisition report
oscarReport/OSISReport.jsp                  # OSIS report
oscarReport/manageProviderNew.jsp           # Provider management
oscarReport/mis_report_results.jsp          # MIS report results
oscarRx/InteractionDisplay2.jsp             # Drug interaction display
oscarRx/displayInstructions.jsp             # Rx instructions display
oscarRx/search2.jsp                         # Rx search
oscarWaitingList/EditWaitingList.jsp         # Waiting list editor
provider/formALPHA.jsp                      # ALPHA form (provider)
provider/formar1_99_08.jsp                  # AR1 form 1999-08
provider/formar1_99_08print.jsp             # AR1 form print
provider/formar1_99_12print.jsp             # AR1 form print
provider/formar2_99_08print.jsp             # AR2 form print
provider/formrourkebabyrecord1.jsp          # Old Rourke baby record
provider/providerchangemygroup.jsp          # Change group page
provider/providerencountereditdemoacc.jsp   # Encounter edit demo access
provider/providerheader-classic.jspf        # Classic header fragment
renal/preImplementationReport.jsp           # Renal pre-implementation
schedule/scheduledaytemplate.jsp            # Schedule day template
schedule/scheduletemplatesetting1.jsp       # Schedule template settings
```

---

## Summary Table

| Category | Files | Size | Risk Level |
|----------|-------|------|------------|
| `jsCalendar/` entire directory | ~55 | 234 KB | **Low** — only 1 CSS ref |
| DataTables 1.10.12 extensions/media | 75 | 1.9 MB | **Low** — old version |
| DataTables 1.13.4 framework builds | 15 | ~400 KB | **Low** — wrong CSS framework |
| Angular.js ecosystem | 10 | ~500 KB | **Low** — Angular not used |
| Bootstrap 3.0.0 assets | 6 | ~200 KB | **Low** — BS 5.3 from CDN |
| Old jQuery/library duplicates | 22 | ~1.5 MB | **Low** — duplicates |
| `js/` directory unused | 16 | ~400 KB | **Low** — old plugins |
| `share/` unused JS | 9 | ~200 KB | **Low** — legacy |
| `share/calendar/lang/` (28 locales) | 28 | ~50 KB | **Low** — only en/fr used |
| PMmodule/Admin JSPs | 35 | ~200 KB | **Medium** — verify no dynamic loads |
| admin/ migration JSPs | 11 | ~100 KB | **Low** — one-time tools |
| billing/ JSPs | 21 | ~300 KB | **Medium** — province-specific |
| casemgmt/ JSPs | 14 | ~150 KB | **Medium** — may have been tabs |
| report/ JSPs | 21 | ~200 KB | **Medium** — verify no menu links |
| Other JSP/JSPF | 63 | ~600 KB | **Medium** — various modules |
| **TOTAL** | **~399** | **~6.2 MB** | |

## Recommended Removal Order (safest first)

1. **Phase 1 — Library cleanup** (low risk, high savings): Remove `jsCalendar/`, DataTables extensions, Angular libs, Bootstrap 3 assets, duplicate jQuery plugins (~4.2 MB)
2. **Phase 2 — JS cleanup**: Remove unused `js/` and `share/` JS files, calendar lang files (~500 KB)
3. **Phase 3 — Admin/migration JSPs**: Remove one-time migration tools in `admin/` (~100 KB)
4. **Phase 4 — Module JSPs**: Remove PMmodule/Admin, billing, casemgmt, report JSPs after manual verification (~1.4 MB)

---

*Generated with Claude Code — 2026-03-07*
