/**
 * Case Management Print Service for OpenO EMR.
 *
 * This service provides comprehensive PDF printing capabilities for patient medical records,
 * including encounter notes, clinical prevention profiles (CPP), prescriptions, laboratory results,
 * preventions, and allergies. It supports both local and remote (integrator) notes, date range
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
 *   <li>Integration with CAISI Integrator for remote facility notes</li>
 *   <li>HL7/OLIS laboratory report integration</li>
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
 * @see NoteService
 * @since 2026-01-24
 */
//CHECKSTYLE:OFF
package ca.openosp.openo.casemgmt.service;

import ca.openosp.OscarProperties;
import ca.openosp.openo.encounter.data.EctProviderData;
import ca.openosp.openo.encounter.pageUtil.EctSessionBean;
import com.itextpdf.text.DocumentException;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.PMmodule.caisi_integrator.CaisiIntegratorManager;
import ca.openosp.openo.PMmodule.model.ProgramProvider;
import ca.openosp.openo.PMmodule.service.ProgramManager;
import ca.openosp.openo.caisi_integrator.ws.CachedDemographicNote;
import ca.openosp.openo.caisi_integrator.ws.DemographicWs;
import ca.openosp.openo.casemgmt.model.CaseManagementNote;
import ca.openosp.openo.casemgmt.model.CaseManagementNoteExt;
import ca.openosp.openo.casemgmt.model.Issue;
import ca.openosp.openo.casemgmt.util.ExtPrint;
import ca.openosp.openo.casemgmt.web.NoteDisplay;
import ca.openosp.openo.casemgmt.web.NoteDisplayLocal;
import ca.openosp.openo.commn.model.Allergy;
import ca.openosp.openo.commn.model.Prevention;
import ca.openosp.openo.commn.dao.AllergyDao;
import ca.openosp.openo.managers.PreventionManager;
import ca.openosp.openo.managers.ProgramManager2;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.OscarProperties;
import ca.openosp.openo.lab.ca.all.pageUtil.LabPDFCreator;
import ca.openosp.openo.lab.ca.all.pageUtil.OLISLabPDFCreator;
import ca.openosp.openo.lab.ca.all.parsers.Factory;
import ca.openosp.openo.lab.ca.all.parsers.MessageHandler;
import ca.openosp.openo.lab.ca.all.parsers.OLISHL7Handler;
import ca.openosp.openo.lab.ca.on.CommonLabResultData;
import ca.openosp.openo.lab.ca.on.LabResultData;
import ca.openosp.openo.util.ConcatPDF;
import ca.openosp.openo.util.ConversionUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class CaseManagementPrint {

    private static Logger logger = MiscUtils.getLogger();


    CaseManagementManager caseManagementMgr = SpringUtils.getBean(CaseManagementManager.class);

    private NoteService noteService = SpringUtils.getBean(NoteService.class);


    private ProgramManager2 programManager2 = SpringUtils.getBean(ProgramManager2.class);

    private ProgramManager programMgr = SpringUtils.getBean(ProgramManager.class);

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
     *   <li>Fetches local and remote (integrator) notes as applicable</li>
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
     * @param noteIds String[] array of note IDs to print (ignored if printAllNotes is true); may contain
     *                         "UUID" prefixed strings for remote integrator notes
     * @param printCPP boolean true to include Clinical Prevention Profile sections (OMeds, SocHistory, etc.)
     * @param printRx boolean true to include prescription/medication information
     * @param printLabs boolean true to include laboratory results (HL7/OLIS reports)
     * @param printPreventions boolean true to include prevention/immunization records
     * @param printAllergies boolean true to include patient allergy information
     * @param useDateRange boolean true to filter notes by date range (requires startDate and endDate)
     * @param startDate Calendar the start date for date range filtering (inclusive); may be null if useDateRange is false
     * @param endDate Calendar the end date for date range filtering (inclusive); may be null if useDateRange is false
     * @param request HttpServletRequest the servlet request containing session data and parameters
     * @param os OutputStream the output stream to write the final concatenated PDF document
     *
     * @throws IOException if file I/O operations fail during PDF generation or cleanup
     * @throws DocumentException if PDF document creation or manipulation fails
     */
    public void doPrint(LoggedInInfo loggedInInfo, Integer demographicNo, boolean printAllNotes, String[] noteIds, boolean printCPP, boolean printRx, boolean printLabs, boolean printPreventions, boolean printAllergies, boolean useDateRange, Calendar startDate, Calendar endDate, HttpServletRequest request, OutputStream os) throws IOException, DocumentException {

        String providerNo = loggedInInfo.getLoggedInProviderNo();

        // Get all or date range noteIds.
        if (printAllNotes && useDateRange) {
            List<CaseManagementNote> dateRangeNotes = caseManagementMgr.getNotesInDateRange(String.valueOf(demographicNo), startDate.getTime(), endDate.getTime());
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
        request.setAttribute("mrp", getMRP(request));
        String dob = getDemoDOB(demono);
        dob = convertDateFmt(dob, request);
        request.setAttribute("demoDOB", dob);


        List<CaseManagementNote> notes = new ArrayList<>();
        List<String> remoteNoteUUIDs = new ArrayList<>();
        String uuid;
        for (String noteIdStr : noteIds) {
            if (noteIdStr.startsWith("UUID")) {
                uuid = noteIdStr.substring(4);
                remoteNoteUUIDs.add(uuid);
            } else {
                Long noteId = ConversionUtils.fromLongString(noteIdStr);
                if (noteId > 0) {
                    CaseManagementNote note = this.caseManagementMgr.getNote(noteId.toString());
                    if (note != null && note.getProviderNo() != null
                            && (note.getProviderNo().isEmpty() || Integer.parseInt(note.getProviderNo()) != -1)) {
                        notes.add(note);
                    }
                }
            }
        }

        if (loggedInInfo.getCurrentFacility().isIntegratorEnabled() && !remoteNoteUUIDs.isEmpty()) {
            DemographicWs demographicWs = CaisiIntegratorManager.getDemographicWs(loggedInInfo, loggedInInfo.getCurrentFacility());
            List<CachedDemographicNote> remoteNotes = demographicWs.getLinkedCachedDemographicNotes(Integer.parseInt(demono));
            for (CachedDemographicNote remoteNote : remoteNotes) {
                for (String remoteUUID : remoteNoteUUIDs) {
                    if (remoteUUID.equals(remoteNote.getCachedDemographicNoteCompositePk().getUuid())) {
                        CaseManagementNote fakeNote = getFakedNote(remoteNote);
                        notes.add(fakeNote);
                        break;
                    }
                }
            }
        }

        // we're not guaranteed any ordering of notes given to us, so sort by observation date
        OscarProperties p = OscarProperties.getInstance();
        String noteSort = p.getProperty("CMESort", "");
        if (noteSort.trim().equalsIgnoreCase("UP")) {
            Collections.sort(notes, CaseManagementNote.noteObservationDateComparator);
            Collections.reverse(notes);
        } else {
            Collections.sort(notes, CaseManagementNote.noteObservationDateComparator);
        }

        // Filter notes by date range if specified and not already filtered by caseManagementMgr
        if (useDateRange && (startDate != null && endDate != null) && !printAllNotes) {
            logger.debug("Filtering notes by date range - start date: " + startDate + ", end date: " + endDate);

            notes = notes.stream()
                .filter(cmn -> {
                    Date noteDate = cmn.getObservation_date();
                    if (noteDate == null) {
                        logger.debug("Note " + cmn.getId() + " has null observation date - excluding");
                        return false;
                    }

                    boolean afterStart = !startDate.getTime().after(noteDate);
                    boolean beforeEnd = !endDate.getTime().before(noteDate);
                    boolean inRange = afterStart && beforeEnd;

                    logger.debug("Note " + cmn.getId() + " date " + noteDate +
                        " - after start: " + afterStart + ", before end: " + beforeEnd + ", in range: " + inRange);

                    return inRange;
                })
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
                tmpNotes = caseManagementMgr.getNotes(demono, issueIds);
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
        }
        String demoNo = null;
        List<CaseManagementNote> othermeds = null;
        if (printRx) {
            demoNo = demono;
            if (cpp == null) {
                List<Issue> issues = caseManagementMgr.getIssueInfoByCode(providerNo, "OMeds");
                String[] issueIds = getIssueIds(issues);// new String[issues.size()];
                othermeds = caseManagementMgr.getNotes(demono, issueIds);
            } else {
                othermeds = cpp.get("OMeds");
            }
        }

        List<Prevention> preventions = null;
        if (printPreventions) {
            preventions = preventionManager.getPreventionsByDemographicNo(loggedInInfo, Integer.parseInt(demono));
        }

        List<Allergy> allergies = null;
        if (printAllergies) {
            allergies = allergyDao.findAllergies(demographicNo);
        }

        SimpleDateFormat headerFormat = new SimpleDateFormat("yyyy-MM-dd.hh.mm.ss");
        Date now = new Date();
        String headerDate = headerFormat.format(now);

        // Create new file to save form to
        String path = OscarProperties.getInstance().getProperty("DOCUMENT_DIR");
        String fileName = path + "EncounterForm-" + headerDate + ".pdf";
        File file = null;
        FileOutputStream out = null;
        File file2 = null;
        FileOutputStream os2 = null;

        FileOutputStream fos = null;
        List<Object> pdfDocs = new ArrayList<Object>();


        try {

            file = new File(fileName);
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
                    if (result.isHL7TEXT()) {
                        if (result.accessionNumber == null || result.accessionNumber.equals("")) {
                            accessionMap.put("noAccessionNum" + i + result.labType, result);
                        } else {
                            if (!accessionMap.containsKey(result.accessionNumber + result.labType))
                                accessionMap.put(result.accessionNumber + result.labType, result);
                        }
                    }
                }

                for (LabResultData result : accessionMap.values()) {
                    //Date d = result.getDateObj();
                    // TODO:filter out the ones which aren't in our date range if there's a date range????
                    String segmentId = result.segmentID;
                    MessageHandler handler = Factory.getHandler(segmentId);
                    String fileName2 = OscarProperties.getInstance().getProperty("DOCUMENT_DIR") + "//" + handler.getPatientName().replaceAll("\\s", "_") + "_" + handler.getMsgDate() + "_LabReport.pdf";
                    file2 = new File(fileName2);
                    os2 = new FileOutputStream(file2);

                    if (handler instanceof OLISHL7Handler) {
                        OLISLabPDFCreator olisLabPdfCreator = new OLISLabPDFCreator(os2, request, segmentId);
                        olisLabPdfCreator.printPdf();
                        os2.close();
                        pdfDocs.add(fileName2);
                    } else {

                        LabPDFCreator pdfCreator = new LabPDFCreator(os2, segmentId, loggedInInfo.getLoggedInProviderNo());
                        try {
                            pdfCreator.printPdf();
                        } catch (DocumentException documentException) {
                            throw new DocumentException(documentException);
                        }
                        os2.close();

                        String fileName3 = OscarProperties.getInstance().getProperty("DOCUMENT_DIR") + "//" + handler.getPatientName().replaceAll("\\s", "_") + "_" + handler.getMsgDate() + "_LabReport.1.pdf";
                        File file3 = new File(fileName3);
                        fos = new FileOutputStream(file3);
                        pdfCreator.addEmbeddedDocuments(file2, fos);
                        pdfDocs.add(fileName3);

                    }
                }

            }
            ConcatPDF.concat(pdfDocs, os);
        } catch (IOException e) {
            logger.error("Error ", e);

        } finally {
            if (out != null) {
                out.close();
            }
            if (os2 != null) {
                os2.close();
            }
            if (fos != null) {
                fos.close();
            }
            if (file != null) {
                file.delete();
            }
            if (file2 != null) {
                file2.delete();
            }
            for (Object o : pdfDocs) {
                new File((String) o).delete();
            }
        }

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
     * Converts a remote CAISI Integrator note into a local CaseManagementNote for printing.
     *
     * This method creates a lightweight CaseManagementNote object populated with data from
     * a remote facility's cached demographic note. The resulting "faked" note can be processed
     * by the standard printing pipeline alongside local notes, enabling seamless integration
     * of multi-facility patient records.
     *
     * Only essential fields (observation date and note content) are copied. Other note metadata
     * remains unpopulated as it is not required for basic printing functionality.
     *
     * @param remoteNote CachedDemographicNote the remote note from the CAISI Integrator system
     * @return CaseManagementNote a local note object populated with remote data
     */
    private CaseManagementNote getFakedNote(CachedDemographicNote remoteNote) {
        CaseManagementNote note = new CaseManagementNote();

        if (remoteNote.getObservationDate() != null)
            note.setObservation_date(remoteNote.getObservationDate().getTime());
        note.setNote(remoteNote.getNote());

        return (note);
    }


    /**
     * Retrieves all note IDs for a patient within a specified date range.
     *
     * This method queries the note service with a comprehensive set of filter criteria including
     * date range boundaries, program context, and session-based filters (roles, providers, issues).
     * It respects the user's current program assignment and applies any active E-Chart view filters
     * that may have been set by CaseManagementView2Action.
     *
     * The method defaults to the "OSCAR" program if the provider has not been assigned to a specific
     * program. Only local notes (NoteDisplayLocal) are returned; remote integrator notes are excluded.
     *
     * @param loggedInInfo LoggedInInfo the authenticated session information
     * @param request HttpServletRequest the servlet request containing session attributes and parameters
     * @param demoNo String the patient's demographic number as a string
     * @param startDate Date the start of the date range (inclusive)
     * @param endDate Date the end of the date range (inclusive)
     * @return String[] array of note IDs (as strings) matching the criteria
     */
    @SuppressWarnings("unchecked")
    private String[] getAllNoteIdsWithinDateRange(LoggedInInfo loggedInInfo, HttpServletRequest request, String demoNo, Date startDate, Date endDate) {

        HttpSession se = loggedInInfo.getSession();

        ProgramProvider pp = programManager2.getCurrentProgramInDomain(loggedInInfo, loggedInInfo.getLoggedInProviderNo());
        String programId = null;

        if (pp != null && pp.getProgramId() != null) {
            programId = "" + pp.getProgramId();
        } else {
            programId = String.valueOf(programMgr.getProgramIdByProgramName("OSCAR")); //Default to the oscar program if providers hasn't been assigned to a program
        }

        NoteSelectionCriteria criteria = new NoteSelectionCriteria();
        criteria.setStartDate(startDate);
        criteria.setEndDate(endDate);
        criteria.setMaxResults(Integer.MAX_VALUE);
        criteria.setDemographicId(ConversionUtils.fromIntString(demoNo));
        criteria.setUserRole((String) request.getSession().getAttribute("userrole"));
        criteria.setUserName((String) request.getSession().getAttribute("user"));
        if (request.getParameter("note_sort") != null && request.getParameter("note_sort").length() > 0) {
            criteria.setNoteSort(request.getParameter("note_sort"));
        }
        if (programId != null && !programId.trim().isEmpty()) {
            criteria.setProgramId(programId);
        }

        if (se.getAttribute("CaseManagementViewAction_filter_roles") != null) {
            criteria.getRoles().addAll((List<String>) se.getAttribute("CaseManagementViewAction_filter_roles"));
        }

        if (se.getAttribute("CaseManagementViewAction_filter_providers") != null) {
            criteria.getProviders().addAll((List<String>) se.getAttribute("CaseManagementViewAction_filter_providers"));
        }

        if (se.getAttribute("CaseManagementViewAction_filter_issues") != null) {
            criteria.getIssues().addAll((List<String>) se.getAttribute("CaseManagementViewAction_filter_issues"));
        }

        if (logger.isDebugEnabled()) {
            logger.debug("SEARCHING FOR NOTES WITH CRITERIA: " + criteria);
        }

        NoteSelectionResult result = noteService.findNotes(loggedInInfo, criteria);

        List<String> buf = new ArrayList<String>();
        if (result != null && result.getNotes() != null) {
            for (NoteDisplay nd : result.getNotes()) {
                if (nd instanceof NoteDisplayLocal) {
                    buf.add(nd.getNoteId().toString());
                }
            }
        }

        return buf.toArray(new String[0]);
    }

    /**
     * Retrieves all note IDs for a patient without date range restrictions.
     *
     * This method queries the note service with filter criteria based on program context and
     * session-based filters (roles, providers, issues), but without date range boundaries.
     * It retrieves up to Integer.MAX_VALUE notes and respects the user's current program
     * assignment and any active E-Chart view filters.
     *
     * If no session filters are present (roles, providers, issues), the criteria will have
     * empty lists, which signals the note service to return all notes without filtering by
     * those dimensions. The method defaults to the "OSCAR" program if the provider has not
     * been assigned to a specific program.
     *
     * Only local notes (NoteDisplayLocal) are returned; remote integrator notes are excluded.
     *
     * @param loggedInInfo LoggedInInfo the authenticated session information
     * @param request HttpServletRequest the servlet request containing session attributes and parameters
     * @param demoNo String the patient's demographic number as a string
     * @return String[] array of all note IDs (as strings) matching the criteria
     */
    @SuppressWarnings("unchecked")
    private String[] getAllNoteIds(LoggedInInfo loggedInInfo, HttpServletRequest request, String demoNo) {

        HttpSession se = loggedInInfo.getSession();

        ProgramProvider pp = programManager2.getCurrentProgramInDomain(loggedInInfo, loggedInInfo.getLoggedInProviderNo());
        String programId = null;

        if (pp != null && pp.getProgramId() != null) {
            programId = "" + pp.getProgramId();
        } else {
            programId = String.valueOf(programMgr.getProgramIdByProgramName("OSCAR")); //Default to the oscar program if providers hasn't been assigned to a program
        }

        NoteSelectionCriteria criteria = new NoteSelectionCriteria();
        criteria.setMaxResults(Integer.MAX_VALUE);
        criteria.setDemographicId(ConversionUtils.fromIntString(demoNo));
        criteria.setUserRole((String) request.getSession().getAttribute("userrole"));
        criteria.setUserName((String) request.getSession().getAttribute("user"));
        if (request.getParameter("note_sort") != null && request.getParameter("note_sort").length() > 0) {
            criteria.setNoteSort(request.getParameter("note_sort"));
        }
        if (programId != null && !programId.trim().isEmpty()) {
            criteria.setProgramId(programId);
        }

        // Apply session filters if they exist (set by CaseManagementView2Action)
        // If no filters are present, criteria will have empty lists which means "no filtering" - get all notes
        if (se.getAttribute("CaseManagementViewAction_filter_roles") != null) {
            criteria.getRoles().addAll((List<String>) se.getAttribute("CaseManagementViewAction_filter_roles"));
        }

        if (se.getAttribute("CaseManagementViewAction_filter_providers") != null) {
            criteria.getProviders().addAll((List<String>) se.getAttribute("CaseManagementViewAction_filter_providers"));
        }

        if (se.getAttribute("CaseManagementViewAction_filter_issues") != null) {
            criteria.getIssues().addAll((List<String>) se.getAttribute("CaseManagementViewAction_filter_issues"));
        }

        if (logger.isDebugEnabled()) {
            logger.debug("SEARCHING FOR NOTES WITH CRITERIA: " + criteria);
        }

        NoteSelectionResult result = noteService.findNotes(loggedInInfo, criteria);

        List<String> buf = new ArrayList<String>();
        if (result != null && result.getNotes() != null) {
            for (NoteDisplay nd : result.getNotes()) {
                if (nd instanceof NoteDisplayLocal) {
                    buf.add(nd.getNoteId().toString());
                }
            }
        }

        return buf.toArray(new String[0]);
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
     * Retrieves the Most Responsible Provider (MRP) name from the encounter session.
     *
     * The MRP is the family doctor or primary care provider assigned to the patient.
     * This method extracts the provider information from the EctSessionBean and formats
     * the full name (first name + surname).
     *
     * @param request HttpServletRequest the servlet request containing the EctSessionBean in session
     * @return String the MRP's full name (first name + surname), or empty string if no MRP is assigned
     */
    protected String getMRP(HttpServletRequest request) {
        EctSessionBean bean = (EctSessionBean) request.getSession().getAttribute("EctSessionBean");
        if (bean == null) return new String("");
        if (bean.familyDoctorNo == null) return new String("");
        if (bean.familyDoctorNo.isEmpty()) return new String("");

        EctProviderData.Provider prov = new EctProviderData().getProvider(bean.familyDoctorNo);
        String name = prov.getFirstName() + " " + prov.getSurname();
        return name;
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

}
