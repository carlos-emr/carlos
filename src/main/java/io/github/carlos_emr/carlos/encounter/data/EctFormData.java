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


package io.github.carlos_emr.carlos.encounter.data;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.persistence.PersistenceException;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.EncounterFormDao;
import io.github.carlos_emr.carlos.commn.model.EncounterForm;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

// FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
@SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
public class EctFormData {

    private static final Pattern FORM_TABLE_NAME = Pattern.compile("^\\w+$");
    private static final String LEGACY_FORM_SQL = "SELECT form_no, demographic_no, form_date from form where demographic_no=? order by form_no desc";
    // Legacy built-in form table that is valid but not registered in encounterForm.
    private static final Set<String> INTERNAL_FORM_TABLES = Set.of("formGrowth0_36");
    private static Logger logger = MiscUtils.getLogger();
    private static EncounterFormDao encounterFormDao = (EncounterFormDao) SpringUtils.getBean(EncounterFormDao.class);

    public static final String DATE_FORMAT = "dd-MM-yyyy";
    public static final String DATETIME_FORMAT = "dd-MM-yyyy HH:mm:ss";

    private static final List<String> REMOVED_CAISI_FORM_NAMES = Arrays.asList(
            "Counsellor Assessment",
            "Discharge Summary",
            "Reception Assessment"
    );

    public static boolean isRemovedCaisiForm(String formName) {
        return REMOVED_CAISI_FORM_NAMES.stream().anyMatch(removedName -> removedName.equalsIgnoreCase(formName));
    }

    public static Form[] getForms() {
        List<EncounterForm> results = encounterFormDao.findAll();
        Collections.sort(results, EncounterForm.BC_FIRST_COMPARATOR);

        ArrayList<Form> forms = new ArrayList<Form>();
        for (EncounterForm encounterForm : results) {
            if (isRemovedCaisiForm(encounterForm.getFormName())) {
                continue;
            }

            Form frm = new Form(encounterForm.getFormName(), encounterForm.getFormValue(), encounterForm.getFormTable(), encounterForm.isHidden());
            forms.add(frm);
        }

        return (forms.toArray(new Form[0]));

    }

    public static class Form {
        private String formName;
        private String formPage;
        private String formTable;
        private boolean hidden;

        // Constructor
        public Form(String formName, String formPage, String formTable, boolean hidden) {
            this.formName = formName;
            this.formPage = formPage;
            this.formTable = formTable;
            this.hidden = hidden;
        }

        public Form() {
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

        public boolean isHidden() {
            return hidden;
        }

    }

    public static ArrayList<PatientForm> getGroupedPatientFormsFromAllTables(Integer demographicId) {
        // grab all of the forms
        EncounterFormDao encounterFormDao = (EncounterFormDao) SpringUtils.getBean(EncounterFormDao.class);
        List<EncounterForm> encounterForms = encounterFormDao.findAll();
        Collections.sort(encounterForms, EncounterForm.BC_FIRST_COMPARATOR);

        // grab patient forms for all the above form types grouped by date of edit
        ArrayList<PatientForm> allResults = new ArrayList<PatientForm>();
        for (EncounterForm encounterForm : encounterForms) {
            if (isRemovedCaisiForm(encounterForm.getFormName())) {
                continue;
            }

            String table = StringUtils.trimToNull(encounterForm.getFormTable());
            if (table != null) {
                allResults.addAll(getGroupedPatientFormsAsArrayList(demographicId.toString(), encounterForm.getFormName(), table, encounterForm.getFormValue()));
            }
        }

        return (allResults);
    }

    public static ArrayList<PatientForm> getGroupedPatientFormsAsArrayList(String demoNo, String formName, String table, String jsp) {
        String trustedTable = validateFormTable(table);
        if (trustedTable == null) return (new ArrayList<PatientForm>());

        ArrayList<PatientForm> forms = new ArrayList<PatientForm>();
        Integer demographicNo = parseDemographicNo(demoNo);
        if (demographicNo == null) return forms;

        try {
            if (!trustedTable.equals("form")) {
                String sql = groupedFormTableSql(trustedTable);

                try (ResultSet rs = LegacyJdbcQuery.getPreparedResultSet(
                        LegacyJdbcQuery.trustedSelectSql(sql), demographicNo)) {
                    while (rs.next()) {
                        PatientForm frm = new PatientForm(formName, rs.getInt("ID"), rs.getInt("demographic_no"), rs.getDate("formCreated"), rs.getTimestamp("frmEdited"), jsp);
                        forms.add(frm);
                    }
                }
            } else {
                try (ResultSet rs = LegacyJdbcQuery.getPreparedResultSet(LEGACY_FORM_SQL, demographicNo)) {
                    while (rs.next()) {
                        PatientForm frm = new PatientForm(formName, rs.getInt("form_no"), rs.getInt("demographic_no"), rs.getDate("form_date"), rs.getDate("form_date"));
                        forms.add(frm);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Unexpected error.", e);
            throw (new PersistenceException(e));
        }

        return (forms);
    }

    public static ArrayList<PatientForm> getAllPatientFormsFromAllTables(Integer demographicId) {
        // grab all of the forms
        EncounterFormDao encounterFormDao = (EncounterFormDao) SpringUtils.getBean(EncounterFormDao.class);
        List<EncounterForm> encounterForms = encounterFormDao.findAll();
        Collections.sort(encounterForms, EncounterForm.BC_FIRST_COMPARATOR);

        // grab all patient forms for all the above form types
        ArrayList<PatientForm> allResults = new ArrayList<PatientForm>();
        for (EncounterForm encounterForm : encounterForms) {
            if (isRemovedCaisiForm(encounterForm.getFormName())) {
                continue;
            }

            String table = StringUtils.trimToNull(encounterForm.getFormTable());
            if (table != null) {
                allResults.addAll(getPatientFormsAsArrayList(demographicId.toString(), encounterForm.getFormName(), table));
            }
        }

        return (allResults);
    }

    public static ArrayList<PatientForm> getPatientFormsAsArrayList(String demoNo, String formName, String table) {
        String trustedTable = validateFormTable(table);
        if (trustedTable == null) return (new ArrayList<PatientForm>());

        ArrayList<PatientForm> forms = new ArrayList<PatientForm>();
        Integer demographicNo = parseDemographicNo(demoNo);
        if (demographicNo == null) return forms;

        try {
            if (!trustedTable.equals("form")) {
                String sql = patientFormTableSql(trustedTable);

                try (ResultSet rs = LegacyJdbcQuery.getPreparedResultSet(
                        LegacyJdbcQuery.trustedSelectSql(sql), demographicNo)) {
                    while (rs.next()) {
                        PatientForm frm = new PatientForm(formName, rs.getInt("ID"), rs.getInt("demographic_no"), rs.getDate("formCreated"), rs.getTimestamp("formEdited"));

                        // identify the source table for this form
                        frm.setTable(trustedTable);

                        forms.add(frm);
                    }
                }
            } else {
                try (ResultSet rs = LegacyJdbcQuery.getPreparedResultSet(LEGACY_FORM_SQL, demographicNo)) {
                    while (rs.next()) {
                        PatientForm frm = new PatientForm(formName, rs.getInt("form_no"), rs.getInt("demographic_no"), rs.getDate("form_date"), rs.getDate("form_date"));

                        // identify the source table for this form
                        frm.setTable(trustedTable);

                        forms.add(frm);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Unexpected error.", e);
            throw (new PersistenceException(e));
        }

        return (forms);
    }

    private static Integer parseDemographicNo(String demoNo) {
        try {
            return Integer.valueOf(demoNo);
        } catch (NumberFormatException e) {
            logger.warn("Invalid demographic_no for form lookup: {}", LogSafe.sanitize(demoNo));
            return null;
        }
    }

    private static String validateFormTable(String table) {
        String normalizedTable = StringUtils.trimToNull(table);
        if (normalizedTable == null) {
            return null;
        }
        if (!FORM_TABLE_NAME.matcher(normalizedTable).matches()) {
            if (logger.isWarnEnabled()) {
                logger.warn("Rejected invalid encounter form table name: {}", LogSafe.sanitize(normalizedTable));
            }
            return null;
        }
        if ("form".equals(normalizedTable) || INTERNAL_FORM_TABLES.contains(normalizedTable) || isKnownEncounterFormTable(normalizedTable)) {
            return normalizedTable;
        }
        if (logger.isWarnEnabled()) {
            logger.warn("Rejected unknown encounter form table name: {}", LogSafe.sanitize(normalizedTable));
        }
        return null;
    }

    private static String groupedFormTableSql(String trustedTable) {
        return "SELECT max(ID) ID, demographic_no, formCreated, date(formEdited) 'lastEdited', max(formEdited) 'frmEdited' FROM " + trustedTable + " WHERE demographic_no=? group by lastEdited";
    }

    private static String patientFormTableSql(String trustedTable) {
        return "SELECT ID, demographic_no, formCreated, formEdited FROM " + trustedTable + " WHERE demographic_no=? ORDER BY ID DESC";
    }

    private static boolean isKnownEncounterFormTable(String table) {
        // EncounterFormDaoImpl caches form-table lookups; keep validation here as the boundary.
        return !encounterFormDao.findByFormTable(table).isEmpty();
    }

    public static PatientForm[] getPatientForms(String demoNo, String table) {
        return (getPatientFormsAsArrayList(demoNo, null, table).toArray(new PatientForm[0]));
    }

    public static PatientForm[] getPatientFormsFromLocalAndRemote(LoggedInInfo loggedInInfo, String demoNo, String table) {
        ArrayList<PatientForm> results = getPatientFormsAsArrayList(demoNo, null, table);

        Collections.sort(results, PatientForm.CREATED_DATE_COMPARATOR);

        return (results.toArray(new PatientForm[0]));
    }

    public static PatientForm[] getPatientFormsFromLocalAndRemote(LoggedInInfo loggedInInfo, String demoNo, String table, Boolean sortByEdited) {
        PatientForm[] results = getPatientFormsFromLocalAndRemote(loggedInInfo, demoNo, table);

        if (sortByEdited) {
            Collections.sort(Arrays.asList(results), PatientForm.EDITED_DATE_COMPARATOR);
        }


        return (results);
    }

    /**
     * Due to backwards compatability hack, leave all the getter methods as returning String, direct field access can be used to get native types.
     */
    public static class PatientForm {

        private SimpleDateFormat dateTimeFormat = new SimpleDateFormat(EctFormData.DATETIME_FORMAT);
        private SimpleDateFormat dateFormat = new SimpleDateFormat(EctFormData.DATE_FORMAT);

        /**
         * This comparator sorts PatientForm descending based on the created date
         */
        public static final Comparator<PatientForm> CREATED_DATE_COMPARATOR = new Comparator<PatientForm>() {
            public int compare(PatientForm o1, PatientForm o2) {
                if (o2.created == null && o1.created == null) {
                    return o2.edited.compareTo(o1.edited);
                }
                if (o2.created == null && o1.created != null) {
                    return o2.edited.compareTo(o1.created);
                }
                if (o1.created == null && o2.created != null) {
                    return o2.created.compareTo(o1.edited);
                }

                return (o2.created.compareTo(o1.created));
            }
        };
        public static final Comparator<PatientForm> EDITED_DATE_COMPARATOR = new Comparator<PatientForm>() {
            public int compare(PatientForm o1, PatientForm o2) {
                if (o2.edited == null && o1.edited == null) {
                    return o2.created.compareTo(o1.created);
                }
                if (o2.edited == null && o1.edited != null) {
                    return o2.created.compareTo(o1.edited);
                }
                if (o1.edited == null && o2.edited != null) {
                    return o2.edited.compareTo(o1.created);
                }


                return (o2.edited.compareTo(o1.edited));
            }
        };

        public Integer formId;
        public Integer demographicId;
        public Date created;
        public Date edited;
        public String formName;
        public String jsp;
        public String table;

        // Constructor
        public PatientForm(String table, String formName, Integer formId, Integer demographicId) {
            this.table = table;
            this.formName = formName;
            this.formId = formId;
            this.demographicId = demographicId;
        }

        // Constructor
        public PatientForm(String formName, Integer formId, Integer demographicId, Date created, Date edited) {
            this.formName = formName;
            this.formId = formId;
            this.demographicId = demographicId;
            this.created = created;
            this.edited = edited;
        }

        // Constructor
        public PatientForm(String table, String formName, Integer formId, Integer demographicId, Date created, Date edited) {
            this.table = table;
            this.formName = formName;
            this.formId = formId;
            this.demographicId = demographicId;
            this.created = created;
            this.edited = edited;
        }

        // Constructor
        public PatientForm(String formName, Integer formId, Integer demographicId, Date created, Date edited, String jsp) {
            this.formName = formName;
            this.formId = formId;
            this.demographicId = demographicId;
            this.created = created;
            this.edited = edited;

            if (jsp.indexOf("/") != -1) {
                jsp = jsp.substring(jsp.indexOf("/"));
            }
            this.jsp = jsp + String.valueOf(demographicId) + "&formId=" + String.valueOf(formId);
        }

        public String getFormId() {
            return formId.toString();
        }

        public String getDemoNo() {
            return demographicId.toString();
        }

        public String getCreated() {
            if (created == null) {
                return null;
            }
            return dateFormat.format(created);
        }

        public String getEdited() {
            return dateTimeFormat.format(edited);
        }

        public String getFormName() {
            return formName;
        }

        public void setFormName(String formName) {
            this.formName = formName;
        }

        /**
         * Get the database table name where this PatientForm is stored.
         * This table name can be used to fetch the form data.
         */
        public String getTable() {
            return table;
        }

        /**
         * Set the database table name where this PatientForm is stored.
         */
        public void setTable(String table) {
            this.table = table;
        }

    }
}
