/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * Originally written for Centre for Research on Inner City Health, St. Michael's Hospital.
 * Now maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */


package io.github.carlos_emr.carlos.provider.web;

import javax.persistence.PersistenceException;
import org.apache.commons.lang3.StringUtils;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;

import javax.servlet.http.HttpServletRequest;

/**
 * Handles saving all {@link UserProperty}-based provider preferences from the
 * consolidated preferences page ({@code providerpreference.jsp}) form submission.
 *
 * <p>This action class is the second half of the two-part preference save mechanism:
 * <ol>
 *   <li>{@code ProviderPreferencesUIBean.updateOrCreateProviderPreferences()} saves the
 *       {@code ProviderPreference} entity (schedule, billing, eRx fields)</li>
 *   <li><strong>This class</strong> saves all remaining preferences stored as individual
 *       {@link UserProperty} key-value pairs in the {@code property} table</li>
 * </ol>
 *
 * <p>Preferences are organized into logical groups (schedule, prescriptions, clinical,
 * consultation, display, contact, etc.) using one of five save strategies depending on
 * how the preference value should be interpreted from the HTML form:
 * <ul>
 *   <li>{@link #saveIfPresent} - text/select fields: only saves if non-empty</li>
 *   <li>{@link #saveAllowEmpty} - text fields that can be cleared (e.g., appointment card)</li>
 *   <li>{@link #saveCheckbox} - checkboxes stored as "yes"/"no"</li>
 *   <li>{@link #saveBooleanCheckbox} - checkboxes stored as "true"/"false" (prevention prefs)</li>
 *   <li>{@link #saveBoolean} - boolean-parsed with create-or-update pattern (schedule weekends)</li>
 * </ul>
 *
 * @see io.github.carlos_emr.carlos.web.admin.ProviderPreferencesUIBean
 * @see UserProperty
 * @see UserPropertyDAO
 * @since 2007-12-21 (original), enhanced 2026-02-14 for consolidated preferences
 */
public class ProviderPropertyAction {

    /**
     * Saves all UserProperty-based preferences submitted from {@code providerpreference.jsp}.
     * Called from {@code providerupdatepreference.jsp} after the main
     * {@code ProviderPreference} entity is saved by {@code ProviderPreferencesUIBean}.
     *
     * <p>Each preference group uses the appropriate save strategy based on the HTML
     * form element type and the expected storage format in the {@code property} table.
     *
     * @param request {@link HttpServletRequest} containing form parameters from the
     *                preferences page POST submission
     * @throws SecurityException if the session has expired (null loggedInInfo) or if the
     *         provider lacks write ("w") access to the "_pref" security object
     * @throws PersistenceException if any preference fails to save to the database,
     *         preventing silent partial saves
     */
    public static void updateOrCreateProviderProperties(HttpServletRequest request) throws PersistenceException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            throw new SecurityException("Session expired: cannot save preferences without an authenticated session");
        }

        // Security check: verify user has write access to preferences
        SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_pref", "w", null)) {
            throw new SecurityException("missing required sec object: _pref");
        }

        UserPropertyDAO propertyDAO = SpringUtils.getBean(UserPropertyDAO.class);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        // Schedule preferences
        saveBoolean(request, propertyDAO, providerNo, UserProperty.SCHEDULE_WEEK_VIEW_WEEKENDS);

        // Prescription preferences (rx_page_size and rx_default_quantity can be cleared, so use saveAllowEmpty)
        saveAllowEmpty(request, propertyDAO, providerNo, UserProperty.RX_PAGE_SIZE, "rx_page_size");
        saveCheckbox(request, propertyDAO, providerNo, UserProperty.RX_USE_RX3, "rx_use_rx3");
        saveCheckbox(request, propertyDAO, providerNo, UserProperty.RX_SHOW_PATIENT_DOB, "rx_show_patient_dob");
        saveAllowEmpty(request, propertyDAO, providerNo, UserProperty.RX_DEFAULT_QUANTITY, "rx_default_quantity");

        // Clinical preferences (dropdowns with "--" empty option, so use saveAllowEmpty)
        saveAllowEmpty(request, propertyDAO, providerNo, UserProperty.DEFAULT_SEX, "default_sex");
        saveAllowEmpty(request, propertyDAO, providerNo, UserProperty.HC_TYPE, "HC_Type");
        saveCheckbox(request, propertyDAO, providerNo, UserProperty.CPP_SINGLE_LINE, "cpp_single_line");

        // Stale date preferences
        saveIfPresent(request, propertyDAO, providerNo, UserProperty.STALE_NOTEDATE, "cme_note_date");
        saveIfPresent(request, propertyDAO, providerNo, UserProperty.STALE_FORMAT, "cme_note_format");

        // Consultation preferences
        saveAllowEmpty(request, propertyDAO, providerNo, UserProperty.CONSULTATION_TIME_PERIOD_WARNING, "consultation_time_period_warning");
        saveAllowEmpty(request, propertyDAO, providerNo, UserProperty.CONSULTATION_TEAM_WARNING, "consultation_team_warning");
        saveAllowEmpty(request, propertyDAO, providerNo, UserProperty.WORKLOAD_MANAGEMENT, "workload_management");
        saveAllowEmpty(request, propertyDAO, providerNo, UserProperty.CONSULTATION_REQ_PASTE_FMT, "consultation_req_paste_fmt");

        // Lab preferences
        saveCheckbox(request, propertyDAO, providerNo, UserProperty.LAB_ACK_COMMENT, "lab_ack_comment");

        // Display preferences
        saveAllowEmpty(request, propertyDAO, providerNo, UserProperty.PATIENT_NAME_LENGTH, "patient_name_length");
        saveAllowEmpty(request, propertyDAO, providerNo, UserProperty.DISPLAY_DOCUMENT_AS, "display_document_as");
        saveCheckbox(request, propertyDAO, providerNo, UserProperty.EDOC_BROWSER_IN_DOCUMENT_REPORT, "edoc_browser_in_document_report");
        saveCheckbox(request, propertyDAO, providerNo, UserProperty.EDOC_BROWSER_IN_MASTER_FILE, "edoc_browser_in_master_file");
        saveIfPresent(request, propertyDAO, providerNo, "encounterWindowWidth", "encounterWindowWidth");
        saveIfPresent(request, propertyDAO, providerNo, "encounterWindowHeight", "encounterWindowHeight");
        saveCheckbox(request, propertyDAO, providerNo, "encounterWindowMaximize", "encounterWindowMaximize");
        saveIfPresent(request, propertyDAO, providerNo, "quickChartSize", "quickChartSize");

        // Contact info (use saveAllowEmpty so users can clear previously set values)
        saveAllowEmpty(request, propertyDAO, providerNo, "rxAddress", "rxAddress");
        saveAllowEmpty(request, propertyDAO, providerNo, "rxCity", "rxCity");
        saveAllowEmpty(request, propertyDAO, providerNo, "rxProvince", "rxProvince");
        saveAllowEmpty(request, propertyDAO, providerNo, "rxPostal", "rxPostal");
        saveAllowEmpty(request, propertyDAO, providerNo, "rxPhone", "rxPhone");
        saveAllowEmpty(request, propertyDAO, providerNo, "faxnumber", "faxnumber");

        // Provider colour is managed exclusively via setProviderColour.do / ProviderColourUpdater,
        // which writes to the property table under "ProviderColour" via PropertyDao.
        // Both PropertyDao and UserPropertyDAO read from the same underlying property table,
        // so providerpreference.jsp can read it via UserPropertyDAO for display.
        // Do not persist colour here to maintain a single write path.

        // eForm group (has "None" empty option, so use saveAllowEmpty)
        saveAllowEmpty(request, propertyDAO, providerNo, UserProperty.EFORM_FAVOURITE_GROUP, "favourite_eform_group");

        // Dashboard
        saveCheckbox(request, propertyDAO, providerNo, UserProperty.DASHBOARD_SHARE, "dashboard_share");

        // Appointment card
        saveAllowEmpty(request, propertyDAO, providerNo, "appointmentCardName", "appointmentCardName");
        saveAllowEmpty(request, propertyDAO, providerNo, "appointmentCardPhone", "appointmentCardPhone");
        saveAllowEmpty(request, propertyDAO, providerNo, "appointmentCardFax", "appointmentCardFax");

        // Prevention
        saveBooleanCheckbox(request, propertyDAO, providerNo, UserProperty.PREVENTION_SSO_WARNING, "prevention_sso_warning");
        saveBooleanCheckbox(request, propertyDAO, providerNo, UserProperty.PREVENTION_ISPA_WARNING, "prevention_ispa_warning");
        saveBooleanCheckbox(request, propertyDAO, providerNo, UserProperty.PREVENTION_NON_ISPA_WARNING, "prevention_non_ispa_warning");
    }

    /**
     * Saves a property only if the form parameter is present and non-empty.
     * Used for text inputs and select dropdowns where an empty value means "no change".
     *
     * @param request   {@link HttpServletRequest} containing form parameters
     * @param dao       {@link UserPropertyDAO} for database persistence
     * @param providerNo String provider number identifying the user
     * @param propName  String property key stored in the {@code property} table
     * @param paramName String HTML form parameter name to read from the request
     * @throws PersistenceException if the save operation fails
     */
    private static void saveIfPresent(HttpServletRequest request, UserPropertyDAO dao,
                                      String providerNo, String propName, String paramName) {
        String value = StringUtils.trimToNull(request.getParameter(paramName));
        if (value != null) {
            dao.saveProp(providerNo, propName, value);
        }
    }

    /**
     * Saves a property even if the value is empty string (for text fields that can be cleared).
     * Unlike {@link #saveIfPresent}, this persists blank values so users can clear previously
     * set text (e.g., appointment card name/phone/fax). If the form parameter is entirely
     * absent from the request (not submitted), no change is made.
     *
     * @param request   {@link HttpServletRequest} containing form parameters
     * @param dao       {@link UserPropertyDAO} for database persistence
     * @param providerNo String provider number identifying the user
     * @param propName  String property key stored in the {@code property} table
     * @param paramName String HTML form parameter name to read from the request
     * @throws PersistenceException if the save operation fails
     */
    private static void saveAllowEmpty(HttpServletRequest request, UserPropertyDAO dao,
                                       String providerNo, String propName, String paramName) {
        String value = request.getParameter(paramName);
        if (value != null) {
            dao.saveProp(providerNo, propName, value.trim());
        }
    }

    /**
     * Saves a checkbox as "yes"/"no" (used by most OSCAR checkbox preferences).
     * HTML checkboxes only submit a value when checked; an absent parameter means unchecked.
     *
     * @param request   {@link HttpServletRequest} containing form parameters
     * @param dao       {@link UserPropertyDAO} for database persistence
     * @param providerNo String provider number identifying the user
     * @param propName  String property key stored in the {@code property} table
     * @param paramName String HTML form parameter name to read from the request
     * @throws PersistenceException if the save operation fails
     */
    private static void saveCheckbox(HttpServletRequest request, UserPropertyDAO dao,
                                     String providerNo, String propName, String paramName) {
        String value = request.getParameter(paramName);
        dao.saveProp(providerNo, propName, value != null ? "yes" : "no");
    }

    /**
     * Saves a checkbox as "true"/"false" (used by prevention preferences).
     * Prevention preferences historically use "true"/"false" strings rather than
     * the "yes"/"no" convention used by most other OSCAR preferences.
     *
     * @param request   {@link HttpServletRequest} containing form parameters
     * @param dao       {@link UserPropertyDAO} for database persistence
     * @param providerNo String provider number identifying the user
     * @param propName  String property key stored in the {@code property} table
     * @param paramName String HTML form parameter name to read from the request
     * @throws PersistenceException if the save operation fails
     */
    private static void saveBooleanCheckbox(HttpServletRequest request, UserPropertyDAO dao,
                                            String providerNo, String propName, String paramName) {
        String value = request.getParameter(paramName);
        dao.saveProp(providerNo, propName, value != null ? "true" : "false");
    }

    /**
     * Saves a boolean-parsed property using {@code Boolean.parseBoolean()}.
     * Unlike {@link #saveIfPresent} and {@link #saveAllowEmpty} which store the raw form value, this method parses
     * the value as a boolean first, coercing {@code null} or non-boolean strings
     * to {@code "false"}. Uses a load-or-create pattern with the entity API
     * ({@link UserPropertyDAO#saveProp(UserProperty)}) rather than the three-argument
     * convenience method. Used for the schedule weekends preference.
     *
     * @param request   {@link HttpServletRequest} containing form parameters
     * @param dao       {@link UserPropertyDAO} for database persistence
     * @param providerNo String provider number identifying the user
     * @param propName  String property key (also used as the HTML parameter name)
     * @throws PersistenceException if the save operation fails
     */
    private static void saveBoolean(HttpServletRequest request, UserPropertyDAO dao,
                                    String providerNo, String propName) {
        String value = StringUtils.trimToNull(request.getParameter(propName));
        UserProperty property = dao.getProp(providerNo, propName);
        if (property == null) {
            property = new UserProperty();
            property.setProviderNo(providerNo);
            property.setName(propName);
        }
        property.setValue(String.valueOf(Boolean.parseBoolean(value)));
        dao.saveProp(property);
    }
}
