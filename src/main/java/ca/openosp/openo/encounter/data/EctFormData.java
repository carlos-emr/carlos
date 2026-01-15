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


package ca.openosp.openo.encounter.data;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import javax.persistence.PersistenceException;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.PMmodule.caisi_integrator.CaisiIntegratorManager;
import ca.openosp.openo.PMmodule.caisi_integrator.IntegratorFallBackManager;
import ca.openosp.openo.caisi_integrator.ws.CachedDemographicForm;
import ca.openosp.openo.caisi_integrator.ws.DemographicWs;
import ca.openosp.openo.commn.dao.EncounterFormDao;
import ca.openosp.openo.commn.model.EncounterForm;
import ca.openosp.openo.utility.DbConnectionFilter;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.util.SqlUtils;

public class EctFormData {

    private static Logger logger = MiscUtils.getLogger();
    private static EncounterFormDao encounterFormDao = (EncounterFormDao) SpringUtils.getBean(EncounterFormDao.class);

    public static final String DATE_FORMAT = "dd-MM-yyyy";
    public static final String DATETIME_FORMAT = "dd-MM-yyyy HH:mm:ss";

    public static Form[] getForms() {
        List<EncounterForm> results = encounterFormDao.findAll();
        Collections.sort(results, EncounterForm.BC_FIRST_COMPARATOR);

        ArrayList<Form> forms = new ArrayList<Form>();
        for (EncounterForm encounterForm : results) {
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
            String table = StringUtils.trimToNull(encounterForm.getFormTable());
            if (table != null) {
                allResults.addAll(getGroupedPatientFormsAsArrayList(demographicId.toString(), encounterForm.getFormName(), table, encounterForm.getFormValue()));
            }
        }

        return (allResults);
    }

    public static ArrayList<PatientForm> getGroupedPatientFormsAsArrayList(String demoNo, String formName, String table, String jsp) {
        table = StringUtils.trimToNull(table);
        if (table == null) return (new ArrayList<PatientForm>());

        ArrayList<PatientForm> forms = new ArrayList<PatientForm>();

        Connection c = null;
        try {
            c = DbConnectionFilter.getThreadLocalDbConnection();

            if (!table.equals("form")) {
                String sql = "SELECT max(ID) ID, demographic_no, formCreated, date(formEdited) 'lastEdited', max(formEdited) 'frmEdited' FROM " + table + " WHERE demographic_no=? group by lastEdited";

                java.sql.PreparedStatement ps = c.prepareStatement(sql);
                ps.setInt(1, Integer.parseInt(demoNo));
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    PatientForm frm = new PatientForm(formName, rs.getInt("ID"), rs.getInt("demographic_no"), rs.getDate("formCreated"), rs.getTimestamp("frmEdited"), jsp);
                    forms.add(frm);
                }
            } else {
                String sql = "SELECT form_no, demographic_no, form_date from " + table + " where demographic_no=? order by form_no desc";

                java.sql.PreparedStatement ps = c.prepareStatement(sql);
                ps.setInt(1, Integer.parseInt(demoNo));
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    PatientForm frm = new PatientForm(formName, rs.getInt("form_no"), rs.getInt("demographic_no"), rs.getDate("form_date"), rs.getDate("form_date"));
                    forms.add(frm);
                }
            }
        } catch (SQLException e) {
            logger.error("Unexpected error.", e);
            throw (new PersistenceException(e));
        } finally {
            SqlUtils.closeResources(c, null, null);
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
            String table = StringUtils.trimToNull(encounterForm.getFormTable());
            if (table != null) {
                allResults.addAll(getPatientFormsAsArrayList(demographicId.toString(), encounterForm.getFormName(), table));
            }
        }

        return (allResults);
    }

    public static ArrayList<PatientForm> getPatientFormsAsArrayList(String demoNo, String formName, String table) {
        table = StringUtils.trimToNull(table);
        if (table == null) return (new ArrayList<PatientForm>());

        ArrayList<PatientForm> forms = new ArrayList<PatientForm>();

        Connection c = null;
        try {
            c = DbConnectionFilter.getThreadLocalDbConnection();

            if (!table.equals("form")) {
                String sql = "SELECT ID, demographic_no, formCreated, formEdited FROM " + table + " WHERE demographic_no=? ORDER BY ID DESC";

                java.sql.PreparedStatement ps = c.prepareStatement(sql);
                ps.setInt(1, Integer.parseInt(demoNo));
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    PatientForm frm = new PatientForm(formName, rs.getInt("ID"), rs.getInt("demographic_no"), rs.getDate("formCreated"), rs.getTimestamp("formEdited"));

                    // identify the source table for this form
                    frm.setTable(table);

                    forms.add(frm);
                }
            } else {
                String sql = "SELECT form_no, demographic_no, form_date from " + table + " where demographic_no=? order by form_no desc";

                java.sql.PreparedStatement ps = c.prepareStatement(sql);
                ps.setInt(1, Integer.parseInt(demoNo));
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    PatientForm frm = new PatientForm(formName, rs.getInt("form_no"), rs.getInt("demographic_no"), rs.getDate("form_date"), rs.getDate("form_date"));

                    // identify the source table for this form
                    frm.setTable(table);

                    forms.add(frm);
                }
            }
        } catch (SQLException e) {
            logger.error("Unexpected error.", e);
            throw (new PersistenceException(e));
        } finally {
            SqlUtils.closeResources(c, null, null);
        }

        return (forms);
    }

    public static ArrayList<PatientForm> getRemotePatientForms(LoggedInInfo loggedInInfo, Integer demographicId, String formName, String table) {
        ArrayList<PatientForm> forms = new ArrayList<PatientForm>();
        List<CachedDemographicForm> remoteForms = null;
        table = StringUtils.trimToNull(table);
        if (table == null) return (new ArrayList<PatientForm>());

        try {
            if (!loggedInInfo.getCurrentFacility().isIntegratorEnabled()) return (forms);
            if (!CaisiIntegratorManager.isIntegratorOffline(loggedInInfo.getSession())) {
                DemographicWs demographicWs = CaisiIntegratorManager.getDemographicWs(loggedInInfo, loggedInInfo.getCurrentFacility());
                remoteForms = demographicWs.getLinkedCachedDemographicForms(demographicId, table);
            }
        } catch (Exception e) {
            logger.error("Error retriving remote forms :" + CaisiIntegratorManager.isIntegratorOffline(loggedInInfo.getSession()), e);
            CaisiIntegratorManager.checkForConnectionError(loggedInInfo.getSession(), e);
        }


        if (CaisiIntegratorManager.isIntegratorOffline(loggedInInfo.getSession())) {
            remoteForms = IntegratorFallBackManager.getRemoteForms(loggedInInfo, demographicId, table);
        }

        if (remoteForms == null) return (forms);

        for (CachedDemographicForm cachedDemographicForm : remoteForms) {
            Date date = cachedDemographicForm.getEditDate().getTime();
            PatientForm frm = new PatientForm(formName, cachedDemographicForm.getFacilityIdIntegerCompositePk().getCaisiItemId(), cachedDemographicForm.getCaisiDemographicId(), date, date);
            frm.setRemoteFacilityId(cachedDemographicForm.getFacilityIdIntegerCompositePk().getIntegratorFacilityId());
            forms.add(frm);
        }
        return (forms);
    }

    public static PatientForm[] getPatientForms(String demoNo, String table) {
        return (getPatientFormsAsArrayList(demoNo, null, table).toArray(new PatientForm[0]));
    }

    public static PatientForm[] getPatientFormsFromLocalAndRemote(LoggedInInfo loggedInInfo, String demoNo, String table) {
        ArrayList<PatientForm> results = getPatientFormsAsArrayList(demoNo, null, table);

        if (loggedInInfo.getCurrentFacility().isIntegratorEnabled()) {
            try {
                ArrayList<PatientForm> remoteResults = getRemotePatientForms(loggedInInfo, Integer.parseInt(demoNo), null, table);
                results.addAll(remoteResults);
            } catch (Exception e) {
                logger.error("Retrieving remote forms failed", e);
            }
        }

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
        private Integer remoteFacilityId;
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

        public Integer getRemoteFacilityId() {
            return (remoteFacilityId);
        }

        public void setRemoteFacilityId(Integer remoteFacilityId) {
            this.remoteFacilityId = remoteFacilityId;
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
