//CHECKSTYLE:OFF
package ca.openosp.openo.managers;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.casemgmt.dao.CaseManagementNoteDAO;
import ca.openosp.openo.casemgmt.dao.IssueDAO;
import ca.openosp.openo.casemgmt.model.CaseManagementIssue;
import ca.openosp.openo.casemgmt.model.CaseManagementNote;
import ca.openosp.openo.casemgmt.model.CaseManagementNoteExt;
import ca.openosp.openo.casemgmt.model.Issue;
import ca.openosp.openo.casemgmt.service.CaseManagementManager;
import ca.openosp.openo.commn.model.enumerator.CppCode;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.webserv.rest.conversion.CaseManagementIssueConverter;
import ca.openosp.openo.webserv.rest.to.model.NoteExtTo1;
import ca.openosp.openo.webserv.rest.to.model.NoteTo1;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Manager service for clinical notes and case management in the OpenO EMR system.
 *
 * This service provides business logic for retrieving, converting, and managing clinical notes
 * associated with patient demographics and CPP (Cumulative Patient Profile) codes. It handles
 * the integration between case management notes, clinical issues, and extended note attributes
 * to support comprehensive patient record management in healthcare workflows.
 *
 * The NoteManager coordinates operations between the case management data access layer and
 * REST API transfer objects, ensuring proper conversion and filtering of clinical notes based
 * on issue codes, CPP classification, and active status. It supports both general note retrieval
 * and CPP-specific note queries for integration with patient charts and clinical documentation.
 *
 * @see CaseManagementNote
 * @see CaseManagementNoteDAO
 * @see CaseManagementManager
 * @see NoteTo1
 * @see CppCode
 * @since 2026-01-24
 */
@Service
public class NoteManager {

    private static Logger logger = MiscUtils.getLogger();

    @Autowired
    private CaseManagementManager caseManagementManager;

    @Autowired
    private CaseManagementNoteDAO caseManagementNoteDAO;

    @Autowired
    private IssueDAO issueDAO;

    /**
     * Retrieves all CPP (Cumulative Patient Profile) notes for a specific patient demographic.
     *
     * This method queries all case management notes associated with the specified patient that
     * are classified under CPP issue codes. CPP notes represent cumulative patient profile
     * information including medical history, ongoing problems, medications, allergies, and
     * other persistent clinical data. The returned notes are converted to transfer objects
     * suitable for REST API responses and client consumption.
     *
     * @param loggedInInfo LoggedInInfo object containing the current user's session and security context
     * @param demographicNo Integer unique identifier for the patient demographic record
     * @return List&lt;NoteTo1&gt; collection of CPP notes converted to transfer objects, or empty list if none found
     */
    public List<NoteTo1> getCppNotes(LoggedInInfo loggedInInfo, Integer demographicNo) {
        List<CaseManagementNote> notes = new ArrayList<>(caseManagementNoteDAO.findNotesByDemographicAndIssueCode(demographicNo, CppCode.toArray()));
        List<NoteTo1> noteTo1s = new ArrayList<>();
        for (CaseManagementNote note : notes) {
            noteTo1s.add(convertNote(loggedInInfo, note));
        }
        return noteTo1s;
    }

    /**
     * Retrieves active CPP (Cumulative Patient Profile) notes for a specific patient demographic.
     *
     * This method filters case management notes to return only those with active status
     * and associated with CPP issue codes. Active notes represent current, ongoing clinical
     * information that is relevant to the patient's care. This is commonly used for displaying
     * up-to-date patient profile information in encounter screens and clinical workflows.
     * Inactive or archived notes are excluded from the results.
     *
     * @param loggedInInfo LoggedInInfo object containing the current user's session and security context
     * @param demographicNo Integer unique identifier for the patient demographic record
     * @return List&lt;NoteTo1&gt; collection of active CPP notes converted to transfer objects, or empty list if none found
     */
    public List<NoteTo1> getActiveCppNotes(LoggedInInfo loggedInInfo, Integer demographicNo) {
        String[] issueIds = getIssueIds(null);
        List<CaseManagementNote> notes = new ArrayList<>(caseManagementNoteDAO.getActiveNotesByDemographic(String.valueOf(demographicNo), issueIds));
        List<NoteTo1> noteTo1s = new ArrayList<>();
        for (CaseManagementNote note : notes) {
            noteTo1s.add(convertNote(loggedInInfo, note));
        }
        return noteTo1s;
    }

    /**
     * Retrieves active CPP (Cumulative Patient Profile) notes for a specific patient demographic
     * filtered by custom CPP codes.
     *
     * This method provides the same functionality as {@link #getActiveCppNotes(LoggedInInfo, Integer)}
     * but allows for custom filtering using a specific set of CPP codes rather than the default
     * comprehensive CPP code list. This is useful for retrieving notes related to specific clinical
     * categories or when a subset of CPP information is required for targeted clinical views or reports.
     *
     * @param loggedInInfo LoggedInInfo object containing the current user's session and security context
     * @param demographicNo Integer unique identifier for the patient demographic record
     * @param newCppCodes String[] array of CPP code strings to filter notes by
     * @return List&lt;NoteTo1&gt; collection of active CPP notes matching the specified codes, or empty list if none found
     */
    public List<NoteTo1> getActiveCppNotes(LoggedInInfo loggedInInfo, Integer demographicNo, String[] newCppCodes) {
        String[] issueIds = getIssueIds(newCppCodes);
        List<CaseManagementNote> notes = new ArrayList<>(caseManagementNoteDAO.getActiveNotesByDemographic(String.valueOf(demographicNo), issueIds));
        List<NoteTo1> noteTo1s = new ArrayList<>();
        for (CaseManagementNote note : notes) {
            noteTo1s.add(convertNote(loggedInInfo, note));
        }
        return noteTo1s;
    }

    /**
     * Converts a CaseManagementNote entity to a NoteTo1 transfer object for API consumption.
     *
     * This method performs comprehensive conversion of a case management note entity into
     * a REST API transfer object, including all core note properties, extended attributes,
     * and associated clinical issues. The conversion process includes:
     * - Core note metadata (revision, dates, provider information, status)
     * - Extended note attributes (start date, resolution date, problem status, treatment, etc.)
     * - Associated clinical issues with CPP classification
     * - Summary code generation from all associated issues
     *
     * The method handles the complex mapping of note extensions (date values, problem details,
     * relationships, life stage, etc.) and determines CPP classification based on associated
     * issue codes. This is a central conversion utility used throughout the note retrieval workflow.
     *
     * @param loggedInInfo LoggedInInfo object containing the current user's session and security context
     * @param caseManagementNote CaseManagementNote entity object to convert
     * @return NoteTo1 transfer object containing all converted note data and associations
     */
    public NoteTo1 convertNote(LoggedInInfo loggedInInfo, CaseManagementNote caseManagementNote) {
        NoteTo1 note = new NoteTo1();
        note.setNoteId(caseManagementNote.getId().intValue());
        note.setIsSigned(caseManagementNote.isSigned());
        note.setRevision(caseManagementNote.getRevision());
        note.setObservationDate(caseManagementNote.getObservation_date());
        note.setUpdateDate(caseManagementNote.getUpdate_date());
        note.setProviderName(caseManagementNote.getProviderName());
        note.setProviderNo(caseManagementNote.getProviderNo());
        note.setStatus(caseManagementNote.getStatus());
        note.setProgramName(caseManagementNote.getProgramName());
        note.setRoleName(caseManagementNote.getRoleName());
        note.setUuid(caseManagementNote.getUuid());
        note.setHasHistory(caseManagementNote.getHasHistory());
        note.setLocked(caseManagementNote.isLocked());
        note.setNote(caseManagementNote.getNote());
        note.setRxAnnotation(caseManagementNote.isRxAnnotation());
        note.setEncounterType(caseManagementNote.getEncounter_type());
        note.setPosition(caseManagementNote.getPosition());
        note.setAppointmentNo(caseManagementNote.getAppointmentNo());
        note.setCpp(false);

        //get all note extra values	
        List<CaseManagementNoteExt> lcme = new ArrayList<CaseManagementNoteExt>();
        lcme.addAll(caseManagementManager.getExtByNote(caseManagementNote.getId()));

        NoteExtTo1 noteExt = new NoteExtTo1();
        noteExt.setNoteId(caseManagementNote.getId());

        for (CaseManagementNoteExt l : lcme) {
            logger.debug("NOTE EXT KEY:" + l.getKeyVal() + l.getValue());

            if (l.getKeyVal().equals(CaseManagementNoteExt.STARTDATE)) {
                noteExt.setStartDate(l.getDateValueStr());
            } else if (l.getKeyVal().equals(CaseManagementNoteExt.RESOLUTIONDATE)) {
                noteExt.setResolutionDate(l.getDateValueStr());
            } else if (l.getKeyVal().equals(CaseManagementNoteExt.PROCEDUREDATE)) {
                noteExt.setProcedureDate(l.getDateValueStr());
            } else if (l.getKeyVal().equals(CaseManagementNoteExt.AGEATONSET)) {
                noteExt.setAgeAtOnset(l.getValue());
            } else if (l.getKeyVal().equals(CaseManagementNoteExt.TREATMENT)) {
                noteExt.setTreatment(l.getValue());
            } else if (l.getKeyVal().equals(CaseManagementNoteExt.PROBLEMSTATUS)) {
                noteExt.setProblemStatus(l.getValue());
            } else if (l.getKeyVal().equals(CaseManagementNoteExt.EXPOSUREDETAIL)) {
                noteExt.setExposureDetail(l.getValue());
            } else if (l.getKeyVal().equals(CaseManagementNoteExt.RELATIONSHIP)) {
                noteExt.setRelationship(l.getValue());
            } else if (l.getKeyVal().equals(CaseManagementNoteExt.LIFESTAGE)) {
                noteExt.setLifeStage(l.getValue());
            } else if (l.getKeyVal().equals(CaseManagementNoteExt.HIDECPP)) {
                noteExt.setHideCpp(l.getValue());
            } else if (l.getKeyVal().equals(CaseManagementNoteExt.PROBLEMDESC)) {
                noteExt.setProblemDesc(l.getValue());
            }

        }

        List<CaseManagementIssue> cmIssues = new ArrayList<CaseManagementIssue>(caseManagementNote.getIssues());

        StringBuilder summaryCodes = new StringBuilder();
        for (CaseManagementIssue issue : cmIssues) {
            if (isCppCode(issue)) {
                note.setCpp(true);
            }
            summaryCodes.append((summaryCodes.toString().isEmpty() ? "" : ", ") + issue.getIssue().getCode());
        }

        note.setSummaryCode(summaryCodes.toString());
        note.setNoteExt(noteExt);
        note.setAssignedIssues(new CaseManagementIssueConverter().getAllAsTransferObjects(loggedInInfo, cmIssues));

        return note;
    }

    /**
     * Determines whether a case management issue is classified as a CPP (Cumulative Patient Profile) code.
     *
     * This method checks if the issue code associated with a CaseManagementIssue matches any
     * of the predefined CPP codes defined in the CppCode enumeration. CPP codes represent
     * categories of persistent patient information such as medical history, ongoing problems,
     * medications, allergies, and family history. This classification is used to filter and
     * categorize clinical notes for display in the patient's cumulative profile.
     *
     * @param cmeIssue CaseManagementIssue object to evaluate for CPP classification
     * @return boolean true if the issue's code is a CPP code, false otherwise
     */
    public boolean isCppCode(CaseManagementIssue cmeIssue) {
        return (CppCode.toStringList()).contains(cmeIssue.getIssue().getCode());
    }

    /**
     * Retrieves issue IDs corresponding to specified CPP codes or default CPP codes.
     *
     * This method queries the issue database to find all issue entities that match the provided
     * CPP codes, or defaults to the comprehensive CPP code list if no custom codes are specified.
     * The returned issue IDs are used for filtering case management notes by specific clinical
     * issue categories. This supports dynamic note filtering based on CPP classification and
     * enables flexible retrieval of notes associated with specific clinical domains.
     *
     * @param newCppCodes String[] array of custom CPP code strings to query, or null to use default CPP codes
     * @return String[] array of issue ID strings corresponding to the queried CPP codes
     */
    public String[] getIssueIds(String[] newCppCodes) {
        List<Issue> issues = new ArrayList<>();
        if (newCppCodes != null && newCppCodes.length > 0) {
            issues = issueDAO.findIssueByCode(newCppCodes);
        } else {
            issues = issueDAO.findIssueByCode(CppCode.toArray());
        }

        List<String> issueIdList = new ArrayList<>();
        for (Issue issue : issues) {
            issueIdList.add(String.valueOf(issue.getId()));
        }

        return issueIdList.toArray(new String[issueIdList.size()]);
    }

}
