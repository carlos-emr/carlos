/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * Modifications by CARLOS Contributors, 2026.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 */

/**
 * Case Management Print Service for CARLOS EMR.
 *
 * This service provides comprehensive PDF printing capabilities for patient medical records,
 * including encounter notes, clinical prevention profiles (CPP), prescriptions, laboratory results,
 * preventions, and allergies. It supports date range
 * filtering, and concatenation of multiple PDF documents into a single output.
 *
 * The service is used by both classic E-Chart and flat E-Chart interfaces to generate
 * printable medical documentation for healthcare providers. All printed documents include
 * patient demographics, provider information, and timestamps for audit trail purposes.
 *
 * Key Features:
 * <ul>
 *   <li>Multi-section printing: notes, CPP, Rx, labs, preventions, allergies</li>
 *   <li>Date range filtering for historical data retrieval</li>
 *   <li>HL7 laboratory report integration</li>
 *   <li>Configurable note sorting (ascending/descending by observation date)</li>
 *   <li>Extension point system for custom print sections</li>
 * </ul>
 *
 * Security Considerations:
 * This service handles Protected Health Information (PHI) and must be called within
 * authenticated sessions with appropriate provider privileges. All patient data access
 * is logged for audit compliance.
 *
 * @see CaseManagementPrintPdf
 * @see CaseManagementManager
 * @since 2026-01-24
 */
package io.github.carlos_emr.carlos.casemgmt.service;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext;
import io.github.carlos_emr.carlos.encounter.data.EctProviderData;
import org.openpdf.text.DocumentException;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteExt;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.casemgmt.util.ExtPrint;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.commn.dao.AllergyDao;
import io.github.carlos_emr.carlos.managers.PreventionManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.lab.ca.all.pageUtil.LabPDFCreator;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import io.github.carlos_emr.carlos.util.ConcatPDF;
import io.github.carlos_emr.carlos.util.ConversionUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.File;
import java.nio.file.Files;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class CaseManagementPrint {

    /** The selected notes or supplied chart context fail the print read boundary. */
    public static final class NoteAccessDeniedException extends SecurityException {
        private static final long serialVersionUID = 1L;

        public NoteAccessDeniedException() {
            super("Selected note access denied");
        }

        public NoteAccessDeniedException(SecurityException cause) {
            super("Chart print context access denied", cause);
        }
    }

    private static Logger logger = MiscUtils.getLogger();


    CaseManagementManager caseManagementMgr = SpringUtils.getBean(CaseManagementManager.class);

    private PreventionManager preventionManager = SpringUtils.getBean(PreventionManager.class);

    private AllergyDao allergyDao = SpringUtils.getBean(AllergyDao.class);

    /**
     * Generates a comprehensive PDF printout of patient medical records.
     *
     * This is the primary entry point for printing patient encounter information. It coordinates
     * the retrieval and formatting of multiple data types (notes, CPP, prescriptions, labs,
     * preventions, allergies) into a single concatenated PDF document. The method supports both
     * selective note printing and date-range filtered printing.
     *
     * The method performs the following operations:
     * <ol>
     *   <li>Resolves note IDs based on print mode (all notes, date range, or specific selection)</li>
     *   <li>Retrieves patient demographic information for header</li>
     *   <li>Fetches local notes</li>
     *   <li>Sorts notes according to system configuration (ascending/descending)</li>
     *   <li>Filters notes by date range if specified</li>
     *   <li>Generates CPP sections if requested (OMeds, SocHistory, MedHistory, etc.)</li>
     *   <li>Retrieves prescription, prevention, and allergy data as requested</li>
     *   <li>Creates individual PDF components for notes, labs, and other sections</li>
     *   <li>Concatenates all PDF components into final output stream</li>
     *   <li>Cleans up temporary files</li>
     * </ol>
     *
     * Security Note: This method accesses Protected Health Information (PHI). Ensure the
     * LoggedInInfo contains valid provider credentials with appropriate access privileges.
     *
     * This method was originally in CaseManagementEntry2Action but has been moved to this
     * service class to enable code reuse between classic E-Chart and flat E-Chart interfaces.
     *
     * @param loggedInInfo LoggedInInfo the authenticated session information containing provider and facility context
     * @param demographicNo Integer the patient's unique demographic identifier
     * @param printAllNotes boolean true to print all available notes, false to use noteIds array
     * @param noteIds String[] array of note IDs to print (ignored if printAllNotes is true)
     * @param printCPP boolean true to include Clinical Prevention Profile sections (OMeds, SocHistory, etc.)
     * @param printRx boolean true to include prescription/medication information
     * @param printLabs boolean true to include laboratory results (HL7 reports)
     * @param printPreventions boolean true to include prevention/immunization records
     * @param printAllergies boolean true to include patient allergy information
     * @param useDateRange boolean true to filter notes and labs by date range (requires startDate and endDate)
     * @param startDate Calendar the start date for date range filtering (inclusive); may be null if useDateRange is false
     * @param endDate Calendar the end date for date range filtering (inclusive); may be null if useDateRange is false
     * @param request HttpServletRequest the servlet request containing session data and parameters
     * @param os OutputStream the output stream to write the final concatenated PDF document
     *
     * @throws IOException if file I/O operations fail during PDF generation or cleanup
     * @throws DocumentException if PDF document creation or manipulation fails
     * @throws NoteAccessDeniedException if the chart context is invalid or a selected note is not readable
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = {"IMPROPER_UNICODE", "PATH_TRAVERSAL_IN"}, justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision; path validated for directory containment via PathValidationUtils before use")
    public void doPrint(LoggedInInfo loggedInInfo, Integer demographicNo, boolean printAllNotes, String[] noteIds, boolean printCPP, boolean printRx, boolean printLabs, boolean printPreventions, boolean printAllergies, boolean useDateRange, Calendar startDate, Calendar endDate, HttpServletRequest request, OutputStream os) throws IOException, DocumentException {

        // Extensions read servlet parameters rather than this method's patient argument. Bind every
        // parameter view to the authorized patient so a query string cannot select a second chart.
        request = patientScopedRequest(request, demographicNo);
        String providerNo = loggedInInfo.getLoggedInProviderNo();
        String program;
        try {
            program = ChartProgramContext.resolve(request);
        } catch (SecurityException invalidContext) {
            throw new NoteAccessDeniedException(invalidContext);
        }

        ChartPrintDateRange printRange = useDateRange ? ChartPrintDateRange.from(startDate, endDate) : null;

        // Get all or date range noteIds.
        if (printAllNotes && useDateRange) {
            List<CaseManagementNote> dateRangeNotes = caseManagementMgr.getNotesInDateRange(String.valueOf(demographicNo), Date.from(printRange.startInclusive()), Date.from(printRange.endExclusive()));
            noteIds = dateRangeNotes.stream()
                .map(note -> note.getId().toString())
                .toArray(String[]::new);
        } else if (printAllNotes) {
            List<CaseManagementNote> allNotes = caseManagementMgr.getNotes(String.valueOf(demographicNo));
            noteIds = allNotes.stream()
                .map(note -> note.getId().toString())
                .toArray(String[]::new);
        }

        logger.debug("NOTES2PRINT: " + noteIds);

        String demono = String.valueOf(demographicNo);
        request.setAttribute("demoName", getDemoName(demono));
        request.setAttribute("demoSex", getDemoSex(demono));
        request.setAttribute("demoAge", getDemoAge(demono));
        request.setAttribute("demoPhn", getDemoPhn(demono));
        request.setAttribute("mrp", getMRP(demono));
        String dob = getDemoDOB(demono);
        dob = convertDateFmt(dob, request);
        request.setAttribute("demoDOB", dob);


        List<CaseManagementNote> notes = new ArrayList<>();
        for (String noteIdStr : noteIds) {
            Long noteId = ConversionUtils.fromLongString(noteIdStr);
            if (noteId != null && noteId > 0) {
                CaseManagementNote note = this.caseManagementMgr.getNote(noteId.toString());
                // Callers authorize the print against demographicNo, but the note ids arrive from the
                // request; a note of another patient must never ride into this patient's chart PDF.
                if (note != null && demono.equals(note.getDemographic_no()) && note.getProviderNo() != null
                        && (note.getProviderNo().isEmpty() || Integer.parseInt(note.getProviderNo()) != -1)) {
                    notes.add(note);
                }
            }
        }
        List<CaseManagementNote> readableNotes = readableNotes(loggedInInfo, notes, program);
        if (!printAllNotes && readableNotes.size() != notes.size()) {
            throw new NoteAccessDeniedException();
        }
        notes = readableNotes;

        // we're not guaranteed any ordering of notes given to us, so sort by observation date
        CarlosProperties p = CarlosProperties.getInstance();
        String noteSort = p.getProperty("CMESort", "");
        if (noteSort.trim().equalsIgnoreCase("UP")) {
            Collections.sort(notes, CaseManagementNote.noteObservationDateComparator);
            Collections.reverse(notes);
        } else {
            Collections.sort(notes, CaseManagementNote.noteObservationDateComparator);
        }

        if (printRange != null && !printAllNotes) {
            notes = notes.stream()
                    .filter(note -> printRange.contains(note.getObservation_date()))
                    .collect(Collectors.toList());
        }

        List<CaseManagementNote> issueNotes;
        List<CaseManagementNote> tmpNotes;
        HashMap<String, List<CaseManagementNote>> cpp = null;
        if (printCPP) {
            cpp = new HashMap<>();
            String[] issueCodes = {"OMeds", "SocHistory", "MedHistory", "Concerns", "Reminders", "FamHistory", "RiskFactors"};
            for (String issueCode : issueCodes) {
                List<Issue> issues = caseManagementMgr.getIssueInfoByCode(providerNo, issueCode);
                String[] issueIds = getIssueIds(issues);
                tmpNotes = readableNotes(loggedInInfo, caseManagementMgr.getNotes(demono, issueIds), program);
                issueNotes = new ArrayList<>();
                for (CaseManagementNote tmpNote : tmpNotes) {
                    if (!tmpNote.isLocked() && !tmpNote.isArchived()) {
                        List<CaseManagementNoteExt> exts = caseManagementMgr.getExtByNote(tmpNote.getId());
                        boolean exclude = false;
                        for (CaseManagementNoteExt ext : exts) {
                            if (ext.getKeyVal().equals("Hide Cpp")) {
                                if (ext.getValue().equals("1")) {
                                    exclude = true;
                                }
                            }
                        }
                        if (!exclude) {
                            issueNotes.add(tmpNote);
                        }
                    }
                }
                cpp.put(issueCode, issueNotes);
            }
            // CPP notes already rendered in their sections must not repeat in the notes body.
            Set<Long> printedCppIds = cpp.values().stream().flatMap(Collection::stream)
                    .map(CaseManagementNote::getId).collect(Collectors.toSet());
            notes.removeIf(note -> printedCppIds.contains(note.getId()));
        }
        String demoNo = null;
        List<CaseManagementNote> othermeds = null;
        if (printRx) {
            demoNo = demono;
            if (cpp == null) {
                List<Issue> issues = caseManagementMgr.getIssueInfoByCode(providerNo, "OMeds");
                String[] issueIds = getIssueIds(issues); // new String[issues.size()];
                othermeds = readableNotes(loggedInInfo, caseManagementMgr.getNotes(demono, issueIds), program);
                // Rx-only printing renders Other Meds here, so omit those notes from the body.
                Set<Long> printedMedicationIds = othermeds.stream().map(CaseManagementNote::getId)
                        .collect(Collectors.toSet());
                notes.removeIf(note -> printedMedicationIds.contains(note.getId()));
            }
            // When CPP is selected, it already renders Other Meds; Rx prints prescriptions only.
        }

        List<Prevention> preventions = null;
        if (printPreventions) {
            preventions = preventionManager.getPreventionsByDemographicNo(loggedInInfo, Integer.parseInt(demono));
        }

        List<Allergy> allergies = null;
        if (printAllergies) {
            // Keep the existing severity order while excluding allergies removed from the chart.
            allergies = allergyDao.findAllergies(demographicNo).stream()
                    .filter(allergy -> !allergy.getArchived()).toList();
        }

        SimpleDateFormat headerFormat = new SimpleDateFormat("yyyy-MM-dd.hh.mm.ss");
        Date now = new Date();
        String headerDate = headerFormat.format(now);

        // Create new file to save form to
        File documentDir = PathValidationUtils.resolveConfiguredDirectory(CarlosProperties.getInstance().getProperty("DOCUMENT_DIR"), "DOCUMENT_DIR");
        File encounterFile = PathValidationUtils.validateGeneratedChildPath("EncounterForm-" + headerDate + ".pdf", documentDir);
        String fileName = encounterFile.getAbsolutePath();
        File file = null;
        FileOutputStream out = null;
        File file2 = null;
        FileOutputStream os2 = null;

        FileOutputStream fos = null;
        List<Object> pdfDocs = new ArrayList<Object>();
        // Intermediate lab PDFs (PHI) whose per-iteration delete failed; the finally block retries
        // them, since clearing file2 below would otherwise lose the only reference.
        List<File> undeletedLabPdfs = new ArrayList<File>();


        try {

            file = PathValidationUtils.resolveTrustedPath(new File(fileName));
            out = new FileOutputStream(file);

            CaseManagementPrintPdf printer = new CaseManagementPrintPdf(request, out);
            printer.printDocHeaderFooter();
            printer.printCPP(cpp);
            printer.printRx(demoNo, othermeds);
            printer.printPreventions(preventions);
            if (printAllergies && allergies != null) {
                printer.printAllergies(allergies);
            }
            printer.printNotes(notes);

            /* check extensions */
            Enumeration<String> e = request.getParameterNames();
            while (e.hasMoreElements()) {
                String name = e.nextElement();
                if (name.startsWith("extPrint")) {
                    if (request.getParameter(name).equals("true")) {
                        ExtPrint printBean = (ExtPrint) SpringUtils.getBean(name);
                        if (printBean != null) {
                            printBean.printExt(printer, request);
                        }
                    }
                }
            }
            printer.finish();

            pdfDocs.add(fileName);

            if (printLabs) {
                // get the labs which fall into the date range which are attached to this patient
                CommonLabResultData comLab = new CommonLabResultData();
                ArrayList<LabResultData> labs = comLab.populateLabResultsData(loggedInInfo, "", demono, "", "", "", "U");

                Collections.sort(labs);

                LinkedHashMap<String, LabResultData> accessionMap = new LinkedHashMap<String, LabResultData>();
                for (int i = 0; i < labs.size(); i++) {
                    LabResultData result = labs.get(i);
                    // Filter before accession de-duplication so an out-of-range version
                    // cannot hide a report that belongs to the selected calendar days.
                    if (result.isHL7TEXT() && (printRange == null || printRange.contains(result.getDateObj()))) {
                        if (result.accessionNumber == null || result.accessionNumber.equals("")) {
                            accessionMap.put("noAccessionNum" + i + result.labType, result);
                        } else {
                            if (!accessionMap.containsKey(result.accessionNumber + result.labType))
                                accessionMap.put(result.accessionNumber + result.labType, result);
                        }
                    }
                }

                for (LabResultData result : accessionMap.values()) {
                    String segmentId = result.segmentID;
                    // Each lab is rendered into the application temp directory, as every other
                    // LabPDFCreator caller does, NOT under DOCUMENT_DIR: addEmbeddedDocuments()
                    // validates its source with validateUpload(), which admits temp locations only,
                    // so a lab written under DOCUMENT_DIR was rejected there as a security violation
                    // and the chart printed with every lab silently omitted (the empty embedded file
                    // was then skipped by ConcatPDF). The neutral prefix also keeps the patient's
                    // name, which the old file name carried, out of the filesystem.
                    String tempPrefix = PathValidationUtils.validateGeneratedFileName("chart-print-lab-" + segmentId);
                    file2 = PathValidationUtils.createSecureTempFile(tempPrefix + "-", ".pdf");
                    os2 = new FileOutputStream(PathValidationUtils.resolveTrustedPath(file2));

                    {
                        LabPDFCreator pdfCreator = new LabPDFCreator(os2, segmentId, loggedInInfo.getLoggedInProviderNo());
                        try {
                            pdfCreator.printPdf();
                        } catch (DocumentException documentException) {
                            throw new DocumentException(documentException);
                        }
                        os2.close();
                        os2 = null;

                        File file3 = PathValidationUtils.createSecureTempFile(tempPrefix + "-embedded-", ".pdf");
                        // Registered for cleanup BEFORE the stream is opened and the lab embedded: the
                        // finally block only deletes what pdfDocs names, so a failure in either step
                        // used to leave this file, which holds lab PHI, in the temp directory. Concat
                        // below tolerates an empty entry, and on the failure path it is never reached.
                        pdfDocs.add(file3.getAbsolutePath());
                        fos = new FileOutputStream(PathValidationUtils.resolveTrustedPath(file3));
                        pdfCreator.addEmbeddedDocuments(file2, fos);
                        fos.close();
                        fos = null;

                        // One lab per iteration: the finally block below only sees the last file2, so
                        // every earlier lab's intermediate PDF (PHI) used to outlive the print.
                        if (!deleteTempPdf(file2, "temporary lab PDF")) {
                            undeletedLabPdfs.add(file2);
                        }
                        file2 = null;
                    }
                }

            }
            mergeCompletePdf(pdfDocs, os);
        } catch (IOException | RuntimeException e) {
            // Missing inputs fail before the response stream is written. Propagate assembly failures
            // instead of returning a successful incomplete PDF. The Struts direct-response caller
            // resets an uncommitted response and sends a real error;
            // the REST StreamingOutput caller returns HTTP 500. Mapped to IOException per the method contract.
            logger.error("Chart print generation failed ({})", e.getClass().getSimpleName());
            throw new IOException("Failed to generate complete chart print PDF");
        } finally {
            closeTempStream(out);
            closeTempStream(os2);
            closeTempStream(fos);
            // File.delete() fails silently; these are the encounter PDF and, on the exception
            // path, the lab PDF that was in flight, both PHI, so a refusal must at least be logged.
            if (file != null) {
                deleteTempPdf(file, "temporary encounter PDF");
            }
            if (file2 != null) {
                deleteTempPdf(file2, "temporary lab PDF");
            }
            // Second attempt for any intermediate lab PDF the loop could not delete: a transient
            // filesystem refusal is the usual cause, and these files hold lab results.
            for (File leftover : undeletedLabPdfs) {
                deleteTempPdf(leftover, "temporary lab PDF (retry)");
            }
            for (Object o : pdfDocs) {
                // Resolve+delete must never throw out of this finally: a malformed temp path would
                // otherwise mask any in-flight exception AND skip deletion of the remaining temp PDFs,
                // which contain PHI. Degrade to a warning and continue cleaning up the rest.
                try {
                    File tempPdf = PathValidationUtils.resolveTrustedPath(new File((String) o));
                    // The encounter PDF is in this list AND deleted via `file` above, so a missing
                    // file here is the normal case for it, not a failed delete worth a warning.
                    deleteTempPdf(tempPdf, "temporary print PDF");
                } catch (RuntimeException ex) {
                    logger.warn("Could not delete temporary print PDF ({})", ex.getClass().getSimpleName());
                }
            }
        }

    }

    /** Preserve legacy no-program visibility; configured charts use the same role/facility policy as their list. */
    private List<CaseManagementNote> readableNotes(LoggedInInfo login, Collection<CaseManagementNote> notes,
                                                  String program) {
        if (program == null || "0".equals(program)) return new ArrayList<>(notes);
        return new ArrayList<>(caseManagementMgr.filterNotes(login, login.getLoggedInProviderNo(), notes, program));
    }

    private static HttpServletRequest patientScopedRequest(HttpServletRequest request, Integer demographicNo) {
        String patient = String.valueOf(demographicNo);
        return new HttpServletRequestWrapper(request) {
            @Override
            public String getParameter(String name) {
                return isPatientParameter(name) ? patient : super.getParameter(name);
            }

            @Override
            public String[] getParameterValues(String name) {
                return isPatientParameter(name) ? new String[]{patient} : super.getParameterValues(name);
            }

            @Override
            public Map<String, String[]> getParameterMap() {
                Map<String, String[]> parameters = new HashMap<>(super.getParameterMap());
                parameters.put("demographicNo", new String[]{patient});
                parameters.put("demographic_no", new String[]{patient});
                return Collections.unmodifiableMap(parameters);
            }

            @Override
            public Enumeration<String> getParameterNames() {
                return Collections.enumeration(getParameterMap().keySet());
            }

            private boolean isPatientParameter(String name) {
                return "demographicNo".equals(name) || "demographic_no".equals(name);
            }
        };
    }

    /**
     * Extracts issue IDs from a list of Issue objects into a String array.
     *
     * This utility method converts a List of Issue domain objects into an array of
     * String representations of their IDs, suitable for passing to DAO methods that
     * require issue ID arrays.
     *
     * @param issues List&lt;Issue&gt; the list of Issue objects to extract IDs from
     * @return String[] array of issue IDs as strings, in the same order as the input list
     */
    public String[] getIssueIds(List<Issue> issues) {
        String[] issueIds = new String[issues.size()];
        int idx = 0;
        for (Issue i : issues) {
            issueIds[idx] = String.valueOf(i.getId());
            ++idx;
        }
        return issueIds;
    }

    /**
     * Retrieves the patient's full name for the specified demographic number.
     *
     * @param demoNo String the patient's demographic number
     * @return String the patient's full name, or empty string if demoNo is null
     */
    protected String getDemoName(String demoNo) {
        if (demoNo == null) {
            return "";
        }
        return caseManagementMgr.getDemoName(demoNo);
    }

    /**
     * Retrieves the patient's gender/sex for the specified demographic number.
     *
     * @param demoNo String the patient's demographic number
     * @return String the patient's gender code (e.g., "M", "F"), or empty string if demoNo is null
     */
    protected String getDemoSex(String demoNo) {
        if (demoNo == null) {
            return "";
        }
        return caseManagementMgr.getDemoGender(demoNo);
    }

    /**
     * Retrieves the patient's age for the specified demographic number.
     *
     * @param demoNo String the patient's demographic number
     * @return String the patient's age as a string, or empty string if demoNo is null
     */
    protected String getDemoAge(String demoNo) {
        if (demoNo == null) return "";
        return caseManagementMgr.getDemoAge(demoNo);
    }

    /**
     * Retrieves the patient's date of birth for the specified demographic number.
     *
     * @param demoNo String the patient's demographic number
     * @return String the patient's date of birth in YYYY-MM-DD format, or empty string if demoNo is null
     */
    protected String getDemoDOB(String demoNo) {
        if (demoNo == null) return "";
        return caseManagementMgr.getDemoDOB(demoNo);
    }

    /**
     * Retrieves the patient's Personal Health Number (PHN) for the specified demographic number.
     *
     * The PHN is the provincial health insurance number (e.g., BC CareCard, Ontario Health Card).
     *
     * @param demoNo String the patient's demographic number
     * @return String the patient's PHN/health card number, or empty string if demoNo is null
     */
    protected String getDemoPhn(String demoNo) {
        if (demoNo == null) return "";
        return caseManagementMgr.getDemoPhn(demoNo);
    }

    /**
     * Retrieves the printed patient's Most Responsible Provider (MRP) name.
     *
     * The MRP is the family doctor or primary care provider assigned to the patient
     * ({@code demographic.provider_no}), formatted as first name + surname. It is read from the
     * patient being printed, never from the login-wide {@code EctSessionBean}: that bean names
     * whichever chart was entered last in any window of the login, so it printed another
     * patient's family doctor on this patient's record (issue #4673).
     *
     * @param demoNo String the printed patient's demographic number
     * @return String the MRP's full name, or empty string if the patient has no MRP or the MRP's
     *         provider record no longer exists
     */
    protected String getMRP(String demoNo) {
        if (demoNo == null) return "";
        String providerNo = caseManagementMgr.getDemoProviderNo(demoNo);
        if (providerNo == null || providerNo.isEmpty()) return "";

        EctProviderData.Provider prov = new EctProviderData().getProvider(providerNo);
        if (prov == null) return "";
        return prov.getFirstName() + " " + prov.getSurname();
    }

    /**
     * Converts a date string from YYYY-MM-DD format to DD-MMM-YYYY format.
     *
     * This method reformats dates for human-readable display on printed documents,
     * using the locale from the HTTP request to ensure proper month name localization.
     * For example, "2024-03-15" becomes "15-Mar-2024" in English locale.
     *
     * @param strOldDate String the date in YYYY-MM-DD format
     * @param request HttpServletRequest the servlet request providing locale information
     * @return String the reformatted date in DD-MMM-YYYY format, or empty string if input is null/empty or parsing fails
     */
    protected String convertDateFmt(String strOldDate, HttpServletRequest request) {
        String strNewDate = new String();
        if (strOldDate != null && strOldDate.length() > 0) {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", request.getLocale());
            try {

                Date tempDate = fmt.parse(strOldDate);
                strNewDate = new SimpleDateFormat("dd-MMM-yyyy", request.getLocale()).format(tempDate);

            } catch (ParseException ex) {
                MiscUtils.getLogger().error("Error", ex);
            }
        }

        return strNewDate;
    }


    private static void closeTempStream(java.io.Closeable stream) {
        if (stream == null) return;
        try {
            stream.close();
        } catch (IOException | RuntimeException failure) {
            // Keep cleaning every PHI-bearing file and preserve the original print failure.
            logger.warn("Could not close temporary chart print stream ({})", failure.getClass().getSimpleName());
        }
    }

    /** Assemble completely before committing either HTTP endpoint's response. */
    static void mergeCompletePdf(List<Object> inputs, OutputStream response) throws IOException {
        File complete = PathValidationUtils.createSecureTempFile("chart-print-complete-", ".pdf");
        try {
            try (OutputStream staged = Files.newOutputStream(complete.toPath())) {
                ConcatPDF.concatRequired(inputs, staged);
            }
            Files.copy(complete.toPath(), response);
        } finally {
            if (!deleteTempPdf(complete, "completed chart print PDF")) {
                deleteTempPdf(complete, "completed chart print PDF (retry)");
            }
        }
    }

    /**
     * Deletes a temp PDF that holds PHI, tolerating one that is already gone (the encounter PDF is
     * deleted through two handles) and never throwing out of cleanup: a failure is a warning with
     * the reason, which {@code File#delete}'s boolean never gave.
     *
     * @return {@code true} when the file is gone, {@code false} after the warning when the delete
     *         was refused, so the caller can keep the file for a retry
     */
    private static boolean deleteTempPdf(File tempPdf, String description) {
        try {
            Files.deleteIfExists(tempPdf.toPath());
            return true;
        } catch (IOException | RuntimeException ex) {
            logger.warn("Failed to delete {}; leaving it for the OS temp sweep ({})", description, ex.getClass().getSimpleName());
            return false;
        }
    }
}
