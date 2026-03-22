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

package io.github.carlos_emr.carlos.web;

import io.github.carlos_emr.carlos.commn.dao.EFormDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderPreferenceDao;
import io.github.carlos_emr.carlos.commn.model.EForm;
import io.github.carlos_emr.carlos.commn.model.ProviderPreference;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * UI helper bean for the appointment provider admin day view.
 *
 * <p>Provides utility methods used by the daily appointment schedule JSP pages,
 * including truncating form link names to the provider-configured display length
 * and retrieving eForms by their identifier.
 *
 * @since 2012-08-13
 */
public final class AppointmentProviderAdminDayUIBean {
    private static EFormDao eFormDao = (EFormDao) SpringUtils.getBean(EFormDao.class);
    private static ProviderPreferenceDao providerPreferenceDao = (ProviderPreferenceDao) SpringUtils.getBean(ProviderPreferenceDao.class);

    /**
     * Truncates a form link name to the provider's configured display length.
     *
     * <p>If the provider has not configured a preference, the default maximum length is 3 characters.
     * Names exceeding the limit are truncated and suffixed with a period.
     *
     * @param loggedInInfo LoggedInInfo the current user's session information, used to look up the provider number
     * @param formName String the full form name to potentially truncate
     * @return String the original name if within the limit, or a truncated version ending with "."
     */
    public static String getLengthLimitedLinkName(LoggedInInfo loggedInInfo, String formName) {
        int maxLength = 3;

        ProviderPreference providerPreference = providerPreferenceDao.find(loggedInInfo.getLoggedInProviderNo());
        if (providerPreference != null) maxLength = providerPreference.getAppointmentScreenLinkNameDisplayLength();


        if (formName.length() <= maxLength) return (formName);
        else return (formName.substring(0, maxLength - 1) + ".");
    }

    /**
     * Retrieves an electronic form by its identifier.
     *
     * @param eformId Integer the unique identifier of the eForm
     * @return EForm the matching electronic form, or {@code null} if not found
     */
    public static EForm getEForms(Integer eformId) {
        return (eFormDao.find(eformId));
    }
}
