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
 */


package io.github.carlos_emr.carlos.casemgmt.web;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
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

import io.github.carlos_emr.carlos.log.LogAction;

import org.openpdf.text.DocumentException;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

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
 * <p><strong>Security:</strong> Requires {@code _demographic} read privilege via
 * {@link SecurityInfoManager#hasPrivilege}. Throws {@link SecurityException} if the
 * logged-in provider lacks access to the requested patient record.
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
     * @throws SecurityException if the provider lacks {@code _demographic} read privilege
     */
    public String print() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String demographicNo = request.getParameter("demographicNo");

        // Validate numeric to prevent HTTP response splitting (CRLF injection)
        if (demographicNo == null || !demographicNo.matches("\\d+")) {
            throw new IllegalArgumentException("Invalid demographicNo: must contain only digits");
        }

        DemographicDao demographicDao = (DemographicDao) SpringUtils.getBean(DemographicDao.class);

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", "r", demographicNo)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }


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

            printCppItem(printer, "Social History", "SocHistory", demographic.getDemographicNo());
            printCppItem(printer, "Medical History", "MedHistory", demographic.getDemographicNo());
            printCppItem(printer, "Ongoing Concerns", "Concerns", demographic.getDemographicNo());
            printCppItem(printer, "Reminders", "Reminders", demographic.getDemographicNo());
            printCppItem(printer, "Family History", "FamHistory", demographic.getDemographicNo());
            printCppItem(printer, "Risk Factors", "RiskFactors", demographic.getDemographicNo());
            printCppItem(printer, "Other Medications", "OMeds", demographic.getDemographicNo());
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
        Collection<CaseManagementNote> notes = null;
        notes = caseManagementNoteDao.findNotesByDemographicAndIssueCode(demographicNo, new String[]{issueCode});

        if (notes.size() > 0) {
            printer.printCPPItem(header, notes);
            printer.printBlankLine();
        }
    }

}
