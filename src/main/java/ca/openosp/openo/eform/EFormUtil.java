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

package ca.openosp.openo.eform;

import ca.openosp.Misc;
import ca.openosp.openo.commn.dao.*;
import ca.openosp.openo.commn.model.*;
import ca.openosp.openo.model.security.Secobjprivilege;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.PMmodule.dao.ProviderDao;
import ca.openosp.openo.PMmodule.model.ProgramProvider;
import ca.openosp.openo.casemgmt.dao.CaseManagementNoteLinkDAO;
import ca.openosp.openo.casemgmt.model.CaseManagementIssue;
import ca.openosp.openo.casemgmt.model.CaseManagementNote;
import ca.openosp.openo.casemgmt.model.CaseManagementNoteLink;
import ca.openosp.openo.casemgmt.model.Issue;
import ca.openosp.openo.casemgmt.service.CaseManagementManager;
import ca.openosp.openo.commn.dao.EFormDao.EFormSortOrder;
import ca.openosp.openo.documentManager.EDoc;
import ca.openosp.openo.documentManager.EDocUtil;
import ca.openosp.openo.managers.MessagingManager;
import ca.openosp.openo.managers.PreventionManager;
import ca.openosp.openo.managers.ProgramManager2;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.OscarProperties;
import ca.openosp.openo.eform.data.EForm;
import ca.openosp.openo.eform.data.EFormBase;
import ca.openosp.openo.clinic.ClinicData;
import ca.openosp.openo.db.DBHandler;
import ca.openosp.openo.messenger.data.MessengerSystemMessage;
import ca.openosp.openo.util.ConversionUtils;
import ca.openosp.openo.util.OscarRoleObjectPrivilege;
import ca.openosp.openo.util.UtilDateUtilities;

import javax.persistence.PersistenceException;
import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EFormUtil {
    private static final Logger logger = MiscUtils.getLogger();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // for sorting....
    public static final String NAME = "form_name";
    public static final String SUBJECT = "subject";
    public static final String DATE = "form_date DESC, form_time DESC";
    public static final String FILE_NAME = "file_name";
    public static final String PROVIDER = "form_provider";
    // -----------
    public static final String DELETED = "deleted";
    public static final String CURRENT = "current";
    public static final String ALL = "all";

    private static final CaseManagementManager cmm = SpringUtils.getBean(CaseManagementManager.class);
    private static final CaseManagementNoteLinkDAO cmDao = SpringUtils.getBean(CaseManagementNoteLinkDAO.class);
    private static final EFormDataDao eFormDataDao = SpringUtils.getBean(EFormDataDao.class);
    private static final EFormValueDao eFormValueDao = SpringUtils.getBean(EFormValueDao.class);
    private static final EFormGroupDao eFormGroupDao = SpringUtils.getBean(EFormGroupDao.class);
    private static final ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
    private static final TicklerDao ticklerDao = SpringUtils.getBean(TicklerDao.class);
    private static final PreventionManager preventionManager = SpringUtils.getBean(PreventionManager.class);
    private static final ProgramManager2 programManager2 = SpringUtils.getBean(ProgramManager2.class);
    private static final ConsultationRequestDao consultationRequestDao = SpringUtils.getBean(ConsultationRequestDao.class);
    private static final ProfessionalSpecialistDao professionalSpecialistDao = SpringUtils.getBean(ProfessionalSpecialistDao.class);
    private static final EFormDao eformDao = SpringUtils.getBean(EFormDao.class);

    private EFormUtil() {
    }

    public static String saveEForm(EForm eForm) {
        return saveEForm(eForm.getFormName(), eForm.getFormSubject(), eForm.getFormFileName(), eForm.getFormHtml(), eForm.getFormCreator(), eForm.isShowLatestFormOnly(), eForm.isPatientIndependent(), eForm.getRoleType());
    }

    public static String saveEForm(String formName, String formSubject, String fileName, String htmlStr) {
        return saveEForm(formName, formSubject, fileName, htmlStr, false, false, null);
    }

    public static String saveEForm(String formName, String formSubject, String fileName, String htmlStr, boolean showLatestFormOnly, boolean patientIndependent, String roleType) {
        return saveEForm(formName, formSubject, fileName, htmlStr, null, showLatestFormOnly, patientIndependent, roleType);
    }

    public static String saveEForm(String formName, String formSubject, String fileName, String htmlStr, String creator, boolean showLatestFormOnly, boolean patientIndependent, String roleType) {
        // called by the upload action, puts the uploaded form into DB

        ca.openosp.openo.commn.model.EForm eform = new ca.openosp.openo.commn.model.EForm();
        eform.setFormName(formName);
        eform.setFileName(fileName);
        eform.setSubject(formSubject);
        eform.setCreator(creator);
        eform.setCurrent(true);
        eform.setFormHtml(htmlStr);
        eform.setShowLatestFormOnly(showLatestFormOnly);
        eform.setPatientIndependent(patientIndependent);
        eform.setRoleType(roleType);

        eformDao.persist(eform);

        return eform.getId().toString();
    }

    public static ArrayList<HashMap<String, ? extends Object>> listEForms(String sortBy, String deleted) {

        // sends back a list of forms that were uploaded (those that can be added to the patient)

        List<ca.openosp.openo.commn.model.EForm> eforms = null;
        Boolean status = null;
        if (deleted.equals("deleted")) {
            status = false;
        } else if (deleted.equals("current")) {
            status = true;
        } else if (deleted.equals("all")) {
            status = null;
        }

        EFormSortOrder sortOrder = null;
        if (NAME.equals(sortBy)) sortOrder = EFormSortOrder.NAME;
        else if (SUBJECT.equals(sortBy)) sortOrder = EFormSortOrder.SUBJECT;
        else if (FILE_NAME.equals(sortBy)) sortOrder = EFormSortOrder.FILE_NAME;
        else if (DATE.equals(sortBy)) sortOrder = EFormSortOrder.DATE;

        eforms = eformDao.findByStatus(status, sortOrder);

        ArrayList<HashMap<String, ? extends Object>> results = new ArrayList<HashMap<String, ? extends Object>>();
        for (ca.openosp.openo.commn.model.EForm eform : eforms) {
            HashMap<String, Object> curht = new HashMap<String, Object>();
            curht.put("fid", eform.getId().toString());
            curht.put("formName", eform.getFormName());
            curht.put("formSubject", eform.getSubject());
            curht.put("formFileName", eform.getFileName());
            curht.put("formDate", ConversionUtils.toDateString(eform.getFormDate()));
            curht.put("formDateAsDate", eform.getFormDate());
            curht.put("formTime", ConversionUtils.toTimeString(eform.getFormTime()));
            curht.put("roleType", eform.getRoleType());
            results.add(curht);
        }
        return (results);
    }

    public static ArrayList<HashMap<String, ? extends Object>> listEForms(String sortBy, String deleted, String userRoles) {
        ArrayList<HashMap<String, ? extends Object>> results = new ArrayList<HashMap<String, ? extends Object>>();
        ArrayList<HashMap<String, ? extends Object>> eForms = listEForms(sortBy, deleted);
        if (eForms.size() > 0) {
            for (int i = 0; i < eForms.size(); i++) {
                HashMap<String, ? extends Object> curForm = eForms.get(i);
                // filter eform by role type
                if (curForm.get("roleType") != null && !curForm.get("roleType").equals("")) {
                    // ojectName: "_admin,_admin.eform"
                    // roleName: "doctor,admin"
                    String objectName = "_eform." + curForm.get("roleType");
                    Vector v = OscarRoleObjectPrivilege.getPrivilegeProp(objectName);
                    if (!OscarRoleObjectPrivilege.checkPrivilege(userRoles, (Properties) v.get(0), (Vector) v.get(1))) {
                        continue;
                    }
                }
                results.add(curForm);
            }
        }
        return (results);
    }

    public static ArrayList<String> listSecRole() {
        SecRoleDao dao = (SecRoleDao) SpringUtils.getBean(SecRoleDao.class);
        ArrayList<String> results = new ArrayList<String>();
        for (SecRole role : dao.findAll())
            results.add(role.getName());

        return (results);
    }

    public static ArrayList<String> listImages() {
        String imagePath = OscarProperties.getInstance().getEformImageDirectory();
        logger.debug("Img Path: " + imagePath);
        File dir = new File(imagePath);
        String[] files = dir.list();
        ArrayList<String> fileList;
        if (files != null) {
            Arrays.sort(files);
            fileList = new ArrayList<String>(Arrays.asList(files));
        } else {
            fileList = new ArrayList<String>();
        }

        return fileList;
    }

    public static List<EFormData> listPatientEformsCurrent(Integer demographicNo, Boolean current, int startIndex, int numToReturn) {
        return eFormDataDao.findByDemographicIdCurrent(demographicNo, current, startIndex, numToReturn);
    }

    public static List<EFormData> listPatientEformsCurrent(Integer demographicNo, Boolean current) {
        return eFormDataDao.findByDemographicIdCurrent(demographicNo, current);
    }

    public static List<EFormData> listPatientEformsCurrentAttachedToConsult(String consultationId) {
        return eFormDataDao.findByDemographicIdCurrentAttachedToConsult(consultationId);
    }

    public static List<EFormData> listPatientEformsCurrentAttachedToEForm(String fdid) {
        return eFormDataDao.findByDemographicIdCurrentAttachedToEForm(fdid);
    }

    public static ArrayList<HashMap<String, ? extends Object>> listPatientEForms(String sortBy, String deleted, String demographic_no, String userRoles, int offset, int itemsToReturn) {

        Boolean current = null;
        if (deleted.equals("deleted")) current = false;
        else if (deleted.equals("current")) current = true;

        List<EFormData> allEformDatas = eFormDataDao.findByDemographicIdCurrent(Integer.parseInt(demographic_no), current, offset, itemsToReturn, sortBy);

        //	if (NAME.equals(sortBy)) Collections.sort(allEformDatas, EFormData.FORM_NAME_COMPARATOR);
        //	else if (SUBJECT.equals(sortBy)) Collections.sort(allEformDatas, EFormData.FORM_SUBJECT_COMPARATOR);
        //	else Collections.sort(allEformDatas, EFormData.FORM_DATE_COMPARATOR);

        ArrayList<HashMap<String, ? extends Object>> results = new ArrayList<HashMap<String, ? extends Object>>();
        try {
            for (EFormData eFormData : allEformDatas) {
                // filter eform by role type
                String tempRole = StringUtils.trimToNull(eFormData.getRoleType());
                if (userRoles != null && tempRole != null) {
                    // ojectName: "_admin,_admin.eform"
                    // roleName: "doctor,admin"
                    String objectName = "_eform." + tempRole;
                    Vector v = OscarRoleObjectPrivilege.getPrivilegeProp(objectName);
                    if (!OscarRoleObjectPrivilege.checkPrivilege(userRoles, (Properties) v.get(0), (Vector) v.get(1))) {
                        continue;
                    }
                }
                HashMap<String, Object> curht = new HashMap<String, Object>();
                curht.put("fdid", eFormData.getId().toString());
                curht.put("fid", eFormData.getFormId().toString());
                curht.put("formName", eFormData.getFormName());
                curht.put("formSubject", eFormData.getSubject());
                curht.put("formDate", eFormData.getFormDate().toString());
                curht.put("formTime", eFormData.getFormTime().toString());
                curht.put("formDateAsDate", eFormData.getFormDate());
                curht.put("roleType", eFormData.getRoleType());
                curht.put("providerNo", eFormData.getProviderNo());
                results.add(curht);
            }
        } catch (Exception sqe) {
            logger.error("Error", sqe);
        }
        return (results);
    }

    @Deprecated
    public static ArrayList<HashMap<String, ? extends Object>> listPatientEForms(String sortBy, String deleted, String demographic_no, String userRoles) {

        Boolean current = null;
        if (deleted.equals("deleted")) current = false;
        else if (deleted.equals("current")) current = true;

        List<EFormData> allEformDatas = eFormDataDao.findByDemographicIdCurrent(Integer.parseInt(demographic_no), current);

        if (NAME.equals(sortBy)) Collections.sort(allEformDatas, EFormData.FORM_NAME_COMPARATOR);
        else if (SUBJECT.equals(sortBy)) Collections.sort(allEformDatas, EFormData.FORM_SUBJECT_COMPARATOR);
        else Collections.sort(allEformDatas, EFormData.FORM_DATE_COMPARATOR);

        ArrayList<HashMap<String, ? extends Object>> results = new ArrayList<HashMap<String, ? extends Object>>();
        try {
            for (EFormData eFormData : allEformDatas) {
                // filter eform by role type
                String tempRole = StringUtils.trimToNull(eFormData.getRoleType());
                if (userRoles != null && tempRole != null) {
                    // ojectName: "_admin,_admin.eform"
                    // roleName: "doctor,admin"
                    String objectName = "_eform." + tempRole;
                    Vector v = OscarRoleObjectPrivilege.getPrivilegeProp(objectName);
                    if (!OscarRoleObjectPrivilege.checkPrivilege(userRoles, (Properties) v.get(0), (Vector) v.get(1))) {
                        continue;
                    }
                }
                HashMap<String, Object> curht = new HashMap<String, Object>();
                curht.put("fdid", eFormData.getId().toString());
                curht.put("fid", eFormData.getFormId().toString());
                curht.put("formName", eFormData.getFormName());
                curht.put("formSubject", eFormData.getSubject());
                curht.put("formDate", eFormData.getFormDate().toString());
                curht.put("formTime", eFormData.getFormTime().toString());
                curht.put("formDateAsDate", eFormData.getFormDate());
                curht.put("roleType", eFormData.getRoleType());
                curht.put("providerNo", eFormData.getProviderNo());
                results.add(curht);
            }
        } catch (Exception sqe) {
            logger.error("Error", sqe);
        }
        return (results);
    }

    public static ArrayList<HashMap<String, ? extends Object>> listPatientIndependentEForms(String sortBy, String deleted) {

        Boolean current = null;
        if (deleted.equals("deleted")) current = false;
        else if (deleted.equals("current")) current = true;

        List<EFormData> allEformDatas = eFormDataDao.findPatientIndependent(current);

        if (NAME.equals(sortBy)) Collections.sort(allEformDatas, EFormData.FORM_NAME_COMPARATOR);
        else if (SUBJECT.equals(sortBy)) Collections.sort(allEformDatas, EFormData.FORM_SUBJECT_COMPARATOR);
        else if (PROVIDER.equals(sortBy)) sortByProviderName(allEformDatas);
        else Collections.sort(allEformDatas, EFormData.FORM_DATE_COMPARATOR);

        ArrayList<HashMap<String, ? extends Object>> results = new ArrayList<HashMap<String, ? extends Object>>();
        try {
            for (EFormData eFormData : allEformDatas) {
                HashMap<String, Object> curht = new HashMap<String, Object>();
                curht.put("fdid", eFormData.getId().toString());
                curht.put("fid", eFormData.getFormId().toString());
                curht.put("formName", eFormData.getFormName());
                curht.put("formSubject", eFormData.getSubject());
                curht.put("formDate", eFormData.getFormDate().toString());
                curht.put("formTime", eFormData.getFormTime().toString());
                curht.put("formDateAsDate", eFormData.getFormDate());
                curht.put("roleType", eFormData.getRoleType());
                curht.put("providerNo", eFormData.getProviderNo());
                results.add(curht);
            }
        } catch (Exception sqe) {
            logger.error("Error", sqe);
        }
        return (results);
    }

    public static ArrayList<HashMap<String, ? extends Object>> listPatientEFormsNoData(String demographic_no, String userRoles) {

        Boolean current = true;

        List<Map<String, Object>> allEformDatas = eFormDataDao.findByDemographicIdCurrentNoData(Integer.parseInt(demographic_no), current);

        ArrayList<HashMap<String, ? extends Object>> results = new ArrayList<HashMap<String, ? extends Object>>();
        try {
            for (Map<String, Object> eFormData : allEformDatas) {
                // filter eform by role type
                String tempRole = StringUtils.trimToNull((String) eFormData.get("roleType"));
                if (userRoles != null && tempRole != null) {
                    // ojectName: "_admin,_admin.eform"
                    // roleName: "doctor,admin"
                    String objectName = "_eform." + tempRole;
                    Vector v = OscarRoleObjectPrivilege.getPrivilegeProp(objectName);
                    if (!OscarRoleObjectPrivilege.checkPrivilege(userRoles, (Properties) v.get(0), (Vector) v.get(1))) {
                        continue;
                    }
                }
                HashMap<String, Object> curht = new HashMap<String, Object>();
                curht.put("fdid", String.valueOf(eFormData.get("id")));
                curht.put("fid", String.valueOf(eFormData.get("formId")));
                curht.put("formName", eFormData.get("formName"));
                curht.put("formSubject", eFormData.get("subject"));
                curht.put("formDate", String.valueOf(eFormData.get("formDate")));
                curht.put("formTime", String.valueOf(eFormData.get("formTime")));
                curht.put("formDateAsDate", eFormData.get("formDate"));
                curht.put("roleType", eFormData.get("roleType"));
                curht.put("providerNo", eFormData.get("providerNo"));
                results.add(curht);
            }
        } catch (Exception sqe) {
            logger.error("Error", sqe);
        }
        return (results);
    }

    public static ArrayList<HashMap<String, ? extends Object>> loadEformsByFdis(List<Integer> ids) {

        List<EFormData> allEformDatas = eFormDataDao.findByFdids(ids);

        ArrayList<HashMap<String, ? extends Object>> results = new ArrayList<HashMap<String, ? extends Object>>();
        try {
            for (EFormData eFormData : allEformDatas) {
                HashMap<String, Object> curht = new HashMap<String, Object>();
                curht.put("fdid", eFormData.getId().toString());
                curht.put("fid", eFormData.getFormId().toString());
                curht.put("formName", eFormData.getFormName());
                curht.put("formSubject", eFormData.getSubject());
                curht.put("formDate", eFormData.getFormDate().toString());
                curht.put("formTime", eFormData.getFormTime().toString());
                curht.put("formDateAsDate", eFormData.getFormDate());
                curht.put("roleType", eFormData.getRoleType());
                curht.put("providerNo", eFormData.getProviderNo());
                results.add(curht);
            }
        } catch (Exception sqe) {
            logger.error("Error", sqe);
        }
        return (results);
    }

    public static HashMap<String, Object> loadEForm(String fid) {

        Integer id = Integer.valueOf(fid);
        ca.openosp.openo.commn.model.EForm eform = eformDao.find(id);
        HashMap<String, Object> curht = new HashMap<String, Object>();
        if (eform == null) {
            logger.error("Unable to find EForm with ID = " + fid);
            curht.put("formName", "");
            curht.put("formHtml", "No Such Form in Database");
            return curht;
        }

        // must have FID and form_name otherwise throws null pointer on the hashtable
        curht.put("fid", eform.getId());
        curht.put("formName", eform.getFormName());
        curht.put("formSubject", eform.getSubject());
        curht.put("formFileName", eform.getFileName());
        curht.put("formDate", eform.getFormDate().toString());
        curht.put("formTime", eform.getFormTime().toString());
        curht.put("formCreator", eform.getCreator());
        curht.put("formHtml", eform.getFormHtml());
        curht.put("showLatestFormOnly", eform.isShowLatestFormOnly());
        curht.put("patientIndependent", eform.isPatientIndependent());
        curht.put("roleType", eform.getRoleType());

        return (curht);
    }

    public static void updateEForm(EFormBase updatedForm) {
        // Updates the form - used by editForm


        ca.openosp.openo.commn.model.EForm eform = eformDao.find(Integer.parseInt(updatedForm.getFid()));
        if (eform == null) {
            logger.error("Unable to find eform for update: " + updatedForm);
            return;
        }

        eform.setFormName(updatedForm.getFormName());
        eform.setFileName(updatedForm.getFormFileName());
        eform.setSubject(updatedForm.getFormSubject());
        eform.setFormDate(ConversionUtils.fromDateString(updatedForm.getFormDate()));
        eform.setFormTime(ConversionUtils.fromTimeString(updatedForm.getFormTime()));
        eform.setFormHtml(updatedForm.getFormHtml());
        eform.setShowLatestFormOnly(updatedForm.isShowLatestFormOnly());
        eform.setPatientIndependent(updatedForm.isPatientIndependent());
        eform.setRoleType(updatedForm.getRoleType());

        eformDao.merge(eform);
    }

    /*
     * +--------------+--------------+------+-----+---------+----------------+
     * | Field        | Type         | Null | Key | Default | Extra          |
     * +--------------+--------------+------+-----+---------+----------------+
     * | fid          | int(8)       |      | PRI | NULL    | auto_increment |
     * | form_name    | varchar(255) | YES  |     | NULL    |                |
     * | file_name    | varchar(255) | YES  |     | NULL    |                |
     * | subject      | varchar(255) | YES  |     | NULL    |                |
     * | form_date    | date         | YES  |     | NULL    |                |
     * | form_time    | time         | YES  |     | NULL    |                |
     * | form_creator | varchar(255) | YES  |     | NULL    |                |
     * | status       | tinyint(1)   |      |     | 1       |                |
     * | form_html    | text         | YE S |     | NULL    |                |
     * +--------------+--------------+------+-----+---------+----------------+
     */

    public static String getEFormParameter(String fid, String fieldName) {

        ca.openosp.openo.commn.model.EForm eform = eformDao.find(ConversionUtils.fromIntString(fid));
        if (eform == null) {
            logger.error("Unable to find EForm for ID = " + fid);
            return "";
        }

        if (fieldName.equalsIgnoreCase("formName"))
            return eform.getFormName();
        else if (fieldName.equalsIgnoreCase("formSubject"))
            return eform.getSubject();
        else if (fieldName.equalsIgnoreCase("formFileName"))
            return eform.getFileName();
        else if (fieldName.equalsIgnoreCase("formDate"))
            return ConversionUtils.toDateString(eform.getFormDate());
        else if (fieldName.equalsIgnoreCase("formTime"))
            return ConversionUtils.toTimeString(eform.getFormTime());
        else if (fieldName.equalsIgnoreCase("formStatus"))
            return ConversionUtils.toBoolString(eform.isCurrent());
        else if (fieldName.equalsIgnoreCase("formHtml"))
            return eform.getFormHtml();
        else if (fieldName.equalsIgnoreCase("showLatestFormOnly"))
            return ConversionUtils.toBoolString(eform.isShowLatestFormOnly());
        else if (fieldName.equalsIgnoreCase("patientIndependent"))
            return ConversionUtils.toBoolString(eform.isPatientIndependent());
        else if (fieldName.equalsIgnoreCase("roleType"))
            return eform.getRoleType();

        logger.warn("Invalid field name: " + fieldName + ". Please use one of formName, formSubject, formFileName, formDate, formTime, formStatus, formHtml, showLatestFormOnly, patientIndependent or roleType.");

        return null;
    }

    public static String getEFormIdByName(String name) {

        logger.debug("EFORM NAME '" + name + "'");
        Integer maxId = eformDao.findMaxIdForActiveForm(name);

        return (maxId == null ? null : maxId.toString());
    }

    public static void delEForm(String fid) {
        setFormStatus(fid, false);
    }

    public static void restoreEForm(String fid) {
        setFormStatus(fid, true);
    }

    @Deprecated
    public static ArrayList<String> getValues(ArrayList<String> names, String sql) {
        // gets the values for each column name in the sql (used by DatabaseAP)
        ResultSet rs = getSQL(sql);
        ArrayList<String> values = new ArrayList<String>();
        try {
            while (rs.next()) {
                values = new ArrayList<String>();
                for (int i = 0; i < names.size(); i++) {
                    try {
                        values.add(Misc.getString(rs, names.get(i)));
                        logger.debug("VALUE ====" + rs.getObject(names.get(i)) + "|");
                    } catch (Exception sqe) {
                        values.add("<(" + names.get(i) + ")NotFound>");
                        logger.error("Error", sqe);
                    }
                }
            }
            rs.close();
        } catch (SQLException sqe) {
            logger.error("Error", sqe);
        }
        return (values);
    }

    public static ArrayNode getJsonValues(ArrayList<String> names, String sql) {
        // gets the values for each column name in the sql (used by DatabaseAP)
        ResultSet rs = getSQL(sql);
        ArrayNode values = objectMapper.createArrayNode();
        try {
            while (rs.next()) {
                ObjectNode value = objectMapper.createObjectNode();
                for (int i = 0; i < names.size(); i++) {
                    try {
                        value.put(names.get(i), Misc.getString(rs, names.get(i)));
                    } catch (Exception sqe) {
                        value.put(names.get(i), "<(" + names.get(i) + ")NotFound>");
                        logger.error("Error", sqe);
                    }
                }
                values.add(value);
            }
            rs.close();
        } catch (SQLException sqe) {
            logger.error("Error", sqe);
        }
        return values;
    }

    // used by addEForm for escaping characters
    public static String charEscape(String S, char a) {
        if (null == S) {
            return S;
        }
        int N = S.length();
        StringBuilder sb = new StringBuilder(N);
        for (int i = 0; i < N; i++) {
            char c = S.charAt(i);
            // escape the escape characters
            if (c == '\\') {
                sb.append("\\\\");
            } else if (c == a) {
                sb.append("\\" + a);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static void addEFormValues(ArrayList<String> names, ArrayList<String> values, Integer fdid, Integer fid, Integer demographic_no) {
        // adds parsed values and names to DB
        // names.size and values.size must equal!
        try {
            for (int i = 0; i < names.size(); i++) {
                EFormValue eFormValue = new EFormValue();
                eFormValue.setFormId(fid);
                eFormValue.setFormDataId(fdid);
                eFormValue.setDemographicId(demographic_no);
                eFormValue.setVarName(names.get(i));
                eFormValue.setVarValue(values.get(i));

                eFormValueDao.persist(eFormValue);
            }
        } catch (PersistenceException ee) {
            logger.error("Unexpected Error", ee);
        }
    }

    public static boolean formExistsInDB(String eFormName) {

        ca.openosp.openo.commn.model.EForm eform = eformDao.findByName(eFormName);
        return eform != null;
    }

    public static int formExistsInDBn(String formName, String fid) {

        Long result = eformDao.countFormsOtherThanSpecified(formName, ConversionUtils.fromIntString(fid));
        return result.intValue();
    }

    // --------------eform groups---------
    public static ArrayList<HashMap<String, String>> getEFormGroups() {
        String sql;
        sql = "SELECT DISTINCT eform_groups.group_name, count(*)-1 AS 'count' FROM eform_groups "
                + "LEFT JOIN eform ON eform.fid=eform_groups.fid WHERE eform.status=1 OR eform_groups.fid=0 "
                + "GROUP BY eform_groups.group_name;";
        ArrayList<HashMap<String, String>> al = new ArrayList<HashMap<String, String>>();
        try {
            ResultSet rs = getSQL(sql);
            while (rs.next()) {
                HashMap<String, String> curhash = new HashMap<String, String>();
                curhash.put("groupName", Misc.getString(rs, "group_name"));
                curhash.put("count", Misc.getString(rs, "count"));
                al.add(curhash);
            }
        } catch (SQLException sqe) {
            logger.error("Error", sqe);
        }
        return al;
    }

    public static ArrayList<HashMap<String, String>> getEFormGroups(String demographic_no) {
        String sql;
        sql = "SELECT eform_groups.group_name, count(*)-1 AS 'count' FROM eform_groups "
                + "LEFT JOIN eform_data ON eform_data.fid=eform_groups.fid "
                + "WHERE (eform_data.status=1 AND eform_data.demographic_no=" + demographic_no
                + ") OR eform_groups.fid=0 " + "GROUP BY eform_groups.group_name";
        ArrayList<HashMap<String, String>> al = new ArrayList<HashMap<String, String>>();
        try {
            ResultSet rs = getSQL(sql);
            while (rs.next()) {
                HashMap<String, String> curhash = new HashMap<String, String>();
                curhash.put("groupName", Misc.getString(rs, "group_name"));
                curhash.put("count", Misc.getString(rs, "count"));
                al.add(curhash);
            }
        } catch (SQLException sqe) {
            logger.error("Error", sqe);
        }
        return al;
    }

    public static void delEFormGroup(String name) {
        EFormGroupDao dao = SpringUtils.getBean(EFormGroupDao.class);
        dao.deleteByName(name);
    }

    public static void addEFormToGroup(String groupName, String fid) {
        try {

            String sql1 = "SELECT eform_groups.fid FROM eform_groups, eform WHERE eform_groups.fid=" + fid
                    + " AND eform_groups.fid=eform.fid AND eform.status=1 AND eform_groups.group_name='" + groupName + "'";
            ResultSet rs = DBHandler.GetSQL(sql1);
            if (!rs.next()) {
                EFormGroup eg = new EFormGroup();
                eg.setFormId(Integer.parseInt(fid));
                eg.setGroupName(groupName);
                eFormGroupDao.persist(eg);
            }
        } catch (SQLException sqe) {
            logger.error("Error", sqe);
        }
    }

    public static void remEFormFromGroup(String groupName, String fid) {
        EFormGroupDao dao = SpringUtils.getBean(EFormGroupDao.class);
        dao.deleteByNameAndFormId(groupName, ConversionUtils.fromIntString(fid));
    }

    public static ArrayList<HashMap<String, ? extends Object>> listEForms(String sortBy, String deleted, String group, String userRoles) {
        // sends back a list of forms that were uploaded (those that can be added to the patient)
        String sql = "";
        if (deleted.equals("deleted")) {
            sql = "SELECT * FROM eform, eform_groups where eform.status=0 AND eform.fid=eform_groups.fid AND eform_groups.group_name='" + group + "' ORDER BY " + sortBy;
        } else if (deleted.equals("current")) {
            sql = "SELECT * FROM eform, eform_groups where eform.status=1 AND eform.fid=eform_groups.fid AND eform_groups.group_name='" + group + "' ORDER BY " + sortBy;
        } else if (deleted.equals("all")) {
            sql = "SELECT * FROM eform AND eform.fid=eform_groups.fid AND eform_groups.group_name='" + group + "' ORDER BY " + sortBy;
        }
        ResultSet rs = getSQL(sql);
        ArrayList<HashMap<String, ? extends Object>> results = new ArrayList<HashMap<String, ? extends Object>>();
        try {
            while (rs.next()) {
                HashMap<String, String> curht = new HashMap<String, String>();
                curht.put("fid", rsGetString(rs, "fid"));
                curht.put("formName", rsGetString(rs, "form_name"));
                curht.put("formSubject", rsGetString(rs, "subject"));
                curht.put("formFileName", rsGetString(rs, "file_name"));
                curht.put("formDate", rsGetString(rs, "form_date"));
                curht.put("formTime", rsGetString(rs, "form_time"));
                curht.put("roleType", rsGetString(rs, "roleType"));

                // filter eform by role type
                if (curht.get("roleType") != null && !curht.get("roleType").equals("")) {
                    // ojectName: "_admin,_admin.eform"
                    // roleName: "doctor,admin"
                    String objectName = "_eform." + curht.get("roleType");
                    Vector v = OscarRoleObjectPrivilege.getPrivilegeProp(objectName);
                    if (!OscarRoleObjectPrivilege.checkPrivilege(userRoles, (Properties) v.get(0), (Vector) v.get(1))) {
                        continue;
                    }
                }
                results.add(curht);
            }
            rs.close();
        } catch (Exception sqe) {
            logger.error("Error", sqe);
        }
        return (results);
    }

    public static ArrayList<HashMap<String, ? extends Object>> listPatientEForms(LoggedInInfo loggedInInfo, String sortBy, String deleted, String demographic_no, String groupName, int offset, int numToReturn) {
        SecurityInfoManager secInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
        List<String> privs = new ArrayList<String>();
        for (Secobjprivilege p : secInfoManager.getSecurityObjects(loggedInInfo)) {
            if (p.getObjectname_code().startsWith("_eform.")) {
                privs.add(p.getObjectname_code());
            }
        }

        Boolean current = true;
        if (deleted.equals("deleted")) {
            current = false;
        } else if (deleted.equals("all")) {
            current = null;
        }


        List<EFormData> results1 = eFormDataDao.findInGroups(current, Integer.valueOf(demographic_no), groupName, sortBy, offset, numToReturn, privs);
        ArrayList<HashMap<String, ? extends Object>> results = new ArrayList<HashMap<String, ? extends Object>>();

        for (EFormData x : results1) {
            HashMap<String, String> curht = new HashMap<String, String>();
            curht.put("fdid", String.valueOf(x.getId()));
            curht.put("fid", x.getId().toString());
            curht.put("formName", x.getFormName());
            curht.put("formSubject", x.getSubject());
            curht.put("formDate", DateFormatUtils.ISO_DATE_FORMAT.format(x.getFormDate()));
            curht.put("formTime", DateFormatUtils.ISO_TIME_NO_T_FORMAT.format(x.getFormTime()));
            curht.put("roleType", x.getRoleType());
            results.add(curht);
        }

        return (results);
    }

    @Deprecated
    public static ArrayList<HashMap<String, ? extends Object>> listPatientEForms(String sortBy, String deleted, String demographic_no, String groupName, String userRoles) {
        // sends back a list of forms added to the patient
        String sql = "";
        if (deleted.equals("deleted")) {
            sql = "SELECT * FROM eform_data, eform_groups WHERE eform_data.status=0 AND eform_data.patient_independent=0 AND eform_data.demographic_no=" + demographic_no + " AND eform_data.fid=eform_groups.fid AND eform_groups.group_name='" + groupName + "' ORDER BY " + sortBy;
        } else if (deleted.equals("current")) {
            sql = "SELECT * FROM eform_data, eform_groups WHERE eform_data.status=1 AND eform_data.patient_independent=0 AND eform_data.demographic_no=" + demographic_no + " AND eform_data.fid=eform_groups.fid AND eform_groups.group_name='" + groupName + "' ORDER BY " + sortBy;
        } else if (deleted.equals("all")) {
            sql = "SELECT * FROM eform_data, eform_groups WHERE eform_data.patient_independent=0 AND eform_data.demographic_no=" + demographic_no + " AND eform_data.fid=eform_groups.fid AND eform_groups.group_name='" + groupName + "' ORDER BY " + sortBy;
        }
        ResultSet rs = getSQL(sql);
        ArrayList<HashMap<String, ? extends Object>> results = new ArrayList<HashMap<String, ? extends Object>>();
        try {
            while (rs.next()) {
                // filter eform by role type
                if (rsGetString(rs, "roleType") != null && !rsGetString(rs, "roleType").equals("") && !rsGetString(rs, "roleType").equals("null")) {
                    // ojectName: "_admin,_admin.eform"
                    // roleName: "doctor,admin"
                    String objectName = "_eform." + rsGetString(rs, "roleType");
                    Vector v = OscarRoleObjectPrivilege.getPrivilegeProp(objectName);
                    if (!OscarRoleObjectPrivilege.checkPrivilege(userRoles, (Properties) v.get(0), (Vector) v.get(1))) {
                        continue;
                    }
                }
                HashMap<String, String> curht = new HashMap<String, String>();
                curht.put("fdid", Misc.getString(rs, "fdid"));
                curht.put("fid", rsGetString(rs, "fid"));
                curht.put("formName", rsGetString(rs, "form_name"));
                curht.put("formSubject", rsGetString(rs, "subject"));
                curht.put("formDate", rsGetString(rs, "form_date"));
                curht.put("formTime", rsGetString(rs, "form_time"));
                curht.put("roleType", rsGetString(rs, "roleType"));
                results.add(curht);
            }
            rs.close();
        } catch (Exception sqe) {
            logger.error("Error", sqe);
        }
        return (results);
    }

    public static void writeEformTemplate(LoggedInInfo loggedInInfo, ArrayList<String> paramNames, ArrayList<String> paramValues, EForm eForm, String fdid, String programNo, String context_path) {
        String text = eForm != null ? eForm.getTemplate() : null;
        if (StringUtils.isBlank(text)) return;

        text = putTemplateValues(paramNames, paramValues, text);
        text = putTemplateEformValues(eForm, fdid, context_path, text);
        String[] template_echart = {"EncounterNote", "SocialHistory", "FamilyHistory", "MedicalHistory", "OngoingConcerns", "RiskFactors", "Reminders", "OtherMeds"};
        String[] code = {"", "SocHistory", "FamHistory", "MedHistory", "Concerns", "RiskFactors", "Reminders", "OMeds"};
        ArrayList<String> templates = new ArrayList<String>();

        /* write to echart
         * <EncounterNote {or another template_echart}>
         * 		content to write to echart
         * </EncounterNote>
         */
        for (int i = 0; i < template_echart.length; i++) {
            templates = getWithin(template_echart[i], text);
            for (String template : templates) {
                if (StringUtils.isBlank(template)) continue;

                template = putTemplateEformHtml(eForm.getFormHtml(), template);
                saveCMNote(eForm, fdid, programNo, code[i], template);
            }
        }

        /* write to document
         * <document {optional:belong=providers/patient}>
         * 		<docdesc>{optional:documentDescription}</docdesc>
         * 		<docowner>{optional:provider_no/demographic_no}</docowner>
         * 		<content>
         * 			content to write to document
         * 		</content>
         * </document>
         */
        templates = getWhole("document", text);
        for (String template : templates) {
            if (StringUtils.isBlank(template)) continue;

            String belong = getAttribute("belong", getBeginTag("document", template));
            if (!"patient".equalsIgnoreCase(belong)) belong = "providers";
            else belong = "demographic";

            String docOwner = getContent("docowner", template, null);
            if (docOwner == null) {
                if (belong.equals("demographic")) docOwner = eForm.getDemographicNo();
                else docOwner = eForm.getProviderNo();
            }

            String docDesc = getContent("docdesc", template, eForm.getFormName());
            String docText = getContent("content", template, "");
            docText = putTemplateEformHtml(eForm.getFormHtml(), docText);

            if (NumberUtils.isDigits(docOwner)) {
                EDoc edoc = new EDoc(docDesc, "forms", "html", docText, docOwner, eForm.getProviderNo(), "", 'H', eForm.getFormDate().toString(), "", null, belong, docOwner);
                edoc.setContentType("text/html");
                edoc.setDocPublic("0");
                EDocUtil.addDocumentSQL(edoc);
            }
        }

        /* write to prevention
         * <prevention>
         * 		<type>{preventionType: must be identical to Oscar prevention types}</type>
         * 		<providers>{optional:providerNo}</providers>
         * 		<date>{optional:preventionDate}</date>
         * 		<status>{optional:completed/refused/ineligible}</status>
         * 		<name>{optional}</name>
         * 		<dose>{optional}</dose>
         * 		<manufacture>{optional}</manufacture>
         * 		<route>{optional}</route>
         * 		<lot>{optional}</lot>
         * 		<location>{optional}</location>
         * 		<comments>{optional}</comments>
         * 		<reason>{optional}</reason>
         * 		<result>{optional:pending/normal/abnormal/other}</result>
         * </prevention>
         */

        templates = getWithin("prevention", text);
        for (String template : templates) {
            if (StringUtils.isBlank(template)) continue;

            String preventionType = getEqualIgnoreCase(preventionManager.getPreventionTypeList(), getContent("type", template, null));
            if (preventionType == null) continue;

            String preventionProvider = getContent("providers", template, eForm.getProviderNo());
            String preventionDate = getContent("date", template, eForm.getFormDate());
            String preventionStatus = getContent("status", template, "completed"); //completed(0)/refused(1)/ineligible(2)

            Prevention prevention = new Prevention();
            prevention.setPreventionType(preventionType);
            prevention.setPreventionDate(UtilDateUtilities.StringToDate(preventionDate, "yyyy-MM-dd"));
            prevention.setProviderNo(preventionProvider);
            prevention.setDemographicId(Integer.valueOf(eForm.getDemographicNo()));
            prevention.setCreatorProviderNo(eForm.getProviderNo());
            if (preventionStatus.equalsIgnoreCase("refused")) prevention.setRefused(true);
            else if (preventionStatus.equalsIgnoreCase("ineligible")) prevention.setIneligible(true);

            HashMap<String, String> extHash = new HashMap<String, String>();
            String extData = null;
            if ((extData = getContent("name", template, null)) != null) extHash.put("name", extData);
            if ((extData = getContent("dose", template, null)) != null) extHash.put("dose", extData);
            if ((extData = getContent("manufacture", template, null)) != null) extHash.put("manufacture", extData);
            if ((extData = getContent("route", template, null)) != null) extHash.put("route", extData);
            if ((extData = getContent("lot", template, null)) != null) extHash.put("lot", extData);
            if ((extData = getContent("location", template, null)) != null) extHash.put("location", extData);
            if ((extData = getContent("comments", template, null)) != null) extHash.put("comments", extData);
            if ((extData = getContent("reason", template, null)) != null) extHash.put("reason", extData);
            if ((extData = getContent("result", template, null)) != null) {
                extData = extData.toLowerCase();
                if (extData.equals("pending") || extData.equals("normal") || extData.equals("abnormal") || extData.equals("other")) {
                    extHash.put("result", extData);
                }
            }
            preventionManager.addPreventionWithExts(prevention, extHash);
        }

        /* write to message
         * <message>
         * 		<subject>{optional}</subject>
         * 		<sendto>{list of providerNo to receive message, separated by comma}</sendto>
         * 		<content>
         * 			content of message
         * 		</content>
         * </message>
         */
        templates = getWithin("message", text);
        for (String template : templates) {
            if (StringUtils.isBlank(template)) continue;

            String subject = getContent("subject", template, eForm.getFormName());
            String[] sentList = getSentList(template);
            String userNo = eForm.getProviderNo();
            String userName = providerDao.getProviderName(eForm.getProviderNo());
            String message = getContent("content", template, "");
            message = putTemplateEformHtml(eForm.getFormHtml(), message);

            MessagingManager messagingManager = SpringUtils.getBean(MessagingManager.class);
            MessengerSystemMessage systemMessage = new MessengerSystemMessage(sentList);
            systemMessage.setType(OscarMsgType.GENERAL_TYPE);
            systemMessage.setSentByNo(userNo);
            systemMessage.setSentBy(userName);
            systemMessage.setSubject(subject);
            systemMessage.setMessage(message);

            messagingManager.sendSystemMessage(loggedInInfo, systemMessage);
        }

        /* write to ticklers
         * <tickler>
         * 		<taskAssignedTo>{providerNo}</taskAssignedTo>
         * 		<tickMsg>
         * 			message of the tickler
         * 		</tickMsg>
         * </tickler>
         */
        templates = getWithin("tickler", text);
        for (String template : templates) {
            if (StringUtils.isBlank(template)) continue;

            String taskAssignedTo = getContent("taskAssignedTo", template, null);
            if (taskAssignedTo == null) continue; //no assignee
            if (providerDao.getProvider(taskAssignedTo.trim()) == null) continue; //assignee providers no not exists

            String message = getContent("tickMsg", template, "");
            Tickler tickler = new Tickler();
            tickler.setTaskAssignedTo(taskAssignedTo);
            tickler.setMessage(message);
            tickler.setDemographicNo(Integer.valueOf(eForm.getDemographicNo()));
            tickler.setCreator(eForm.getProviderNo());

            ProgramProvider pp = programManager2.getCurrentProgramInDomain(loggedInInfo, loggedInInfo.getLoggedInProviderNo());

            if (pp != null) {
                tickler.setProgramId(pp.getProgramId().intValue());
            }

            ticklerDao.persist(tickler);
        }

        /* write to consult request
         * <consultRequest>
         * 		<referredToService></referredToService>
         * 		<referredToSpecialist></referredToSpecialist>
         * 		<urgency></urgency>
         * 		<referredBy></referredBy>
         * 		<referralDate></referralDate>
         * 		<reason>
         * 			reason
         * 		</reason>
         * 		<clinicalInfo>
         * 			clinicalInfo
         * 		</clinicalInfo>
         * 		<currentMeds>
         * 			currentMeds
         * 		</currentMeds>
         * 		<allergies>
         * 			allergies
         * 		</allergies>
         * 		<concurrentProblems>
         * 			concurrentProblems
         * 		</concurrentProblems>
         *		<patientWillBook></patientWillBook>
         *		<letterheadName></letterheadName>
         *		<letterheadAddresss></letterheadAddresss>
         *		<letterheadPhone></letterheadPhone>
         *		<letterheadFax></letterheadFax>
         *		<status></status>
         *		<source></source>
         * </consultRequest>
         */
        templates = getWithin("consultRequest", text);
        for (String template : templates) {
            if (StringUtils.isBlank(template)) continue;

            String referredToService = getContent("referredToService", template, null);
            String referredToSpecialist = getContent("referredToSpecialist", template, null);
            String referredBy = getContent("referredBy", template, null);

            String urgency = getContent("urgency", template, null);
            String referralDate = getContent("referralDate", template, null);
            String reason = getContent("reason", template, "");
            String clinicalInfo = getContent("clinicalInfo", template, "");
            String currentMeds = getContent("currentMeds", template, "");
            String allergies = getContent("allergies", template, "");
            String concurrentProblems = getContent("concurrentProblems", template, "");

            String patientWillBook = getContent("patientWillBook", template, null);
            String letterheadName = getContent("letterheadName", template, null);
            String letterheadAddress = getContent("letterheadAddress", template, null);
            String letterheadPhone = getContent("letterheadPhone", template, null);
            String letterheadFax = getContent("letterheadFax", template, null);
            String status = getContent("urgency", template, null);
            String source = getContent("source", template, "");

            ConsultationRequest consult = new ConsultationRequest();

            ProfessionalSpecialist ps = professionalSpecialistDao.find(Integer.parseInt(referredToSpecialist));
            if (ps == null) continue;
            if (referredToService == null || referredBy == null) continue;

            consult.setServiceId(Integer.parseInt(referredToService));
            consult.setDemographicId(Integer.parseInt(eForm.getDemographicNo()));
            consult.setProviderNo(referredBy);
            consult.setFdid(Integer.parseInt(fdid));

            if (referralDate != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                try {
                    consult.setReferralDate(sdf.parse(referralDate));
                } catch (ParseException e) {
                }
            } else {
                consult.setReferralDate(new Date());
            }

            consult.setReasonForReferral(reason);
            consult.setClinicalInfo(clinicalInfo);
            consult.setCurrentMeds(currentMeds);
            consult.setAllergies(allergies);
            consult.setConcurrentProblems(concurrentProblems);

            consult.setUrgency(urgency != null ? urgency : "2");
            consult.setStatus(status != null ? status : "1");
            consult.setSendTo("-1");
            consult.setPatientWillBook(patientWillBook != null ? Boolean.valueOf(patientWillBook) : false);

            consult.setSource(source);

            ClinicData clinic = new ClinicData();

            consult.setLetterheadName(letterheadName != null ? letterheadName : clinic.getClinicName());
            consult.setLetterheadAddress(letterheadAddress != null ? letterheadAddress : clinic.getClinicAddress() + " " + clinic.getClinicCity() + " " + clinic.getClinicProvince() + " " + clinic.getClinicPostal());
            consult.setLetterheadPhone(letterheadPhone != null ? letterheadPhone : clinic.getClinicPhone());
            consult.setLetterheadFax(letterheadFax != null ? letterheadFax : clinic.getClinicFax());


            consultationRequestDao.persist(consult);

            consult.setProfessionalSpecialist(ps);
            consultationRequestDao.merge(consult);


        }

    }

    public static int findIgnoreCase(String phrase, String text, int start) {
        if (StringUtils.isBlank(phrase) || StringUtils.isBlank(text)) return -1;

        text = text.toLowerCase();
        phrase = phrase.toLowerCase();
        return text.indexOf(phrase, start);
    }

    public static String removeQuotes(String s) {
        if (StringUtils.isBlank(s)) return s;

        s = s.trim();
        if (StringUtils.isBlank(s)) return s;

        if (s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') s = s.substring(1, s.length() - 1);
        if (StringUtils.isBlank(s)) return s;

        if (s.charAt(0) == '\'' && s.charAt(s.length() - 1) == '\'') s = s.substring(1, s.length() - 1);
        return s.trim();
    }

    public static String getAttribute(String key, String htmlTag) {
        return getAttribute(key, htmlTag, false);
    }

    public static String getAttribute(String key, String htmlTag, boolean startsWith) {
        if (StringUtils.isBlank(key) || StringUtils.isBlank(htmlTag)) return null;

        Matcher m = getAttributeMatcher(key, htmlTag, startsWith);
        if (m == null) return null;

        // With the new regex pattern:
        // Group 0: Full match (attribute with optional value)
        // Group 1: Attribute name
        // Group 2: =value part (optional, including =)
        // Group 3: Just the value (optional, without = and quotes)
        String value = m.group();
        if (!startsWith && m.group(3) != null) {
            // Return just the value part without quotes
            value = m.group(3);
            // Remove quotes if present
            if ((value.startsWith("\"") && value.endsWith("\"")) || 
                (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
        }

        return value.trim();
    }

    public static int getAttributePos(String key, String htmlTag) {
        int pos = -1;
        if (StringUtils.isBlank(key) || StringUtils.isBlank(htmlTag)) return pos;

        Matcher m = getAttributeMatcher(key, htmlTag, false);
        if (m == null) return pos;

        return m.start();
    }

    /**
     * Returns the end position of an attribute in an HTML tag (after the attribute value and closing quote).
     * This is useful for inserting new attributes after an existing attribute.
     *
     * @param key the attribute name to find
     * @param htmlTag the HTML tag content to search in
     * @return the position after the attribute's closing quote, or -1 if not found
     * @since 2025-10-30
     */
    public static int getAttributeEndPos(String key, String htmlTag) {
        int pos = -1;
        if (StringUtils.isBlank(key) || StringUtils.isBlank(htmlTag)) return pos;

        Matcher m = getAttributeMatcher(key, htmlTag, false);
        if (m == null) return pos;

        return m.end();
    }

    public static ArrayList<String> listRichTextLetterTemplates() {
        String imagePath = OscarProperties.getInstance().getEformImageDirectory();
        MiscUtils.getLogger().debug("Img Path: " + imagePath);
        File dir = new File(imagePath);
        String[] files = getRichTextLetterTemplates(dir);
        ArrayList<String> fileList;
        if (files != null) fileList = new ArrayList<String>(Arrays.asList(files));
        else fileList = new ArrayList<String>();

        return fileList;
    }

    public static String[] getRichTextLetterTemplates(File dir) {
        ArrayList<String> results = getFiles(dir, ".*(rtl)$", null);
        return results.toArray(new String[0]);
    }

    public static ArrayList<String> getFiles(File dir, String ext, ArrayList<String> files) {
        if (files == null) {
            files = new ArrayList<String>();
        }
        if (dir.isDirectory()) {
            for (String fileName : dir.list()) {
                if (fileName.toLowerCase().matches(ext)) {
                    files.add(fileName);
                }
            }
        }
        return files;
    }

    public static ArrayList<HashMap<String, ? extends Object>> getFormsSameFidSamePatient(String fdid, String sortBy, String userRoles) {
        List<EFormData> allEformDatas = eFormDataDao.getFormsSameFidSamePatient(Integer.valueOf(fdid));

        if (SUBJECT.equals(sortBy)) Collections.sort(allEformDatas, EFormData.FORM_SUBJECT_COMPARATOR);
        else Collections.sort(allEformDatas, EFormData.FORM_DATE_COMPARATOR);

        ArrayList<HashMap<String, ? extends Object>> results = new ArrayList<HashMap<String, ? extends Object>>();
        try {
            for (EFormData eFormData : allEformDatas) {
                // filter eform by role type
                String tempRole = StringUtils.trimToNull(eFormData.getRoleType());
                if (userRoles != null && tempRole != null) {
                    // ojectName: "_admin,_admin.eform"
                    // roleName: "doctor,admin"
                    String objectName = "_eform." + tempRole;
                    Vector v = OscarRoleObjectPrivilege.getPrivilegeProp(objectName);
                    if (!OscarRoleObjectPrivilege.checkPrivilege(userRoles, (Properties) v.get(0), (Vector) v.get(1))) {
                        continue;
                    }
                }
                HashMap<String, Object> curht = new HashMap<String, Object>();
                curht.put("fdid", eFormData.getId().toString());
                curht.put("fid", eFormData.getFormId().toString());
                curht.put("formName", eFormData.getFormName());
                curht.put("formSubject", eFormData.getSubject());
                curht.put("formDate", eFormData.getFormDate().toString());
                curht.put("formTime", eFormData.getFormTime().toString());
                curht.put("formDateAsDate", eFormData.getFormDate());
                curht.put("roleType", eFormData.getRoleType());
                curht.put("providerNo", eFormData.getProviderNo());
                results.add(curht);
            }
        } catch (Exception sqe) {
            logger.error("Error", sqe);
        }
        return (results);
    }


    public static List<EFormData> listPatientEFormsShowLatestOnly(String demographicNo) {
        //return all current eforms belonging to patient
        //if eform is showLatestFormOnly, return only the latest one

        List<EFormData> list = new ArrayList<EFormData>();
        List<EFormData> currentEForms = eFormDataDao.findByDemographicIdCurrent(NumberUtils.toInt(demographicNo), true);
        if (currentEForms == null) return list;

        for (EFormData eform : currentEForms) {
            if (eform.isShowLatestFormOnly()) {
                if (EFormUtil.isLatestShowLatestFormOnlyPatientForm(eform.getId())) {
                    list.add(eform);
                }
            } else {
                list.add(eform);
            }
        }
        return list;
    }

    public static boolean isLatestShowLatestFormOnlyPatientForm(Integer fdid) {
        return eFormDataDao.isLatestShowLatestFormOnlyPatientForm(fdid);
    }


    @Deprecated
    private static ResultSet getSQL(String sql) {
        ResultSet rs = null;
        try {

            rs = DBHandler.GetSQL(sql);
        } catch (SQLException sqe) {
            logger.error("Error", sqe);
        }
        return (rs);
    }

    private static void setFormStatus(String fid, boolean status) {

        ca.openosp.openo.commn.model.EForm eform = eformDao.find(ConversionUtils.fromIntString(fid));
        if (eform == null) {
            logger.error("Unable to find EForm for " + fid);
            return;
        }
        eform.setCurrent(status);
        eformDao.merge(eform);
    }

    private static String rsGetString(ResultSet rs, String column) throws SQLException {
        // protects agianst null values;
        String thisStr = Misc.getString(rs, column);
        if (thisStr == null) return "";
        return thisStr;
    }

    private static Matcher getAttributeMatcher(String key, String htmlTag, boolean startsWith) {
        Matcher m_return = null;
        if (StringUtils.isBlank(key) || StringUtils.isBlank(htmlTag)) return m_return;

        // Fixed ReDoS vulnerability by removing word boundaries and simplifying the regex
        // This pattern matches HTML attributes in a safer way without polynomial complexity
        Pattern p = Pattern.compile("([a-zA-Z][a-zA-Z0-9_:-]*)(\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+))?", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(htmlTag);

        while (m.find()) {
            // Group 1 contains the attribute name
            String keypart = m.group(1);
            if (keypart == null) continue;
            
            keypart = keypart.trim().toLowerCase();
            key = key.trim().toLowerCase();
            if ((keypart.equals(key)) || (startsWith && keypart.startsWith(key))) {
                m_return = m;
                break;
            }
        }
        return m_return;
    }

    private static String putTemplateValues(ArrayList<String> paramNames, ArrayList<String> paramValues, String template) {
        if (StringUtils.isBlank(template)) return template;

        String tag = "$t{";
        String nwTemplate = "";
        int pointer = 0;
        ArrayList<Integer> fieldBeginList = getFieldIndices(tag, template);
        for (int fieldBegin : fieldBeginList) {
            nwTemplate += template.substring(pointer, fieldBegin);
            int fieldEnd = template.indexOf("}", fieldBegin);
            pointer = fieldEnd + 1;
            String field = template.substring(fieldBegin + tag.length(), fieldEnd);
            if (paramNames.contains(field)) {
                nwTemplate += paramValues.get(paramNames.indexOf(field));
            } else {
                nwTemplate += "";
                logger.debug("Cannot find input name {" + field + "} in eform");
            }
        }
        nwTemplate += template.substring(pointer, template.length());
        return nwTemplate;
    }

    private static String putTemplateEformValues(EForm eForm, String fdid, String path, String template) {
        if (eForm == null || StringUtils.isBlank(template)) return template;

        String[] efields = {"name", "subject", "patient", "providers", "link"};
        String[] eValues = {eForm.getFormName(), eForm.getFormSubject(), eForm.getDemographicNo(), eForm.getProviderNo(), "<a href='" + path + "/eform/efmshowform_data.jsp?fdid=" + fdid + "' target='_blank'>" + eForm.getFormName() + "</a>"};

        String tag = "$te{";
        String nwTemplate = "";
        int pointer = 0;
        ArrayList<Integer> fieldBeginList = getFieldIndices(tag, template);
        for (int fieldBegin : fieldBeginList) {
            nwTemplate += template.substring(pointer, fieldBegin);
            int fieldEnd = template.indexOf("}", fieldBegin);
            pointer = fieldEnd + 1;
            String field = template.substring(fieldBegin + tag.length(), fieldEnd);
            if (field.equalsIgnoreCase("eform.html")) {
                nwTemplate += "$te{eform.html}";
                continue;
            }
            boolean match = false;
            for (int i = 0; i < efields.length; i++) {
                if (field.equalsIgnoreCase("eform." + efields[i])) {
                    nwTemplate += eValues[i];
                    match = true;
                    break;
                }
            }
            if (!match) {
                nwTemplate += "";
                logger.debug("Cannot find input name {" + field + "} in eform");
            }
        }
        nwTemplate += template.substring(pointer, template.length());
        return nwTemplate;
    }

    private static String putTemplateEformHtml(String html, String template) {
        if (StringUtils.isBlank(html) || StringUtils.isBlank(template)) return "";

        html = removeAction(html);
        String tag = "$te{eform.html}";
        String nwTemplate = "";
        int pointer = 0;
        ArrayList<Integer> fieldBeginList = getFieldIndices(tag, template);
        for (int fieldBegin : fieldBeginList) {
            nwTemplate += template.substring(pointer, fieldBegin);
            nwTemplate += html;
            pointer = fieldBegin + tag.length();
        }
        nwTemplate += template.substring(pointer, template.length());
        return nwTemplate;
    }

    private static String removeAction(String html) {
        if (StringUtils.isBlank(html)) return html;

        Pattern p = Pattern.compile("<form[^<>]*>", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(html);
        String nwHtml = "";
        int pointer = 0;
        while (m.find()) {
            nwHtml += html.substring(pointer, m.start());
            String formTag = m.group();
            pointer += m.start() + formTag.length();
            // Fixed ReDoS vulnerability by using possessive quantifiers and atomic groups
            // This regex removes action and other attributes from form tags
            // Using possessive quantifiers (++) and atomic groups to prevent catastrophic backtracking
            p = Pattern.compile("(?:action\\s*+=\\s*+\"[^>\"]*+\"|action\\s*+=\\s*+'[^>']*+'|action\\s*+=\\s*+[^\\s>]++|[a-zA-Z]++\\s*+=\\s*+\"[^>\"]*+\"|[a-zA-Z]++\\s*+=\\s*+'[^>']*+'|[a-zA-Z]++\\s*+=\\s*+[^\\s>]++)", Pattern.CASE_INSENSITIVE);
            m = p.matcher(formTag);
            nwHtml += m.replaceAll("");
        }
        nwHtml += html.substring(pointer, html.length());
        return nwHtml;
    }

    private static void saveCMNote(EForm eForm, String fdid, String programNo, String code, String note) {
        if (StringUtils.isBlank(note)) return;

        CaseManagementNote cNote = createCMNote(eForm.getDemographicNo(), eForm.getProviderNo(), programNo, note);
        if (!StringUtils.isBlank(code)) {
            Set<CaseManagementIssue> scmi = createCMIssue(eForm.getDemographicNo(), code);
            cNote.setIssues(scmi);
        }
        cmm.saveNoteSimple(cNote);
        CaseManagementNoteLink cmLink = new CaseManagementNoteLink(CaseManagementNoteLink.EFORMDATA, Long.valueOf(fdid), cNote.getId());
        cmDao.save(cmLink);
    }

    private static CaseManagementNote createCMNote(String demographicNo, String providerNo, String programNo, String note) {
        CaseManagementNote cmNote = new CaseManagementNote();
        cmNote.setUpdate_date(new Date());
        cmNote.setObservation_date(new Date());
        cmNote.setDemographic_no(demographicNo);
        cmNote.setProviderNo(providerNo);
        cmNote.setSigning_provider_no(providerNo);
        cmNote.setSigned(true);
        cmNote.setHistory("");

        SecRoleDao secRoleDao = (SecRoleDao) SpringUtils.getBean(SecRoleDao.class);
        SecRole doctorRole = secRoleDao.findByName("doctor");
        cmNote.setReporter_caisi_role(doctorRole.getId().toString());

        cmNote.setReporter_program_team("0");
        cmNote.setProgram_no(programNo);
        cmNote.setUuid(UUID.randomUUID().toString());
        cmNote.setNote(note);

        return cmNote;
    }

    private static Set<CaseManagementIssue> createCMIssue(String demographicNo, String code) {
        Issue isu = cmm.getIssueInfoByCode(code);
        CaseManagementIssue cmIssu = cmm.getIssueById(demographicNo, isu.getId().toString());
        if (cmIssu == null) {
            cmIssu = new CaseManagementIssue();
            cmIssu.setDemographic_no(Integer.valueOf(demographicNo));
            cmIssu.setIssue_id(isu.getId());
            cmIssu.setType(isu.getType());
            cmm.saveCaseIssue(cmIssu);
        }

        Set<CaseManagementIssue> sCmIssu = new HashSet<CaseManagementIssue>();
        sCmIssu.add(cmIssu);
        return sCmIssu;
    }

    private static ArrayList<Integer> getFieldIndices(String fieldtag, String s) {
        ArrayList<Integer> fieldIndexList = new ArrayList<Integer>();
        if (StringUtils.isBlank(fieldtag) || StringUtils.isBlank(s)) return fieldIndexList;

        fieldtag = fieldtag.toLowerCase();
        s = s.toLowerCase();

        int from = 0;
        int fieldAt = s.indexOf(fieldtag, from);
        while (fieldAt >= 0) {
            fieldIndexList.add(fieldAt);
            from = fieldAt + 1;
            fieldAt = s.indexOf(fieldtag, from);
        }
        return fieldIndexList;
    }

    private static String getContent(String tag, String template, String dflt) {
        ArrayList<String> contents = getWithin(tag, template);
        if (contents.isEmpty()) return dflt;

        String content = contents.get(0).trim();

        while (content.length() > 0 && Character.isWhitespace(content.charAt(0))) {
            content = content.substring(1);
            content = content.trim();
        }
        while (content.length() > 0 && Character.isWhitespace(content.charAt(content.length() - 1))) {
            content = content.substring(0, content.length() - 1);
            content = content.trim();
        }
        return content;
    }

    private static ArrayList<String> getWithin(String tag, String s) {
        ArrayList<String> within = new ArrayList<String>();
        if (StringUtils.isBlank(tag) || StringUtils.isBlank(s)) return within;

        ArrayList<String> w = getWhole(tag, s);
        for (String whole : w) {
            int begin = getBeginTag(tag, whole).length();
            int end = whole.length() - ("</" + tag + ">").length();
            within.add(whole.substring(begin, end));
        }
        return within;
    }

    private static ArrayList<String> getWhole(String tag, String s) {
        ArrayList<String> whole = new ArrayList<String>();
        if (StringUtils.isBlank(tag) || StringUtils.isBlank(s)) return whole;

        String sBegin = "<" + tag;
        String sEnd = "</" + tag + ">";
        int begin = -1;
        boolean search = true;
        while (search) {
            begin = findIgnoreCase(sBegin, s, begin + 1);
            if (begin == -1) {
                search = false;
                break;
            }
            int end = findIgnoreCase(sEnd, s, begin);

            // search for middle begin tags; if found, extend end tag to include complete begin-end within
            int mid_begin = findIgnoreCase(sBegin, s, begin + 1);
            int count_mbegin = 0;
            while (mid_begin >= 0 && mid_begin < end) {
                count_mbegin++;
                mid_begin = findIgnoreCase(sBegin, s, mid_begin + 1);
            }
            while (count_mbegin > 0) {
                end = findIgnoreCase(sEnd, s, end + 1);
                if (end >= 0) count_mbegin--;
                else count_mbegin = -1;
            }
            // end search

            if (begin >= 0 && end >= 0) {
                whole.add(s.substring(begin, end + sEnd.length()));
            }
        }
        return whole;
    }

    private static String getBeginTag(String tag, String template) {
        if (StringUtils.isBlank(tag) || StringUtils.isBlank(template)) return "";

        Pattern p = Pattern.compile("<" + tag + "[^<>]*>", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(template);
        return m.find() ? m.group() : "";
    }

    private static String[] getSentList(String msgtxt) {
        String sentListTxt = getContent("sendto", msgtxt, "");
        String[] sentList = sentListTxt.split(",");
        for (int i = 0; i < sentList.length; i++) {
            sentList[i] = sentList[i].trim();
        }
        return sentList;
    }

    private static void sortByProviderName(List<EFormData> allEformDatas) {
        if (allEformDatas == null || allEformDatas.isEmpty()) return;

        for (int i = allEformDatas.size() - 1; i > 0; i--) {

            boolean swapped = false;
            for (int j = 0; j < i; j++) {
                String providerName = providerDao.getProviderNameLastFirst(allEformDatas.get(j).getProviderNo());
                String providerNamePlus = providerDao.getProviderNameLastFirst(allEformDatas.get(j + 1).getProviderNo());

                if (providerName.compareToIgnoreCase(providerNamePlus) > 0) {
                    EFormData tmp = allEformDatas.get(j);
                    allEformDatas.set(j, allEformDatas.get(j + 1));
                    allEformDatas.set(j + 1, tmp);
                    swapped = true;
                }
            }
            if (!swapped) break;
        }
    }

    private static String getEqualIgnoreCase(ArrayList<String> lst, String str) {
        if (lst == null || lst.isEmpty() || StringUtils.isBlank(str)) return null;

        for (String strLst : lst) {
            if (str.trim().equalsIgnoreCase(strLst.trim())) return strLst.trim();
        }
        return null;
    }

    /**
     * Local EFormData Factory
     */
    public static EFormData toEFormData(EForm eForm) {
        EFormData eFormData = new EFormData();
        eFormData.setFormId(Integer.parseInt(eForm.getFid()));
        eFormData.setFormName(eForm.getFormName());
        eFormData.setSubject(eForm.getFormSubject());
        eFormData.setDemographicId(Integer.parseInt(eForm.getDemographicNo()));
        eFormData.setCurrent(true);
        eFormData.setFormDate(new Date());
        eFormData.setFormTime(eFormData.getFormDate());
        eFormData.setProviderNo(eForm.getProviderNo());
        eFormData.setFormData(eForm.getFormHtml());
        eFormData.setShowLatestFormOnly(eForm.isShowLatestFormOnly());
        eFormData.setPatientIndependent(eForm.isPatientIndependent());
        eFormData.setRoleType(eForm.getRoleType());

        return (eFormData);
    }

    /**
     * Errors found during the eForm runtime are logged into the eForm
     * table and a status of unstable is set.
     * These logs are added in order to track and pre-emptively discourage use of poorly
     * developed eForms that are imported from external sources.
     */
    public static void logError(int formId, String error) {
        ca.openosp.openo.commn.model.EForm eform = eformDao.findById(formId);

        /*
         * DEFAULT is always stable = true
         * Reaching this point indicates an error. This form is not stable.
         * This status can be reversed by deleting this entry and then uploading
         * a new stable eform
         */
        eform.setStable(Boolean.FALSE);

//		/*
//		 * Logs are stored and compared with JSON.
//		 */
//		if(error != null && ! error.isEmpty()) {
//			String currentErrors = eform.getErrorLog();
//			JSONArray jsonArray;
//			if(currentErrors == null || currentErrors.isEmpty()) {
//				jsonArray = objectMapper.createArrayNode();
//				jsonArray.add(error);
//			} else {
//				jsonArray = objectMapper.valueToTree(currentErrors);
//				boolean addError = true;
//				for(Object jsonArrayObject : jsonArray) {
//					if(((String)jsonArrayObject).equalsIgnoreCase(error)) {
//						addError = false;
//						break;
//					}
//				}
//				if(addError) {
//					jsonArray.add(error);
//				}
//			}
//			eform.setErrorLog(jsonArray.toString());
//		}

        eformDao.merge(eform);
    }
}
