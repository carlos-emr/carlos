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


package io.github.carlos_emr.carlos.provider.web;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.entities.Provider;
import org.apache.struts2.ActionSupport;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.model.EForm;
import io.github.carlos_emr.carlos.commn.model.EncounterForm;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.billings.ca.bc.MSP.MSPReconcile;
import io.github.carlos_emr.carlos.util.LabelValueBean;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import io.github.carlos_emr.carlos.managers.SecurityManager;
import java.util.*;

/**
 * Struts2 action for the legacy provider preferences page.
 *
 * <p>Manages a wide range of provider-configurable settings including schedule hours,
 * prescription defaults (Rx3, page size, DOB display), clinical defaults (sex, HC type),
 * encounter form selections, eForm favorites, consultation team warnings, workload management,
 * and password changes. Preferences are stored as {@link UserProperty} records keyed by
 * {@code "pref.<property_name>"}.</p>
 *
 * <p>Also provides static utility methods for constructing HTML form elements (selects,
 * checkboxes) used by the preference JSP views, and reference data lists for Canadian
 * provinces, billing service types, schedule periods, and provider teams.</p>
 *
 * @since 2026-03-17
 */
public class UserPreference2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private Logger logger = MiscUtils.getLogger();
    protected SecurityDao securityDao = SpringUtils.getBean(SecurityDao.class);
    protected UserPropertyDAO userPropertyDao = SpringUtils.getBean(UserPropertyDAO.class);
    static Map<String, String> defaults = new HashMap<String, String>();
    protected Map<String, String> siteDefaults = new HashMap<String, String>();
    private boolean inited = false;

    private final SecurityManager securityManager = SpringUtils.getBean(SecurityManager.class);
	
    static {
        defaults.put("pref." + UserProperty.SCHEDULE_START_HOUR, "8");
        defaults.put("pref." + UserProperty.SCHEDULE_END_HOUR, "18");
        defaults.put("pref." + UserProperty.SCHEDULE_PERIOD, "15");
        defaults.put("pref." + UserProperty.NEW_CME, "Enabled");
        defaults.put("pref." + UserProperty.ENCOUNTER_FORM_LENGTH, "3");
        defaults.put("pref." + UserProperty.RX_USE_RX3, "yes");
    }

    /**
     * Retrieves a request parameter, returning an empty string if the parameter is absent.
     *
     * @param request {@link HttpServletRequest} the current HTTP request
     * @param name String the parameter name to retrieve
     * @return String the parameter value, or empty string if null
     */
    public String getParameter(HttpServletRequest request, String name) {
        String val = request.getParameter(name);
        if (val == null) {
            return new String();
        }
        return val;
    }


    /**
     * Loads site-level preference defaults from the {@code /WEB-INF/classes/pref.defaults} file.
     */
    protected void init() {
        try {
            InputStream in = ServletActionContext.getServletContext().getResourceAsStream("/WEB-INF/classes/pref.defaults");
            Properties p = new Properties();
            p.load(in);

            Iterator<Object> i = p.keySet().iterator();
            while (i.hasNext()) {
                String key = (String) i.next();
                String value = p.getProperty(key);
                this.siteDefaults.put(key, value);
                logger.info("site default:" + key + "=" + value);
            }
        } catch (IOException e) {
            logger.info("Error", e);
        }
    }

    /**
     * Dispatches to {@link #saveGeneral()} if the {@code method} parameter is {@code "saveGeneral"},
     * otherwise displays the preference form via {@link #form()}.
     *
     * @return String the Struts2 result name
     */
    @Override
    public String execute() {
        if ("saveGeneral".equals(request.getParameter("method"))) {
            return saveGeneral();
        }
        return form();
    }

    /**
     * Prepares the preference form by loading default, site, and provider-specific preferences.
     *
     * <p>Merges preferences in order of priority: code defaults, site defaults from
     * {@code pref.defaults}, then provider-specific values from the database.</p>
     *
     * @return String {@code "form"} to render the preferences JSP
     */
    public String form() {
        if (!inited) init();
        Map<String, String> prefs = new HashMap<String, String>();

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        List<UserProperty> userProperties = userPropertyDao.getDemographicProperties(providerNo);
        prefs.putAll(defaults);
        prefs.putAll(siteDefaults);
        for (UserProperty up : userProperties) {
            prefs.put(up.getName(), up.getValue());
        }
        request.setAttribute("prefs", prefs);
        return "form";
    }

    /**
     * Saves all submitted preferences and optionally processes a password change.
     *
     * <p>Iterates over all request parameters prefixed with {@code "pref."}, persisting each
     * as a {@link UserProperty}. Multi-value parameters (encounter forms, eForms) are
     * comma-joined before saving. If a new password is provided, delegates to
     * {@link #changePassword(HttpServletRequest)}.</p>
     *
     * @return String the result from {@link #form()} to re-display with updated values
     */
    public String saveGeneral() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        //Is there a password change?
        if (!getParameter(request, "new_password").isEmpty()) {
            try {
                changePassword(request);
            } catch (Exception e) {
                logger.error("Error", e);
            }
        }

        Iterator<String> iter = request.getParameterMap().keySet().iterator();
        while (iter.hasNext()) {
            String key = iter.next();
            if (!key.startsWith("pref.")) continue;

            String value = request.getParameter(key);

            //a couple special cases
            if (key.equals("pref." + UserProperty.ENCOUNTER_FORM_NAME) || key.equals("pref." + UserProperty.EFORM_NAME)) {
                String[] values = request.getParameterValues(key);
                StringBuilder sb = new StringBuilder();
                for (int x = 0; x < values.length; x++) {
                    if (x > 0) sb.append(",");
                    sb.append(values[x]);
                }
                value = sb.toString();
            }

            UserProperty up = userPropertyDao.getProp(providerNo, key);
            if (up != null) {
                up.setValue(value);
                userPropertyDao.saveProp(up);
            } else {
                up = new UserProperty();
                up.setProviderNo(providerNo);
                up.setName(key);
                up.setValue(value);
                userPropertyDao.saveProp(up);
            }
        }

        return form();
    }

    private void changePassword(HttpServletRequest request) throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        String currentPassword = getParameter(request, "current_password");
        String newPassword = getParameter(request, "new_password");

        //get password from db
        Security secRecord = securityDao.getByProviderNo(providerNo);

        if (Objects.nonNull(secRecord) && this.securityManager.matchesPassword(currentPassword, secRecord.getPassword())) {
            secRecord.setPassword(this.securityManager.encodePassword(newPassword));
            securityDao.merge(secRecord);

            logger.info("password changed for providers");
        } else {					
            throw new Exception("Current password did not match.");   
        }
    }

    /**
     * Generates HTML {@code name} and {@code value} attributes for a text input from the preferences map.
     *
     * @param prefs Map&lt;String, String&gt; the current preferences
     * @param key String the preference key
     * @return String HTML attributes in the form {@code name="key" value="value"}
     */
    public static String getTextData(Map<String, String> prefs, String key) {
        String val = prefs.get(key);
        if (val == null) {
            val = new String();
        }
        return "name=\"" + key + "\" value=\"" + val + "\"";
    }

    /**
     * Generates an HTML {@code <select>} element with options for the given preference key.
     *
     * <p>Options are determined by {@link #getOptions(String)} based on the key.
     * The option matching the current preference value is marked as selected.</p>
     *
     * @param prefs Map&lt;String, String&gt; the current preferences
     * @param key String the preference key determining which options to display
     * @return String the complete HTML select element
     */
    public static String getSelect(Map<String, String> prefs, String key) {
        StringBuilder sb = new StringBuilder();

        List<LabelValueBean> options = getOptions(key);
        String selectedValue = prefs.get(key);
        if (selectedValue == null) {
            selectedValue = new String();
        }
        sb.append("<select name=\"" + key + "\">");
        for (LabelValueBean option : options) {
            String selected = (option.getValue().equals(selectedValue)) ? "selected=\"selected\"" : "";
            sb.append("<option value=\"" + option.getValue() + "\" " + selected + ">" + option.getLabel() + "</option>\n");
        }
        sb.append("</select>");
        return sb.toString();
    }

    private static List<LabelValueBean> getOptions(String key) {
        List<LabelValueBean> options = new ArrayList<LabelValueBean>();
        if (key.equals("pref." + UserProperty.SEX)) {
            options.add(new LabelValueBean("", ""));
            options.add(new LabelValueBean("Male", "M"));
            options.add(new LabelValueBean("Female", "F"));
        }
        if (key.equals("pref." + UserProperty.HC_TYPE)) {
            options.add(new LabelValueBean("", ""));
            options.addAll(constructProvinceList());
        }
        if (key.equals("pref." + UserProperty.WORKLOAD_MANAGEMENT)) {
            options.add(new LabelValueBean("none", ""));
            options.addAll(constructWorkloadManagementList());
        }
        if (key.equals("pref." + UserProperty.SCHEDULE_START_HOUR)) {
            options.addAll(constructScheduleHourList());
        }
        if (key.equals("pref." + UserProperty.SCHEDULE_END_HOUR)) {
            options.addAll(constructScheduleHourList());
        }
        if (key.equals("pref." + UserProperty.SCHEDULE_PERIOD)) {
            options.addAll(constructSchedulePeriodList());
        }
        if (key.equals("pref." + UserProperty.MYGROUP_NO)) {
            options.addAll(constructMyGroupList());
        }
        if (key.equals("pref." + UserProperty.NEW_CME)) {
            options.add(new LabelValueBean("Enabled", "Enabled"));
            options.add(new LabelValueBean("Disabled", "Disabled"));
        }
        if (key.equals("pref." + UserProperty.STALE_NOTEDATE)) {
            options.add(new LabelValueBean("All", "All"));
            for (int x = 1; x <= 36; x++) {
                options.add(new LabelValueBean(String.valueOf(x), String.valueOf(x)));
            }
        }
        if (key.equals("pref." + UserProperty.RX_USE_RX3)) {
            options.add(new LabelValueBean("Yes", "yes"));
            options.add(new LabelValueBean("No", "no"));
        }
        if (key.equals("pref." + UserProperty.RX_SHOW_QR_CODE)) {
            options.add(new LabelValueBean("Yes", "yes"));
            options.add(new LabelValueBean("No", "no"));
        }
        if (key.equals("pref." + UserProperty.RX_PAGE_SIZE)) {
            options.add(new LabelValueBean("A4", "A4"));
            options.add(new LabelValueBean("A6", "A6"));
        }
        if (key.equals("pref." + UserProperty.RX_SHOW_PATIENT_DOB)) {
            options.add(new LabelValueBean("Yes", "yes"));
            options.add(new LabelValueBean("No", "no"));
        }
        if (key.equals("pref." + UserProperty.EFORM_FAVOURITE_GROUP)) {
            EFormGroupDao eFormGroupDao = (EFormGroupDao) SpringUtils.getBean(EFormGroupDao.class);
            options.add(new LabelValueBean("None", ""));
            List<String> groups = eFormGroupDao.getGroupNames();
            for (String group : groups) {
                options.add(new LabelValueBean(group, group));
            }
        }
        if (key.equals("pref." + UserProperty.CONSULTATION_TEAM_WARNING)) {
            options.add(new LabelValueBean("All", "-1"));
            options.addAll(constructProviderTeamList());
            options.add(new LabelValueBean("None", ""));
        }
        if (key.equals("pref." + UserProperty.CONSULTATION_REQ_PASTE_FMT)) {
            options.add(new LabelValueBean("Single Line", "single"));
            options.add(new LabelValueBean("Multi Line", "multi"));
        }
        if (key.equals("pref." + UserProperty.NEW_TICKLER_WARNING_WINDOW)) {
            options.add(new LabelValueBean("Yes", "yes"));
            options.add(new LabelValueBean("No", "no"));
        }
        if (key.equals("pref." + UserProperty.CAISI_DEFAULT_PMM)) {
            options.add(new LabelValueBean("Yes", "yes"));
            options.add(new LabelValueBean("No", "no"));
        }
        if (key.equals("pref." + UserProperty.CAISI_PREV_BILLING)) {
            options.add(new LabelValueBean("Yes", "yes"));
            options.add(new LabelValueBean("No", "no"));
        }
        if (key.equals("pref." + UserProperty.DEFAULT_BILLING_FORM)) {
            options.add(new LabelValueBean("-- no --", "no"));
            options.addAll(constructWorkloadManagementList());
        }
        if (key.equals("pref." + UserProperty.DEFAULT_REFERRAL_TYPE)) {
            options.add(new LabelValueBean("Refer To", "1"));
            options.add(new LabelValueBean("Refer By", "2"));
            options.add(new LabelValueBean("Neither", "3"));
        }
        if (key.equals("pref." + UserProperty.DEFAULT_PAYEE)) {
            options.add(new LabelValueBean("", "0"));
            options.addAll(constructPayeeList());
        }
        return options;
    }

    /**
     * Constructs a list of Canadian provinces/territories and US states for health card type selection.
     *
     * @return ArrayList&lt;LabelValueBean&gt; province/state options with display labels and code values
     */
    public static ArrayList<LabelValueBean> constructProvinceList() {

        ArrayList<LabelValueBean> provinces = new ArrayList<LabelValueBean>();

        provinces.add(new LabelValueBean("AB-Alberta", "AB"));
        provinces.add(new LabelValueBean("BC-British Columbia", "BC"));
        provinces.add(new LabelValueBean("MB-Manitoba", "MB"));
        provinces.add(new LabelValueBean("NB-New Brunswick", "NB"));
        provinces.add(new LabelValueBean("NL-Newfoundland", "NL"));
        provinces.add(new LabelValueBean("NT-Northwest Territory", "NT"));
        provinces.add(new LabelValueBean("NS-Nova Scotia", "NS"));
        provinces.add(new LabelValueBean("NU-Nunavut", "NU"));
        provinces.add(new LabelValueBean("ON-Ontario", "ON"));
        provinces.add(new LabelValueBean("PE-Prince Edward Island", "PE"));
        provinces.add(new LabelValueBean("QC-Quebec", "QC"));
        provinces.add(new LabelValueBean("SK-Saskatchewan", "SK"));
        provinces.add(new LabelValueBean("YT-Yukon", "YK"));
        provinces.add(new LabelValueBean("US resident", "US"));
        provinces.add(new LabelValueBean("US-AK-Alaska", "US-AK"));
        provinces.add(new LabelValueBean("US-AL-Alabama", "US-AL"));
        provinces.add(new LabelValueBean("US-AR-Arkansas", "US-AR"));
        provinces.add(new LabelValueBean("US-AZ-Arizona", "US-AZ"));
        provinces.add(new LabelValueBean("US-CA-California", "US-CA"));
        provinces.add(new LabelValueBean("US-CO-Colorado", "US-CO"));
        provinces.add(new LabelValueBean("US-CT-Connecticut", "US-CT"));
        provinces.add(new LabelValueBean("US-CZ-Canal Zone", "US-CZ"));
        provinces.add(new LabelValueBean("US-DC-District of Columbia", "US-DC"));
        provinces.add(new LabelValueBean("US-DE-Delaware", "US-DE"));
        provinces.add(new LabelValueBean("US-FL-Florida", "US-FL"));
        provinces.add(new LabelValueBean("US-GA-Georgia", "US-GA"));
        provinces.add(new LabelValueBean("US-GU-Guam", "US-GU"));
        provinces.add(new LabelValueBean("US-HI-Hawaii", "US-HI"));
        provinces.add(new LabelValueBean("US-IA-Iowa", "US-IA"));
        provinces.add(new LabelValueBean("US-ID-Idaho", "US-ID"));
        provinces.add(new LabelValueBean("US-IL-Illinois", "US-IL"));
        provinces.add(new LabelValueBean("US-IN-Indiana", "US-IN"));
        provinces.add(new LabelValueBean("US-KS-Kansas", "US-KS"));
        provinces.add(new LabelValueBean("US-KY-Kentucky", "US-KY"));
        provinces.add(new LabelValueBean("US-LA-Louisiana", "US-LA"));
        provinces.add(new LabelValueBean("US-MA-Massachusetts", "US-MA"));
        provinces.add(new LabelValueBean("US-MD-Maryland", "US-MD"));
        provinces.add(new LabelValueBean("US-ME-Maine", "US-ME"));
        provinces.add(new LabelValueBean("US-MI-Michigan", "US-MI"));
        provinces.add(new LabelValueBean("US-MN-Minnesota", "US-MN"));
        provinces.add(new LabelValueBean("US-MO-Missouri", "US-MO"));
        provinces.add(new LabelValueBean("US-MS-Mississippi", "US-MS"));
        provinces.add(new LabelValueBean("US-MT-Montana", "US-MT"));
        provinces.add(new LabelValueBean("US-NC-North Carolina", "US-NC"));
        provinces.add(new LabelValueBean("US-ND-North Dakota", "US-ND"));
        provinces.add(new LabelValueBean("US-NE-Nebraska", "US-NE"));
        provinces.add(new LabelValueBean("US-NH-New Hampshire", "US-NH"));
        provinces.add(new LabelValueBean("US-NJ-New Jersey", "US-NJ"));
        provinces.add(new LabelValueBean("US-NM-New Mexico", "US-NM"));
        provinces.add(new LabelValueBean("US-NU-Nunavut", "US-NU"));
        provinces.add(new LabelValueBean("US-NV-Nevada", "US-NV"));
        provinces.add(new LabelValueBean("US-NY-New York", "US-NY"));
        provinces.add(new LabelValueBean("US-OH-Ohio", "US-OH"));
        provinces.add(new LabelValueBean("US-OK-Oklahoma", "US-OK"));
        provinces.add(new LabelValueBean("US-OR-Oregon", "US-OR"));
        provinces.add(new LabelValueBean("US-PA-Pennsylvania", "US-PA"));
        provinces.add(new LabelValueBean("US-PR-Puerto Rico", "US-PR"));
        provinces.add(new LabelValueBean("US-RI-Rhode Island", "US-RI"));
        provinces.add(new LabelValueBean("US-SC-South Carolina", "US-SC"));
        provinces.add(new LabelValueBean("US-SD-South Dakota", "US-SD"));
        provinces.add(new LabelValueBean("US-TN-Tennessee", "US-TN"));
        provinces.add(new LabelValueBean("US-TX-Texas", "US-TX"));
        provinces.add(new LabelValueBean("US-UT-Utah", "US-UT"));
        provinces.add(new LabelValueBean("US-VA-Virginia", "US-VA"));
        provinces.add(new LabelValueBean("US-VI-Virgin Islands", "US-VI"));
        provinces.add(new LabelValueBean("US-VT-Vermont", "US-VT"));
        provinces.add(new LabelValueBean("US-WA-Washington", "US-WA"));
        provinces.add(new LabelValueBean("US-WI-Wisconsin", "US-WI"));
        provinces.add(new LabelValueBean("US-WV-West Virginia", "US-WV"));
        provinces.add(new LabelValueBean("US-WY-Wyoming", "US-WY"));

        return provinces;
    }

    /**
     * Constructs a list of billing service types for workload management selection.
     *
     * @return ArrayList&lt;LabelValueBean&gt; unique billing service type options
     */
    public static ArrayList<LabelValueBean> constructWorkloadManagementList() {
        ArrayList<LabelValueBean> results = new ArrayList<LabelValueBean>();

        CtlBillingServiceDao ctlBillingServiceDao = (CtlBillingServiceDao) SpringUtils.getBean(CtlBillingServiceDao.class);
        List<Object[]> cbsList = ctlBillingServiceDao.getUniqueServiceTypes();
        for (Object[] cbs : cbsList) {
            results.add(new LabelValueBean((String) cbs[1], (String) cbs[0]));
        }
        return results;
    }

    /**
     * Constructs a list of hours (0-22) for schedule start/end hour selection.
     *
     * @return ArrayList&lt;LabelValueBean&gt; hour options from 0 to 22
     */
    public static ArrayList<LabelValueBean> constructScheduleHourList() {
        ArrayList<LabelValueBean> results = new ArrayList<LabelValueBean>();
        for (int x = 0; x < 23; x++) {
            results.add(new LabelValueBean(String.valueOf(x), (String.valueOf(x))));
        }
        return results;
    }

    /**
     * Constructs a list of schedule time slot periods (5, 10, 15, 20, 30, 60 minutes).
     *
     * @return ArrayList&lt;LabelValueBean&gt; period options in minutes
     */
    public static ArrayList<LabelValueBean> constructSchedulePeriodList() {
        ArrayList<LabelValueBean> results = new ArrayList<LabelValueBean>();
        results.add(new LabelValueBean("5", "5"));
        results.add(new LabelValueBean("10", "10"));
        results.add(new LabelValueBean("15", "15"));
        results.add(new LabelValueBean("20", "20"));
        results.add(new LabelValueBean("30", "30"));
        results.add(new LabelValueBean("60", "60"));
        return results;
    }

    /**
     * Constructs a list of provider group names for the "My Group" preference selection.
     *
     * @return ArrayList&lt;LabelValueBean&gt; available provider group options
     */
    public static ArrayList<LabelValueBean> constructMyGroupList() {
        ArrayList<LabelValueBean> results = new ArrayList<LabelValueBean>();

        MyGroupDao myGroupDao = (MyGroupDao) SpringUtils.getBean(MyGroupDao.class);
        List<String> cbsList = myGroupDao.getGroups();
        for (String cbs : cbsList) {
            results.add(new LabelValueBean(cbs, cbs));
        }
        return results;
    }

    /**
     * Constructs a sorted list of all available encounter forms.
     *
     * @return ArrayList&lt;LabelValueBean&gt; encounter form names sorted alphabetically
     */
    public static ArrayList<LabelValueBean> constructEncounterFormList() {
        ArrayList<LabelValueBean> results = new ArrayList<LabelValueBean>();

        EncounterFormDao encounterFormDao = (EncounterFormDao) SpringUtils.getBean(EncounterFormDao.class);
        List<EncounterForm> forms = encounterFormDao.findAll();
        Collections.sort(forms, EncounterForm.FORM_NAME_COMPARATOR);
        for (EncounterForm form : forms) {
            results.add(new LabelValueBean(form.getFormName(), form.getFormName()));
        }

        return results;
    }

    /**
     * Generates HTML checkboxes for encounter form multi-selection, pre-checking saved values.
     *
     * @param prefs Map&lt;String, String&gt; the current preferences (comma-separated form names)
     * @param key String the preference key for encounter form selection
     * @return String HTML checkbox elements for each available encounter form
     */
    public static String getEncounterFormHTML(Map<String, String> prefs, String key) {
        StringBuilder sb = new StringBuilder();
        List<LabelValueBean> forms = constructEncounterFormList();
        for (LabelValueBean lvb : forms) {
            String checked = new String();

            if (prefs.get(key) != null) {
                String[] savedValues = prefs.get(key).split(",");
                for (int x = 0; x < savedValues.length; x++) {
                    if (savedValues[x].equals(lvb.getValue())) {
                        checked = "checked=\"checked\"";
                    }
                }
            }
            sb.append("<input name=\"pref." + UserProperty.ENCOUNTER_FORM_NAME + "\" value=\"" + lvb.getValue() + "\" type=\"checkbox\" " + checked + "/>" + lvb.getLabel() + "\n");
            sb.append("<br/>\n");
        }
        return sb.toString();
    }

    /**
     * Constructs a sorted list of all active eForms.
     *
     * @return ArrayList&lt;LabelValueBean&gt; eForm names and IDs sorted alphabetically
     */
    public static ArrayList<LabelValueBean> constructEformList() {
        ArrayList<LabelValueBean> results = new ArrayList<LabelValueBean>();

        EFormDao eFormDao = (EFormDao) SpringUtils.getBean(EFormDao.class);
        List<EForm> forms = eFormDao.findAll(true);
        Collections.sort(forms, EForm.FORM_NAME_COMPARATOR);

        for (EForm form : forms) {
            results.add(new LabelValueBean(form.getFormName(), String.valueOf(form.getId())));
        }

        return results;
    }

    /**
     * Generates HTML checkboxes for eForm multi-selection, pre-checking saved values.
     *
     * @param prefs Map&lt;String, String&gt; the current preferences (comma-separated eForm IDs)
     * @param key String the preference key for eForm selection
     * @return String HTML checkbox elements for each available eForm
     */
    public static String getEformHTML(Map<String, String> prefs, String key) {
        StringBuilder sb = new StringBuilder();
        List<LabelValueBean> forms = constructEformList();
        for (LabelValueBean lvb : forms) {
            String checked = new String();

            if (prefs.get(key) != null) {
                String[] savedValues = prefs.get(key).split(",");
                for (int x = 0; x < savedValues.length; x++) {
                    if (savedValues[x].equals(lvb.getValue())) {
                        checked = "checked=\"checked\"";
                    }
                }
            }
            sb.append("<input name=\"pref." + UserProperty.EFORM_NAME + "\" value=\"" + lvb.getValue() + "\" type=\"checkbox\" " + checked + "/>" + lvb.getLabel() + "\n");
            sb.append("<br/>\n");
        }
        return sb.toString();
    }

    /**
     * Constructs a list of unique provider team names for consultation team warning configuration.
     *
     * @return ArrayList&lt;LabelValueBean&gt; non-empty team names
     */
    public static ArrayList<LabelValueBean> constructProviderTeamList() {
        ArrayList<LabelValueBean> results = new ArrayList<LabelValueBean>();

        ProviderDao providerDao = (ProviderDao) SpringUtils.getBean(ProviderDao.class);
        List<String> teams = providerDao.getUniqueTeams();
        for (String team : teams) {
            if (team.length() > 0) {
                results.add(new LabelValueBean(team, team));
            }
        }

        return results;
    }

    /**
     * Constructs a list of providers for default billing payee selection.
     *
     * @return ArrayList&lt;LabelValueBean&gt; providers with "LastName,FirstName" labels and provider numbers
     */
    public static ArrayList<LabelValueBean> constructPayeeList() {
        ArrayList<LabelValueBean> results = new ArrayList<LabelValueBean>();

        MSPReconcile rec = new MSPReconcile();
        @SuppressWarnings("unchecked")
        List<Provider> providers = rec.getAllProviders();

        for (Provider provider : providers) {
            results.add(new LabelValueBean(provider.getLastName() + "," + provider.getFirstName(), provider.getProviderNo()));
        }

        return results;
    }
}
