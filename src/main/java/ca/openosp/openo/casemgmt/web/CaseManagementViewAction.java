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

package ca.openosp.openo.casemgmt.web;

import ca.openosp.openo.util.UtilDateUtilities;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.PMmodule.dao.SecUserRoleDao;
import ca.openosp.openo.casemgmt.common.Colour;
import ca.openosp.openo.casemgmt.dao.CaseManagementNoteDAO;
import ca.openosp.openo.casemgmt.dao.IssueDAO;
import ca.openosp.openo.casemgmt.model.CaseManagementIssue;
import ca.openosp.openo.casemgmt.model.CaseManagementNote;
import ca.openosp.openo.casemgmt.model.CaseManagementNoteExt;
import ca.openosp.openo.casemgmt.model.Issue;
import ca.openosp.openo.casemgmt.service.CaseManagementManager;
import ca.openosp.openo.casemgmt.service.NoteService;
import ca.openosp.openo.commn.dao.BillingONCHeader1Dao;
import ca.openosp.openo.commn.dao.CaseManagementIssueNotesDao;
import ca.openosp.openo.commn.dao.DemographicDao;
import ca.openosp.openo.commn.dao.GroupNoteDao;
import ca.openosp.openo.managers.TicklerManager;
import ca.openosp.openo.provider.web.CppPreferencesUIBean;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.OscarProperties;
import ca.openosp.openo.util.OscarRoleObjectPrivilege;

import java.net.MalformedURLException;
import java.util.*;

/*
 * Updated by Eugene Petruhin on 21 jan 2009 while fixing missing "New Note" link
 */
public class CaseManagementViewAction {

    private static final Integer MAX_INVOICES = 20;
    private static Logger logger = MiscUtils.getLogger();
    private CaseManagementManager caseManagementManager = (CaseManagementManager) SpringUtils.getBean(CaseManagementManager.class);
    private IssueDAO issueDao = (IssueDAO) SpringUtils.getBean(IssueDAO.class);
    private CaseManagementNoteDAO caseManagementNoteDao = (CaseManagementNoteDAO) SpringUtils.getBean(CaseManagementNoteDAO.class);
    private SecUserRoleDao secUserRoleDao = (SecUserRoleDao) SpringUtils.getBean(SecUserRoleDao.class);
    private GroupNoteDao groupNoteDao = (GroupNoteDao) SpringUtils.getBean(GroupNoteDao.class);
    private DemographicDao demographicDao = (DemographicDao) SpringUtils.getBean(DemographicDao.class);
    private CaseManagementIssueNotesDao cmeIssueNotesDao = (CaseManagementIssueNotesDao) SpringUtils.getBean(CaseManagementIssueNotesDao.class);
    private BillingONCHeader1Dao billingONCHeader1Dao = (BillingONCHeader1Dao) SpringUtils.getBean(BillingONCHeader1Dao.class);
    private NoteService noteService = SpringUtils.getBean(NoteService.class);
    private TicklerManager ticklerManager = SpringUtils.getBean(TicklerManager.class);


    public static class IssueDisplay {
        public boolean writeAccess = true;
        public String codeType = null;
        public String code = null;
        public String description = null;
        public String location = null;
        public String acute = null;
        public String certain = null;
        public String major = null;
        public String resolved = null;
        public String role = null;
        public String priority = null;
        public Integer sortOrderId = null;

        public Integer getSortOrderId() {
            return sortOrderId;
        }

        public void setSortOrderId(Integer sortOrderId) {
            this.sortOrderId = sortOrderId;
        }

        public String getCodeType() {
            return codeType;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }

        public String getLocation() {
            return location;
        }

        public String getAcute() {
            return acute;
        }

        public String getCertain() {
            return certain;
        }

        public String getMajor() {
            return major;
        }

        public String getResolved() {
            return resolved;
        }

        public String getRole() {
            return role;
        }

        public String getPriority() {
            return priority;
        }

        public boolean isWriteAccess() {
            return writeAccess;
        }

        public void setWriteAccess(boolean writeAccess) {
            this.writeAccess = writeAccess;
        }

        public void setCodeType(String codeType) {
            this.codeType = codeType;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        public void setAcute(String acute) {
            this.acute = acute;
        }

        public void setCertain(String certain) {
            this.certain = certain;
        }

        public void setMajor(String major) {
            this.major = major;
        }

        public void setResolved(String resolved) {
            this.resolved = resolved;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public void setPriority(String priority) {
            this.priority = priority;
        }

        public String toString() {
            return (ReflectionToStringBuilder.toString(this));
        }
    }


    public void sortIssuesByOrderId(ArrayList<CheckBoxBean> checkBoxBeanList) {
        Comparator<CheckBoxBean> cbbComparator = new Comparator<CheckBoxBean>() {
            public int compare(CheckBoxBean o1, CheckBoxBean o2) {
                if (o1.getIssueDisplay() != null && o2.getIssueDisplay() != null && o1.getIssueDisplay().sortOrderId != null && o2.getIssueDisplay().sortOrderId != null) {
                    return (o1.getIssueDisplay().sortOrderId.compareTo(o2.getIssueDisplay().sortOrderId));
                } else return (0);
            }
        };

        Collections.sort(checkBoxBeanList, cbbComparator);
    }







    public static boolean hasSameAttributes(IssueDisplay issueDisplay1, IssueDisplay issueDisplay2) {
        if (issueDisplay1.code != null && !issueDisplay1.code.equals(issueDisplay2.code)) return (false);
        if (issueDisplay1.acute != null && !issueDisplay1.acute.equals(issueDisplay2.acute)) return (false);
        if (issueDisplay1.certain != null && !issueDisplay1.certain.equals(issueDisplay2.certain)) return (false);
        if (issueDisplay1.major != null && !issueDisplay1.major.equals(issueDisplay2.major)) return (false);
        if (issueDisplay1.priority != null && !issueDisplay1.priority.equals(issueDisplay2.priority)) return (false);
        if (issueDisplay1.resolved != null && !issueDisplay1.resolved.equals(issueDisplay2.resolved)) return (false);

        return (true);
    }


    protected void addLocalIssues(String providerNo, ArrayList<CheckBoxBean> checkBoxBeanList, Integer demographicNo, boolean hideInactiveIssues, Integer programId) {
        List<CaseManagementIssue> localIssues = caseManagementManager.getIssues(demographicNo, hideInactiveIssues ? false : null);

        for (CaseManagementIssue cmi : localIssues) {
            CheckBoxBean checkBoxBean = new CheckBoxBean();

            checkBoxBean.setIssue(cmi);

            IssueDisplay issueDisplay = getIssueDisplay(providerNo, programId, cmi);
            checkBoxBean.setIssueDisplay(issueDisplay);

            checkBoxBean.setUsed(caseManagementNoteDao.haveIssue(cmi.getIssue().getCode(), demographicNo));

            checkBoxBeanList.add(checkBoxBean);
        }
    }

    protected IssueDisplay getIssueDisplay(String providerNo, Integer programId, CaseManagementIssue cmi) {
        IssueDisplay issueDisplay = new IssueDisplay();

        if (programId != null) issueDisplay.writeAccess = cmi.isWriteAccess(providerNo, programId);

        issueDisplay.acute = cmi.isAcute() ? "acute" : "chronic";
        issueDisplay.certain = cmi.isCertain() ? "certain" : "uncertain";

        long issueId = cmi.getIssue_id();
        Issue issue = issueDao.getIssue(issueId);

        issueDisplay.code = issue.getCode();
        issueDisplay.codeType = OscarProperties.getInstance().getProperty("COMMUNITY_ISSUE_CODETYPE").toUpperCase();
        issueDisplay.description = issue.getDescription();
        issueDisplay.location = "local";
        issueDisplay.major = cmi.isMajor() ? "major" : "not major";
        issueDisplay.priority = issue.getPriority();
        issueDisplay.resolved = cmi.isResolved() ? "resolved" : "unresolved";
        issueDisplay.role = issue.getRole();
        issueDisplay.sortOrderId = issue.getSortOrderId();

        return issueDisplay;
    }


    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean hasPrivilege(String objectName, String roleName) {
        Vector v = OscarRoleObjectPrivilege.getPrivilegeProp(objectName);
        return OscarRoleObjectPrivilege.checkPrivilege(roleName, (Properties) v.get(0), (Vector) v.get(1));
    }

    public static String getNoteColour(NoteDisplay noteDisplay) {
        // set all colors
        String blackColour = "FFFFFF";
        String documentColour = "color:#" + blackColour + ";background-color:#" + Colour.getInstance().documents + ";";
        //String diseaseColour = "color:#" + blackColour + ";background-color:#" + Colour.getInstance().disease + ";";
        String eFormsColour = "color:#" + blackColour + ";background-color:#" + Colour.getInstance().eForms + ";";
        String formsColour = "color:#" + blackColour + ";background-color:#" + Colour.getInstance().forms + ";";
        //String labsColour = "color:#" + blackColour + ";background-color:#" + Colour.getInstance().labs + ";";
        //String measurementsColour = "color:#" + blackColour + ";background-color:#" + Colour.getInstance().measurements + ";";
        //String messagesColour = "color:#" + blackColour + ";background-color:#" + Colour.getInstance().messages + ";";
        //String preventionColour = "color:#" + blackColour + ";background-color:#" + Colour.getInstance().prevention + ";";
        //String ticklerColour = "color:#" + blackColour + ";background-color:#" + Colour.getInstance().tickler + ";";
        String rxColour = "color:#" + blackColour + ";background-color:#" + Colour.getInstance().rx + ";";
        String invoiceColour = "color:#" + blackColour + ";background-color:#" + Colour.getInstance().invoices + ";";
        String ticklerNoteColour = "color:#" + blackColour + ";background-color:#" + Colour.getInstance().ticklerNotes + ";";
        String externalNoteColour = "color:#" + blackColour + ";background-color:#" + Colour.getInstance().externalNotes + ";";
        String emailNoteColour = "color:#" + blackColour + ";background-color:#" + Colour.getInstance().emailNotes + ";";

        String bgColour = "color:#000000;background-color:#CCCCFF;";

        if (noteDisplay.isCpp()) {
            bgColour = "color:#FFFFFF;background-color:#" + getCppColour(noteDisplay) + ";";
            if (noteDisplay.isTicklerNote()) {
                bgColour = ticklerNoteColour;
            } else if (noteDisplay.isExternalNote()) {
                bgColour = externalNoteColour;
            }
        } else if (noteDisplay.isDocument()) {
            bgColour = documentColour;
        } else if (noteDisplay.isRxAnnotation()) {
            bgColour = rxColour;
        } else if (noteDisplay.isEformData()) {
            bgColour = eFormsColour;
        } else if (noteDisplay.isEncounterForm()) {
            bgColour = formsColour;
        } else if (noteDisplay.isInvoice()) {
            bgColour = invoiceColour;
        } else if (noteDisplay.isEmailNote()) {
            bgColour = emailNoteColour;
        }

        return (bgColour);
    }

    private static String getCppColour(NoteDisplay noteDisplay) {
        Colour colour = Colour.getInstance();

        if (noteDisplay.containsIssue("OMeds")) return (colour.omed);
        else if (noteDisplay.containsIssue("FamHistory")) return (colour.familyHistory);
        else if (noteDisplay.containsIssue("RiskFactors")) return (colour.riskFactors);
        else if (noteDisplay.containsIssue("SocHistory")) return (colour.socialHistory);
        else if (noteDisplay.containsIssue("MedHistory")) return (colour.medicalHistory);
        else if (noteDisplay.containsIssue("Concerns")) return (colour.ongoingConcerns);
        else if (noteDisplay.containsIssue("Reminders")) return (colour.reminders);
        else return colour.prevention;

    }

    public static CaseManagementNote getLatestCppNote(String demographicNo, long issueId, int appointmentNo, boolean filterByAppointment) {
        CaseManagementManager caseManagementMgr = (CaseManagementManager) SpringUtils.getBean(CaseManagementManager.class);
        Collection<CaseManagementNote> notes = caseManagementMgr.getActiveNotes(demographicNo, new String[]{String.valueOf(issueId)});
        List<CaseManagementNote> filteredNotes = new ArrayList<CaseManagementNote>();

        if (notes.size() == 0) {
            return null;
        }
        if (filterByAppointment) {
            for (CaseManagementNote note : notes) {
                if (note.getAppointmentNo() == appointmentNo) {
                    filteredNotes.add(note);
                }
            }
            if (filteredNotes.size() == 0) {
                return null;
            }
        } else {
            filteredNotes.addAll(notes);
        }
        return filteredNotes.iterator().next();
    }

    public static String getCppAdditionalData(Long noteId, String issueCode, List<CaseManagementNoteExt> noteExts, CppPreferencesUIBean prefsBean) {
        if (prefsBean.getEnable() == null || !prefsBean.getEnable().equals("on")) {
            return new String();
        }
        String issueCodeArr[] = issueCode.split(";");
        StringBuilder sb = new StringBuilder();
        if (issueCodeArr[1].equals("SocHistory")) {
            if (prefsBean.getSocialHxStartDate().equals("on")) {
                sb.append("Start Date:" + getNoteExt(noteId, "Start Date", noteExts));
            }
            if (prefsBean.getSocialHxResDate().equals("on")) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append("Resolution Date:" + getNoteExt(noteId, "Resolution Date", noteExts));
            }
        }
        if (issueCodeArr[1].equals("Reminders")) {
            if (prefsBean.getRemindersStartDate().equals("on")) {
                sb.append("Start Date:" + getNoteExt(noteId, "Start Date", noteExts));
            }
            if (prefsBean.getRemindersResDate().equals("on")) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append("Resolution Date:" + getNoteExt(noteId, "Resolution Date", noteExts));
            }
        }
        if (issueCodeArr[1].equals("Concerns")) {
            if (prefsBean.getOngoingConcernsStartDate().equals("on")) {
                sb.append("Start Date:" + getNoteExt(noteId, "Start Date", noteExts));
            }
            if (prefsBean.getOngoingConcernsResDate().equals("on")) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append("Resolution Date:" + getNoteExt(noteId, "Resolution Date", noteExts));
            }
            if (prefsBean.getOngoingConcernsProblemStatus().equals("on")) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append("Status:" + getNoteExt(noteId, "Problem Status", noteExts));
            }
        }
        if (issueCodeArr[1].equals("MedHistory")) {
            if (prefsBean.getMedHxStartDate().equals("on")) {
                sb.append("Start Date:" + getNoteExt(noteId, "Start Date", noteExts));
            }
            if (prefsBean.getMedHxResDate().equals("on")) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append("Resolution Date:" + getNoteExt(noteId, "Resolution Date", noteExts));
            }
            if (prefsBean.getMedHxProcedureDate().equals("on")) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append("Procedure Date:" + getNoteExt(noteId, "Procedure Date", noteExts));
            }
            if (prefsBean.getMedHxTreatment().equals("on")) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append("Treatment:" + getNoteExt(noteId, "Treatment", noteExts));
            }
        }

        if (issueCodeArr[1].equals("RiskFactors")) {
            if (prefsBean.getRiskFactorsStartDate().equals("on")) {
                sb.append("Start Date:" + getNoteExt(noteId, "Start Date", noteExts));
            }
            if (prefsBean.getRiskFactorsResDate().equals("on")) {
                sb.append("Resolution Date:" + getNoteExt(noteId, "Resolution Date", noteExts));
            }
        }

        if (issueCodeArr[1].equals("OMeds")) {
            if (prefsBean.getOtherMedsStartDate().equals("on")) {
                sb.append("Start Date:" + getNoteExt(noteId, "Start Date", noteExts));
            }
            if (prefsBean.getOtherMedsResDate().equals("on")) {
                sb.append("Resolution Date:" + getNoteExt(noteId, "Resolution Date", noteExts));
            }
        }

        if (issueCodeArr[1].equals("FamHistory")) {
            if (prefsBean.getFamilyHistoryStartDate().equals("on")) {
                sb.append("Start Date:" + getNoteExt(noteId, "Start Date", noteExts));
            }
            if (prefsBean.getFamilyHistoryResDate().equals("on")) {
                sb.append("Resolution Date:" + getNoteExt(noteId, "Resolution Date", noteExts));
            }
            if (prefsBean.getFamilyHistoryTreatment().equals("on")) {
                sb.append("Treatment:" + getNoteExt(noteId, "Treatment", noteExts));
            }
            if (prefsBean.getFamilyHistoryRelationship().equals("on")) {
                sb.append("Relationship:" + getNoteExt(noteId, "Relationship", noteExts));
            }
        }


        if (sb.length() > 0) {
            sb.insert(0, " (");
            sb.append(")");
        }
        return sb.toString();
    }

    static String getNoteExt(Long noteId, String key, List<CaseManagementNoteExt> lcme) {
        for (CaseManagementNoteExt cme : lcme) {
            if (cme.getNoteId().equals(noteId) && cme.getKeyVal().equals(key)) {
                String val = null;

                if (key.contains(" Date")) {
                    val = UtilDateUtilities.DateToString(cme.getDateValue(), "yyyy-MM-dd");
                } else {
                    val = StringEscapeUtils.escapeEcmaScript(cme.getValue());
                }
                return val;
            }
        }
        return "";
    }
}
