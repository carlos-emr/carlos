/**
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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.prevention;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * What the prevention page reads about one patient, read once for the request.
 *
 * <p>The page goes through every prevention type, and each type used to look the patient up
 * again, with its privilege checks, for the date of birth, age and sex. With the vaccine
 * catalogue loaded there are a few hundred types. This looks the patient up once, through
 * {@link DemographicManager}, which makes the same {@code _demographic} read checks, and keeps
 * each type's prevention list for the rest of the request.
 *
 * <p>Create one per request and drop it with the request: it holds one user's view of one
 * patient. It is not thread-safe.
 *
 * @since 2026-10-05
 */
public final class PreventionPageData {

    private final Integer demographicId;
    private final Demographic demographic;
    private final Date dateOfBirth;
    private final Map<String, ArrayList<Map<String, Object>>> preventionDataByType = new HashMap<>();

    /**
     * Looks the patient up for the logged-in user.
     *
     * @param loggedInInfo the logged-in user
     * @param demographicNo the patient's demographic number
     * @throws NumberFormatException if {@code demographicNo} is not a number, or the patient's
     *         stored birth date is not numeric
     * @throws RuntimeException if the user lacks {@code _demographic} read, in general or for
     *         this patient, as {@link DemographicManager#getDemographic(LoggedInInfo, String)}
     *         throws it
     */
    public PreventionPageData(LoggedInInfo loggedInInfo, String demographicNo) {
        this(loggedInInfo, demographicNo, SpringUtils.getBean(DemographicManager.class));
    }

    PreventionPageData(LoggedInInfo loggedInInfo, String demographicNo, DemographicManager demographicManager) {
        this.demographicId = Integer.valueOf(demographicNo);
        // Stands in for the per-type lookups: display's, which made these same checks (general and
        // for this patient), and getPreventionData's, which made the patient check alone.
        this.demographic = demographicManager.getDemographic(loggedInInfo, demographicNo);
        Calendar birthDay = demographic == null ? null : demographic.getBirthDay();
        this.dateOfBirth = birthDay == null ? null : birthDay.getTime();
    }

    /**
     * @return the patient, or {@code null} if no patient has this number
     */
    public Demographic getDemographic() {
        return demographic;
    }

    /**
     * @return the patient's date of birth, or {@code null} if it is not known
     */
    public Date getDateOfBirth() {
        return dateOfBirth == null ? null : new Date(dateOfBirth.getTime());
    }

    /**
     * Returns the patient's preventions of one type, as
     * {@link PreventionData#getPreventionData(LoggedInInfo, String, Integer)} lists them. The
     * first call for a type reads them; later calls in the request return the same list, which
     * callers must not change.
     *
     * @param preventionType the prevention type's name
     * @return the preventions of that type, never {@code null}
     */
    public ArrayList<Map<String, Object>> getPreventionData(String preventionType) {
        return preventionDataByType.computeIfAbsent(preventionType,
                type -> PreventionData.getPreventionData(type, demographicId, dateOfBirth));
    }
}
