//CHECKSTYLE:OFF
/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 */
package ca.openosp.openo.web;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.PMmodule.dao.ProgramDao;
import ca.openosp.openo.PMmodule.model.Program;
import ca.openosp.openo.casemgmt.dao.CaseManagementNoteDAO;
import ca.openosp.openo.casemgmt.model.CaseManagementNote;
import ca.openosp.openo.commn.dao.AdmissionDao;
import ca.openosp.openo.commn.dao.DemographicDao;
import ca.openosp.openo.commn.dao.FunctionalCentreDao;
import ca.openosp.openo.commn.model.Admission;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.commn.model.FunctionalCentre;
import ca.openosp.openo.utility.EncounterUtil;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

public final class MisReportUIBean {

    private static Logger logger = MiscUtils.getLogger();

    private FunctionalCentreDao functionalCentreDao = (FunctionalCentreDao) SpringUtils.getBean(FunctionalCentreDao.class);
    private AdmissionDao admissionDao = (AdmissionDao) SpringUtils.getBean(AdmissionDao.class);
    private ProgramDao programDao = (ProgramDao) SpringUtils.getBean(ProgramDao.class);
    private DemographicDao demographicDao = (DemographicDao) SpringUtils.getBean(DemographicDao.class);
    private CaseManagementNoteDAO caseManagementNoteDAO = (CaseManagementNoteDAO) SpringUtils.getBean(CaseManagementNoteDAO.class);

    private static int ELDERLY_AGE = 65;

    public static class DataRow {
        public int dataReportId;
        public String dataReportDescription;
        public ArrayList<Integer> dataReportResult = new ArrayList<Integer>();

        private DataRow(int dataReportId, String dataReportDescription, int dataReportResult) {
            this.dataReportId = dataReportId;
            this.dataReportDescription = StringEscapeUtils.escapeHtml4(dataReportDescription);
            this.dataReportResult.add(dataReportResult);
        }
    }

    private String reportByDescription = null;
    private GregorianCalendar startDate = null;
    private GregorianCalendar endDate = null;
    private List<Program> selectedPrograms = null;
    private List<Admission> admissions = null;
    private ArrayList<DataRow> dataRows = null;
    private ArrayList<String> headerRow = new ArrayList<String>();

    {
        headerRow.add("Id");
        headerRow.add("Description");
        headerRow.add("Results");
    }

    /**
     * End dates should be treated as inclusive.
     */
    public MisReportUIBean(LoggedInInfo loggedInInfo, String functionalCentreId, GregorianCalendar startDate, GregorianCalendar endDate) {

        this.startDate = startDate;
        this.endDate = endDate;

        FunctionalCentre functionalCentre = functionalCentreDao.find(functionalCentreId);
        reportByDescription = StringEscapeUtils.escapeHtml4(functionalCentre.getAccountId() + ", " + functionalCentre.getDescription());
        selectedPrograms = programDao.getProgramsByFacilityIdAndFunctionalCentreId(loggedInInfo.getCurrentFacility().getId(), functionalCentreId);

        populateAdmissions();
        generateDataRows();
    }

    /**
     * End dates should be treated as inclusive.
     */
    public MisReportUIBean(String[] programIds, GregorianCalendar startDate, GregorianCalendar endDate) {

        this.startDate = startDate;
        this.endDate = endDate;

        selectedPrograms = new ArrayList<Program>();
        StringBuilder programNameList = new StringBuilder();
        for (String programIdString : programIds) {
            int programId = Integer.parseInt(programIdString);
            Program program = programDao.getProgram(programId);
            selectedPrograms.add(program);

            if (programNameList.length() > 0) programNameList.append(", ");
            programNameList.append(program.getName());
        }
        reportByDescription = StringEscapeUtils.escapeHtml4(programNameList.toString());

        populateAdmissions();
        generateDataRows();
    }


    public ArrayList<String> getHeaderRow() {
        return (headerRow);
    }

    public String getReportByDescription() {
        return (reportByDescription);
    }

    public static String getDateRangeForDisplay(GregorianCalendar startDate, GregorianCalendar endDate) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        return (StringEscapeUtils.escapeHtml4(simpleDateFormat.format(startDate.getTime()) + " to " + simpleDateFormat.format(endDate.getTime()) + " (inclusive)"));
    }

    private void populateAdmissions() {

        admissions = new ArrayList<Admission>();

        for (Program program : selectedPrograms) {
            List<Admission> programAdmissions = admissionDao.getAdmissionsByProgramAndDate(program.getId(), startDate.getTime(), endDate.getTime());

            logger.debug("corresponding mis admissions count:" + admissions.size());

            for (Admission admission : programAdmissions) {
                admissions.add(admission);
                logger.debug("valid mis admission, id=" + admission.getId());
            }
        }
    }

    public void generateDataRows() {
        dataRows = new ArrayList<DataRow>();


        dataRows.add(getFaceToFace());
        dataRows.add(getPhone());
        dataRows.add(getIndividualsSeen());
        dataRows.add(getTotalIndividualsServed());
    }

    public ArrayList<DataRow> getDataRows() {
        return (dataRows);
    }






    private DataRow getFaceToFace() {
        int visitsCount = 0;

        // count face to face notes in the system pertaining to the allowed programs during that time frame.
        for (Program program : selectedPrograms) {
            visitsCount = visitsCount + countFaceToFaceNotesInProgram(program);
        }

        return (new DataRow(4502440, "Visits Face to Face (must have client record)", visitsCount));
    }

    private int countFaceToFaceNotesInProgram(Program program) {
        List<CaseManagementNote> notes = caseManagementNoteDAO.getCaseManagementNoteByProgramIdAndObservationDate(program.getId(), startDate.getTime(), endDate.getTime());

        int count = 0;

        for (CaseManagementNote note : notes) {
            if (EncounterUtil.EncounterType.FACE_TO_FACE_WITH_CLIENT.getOldDbValue().equals(note.getEncounter_type())) {
                // as per request/requirements only count individuals with a client record.
                // I know, this doesn't entirely make sense as a note must have a client record...
                Demographic demographic = demographicDao.getDemographic(note.getDemographic_no());
                if (demographic != null) count++;
            }
        }

        return (count);
    }

    private DataRow getPhone() {
        int visitsCount = 0;

        // count phone notes in the system pertaining to the allowed programs during that time frame.
        for (Program program : selectedPrograms) {
            visitsCount = visitsCount + countPhoneNotesInProgram(program);
        }

        return (new DataRow(4512440, "Visits - Telephone (named and unique anonymous)", visitsCount));
    }

    private int countPhoneNotesInProgram(Program program) {
        List<CaseManagementNote> notes = caseManagementNoteDAO.getCaseManagementNoteByProgramIdAndObservationDate(program.getId(), startDate.getTime(), endDate.getTime());

        int count = 0;

        for (CaseManagementNote note : notes) {
            if (EncounterUtil.EncounterType.TELEPHONE_WITH_CLIENT.getOldDbValue().equals(note.getEncounter_type())) {
                // as per request/requirements only count individuals who are named or are unique anonymous
                Demographic demographic = demographicDao.getDemographic(note.getDemographic_no());
                if (demographic != null) {
                    if (demographic.getAnonymous() == null || Demographic.UNIQUE_ANONYMOUS.equals(demographic.getAnonymous()))
                        count++;
                }
            }
        }

        return (count);
    }

    private DataRow getIndividualsSeen() {
        HashSet<Integer> individuals = new HashSet<Integer>();

        // count face to face individuals in the system pertaining to the allowed programs during that time frame.
        for (Program program : selectedPrograms) {
            addIndividualsSeenInProgram(individuals, program);
        }

        return (new DataRow(4526000, "Service Recipients Seen, (anonymous or no client record, excluding unique anonymous)", individuals.size()));
    }

    private void addIndividualsSeenInProgram(HashSet<Integer> individuals, Program program) {
        List<CaseManagementNote> notes = caseManagementNoteDAO.getCaseManagementNoteByProgramIdAndObservationDate(program.getId(), startDate.getTime(), endDate.getTime());

        for (CaseManagementNote note : notes) {
            if (EncounterUtil.EncounterType.FACE_TO_FACE_WITH_CLIENT.getOldDbValue().equals(note.getEncounter_type())) {
                // as per request/requirements only count individuals who are anonymous or have no client record, excluding unique_anonymous
                // sorry can only count people who have demographic records.
                Demographic demographic = demographicDao.getDemographic(note.getDemographic_no());
                if (demographic != null && Demographic.ANONYMOUS.equals(demographic.getAnonymous()))
                    individuals.add(demographic.getDemographicNo());
            }
        }
    }

    private DataRow getTotalIndividualsServed() {
        HashSet<Integer> individuals = new HashSet<Integer>();

        // count individuals in the system pertaining to the allowed programs during that time frame.
        for (Program program : selectedPrograms) {
            addIndividualsServedInProgram(individuals, program);
        }

        return (new DataRow(4552440, "Individuals Served, (total clients)", individuals.size()));
    }

    private void addIndividualsServedInProgram(HashSet<Integer> individuals, Program program) {
        List<CaseManagementNote> notes = caseManagementNoteDAO.getCaseManagementNoteByProgramIdAndObservationDate(program.getId(), startDate.getTime(), endDate.getTime());

        for (CaseManagementNote note : notes) {
            Demographic demographic = demographicDao.getDemographic(note.getDemographic_no());
            if (demographic != null) individuals.add(demographic.getDemographicNo());
        }
    }

    public static MisReportUIBean getSplitProgramReports(String[] programIds, GregorianCalendar startDate, GregorianCalendar endDate) {
        ArrayList<MisReportUIBean> misReportBeans = new ArrayList<MisReportUIBean>();
        StringBuilder description = new StringBuilder();
        MisReportUIBean tempMisReportBean = null;

        ArrayList<String> headerRow = new ArrayList<String>();
        headerRow.add("Id");
        headerRow.add("Description");

        for (String programIdString : programIds) {
            tempMisReportBean = new MisReportUIBean(new String[]{programIdString}, startDate, endDate);

            if (description.length() > 0) description.append(", ");
            description.append(tempMisReportBean.getReportByDescription());

            headerRow.add(tempMisReportBean.getReportByDescription());

            misReportBeans.add(tempMisReportBean);
        }

        tempMisReportBean.reportByDescription = description.toString();
        tempMisReportBean.dataRows = combineDataSet(misReportBeans);
        tempMisReportBean.headerRow = headerRow;
        return (tempMisReportBean);
    }

    private static ArrayList<DataRow> combineDataSet(ArrayList<MisReportUIBean> misReportBeans) {
        ArrayList<DataRow> combinedData = new ArrayList<DataRow>();

        combineDataIds(combinedData, misReportBeans);
        combineDataResults(combinedData, misReportBeans);

        return (combinedData);
    }

    private static void combineDataIds(ArrayList<DataRow> combinedData, ArrayList<MisReportUIBean> misReportBeans) {
        for (MisReportUIBean misReportUIBean : misReportBeans) combineDataIds(combinedData, misReportUIBean);
    }

    private static void combineDataIds(ArrayList<DataRow> combinedData, MisReportUIBean misReportUIBean) {
        for (DataRow beanRow : misReportUIBean.getDataRows()) {
            DataRow dataRow = getDataRowFromId(combinedData, beanRow.dataReportId);
            if (dataRow == null) combinedData.add(new DataRow(beanRow.dataReportId, beanRow.dataReportDescription, 0));
        }
    }

    private static DataRow getDataRowFromId(ArrayList<DataRow> combinedData, int dataReportId) {
        for (DataRow dataRow : combinedData) {
            if (dataRow.dataReportId == dataReportId) return (dataRow);
        }

        return (null);
    }

    private static void combineDataResults(ArrayList<DataRow> combinedData, ArrayList<MisReportUIBean> misReportBeans) {
        for (DataRow dataRow : combinedData) {
            combineDataResultsFromBeans(dataRow, misReportBeans);
        }
    }

    private static void combineDataResultsFromBeans(DataRow combinedDataRow, ArrayList<MisReportUIBean> misReportBeans) {
        ArrayList<Integer> tempResults = new ArrayList<Integer>();

        for (MisReportUIBean reportBean : misReportBeans) {
            tempResults.add(getDataResultsFromArrayRows(reportBean.getDataRows(), combinedDataRow.dataReportId));
        }

        combinedDataRow.dataReportResult = tempResults;
    }

    private static Integer getDataResultsFromArrayRows(ArrayList<DataRow> dataRows, int dataReportId) {
        DataRow tempDataRow = getDataRowFromId(dataRows, dataReportId);
        if (tempDataRow == null) return (null);
        else return (tempDataRow.dataReportResult.get(0));

    }

}
