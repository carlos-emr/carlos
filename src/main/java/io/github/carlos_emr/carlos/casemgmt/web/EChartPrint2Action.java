/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 * Modifications by CARLOS Contributors, 2026.
 */


package io.github.carlos_emr.carlos.casemgmt.web;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.print.OscarChartPrinter;
import io.github.carlos_emr.carlos.commn.dao.AllergyDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.PatientRecordAccess;
import io.github.carlos_emr.carlos.encounter.pageUtil.EncounterPatientContext;

import io.github.carlos_emr.carlos.log.LogAction;

import org.openpdf.text.DocumentException;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Struts2 action (2Action pattern) that generates a comprehensive PDF export of a
 * patient's electronic chart (E-Chart). The printed output includes the master
 * demographic record, appointment history, Cumulative Patient Profile (CPP) sections,
 * allergies, prescriptions, preventions, ticklers, disease registry, admissions,
 * current issues, and clinical notes.
 *
 * <p>Uses {@link OscarChartPrinter} with try-with-resources for safe PDF resource
 * management. The printer is closed automatically after all sections are rendered.
 *
 * <p><strong>Security:</strong> Requires demographic, chart and note read privileges,
 * and access to the explicitly requested patient's record before loading clinical data.
 *
 * @see io.github.carlos_emr.carlos.casemgmt.print.OscarChartPrinter
 * @see io.github.carlos_emr.carlos.casemgmt.service.CaseManagementPrintPdf
 * @since 2011-08-16
 */
public class EChartPrint2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    CaseManagementNoteDAO caseManagementNoteDao = (CaseManagementNoteDAO) SpringUtils.getBean(CaseManagementNoteDAO.class);
    AllergyDao allergyDao = (AllergyDao) SpringUtils.getBean(AllergyDao.class);

    /** Issue codes for Cumulative Patient Profile sections that are printed separately from notes. */
    static String[] cppIssues = {"MedHistory", "OMeds", "SocHistory", "FamHistory", "Reminders", "Concerns", "RiskFactors"};
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private CaseManagementManager caseManagementManager = SpringUtils.getBean(CaseManagementManager.class);


    /**
     * Default action entry point; delegates to {@link #print()}.
     *
     * @return String always {@code null} (response written directly to output stream)
     * @throws Exception if PDF generation fails
     */
    public String execute() throws Exception {
        // Default action is to print
        return print();
    }

    /**
     * Generates the full E-Chart PDF for the patient identified by the
     * {@code demographicNo} request parameter. Writes the PDF directly to the
     * HTTP response output stream with {@code application/pdf} content type.
     *
     * <p>The print sequence is: header/footer, master record, appointment history,
     * all CPP sections, allergies, prescriptions, preventions, ticklers, disease
     * registry, current/past admissions, current issues, and filtered clinical notes.
     *
     * @return String always {@code null} since the response is written directly
     * @throws Exception if PDF generation or database access fails
     * @throws SecurityException if the provider lacks patient chart/note read access
     */
    // Canonical patient parsing rejects CR/LF before demographicNo reaches the download filename.
    @SuppressFBWarnings(value = "HRS_REQUEST_PARAMETER_TO_HTTP_HEADER", justification = "EncounterPatientContext requires one canonical positive Integer patient before it reaches the filename")
    public String print() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String demographicNo;
        try {
            demographicNo = EncounterPatientContext.requireDemographicNo(request);
        } catch (IllegalArgumentException invalidPatient) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        String program = ChartProgramContext.resolve(request);
        PatientRecordAccess.require(securityInfoManager, loggedInInfo, demographicNo, "_demographic", "r");
        PatientRecordAccess.require(securityInfoManager, loggedInInfo, demographicNo, "_eChart", "r");
        PatientRecordAccess.require(securityInfoManager, loggedInInfo, demographicNo, "_casemgmt.notes", "r");

        DemographicDao demographicDao = (DemographicDao) SpringUtils.getBean(DemographicDao.class);
        Demographic demographic = demographicDao.getClientByDemographicNo(Integer.parseInt(demographicNo));

        response.setContentType("application/pdf"); // octet-stream
        response.setHeader("Content-Disposition", "attachment; filename=\"" + demographicNo + ".pdf\"");

        try (OscarChartPrinter printer = new OscarChartPrinter(request, response.getOutputStream())) {
            printer.setDemographic(demographic);
            printer.setNewPage(true);
            printer.printDocHeaderFooter();

            printer.printMasterRecord();
            printer.setNewPage(true);
            printer.printAppointmentHistory();
            printer.setNewPage(true);

            printCppItem(printer, "Social History", "SocHistory", demographic.getDemographicNo(), loggedInInfo, program);
            printCppItem(printer, "Medical History", "MedHistory", demographic.getDemographicNo(), loggedInInfo, program);
            printCppItem(printer, "Ongoing Concerns", "Concerns", demographic.getDemographicNo(), loggedInInfo, program);
            printCppItem(printer, "Reminders", "Reminders", demographic.getDemographicNo(), loggedInInfo, program);
            printCppItem(printer, "Family History", "FamHistory", demographic.getDemographicNo(), loggedInInfo, program);
            printCppItem(printer, "Risk Factors", "RiskFactors", demographic.getDemographicNo(), loggedInInfo, program);
            printCppItem(printer, "Other Medications", "OMeds", demographic.getDemographicNo(), loggedInInfo, program);
            printer.setNewPage(true);

            List<Allergy> allergies = allergyDao.findAllergies(demographic.getDemographicNo());
            if (allergies.size() > 0) {
                printer.printAllergies(allergies);
            }
            printer.printRx(String.valueOf(demographic.getDemographicNo()));

            printer.printPreventions();
            printer.printTicklers(loggedInInfo);
            printer.printDiseaseRegistry();

            printer.printCurrentAdmissions();
            printer.printPastAdmissions();

            printer.printCurrentIssues();


            List<CaseManagementNote> notes = this.caseManagementNoteDao.getMostRecentNotes(demographic.getDemographicNo());
            notes = readableNotes(loggedInInfo, notes, program);
            notes = filterOutCpp(notes);
            if (notes.size() > 0)
                printer.printNotes(notes, true);
        }

        LogAction.addLogSynchronous(loggedInInfo, "print echart", demographicNo);

        return null;
    }

    /**
     * Filters out notes that belong to CPP issue categories (Social History,
     * Other Meds, Medical History, etc.) since those are printed in their own
     * dedicated sections.
     *
     * @param notes Collection of CaseManagementNote to filter
     * @return List of CaseManagementNote containing only non-CPP notes
     */
    public List<CaseManagementNote> filterOutCpp(Collection<CaseManagementNote> notes) {
        List<CaseManagementNote> filteredNotes = new ArrayList<CaseManagementNote>();
        for (CaseManagementNote note : notes) {
            boolean skip = false;
            for (CaseManagementIssue issue : note.getIssues()) {
                for (int x = 0; x < cppIssues.length; x++) {
                    if (issue.getIssue().getCode().equals(cppIssues[x])) {
                        skip = true;
                    }
                }
            }
            if (!skip) {
                filteredNotes.add(note);
            }
        }
        return filteredNotes;
    }

    /**
     * Prints a single CPP section (e.g. "Social History") if any notes exist for
     * the given issue code and demographic.
     *
     * @param printer       OscarChartPrinter the active chart printer
     * @param header        String the section heading to display
     * @param issueCode     String the CPP issue code (e.g. "SocHistory", "MedHistory")
     * @param demographicNo int the patient demographic number
     * @throws DocumentException if an OpenPDF document error occurs
     */
    public void printCppItem(OscarChartPrinter printer, String header, String issueCode, int demographicNo) throws DocumentException {
        String program = ChartProgramContext.resolve(request);
        printCppItem(printer, header, issueCode, demographicNo,
                LoggedInInfo.getLoggedInInfoFromSession(request), program);
    }

    private void printCppItem(OscarChartPrinter printer, String header, String issueCode, int demographicNo,
                              LoggedInInfo login, String program) throws DocumentException {
        Collection<CaseManagementNote> notes = null;
        notes = caseManagementNoteDao.findNotesByDemographicAndIssueCode(demographicNo, new String[]{issueCode});
        notes = readableNotes(login, notes, program);

        if (notes.size() > 0) {
            printer.printCPPItem(header, notes);
            printer.printBlankLine();
        }
    }

    /** Apply the originating chart's role/facility policy without restricting legitimate shared-program notes. */
    private List<CaseManagementNote> readableNotes(LoggedInInfo login, Collection<CaseManagementNote> notes,
                                                 String program) {
        if (program == null || "0".equals(program)) return new ArrayList<>(notes);
        return caseManagementManager.filterNotes(login, login.getLoggedInProviderNo(), notes, program);
    }

}
