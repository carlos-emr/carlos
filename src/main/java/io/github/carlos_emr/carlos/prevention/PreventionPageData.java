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
import io.github.carlos_emr.carlos.prevention.dto.PreventionSourceUnavailableException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * The patient lookup and prevention lists behind the prevention page's per-type loops, read
 * once for the request. The page's name/age header and decision-support input still make their
 * own lookups.
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
    private PreventionSourceUnavailableException unavailable;

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
     * first call for a type reads them; later calls in the request return the same list.
     *
     * <p>The list and its row maps are shared, not copied: every loop in the request that reads
     * this type gets the same objects, so a change made by one would show up in the others.
     * Callers must only read them. Some callers of the public
     * {@code PreventionData.getPreventionData} do change the list they get (for example with
     * {@code addAll}); code like that must copy this list first.
     *
     * <p>A failed read is remembered for the rest of the request: once one type could not be
     * read, every later call throws the same exception, for every type, including types read
     * successfully before. A page therefore cannot show the types it managed to read as if they
     * were the patient's whole history. Pages that write their output as they go should call
     * {@link #canReadAll(Iterable)} before writing any prevention content.
     *
     * @param preventionType the prevention type's name
     * @return the preventions of that type, never {@code null}
     * @throws PreventionSourceUnavailableException if this read, or an earlier read made through
     *         this object, failed; the caller must show that preventions could not be read
     */
    public ArrayList<Map<String, Object>> getPreventionData(String preventionType) {
        if (unavailable != null) {
            throw unavailable;
        }
        try {
            return preventionDataByType.computeIfAbsent(preventionType,
                    type -> PreventionData.listForCheckedPatient(type, demographicId, dateOfBirth));
        } catch (PreventionSourceUnavailableException failure) {
            unavailable = failure;
            throw failure;
        }
    }

    /**
     * Reads the given types now, keeping each list for the rest of the request, and says whether
     * all of them could be read. A page calls this before it writes any prevention content, so
     * that a read failing halfway down the page cannot leave the earlier types on screen looking
     * like the complete history. It costs nothing extra for types the page reads anyway.
     *
     * <p>When this returns {@code false} the failure has been logged where the read failed, and
     * {@link #getPreventionData(String)} throws for every type from then on.
     *
     * @param preventionTypes the prevention type names the page is about to show; {@code null}
     *        names are skipped
     * @return {@code true} if every type was read; {@code false} if any read failed, in which
     *         case the page must show that preventions could not be read and none of the rows
     */
    public boolean canReadAll(Iterable<String> preventionTypes) {
        try {
            for (String preventionType : preventionTypes) {
                if (preventionType != null) {
                    getPreventionData(preventionType);
                }
            }
            return true;
        } catch (PreventionSourceUnavailableException _) {
            return false;
        }
    }
}
