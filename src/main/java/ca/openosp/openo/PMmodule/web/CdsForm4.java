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

package ca.openosp.openo.PMmodule.web;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import ca.openosp.openo.PMmodule.dao.ProgramDao;
import ca.openosp.openo.PMmodule.model.Program;
import ca.openosp.openo.commn.Gender;
import ca.openosp.openo.commn.dao.AdmissionDao;
import ca.openosp.openo.commn.dao.CdsClientFormDao;
import ca.openosp.openo.commn.dao.CdsClientFormDataDao;
import ca.openosp.openo.commn.dao.CdsFormOptionDao;
import ca.openosp.openo.commn.dao.CdsHospitalisationDaysDao;
import ca.openosp.openo.commn.dao.DemographicDao;
import ca.openosp.openo.commn.model.Admission;
import ca.openosp.openo.commn.model.CdsClientForm;
import ca.openosp.openo.commn.model.CdsClientFormData;
import ca.openosp.openo.commn.model.CdsFormOption;
import ca.openosp.openo.commn.model.CdsHospitalisationDays;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.utility.SpringUtils;

public class CdsForm4 {

    private static DemographicDao demographicDao = (DemographicDao) SpringUtils.getBean(DemographicDao.class);
    private static AdmissionDao admissionDao = (AdmissionDao) SpringUtils.getBean(AdmissionDao.class);
    private static CdsFormOptionDao cdsFormOptionDao = (CdsFormOptionDao) SpringUtils.getBean(CdsFormOptionDao.class);
    private static CdsClientFormDao cdsClientFormDao = (CdsClientFormDao) SpringUtils.getBean(CdsClientFormDao.class);
    private static CdsClientFormDataDao cdsClientFormDataDao = (CdsClientFormDataDao) SpringUtils.getBean(CdsClientFormDataDao.class);
    private static CdsHospitalisationDaysDao cdsHospitalisationDaysDao = (CdsHospitalisationDaysDao) SpringUtils.getBean(CdsHospitalisationDaysDao.class);
    private static ProgramDao programDao = (ProgramDao) SpringUtils.getBean(ProgramDao.class);

    private static final int MAX_DISPLAY_NAME_LENGTH = 60;

    public static CdsClientForm getCdsClientFormByClientId(Integer facilityId, Integer clientId) {
        List<Admission> admissions = admissionDao.getCurrentAdmissionsByFacility(clientId, facilityId);

        Admission admission = null;
        // find service program
        for (Admission temp : admissions) {
            Program program = programDao.getProgram(temp.getProgramId());
            if (program.isService()) {
                admission = temp;
                break;
            }
        }

        // find bed program
        if (admission == null) {
            for (Admission temp : admissions) {
                Program program = programDao.getProgram(temp.getProgramId());
                if (program.isService()) {
                    admission = temp;
                    break;
                }
            }
        }

        if (admission == null) {
            admissions = admissionDao.getAdmissionsByFacility(clientId, facilityId);
            if (admissions.size() > 0) admission = admissions.get(0);
        }

        if (admission != null) {
            // at this point we're in a program, try to find existing cds form.
            List<CdsClientForm> cdsClientForms = cdsClientFormDao.findByFacilityClient(facilityId, clientId);

            for (CdsClientForm cdsClientForm : cdsClientForms) {
                if (cdsClientForm.getAdmissionId() == null) continue;

                if (admission.getId().intValue() == cdsClientForm.getAdmissionId().intValue()) {
                    return (cdsClientForm);
                }
            }
        }

        CdsClientForm newForm = new CdsClientForm();
        if (admission != null) newForm.setAdmissionId(admission.getId().intValue());

        //A new CDS form should populate date of initial contact and date of assessment/interview
        //from CBI form's referral date and admission date
        // CBI form references removed - functionality no longer available

        return (newForm);
    }

    public static CdsClientForm getCdsClientFormByCdsFormId(Integer cdsFormId) {
        CdsClientForm cdsClientForm = cdsClientFormDao.find(cdsFormId);
        return (cdsClientForm);
    }

    public static List<Admission> getAdmissions(Integer facilityId, Integer clientId) {
        return (admissionDao.getAdmissionsByFacility(clientId, facilityId));
    }

    public static String getEscapedClientName(Integer clientId) {
        Demographic demographic = demographicDao.getDemographicById(clientId);
        if (demographic != null) {
            return (StringEscapeUtils.escapeHtml4(demographic.getLastName() + ", " + demographic.getFirstName()));
        } else {
            return (null);
        }
    }

    public static String getFormattedClientBirthDay(Integer clientId) {
        Demographic demographic = demographicDao.getDemographicById(clientId);
        if (demographic != null && demographic.getBirthDay() != null) {
            return (DateFormatUtils.ISO_DATE_FORMAT.format(demographic.getBirthDay()));
        } else {
            return (null);
        }
    }

    public static String getClientGenderAsCdsOption(Integer clientId) {
        Demographic demographic = demographicDao.getDemographicById(clientId);
        if (demographic != null && demographic.getSex() != null) {
            String gender = demographic.getSex();
            if (Gender.F.toString().equals(gender)) return ("008-02");
            else if (Gender.M.toString().equals(gender)) return ("008-01");
//			else if (Gender.O.toString().equals(gender) || Gender.T.toString().equals(gender)) return("008-03");
            else return ("008-04");
        } else {
            return (null);
        }
    }

    public static String getEscapedAdmissionSelectionDisplay(Integer admissionId) {
        Admission admission = admissionDao.getAdmission(admissionId);
        return (getEscapedAdmissionSelectionDisplay(admission));
    }

    public static String getEscapedAdmissionSelectionDisplay(Admission admission) {
        StringBuilder sb = new StringBuilder();

        sb.append(admission.getProgramName());
        sb.append(" ( ");
        sb.append(DateFormatUtils.ISO_DATE_FORMAT.format(admission.getAdmissionDate()));
        sb.append(" - ");
        if (admission.getDischargeDate() == null) sb.append("current");
        else sb.append(DateFormatUtils.ISO_DATE_FORMAT.format(admission.getDischargeDate()));
        sb.append(" )");

        return (StringEscapeUtils.escapeHtml4(sb.toString()));
    }

    public static List<CdsFormOption> getCdsFormOptions(String category) {
        List<CdsFormOption> results = cdsFormOptionDao.findByVersionAndCategory("4", category);
        return (results);
    }

    public static String renderSelectQuestion(boolean multiple, boolean dropDown, boolean forPrint, Integer cdsClientFormId, String question, List<CdsFormOption> options) {
        if (!forPrint) {
            StringBuilder sb = new StringBuilder();
            sb.append("<select " + (multiple ? "multiple=\"multiple\" " : "") + "name=\"" + question + "\" " + (!dropDown ? "style=\"height:8em\"" : "") + ">");
            sb.append(renderAsSelectOptions(cdsClientFormId, question, options));
            sb.append("</select>");

            return (sb.toString());
        } else {
            StringBuilder sb = new StringBuilder();

            List<CdsClientFormData> existingAnswers = getAnswers(cdsClientFormId, question);
            for (CdsClientFormData answer : existingAnswers) {
                CdsFormOption option = getOptionFromAnswerId(options, answer.getAnswer());
                if (option != null) {
                    if (sb.length() != 0) sb.append("<br />");
                    sb.append(StringEscapeUtils.escapeHtml4(option.getCdsDataCategoryName()));
                }
            }

            return (sb.toString());
        }
    }

    private static CdsFormOption getOptionFromAnswerId(List<CdsFormOption> options, String cdsDataCategory) {
        for (CdsFormOption option : options) {
            if (option.getCdsDataCategory().equals(cdsDataCategory)) return (option);
        }

        return (null);
    }

    /**
     * This method is meant to return a bunch of html <option> tags for each list element.
     */
    private static String renderAsSelectOptions(Integer cdsClientFormId, String question, List<CdsFormOption> options) {
        List<CdsClientFormData> existingAnswers = getAnswers(cdsClientFormId, question);

        StringBuilder sb = new StringBuilder();

        sb.append("<option value=\"\" title=\"\">--- no selection ---</option>");

        for (CdsFormOption option : options) {
            String htmlEscapedName = StringEscapeUtils.escapeHtml4(option.getCdsDataCategoryName());
            String lengthLimitedEscapedName = limitLengthAndEscape(option.getCdsDataCategoryName());
            String selected = (CdsClientFormData.containsAnswer(existingAnswers, option.getCdsDataCategory()) ? "selected=\"selected\"" : "");

            sb.append("<option " + selected + " value=\"" + StringEscapeUtils.escapeHtml4(option.getCdsDataCategory()) + "\" title=\"" + htmlEscapedName + "\">" + lengthLimitedEscapedName + "</option>");
        }

        return (sb.toString());
    }

    /**
     * This method is meant to return a bunch of html <option> tags for each list element.
     */
    public static String renderNumbersAsSelectOptions(Integer cdsClientFormId, String question, int maxNumber) {
        List<CdsClientFormData> existingAnswers = getAnswers(cdsClientFormId, question);

        StringBuilder sb = new StringBuilder();

        for (int i = -1; i < maxNumber; i++) {
            String value = String.valueOf(i);
            String label = value;

            if (i == -1) {
                value = "";
                label = "Unknown / refused to answer";
            }

            if (i == 0) {
                label = "none";
            }

            String selected = (CdsClientFormData.containsAnswer(existingAnswers, value) ? "selected=\"selected\"" : "");

            sb.append("<option " + selected + " value=\"" + value + "\" title=\"" + label + "\">" + label + "</option>");
        }

        return (sb.toString());
    }

    /**
     * This method is meant to return a bunch of html <input type="radio"> tags for each list element on a separate line (br delimited).
     */
    public static String renderAsRadioOptions(Integer cdsClientFormId, String question, List<CdsFormOption> options, String defaultSelected) {
        List<CdsClientFormData> existingAnswers = getAnswers(cdsClientFormId, question);

        StringBuilder sb = new StringBuilder();
        boolean alreadyHaveOneChecked = false;
        for (CdsFormOption option : options) {
            String htmlEscapedName = StringEscapeUtils.escapeHtml4(option.getCdsDataCategoryName());
            String lengthLimitedEscapedName = limitLengthAndEscape(option.getCdsDataCategoryName());

            String selected = "";
            if (CdsClientFormData.containsAnswer(existingAnswers, option.getCdsDataCategory())) {
                selected = "checked=\"checked\"";
                alreadyHaveOneChecked = true;
            } else if (!alreadyHaveOneChecked && option.getCdsDataCategory().equals(defaultSelected)) {
                selected = "checked=\"checked\"";
            }

            sb.append("<div title=\"" + htmlEscapedName + "\"><input type=\"radio\" " + selected + " name=\"" + question + "\" value=\"" + StringEscapeUtils.escapeHtml4(option.getCdsDataCategory()) + "\" /> " + lengthLimitedEscapedName + "</div>");
        }

        return (sb.toString());
    }

    /**
     * This method is meant to return a bunch of html <input type="checkbox"> tags for each list element on a separate line (br delimited).
     */
    public static String renderAsCheckBoxOptions(Integer cdsClientFormId, String question, List<CdsFormOption> options) {
        List<CdsClientFormData> existingAnswers = getAnswers(cdsClientFormId, question);

        StringBuilder sb = new StringBuilder();

        for (CdsFormOption option : options) {
            String htmlEscapedName = StringEscapeUtils.escapeHtml4(option.getCdsDataCategoryName());
            String lengthLimitedEscapedName = limitLengthAndEscape(option.getCdsDataCategoryName());
            String checked = (CdsClientFormData.containsAnswer(existingAnswers, option.getCdsDataCategory()) ? "checked=\"checked\"" : "");

            sb.append("<div title=\"" + htmlEscapedName + "\"><input type=\"checkBox\" " + checked + " name=\"" + question + "\" value=\"" + StringEscapeUtils.escapeHtml4(option.getCdsDataCategory()) + "\" /> " + lengthLimitedEscapedName + "</div>");
        }

        return (sb.toString());
    }

    public static String limitLengthAndEscape(String s) {
        if (s.length() > MAX_DISPLAY_NAME_LENGTH) s = s.substring(0, MAX_DISPLAY_NAME_LENGTH - 3) + "...";
        return (StringEscapeUtils.escapeHtml4(s));
    }

    private static List<CdsClientFormData> getAnswers(Integer cdsClientFormId, String question) {
        if (cdsClientFormId == null) return (new ArrayList<CdsClientFormData>());

        return (cdsClientFormDataDao.findByQuestion(cdsClientFormId, question));
    }

    public static List<CdsHospitalisationDays> getHospitalisationDays(Integer clientId) {
        List<CdsHospitalisationDays> results = cdsHospitalisationDaysDao.findByClientId(clientId);
        Collections.sort(results, CdsHospitalisationDays.ADMISSION_DATE_COMPARATOR);

        return (results);
    }

    public static void addHospitalisationDay(Integer clientId, Calendar admissionDate, Calendar dischargeDate) {
        CdsHospitalisationDays cdsHospitalisationDays = new CdsHospitalisationDays();
        cdsHospitalisationDays.setClientId(clientId);
        cdsHospitalisationDays.setAdmitted(admissionDate);
        cdsHospitalisationDays.setDischarged(dischargeDate);
        cdsHospitalisationDaysDao.persist(cdsHospitalisationDays);
    }

    public static void deleteHospitalisationDay(Integer hospitalisationDayId) {
        cdsHospitalisationDaysDao.remove(hospitalisationDayId);
    }

    public static String getDateAsISOString(Date date) {
        if (date == null) return ("");

        return (DateFormatUtils.ISO_DATE_FORMAT.format(date));
    }

    public static String getSingleAnswer(Integer cdsClientFormId, String question) {
        List<CdsClientFormData> answers = getAnswers(cdsClientFormId, question);
        String result = null;

        if (answers.size() > 0) result = answers.get(0).getAnswer();

        return (result);
    }
}
