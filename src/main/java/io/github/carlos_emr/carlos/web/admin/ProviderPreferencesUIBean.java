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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.web.admin;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang3.StringUtils;
import io.github.carlos_emr.carlos.commn.dao.EFormDao;
import io.github.carlos_emr.carlos.commn.dao.EncounterFormDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderPreferenceDao;
import io.github.carlos_emr.carlos.commn.model.EForm;
import io.github.carlos_emr.carlos.commn.model.EncounterForm;
import io.github.carlos_emr.carlos.commn.model.ProviderPreference;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.WebUtils;

/**
 * UI bean for managing the {@link ProviderPreference} entity (schedule, billing, eRx fields)
 * as part of the consolidated provider preferences page.
 *
 * <p>This bean is the first half of a two-part preference save mechanism:
 * <ol>
 *   <li><strong>This class</strong> loads and persists the {@code ProviderPreference} entity
 *       (schedule hours, billing defaults, encounter forms, eRx settings)</li>
 *   <li>{@link io.github.carlos_emr.carlos.provider.web.ProviderPropertyAction} saves all
 *       remaining preferences stored as {@code UserProperty} key-value pairs</li>
 * </ol>
 *
 * <p>Called from {@code providerupdatepreference.jsp} on form submission, and from
 * {@code providerpreference.jsp} for read-only data loading.
 *
 * @see io.github.carlos_emr.carlos.provider.web.ProviderPropertyAction
 * @see ProviderPreference
 * @since 2010-09-05
 */
public final class ProviderPreferencesUIBean {

    private static final ProviderPreferenceDao providerPreferenceDao = (ProviderPreferenceDao) SpringUtils.getBean(ProviderPreferenceDao.class);
    private static final EFormDao eFormDao = (EFormDao) SpringUtils.getBean(EFormDao.class);
    private static final EncounterFormDao encounterFormDao = (EncounterFormDao) SpringUtils.getBean(EncounterFormDao.class);

    /**
     * Updates or creates a {@link ProviderPreference} entity from the submitted form parameters.
     * Handles schedule hours, billing defaults, encounter/eForm selections, and eRx settings.
     *
     * @param request {@link HttpServletRequest} containing form parameters from the
     *                preferences page POST submission
     * @return ProviderPreference the persisted preference entity
     * @throws SecurityException if the session has expired (null loggedInInfo) or if the
     *         provider lacks write ("w") access to the "_pref" security object
     */
    public static ProviderPreference updateOrCreateProviderPreferences(HttpServletRequest request) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            throw new SecurityException("Session expired: cannot save preferences without an authenticated session");
        }

        // Security check: verify user has write access to preferences
        SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_pref", "w", null)) {
            throw new SecurityException("missing required sec object: _pref");
        }

        String providerNo = loggedInInfo.getLoggedInProviderNo();

        ProviderPreference providerPreference = getProviderPreference(providerNo);

        // update preferences based on request parameters
        String temp;
        HttpSession session = request.getSession();

        boolean updatePreferences = Boolean.parseBoolean(request.getParameter("updatePreference"));

        // new tickler window
        temp = StringUtils.trimToNull(request.getParameter("new_tickler_warning_window"));
        if (temp != null) {
            providerPreference.setNewTicklerWarningWindow(temp);
        } else {
            temp = StringUtils.trimToNull((String) session.getAttribute("newticklerwarningwindow"));
            if (temp != null) providerPreference.setNewTicklerWarningWindow(temp);
        }

        // default pmm
        temp = StringUtils.trimToNull(request.getParameter("default_pmm"));
        if (temp != null) {
            providerPreference.setDefaultCaisiPmm(temp);
        } else {
            temp = StringUtils.trimToNull((String) session.getAttribute("default_pmm"));
            if (temp == null) providerPreference.setDefaultCaisiPmm("disabled");
            else providerPreference.setDefaultCaisiPmm(temp);
        }

        // default billing preference (edit or delete)
        temp = StringUtils.trimToNull(request.getParameter("caisiBillingPreferenceNotDelete"));
        if (temp != null) {
            try {
                providerPreference.setDefaultDoNotDeleteBilling(Integer.parseInt(temp));
            } catch (NumberFormatException e) {
                MiscUtils.getLogger().error("Error", e);
            }
        } else {
            temp = StringUtils.trimToNull(String.valueOf(session.getAttribute("caisiBillingPreferenceNotDelete")));
            if (temp == null)
                providerPreference.setDefaultDoNotDeleteBilling(0);
            else {
                int defBilling = 0;
                try {
                    defBilling = Integer.parseInt(temp);
                } catch (NumberFormatException e) {
                    MiscUtils.getLogger().warn("Invalid caisiBillingPreferenceNotDelete value from session: '{}'", temp, e);
                }
                providerPreference.setDefaultDoNotDeleteBilling(defBilling);
            }
        }

        // default billing dxCode
        temp = StringUtils.trimToNull(request.getParameter("dxCode"));
        if (temp != null) providerPreference.setDefaultDxCode(temp);

        String startHourStr = StringUtils.trimToNull(request.getParameter("start_hour"));
        String endHourStr = StringUtils.trimToNull(request.getParameter("end_hour"));
        if (startHourStr != null && endHourStr != null) {
            try {
                int startHour = Integer.parseInt(startHourStr);
                int endHour = Integer.parseInt(endHourStr);
                if (startHour >= 0 && startHour <= 23 && endHour >= 0 && endHour <= 23) {
                    if (startHour < endHour) {
                        providerPreference.setStartHour(startHour);
                        providerPreference.setEndHour(endHour);
                    } else {
                        MiscUtils.getLogger().warn("start_hour {} must be less than end_hour {} for provider {}", startHour, endHour, providerNo);
                    }
                } else {
                    MiscUtils.getLogger().warn("Schedule hours out of valid range 0-23: start_hour={}, end_hour={} for provider {}", startHour, endHour, providerNo);
                }
            } catch (NumberFormatException e) {
                MiscUtils.getLogger().warn("Invalid schedule hour values: start_hour='{}', end_hour='{}' for provider {}", startHourStr, endHourStr, providerNo, e);
            }
        }

        temp = StringUtils.trimToNull(request.getParameter("every_min"));
        if (temp != null) {
            try {
                int everyMinValue = Integer.parseInt(temp);
                if (everyMinValue > 0 && everyMinValue <= 120) {
                    providerPreference.setEveryMin(everyMinValue);
                } else {
                    MiscUtils.getLogger().warn("every_min value {} out of valid range (1-120) for provider {}", everyMinValue, providerNo);
                }
            } catch (NumberFormatException e) {
                MiscUtils.getLogger().warn("Invalid every_min value: '{}' for provider {}", temp, providerNo, e);
            }
        }

        temp = StringUtils.trimToNull(request.getParameter("mygroup_no"));
        if (temp != null) providerPreference.setMyGroupNo(temp);

        temp = StringUtils.trimToNull(request.getParameter("default_servicetype"));
        if (temp != null) providerPreference.setDefaultServiceType(temp);

        temp = StringUtils.trimToNull(request.getParameter("default_location"));
        if (temp != null) providerPreference.setDefaultBillingLocation(temp);


        temp = StringUtils.trimToNull(request.getParameter("color_template"));
        if (temp != null) providerPreference.setColourTemplate(temp);

        providerPreference.setPrintQrCodeOnPrescriptions(WebUtils.isChecked(request, "prescriptionQrCodes"));

        // get encounterForms for appointment screen
        temp = StringUtils.trimToNull(request.getParameter("appointmentScreenFormsNameDisplayLength"));
        if (temp != null) {
            try {
                providerPreference.setAppointmentScreenLinkNameDisplayLength(Integer.parseInt(temp));
            } catch (NumberFormatException e) {
                MiscUtils.getLogger().warn("Invalid appointmentScreenFormsNameDisplayLength value: '{}'", temp, e);
            }
        }

        String[] formNames = request.getParameterValues("encounterFormName");
        Collection<String> formNamesList = providerPreference.getAppointmentScreenForms();

        formNamesList.clear();
        if (formNames != null) {
            for (String formName : formNames) {
                formNamesList.add(formName);
            }
        }

        /*
         * Get eForms for appointment screen
         *
         * This code is adapted to add the name of each
         * eForm into the datatable.  The display methods in the schedule
         * have been adapted to display a name and ID.
         */
        String[] formIds = request.getParameterValues("eformId");
        Collection<ProviderPreference.EformLink> eFormsIdsList = providerPreference.getAppointmentScreenEForms();
        eFormsIdsList.clear();
        if (formIds != null) {
            for (String formId : formIds) {
                try {
                    Integer formIdInteger = Integer.parseInt(formId);
                    EForm eForm = eFormDao.find(formIdInteger);
                    if (eForm != null) {
                        eFormsIdsList.add(new ProviderPreference.EformLink(formIdInteger, eForm.getFormName()));
                    } else {
                        if (MiscUtils.getLogger().isWarnEnabled()) {
                            MiscUtils.getLogger().warn("EForm not found for id of: {}", formIdInteger);
                        }
                    }
                } catch (NumberFormatException e) {
                    if (MiscUtils.getLogger().isWarnEnabled()) {
                        MiscUtils.getLogger().warn("Invalid eForm ID value: '{}'", formId, e);
                    }
                }
            }
        }

        // external prescriber prefs
        providerPreference.setERxEnabled(WebUtils.isChecked(request, "erx_enable"));

        temp = StringUtils.trimToNull(request.getParameter("erx_username"));
        if (temp != null) providerPreference.setERxUsername(temp);

        temp = StringUtils.trimToNull(request.getParameter("erx_password"));
        if (temp != null) providerPreference.setERxPassword(temp);

        temp = StringUtils.trimToNull(request.getParameter("erx_facility"));
        if (temp != null) providerPreference.setERxFacility(temp);

        providerPreference.setERxTrainingMode(WebUtils.isChecked(request, "erx_training_mode"));

        temp = StringUtils.trimToNull(request.getParameter("erx_sso_url"));
        if (temp != null) providerPreference.setERx_SSO_URL(temp);

        providerPreferenceDao.merge(providerPreference);

        return (providerPreference);
    }

    /**
     * Some day we'll fix this so preferences are created when providers are created, it was suppose to be that way
     * but something got missed somewhere.
     */
    public static ProviderPreference getProviderPreference(String providerNo) {

        ProviderPreference providerPreference = providerPreferenceDao.find(providerNo);

        if (providerPreference == null) {
            providerPreference = new ProviderPreference();
            providerPreference.setProviderNo(providerNo);
            providerPreferenceDao.persist(providerPreference);
        }

        return providerPreference;
    }

    public static List<EForm> getAllEForms() {
        List<EForm> results = eFormDao.findAll(true);
        Collections.sort(results, EForm.FORM_NAME_COMPARATOR);
        return (results);
    }

    public static List<EncounterForm> getAllEncounterForms() {
        List<EncounterForm> results = encounterFormDao.findAll();
        Collections.sort(results, EncounterForm.FORM_NAME_COMPARATOR);
        return (results);
    }

    public static Collection<String> getCheckedEncounterFormNames(String providerNo) {
        ProviderPreference providerPreference = getProviderPreference(providerNo);
        return (providerPreference.getAppointmentScreenForms());
    }

    public static Collection<ProviderPreference.EformLink> getCheckedEFormIds(String providerNo) {
        ProviderPreference providerPreference = getProviderPreference(providerNo);
        return (providerPreference.getAppointmentScreenEForms());
    }

    public static ProviderPreference getProviderPreferenceByProviderNo(String providerNo) {
        return providerPreferenceDao.find(providerNo);
    }

    public static Collection<ProviderPreference.QuickLink> getQuickLinks(String providerNo) {
        ProviderPreference providerPreference = getProviderPreference(providerNo);

        return (providerPreference.getAppointmentScreenQuickLinks());
    }

    public static void addQuickLink(String providerNo, String name, String url) {
        ProviderPreference providerPreference = getProviderPreference(providerNo);

        Collection<ProviderPreference.QuickLink> quickLinks = providerPreference.getAppointmentScreenQuickLinks();

        ProviderPreference.QuickLink quickLink = new ProviderPreference.QuickLink();
        quickLink.setName(name);
        quickLink.setUrl(url);

        quickLinks.add(quickLink);

        providerPreferenceDao.merge(providerPreference);
    }

    public static void removeQuickLink(String providerNo, String name) {
        ProviderPreference providerPreference = getProviderPreference(providerNo);

        Collection<ProviderPreference.QuickLink> quickLinks = providerPreference.getAppointmentScreenQuickLinks();

        for (ProviderPreference.QuickLink quickLink : quickLinks) {
            if (name.equals(quickLink.getName())) {
                // it should be okay to modify the list while we're iterating through it, as long as we don't touch it after it's modified.
                quickLinks.remove(quickLink);
                break;
            }
        }

        providerPreferenceDao.merge(providerPreference);
    }
}
