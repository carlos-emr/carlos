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


package io.github.carlos_emr.carlos.form.data;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.dao.StudyDao;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.EncounterFormDao;
import io.github.carlos_emr.carlos.commn.model.EncounterForm;
import io.github.carlos_emr.carlos.encounter.data.EctFormData;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.util.UtilDateUtilities;

public class FrmData {
    private static final Logger _log = MiscUtils.getLogger();
    private static final String FORM_TABLE_IDENTIFIER_PATTERN = "[a-zA-Z]\\w*";
    private static EncounterFormDao encounterFormDao() { return SpringUtils.getBean(EncounterFormDao.class); }

    public class Form {
        private String formName;
        private String formPage;
        private String formTable;

        public Form(String formName, String formPage, String formTable) {
            this.formName = formName;
            this.formPage = formPage;
            this.formTable = formTable;
        }

        public String getFormName() {
            return formName;
        }

        public String getFormPage() {
            return formPage;
        }

        public String getFormTable() {
            return formTable;
        }
    }

    public Form[] getForms() {

        List<EncounterForm> results = encounterFormDao().findAll();

        ArrayList<Form> forms = new ArrayList<Form>();
        for (EncounterForm encounterForm : results) {
            if (EctFormData.isRemovedCaisiForm(encounterForm.getFormName())) {
                continue;
            }

            Form frm = new Form(encounterForm.getFormName(), encounterForm.getFormValue(), encounterForm.getFormTable());
            forms.add(frm);
        }

        return (forms.toArray(new Form[0]));
    }

    public class PatientForm {
        private String formId;
        private String demoNo;
        private String created;
        private String edited;

        public PatientForm(String formId, String demoNo, String created, String edited) {
            this.formId = formId;
            this.demoNo = demoNo;
            this.created = created;
            this.edited = edited;
        }

        public String getFormId() {
            return formId;
        }

        public String getDemoNo() {
            return demoNo;
        }

        public String getCreated() {
            return created;
        }

        public String getEdited() {
            return edited;
        }
    }

    public PatientForm[] getPatientForms(String demoNo, String table) throws SQLException {
        ArrayList<PatientForm> forms = new ArrayList<PatientForm>();

        // Validate table name: must be an alphanumeric identifier (no SQL-special characters)
        if (table == null || !table.matches(FORM_TABLE_IDENTIFIER_PATTERN)) {
            throw new IllegalArgumentException("Invalid form table name");
        }
        for (EncounterFormDao.HistoryRow row : encounterFormDao().findPatientHistory(table, demoNo, false)) {
            forms.add(new PatientForm(Integer.toString(row.id()), Integer.toString(row.demographicNo()),
                    UtilDateUtilities.DateToString(row.created(), "yy/MM/dd"),
                    UtilDateUtilities.DateToString(row.edited(), "yy/MM/dd")));
        }

        PatientForm[] ret = {};
        ret = forms.toArray(ret);
        return ret;
    }

    public PatientForm getCurrentPatientForm(String demoNo, String studyNo) throws SQLException {
        PatientForm frm = null;

        String table = StringUtils.trimToEmpty(SpringUtils.getBean(StudyDao.class).findFormTable(studyNo));
        if (table.isEmpty()) return null;
        EncounterFormDao.HistoryRow row = encounterFormDao().findNewestPatientHistory(table, demoNo);
        if (row != null) {
            frm = new PatientForm(Integer.toString(row.id()), Integer.toString(row.demographicNo()),
                    UtilDateUtilities.DateToString(row.created(), "yy/MM/dd"),
                    UtilDateUtilities.DateToString(row.edited(), "yy/MM/dd"));
        }
        return frm;
    }

    public String[] getStudyNameLink(String studyNo) throws java.sql.SQLException {
        StudyDao.StudyLink link = SpringUtils.getBean(StudyDao.class).findLink(studyNo);
        return link == null ? new String[2] : new String[]{StringUtils.trimToEmpty(link.name()), StringUtils.trimToEmpty(link.link())};
    }

    //ret[0] = form path;   ret[1] = formId
    public String[] getShortcutFormValue(String demoNo, String formName) throws java.sql.SQLException {
        String[] ret = new String[2];
        String table = null;

        EncounterFormDao encounterFormDao = (EncounterFormDao) SpringUtils.getBean(EncounterFormDao.class);
        List<EncounterForm> forms = encounterFormDao().findByFormName(formName);

        for (EncounterForm encounterForm : forms) {
            ret[0] = encounterForm.getFormValue();
            table = encounterForm.getFormTable();
        }

        // No matching EncounterForm found — return safe defaults so callers don't NPE
        if (table == null) {
            ret[0] = "";
            ret[1] = "0";
            return ret;
        }

        // Defense-in-depth: validate table name from DB (consistent with getPatientForms / getCurrentPatientForm)
        if (!table.isEmpty() && !table.matches(FORM_TABLE_IDENTIFIER_PATTERN)) {
            throw new IllegalArgumentException("Invalid form table name returned from database");
        }

        ret[1] = "0";
        if ("form".equals(table)) {
            String searchFormName = formName;
            if (searchFormName.equals("AR1"))
                searchFormName = "ar1_99_12"; // quick hack for ease of migration from old forms to new
            if (searchFormName.equals("AR2")) searchFormName = "ar2_99_08"; // ditto
            ret[1] = encounterFormDao().findLatestPatientFormId(table, demoNo, searchFormName, false);
            String[] xmlForm = ret.clone();

            if (formName.equals("AR1")) {
                //First check to see if there are records for 2005

                ret = getShortcutFormValue(demoNo, "AR2005");
                MiscUtils.getLogger().debug("ret[0] is: " + ret[0]);
                String[] foo = ret[0].split(".jsp");
                ret[0] = foo[0] + "pg1.jsp" + foo[1];
                MiscUtils.getLogger().debug("getShortcutFormValue forwarding new AR1 to: " + ret[0]);
                String[] first_pick = ret.clone();

                //Check if AR has any forms
                if (ret[1].equals("0")) {
                    ret = getShortcutFormValue(demoNo, "AR");
                    MiscUtils.getLogger().debug("ret[0] is: " + ret[0]);
                    foo = ret[0].split(".jsp");
                    ret[0] = foo[0] + "pg1.jsp" + foo[1];
                    MiscUtils.getLogger().debug("getShortcutFormValue forwarding new AR1 to: " + ret[0]);

                    if (ret[1].equals("0") && !xmlForm[1].equals("0")) { // Nothing found in AR but there are records in the old xml form
                        ret = xmlForm;
                    } else if (ret[1].equals("0")) {  // Nothing in either old form Go with new one
                        ret = first_pick;
                    }
                }

            } else if (formName.equals("AR2")) {
                ret = getShortcutFormValue(demoNo, "AR");
                String[] foo = ret[0].split(".jsp");
                ret[0] = foo[0] + "pg2.jsp" + foo[1];
                MiscUtils.getLogger().debug("getShortcutFormValue forwarding new AR2 to: " + ret[0]);
                String[] first_pick = ret.clone();
                if (ret[1].equals("0")) {
                    ret = getShortcutFormValue(demoNo, "AR");
                    foo = ret[0].split(".jsp");
                    ret[0] = foo[0] + "pg2.jsp" + foo[1];
                    MiscUtils.getLogger().debug("getShortcutFormValue forwarding new AR2 to: " + ret[0]);
                    if (ret[1].equals("0") && !xmlForm[1].equals("0")) { // Nothing found in AR but there are records in the old xml form
                        ret = xmlForm;
                    } else if (ret[1].equals("0")) {  // Nothing in either old form Go with new one
                        ret = first_pick;
                    }
                }
            }


        } else if ("".equals(table)) {
            ret[1] = "0";
        } else {
            ret[1] = encounterFormDao().findLatestPatientFormId(table, demoNo, null, true);
        }

        _log.debug("RETURNING " + ret[0] + " = " + ret[1]);
        return ret;
    }

    public String getResource() throws java.sql.SQLException {
        String ret = "";


        for (var property : SpringUtils.getBean(PropertyDao.class).findByName("resource")) {
            ret = StringUtils.trimToEmpty(property.getValue());
        }

        if (ret.compareTo("") == 0)
            ret = "http://resource.oscarmcmaster.org/oscarResource/";
        return ret;

    }

    public String getResource(String name) throws java.sql.SQLException {
        String ret = "";


        for (var property : SpringUtils.getBean(PropertyDao.class).findByName(name)) {
            ret = StringUtils.trimToEmpty(property.getValue());
        }

        return ret;
    }

}
