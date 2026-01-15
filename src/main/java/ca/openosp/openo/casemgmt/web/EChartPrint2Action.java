//CHECKSTYLE:OFF
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
 */


package ca.openosp.openo.casemgmt.web;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.casemgmt.dao.CaseManagementNoteDAO;
import ca.openosp.openo.casemgmt.model.CaseManagementIssue;
import ca.openosp.openo.casemgmt.model.CaseManagementNote;
import ca.openosp.openo.casemgmt.print.OscarChartPrinter;
import ca.openosp.openo.commn.dao.AllergyDao;
import ca.openosp.openo.commn.dao.DemographicDao;
import ca.openosp.openo.commn.model.Allergy;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.log.LogAction;

import com.itextpdf.text.DocumentException;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class EChartPrint2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    CaseManagementNoteDAO caseManagementNoteDao = (CaseManagementNoteDAO) SpringUtils.getBean(CaseManagementNoteDAO.class);
    AllergyDao allergyDao = (AllergyDao) SpringUtils.getBean(AllergyDao.class);
    static String[] cppIssues = {"MedHistory", "OMeds", "SocHistory", "FamHistory", "Reminders", "Concerns", "RiskFactors"};
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);


    public String execute() throws Exception {
        // Default action is to print
        return print();
    }

    public String print() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String demographicNo = request.getParameter("demographicNo");
        DemographicDao demographicDao = (DemographicDao) SpringUtils.getBean(DemographicDao.class);

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_demographic", "r", demographicNo)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }


        Demographic demographic = demographicDao.getClientByDemographicNo(Integer.parseInt(demographicNo));

        response.setContentType("application/pdf"); // octet-stream
        response.setHeader("Content-Disposition", "attachment; filename=\"" + demographicNo + ".pdf\"");

        OscarChartPrinter printer = new OscarChartPrinter(request, response.getOutputStream());
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

        printer.finish();

        LogAction.addLogSynchronous(loggedInInfo, "print echart", demographicNo);

        return null;
    }

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

    public void printCppItem(OscarChartPrinter printer, String header, String issueCode, int demographicNo) throws DocumentException {
        Collection<CaseManagementNote> notes = null;
        notes = caseManagementNoteDao.findNotesByDemographicAndIssueCode(demographicNo, new String[]{issueCode});

        if (notes.size() > 0) {
            printer.printCPPItem(header, notes);
            printer.printBlankLine();
        }
    }

}
