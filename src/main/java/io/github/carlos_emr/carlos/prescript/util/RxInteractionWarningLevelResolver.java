/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.prescript.util;

import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.DemographicExt;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Resolves the drug-drug interaction warning level that applies when one provider prescribes for
 * one patient.
 *
 * <p>The level is stored in three places, all under the name {@value #PROPERTY_NAME}: the
 * facility (Administration &gt; Facilities), the provider's preferences ({@code property} row)
 * and the patient's demographic record ({@code demographic_ext} row). The facility value is the
 * default; a provider value above {@link #NOT_SPECIFIED} replaces it, and a patient value above
 * {@link #NOT_SPECIFIED} replaces both. This is the precedence {@code DrugrefUtil} has always
 * documented; before issue #4506 nothing read any of the three, so the setting was stored and
 * ignored.</p>
 *
 * <p>Levels follow the option values of the three settings pages: {@link #LOW} shows every
 * interaction, {@link #MEDIUM} moderate and major, {@link #HIGH} major only, and {@link #NONE}
 * none. {@link #NOT_SPECIFIED} shows every interaction. A stored value that is not one of these
 * (hand-edited data, a value from another system) is treated as not specified rather than failing
 * the check: an unreadable preference must not turn interaction warnings off.</p>
 *
 * @since 2026-10-15
 */
public class RxInteractionWarningLevelResolver {

    /** The facility, provider-property and demographic-extension name of the setting. */
    public static final String PROPERTY_NAME = "rxInteractionWarningLevel";

    /** No level chosen at this scope; the next scope down (or every interaction) applies. */
    public static final int NOT_SPECIFIED = 0;
    /** Show minor, moderate and major interactions. */
    public static final int LOW = 1;
    /** Show moderate and major interactions. */
    public static final int MEDIUM = 2;
    /** Show major interactions only. */
    public static final int HIGH = 3;
    /** Show no interaction warnings. */
    public static final int NONE = 4;

    private final UserPropertyDAO userPropertyDao;
    private final DemographicExtDao demographicExtDao;

    /**
     * @param userPropertyDao   reads the provider's {@value #PROPERTY_NAME} preference
     * @param demographicExtDao reads the patient's {@value #PROPERTY_NAME} setting
     */
    public RxInteractionWarningLevelResolver(UserPropertyDAO userPropertyDao, DemographicExtDao demographicExtDao) {
        this.userPropertyDao = userPropertyDao;
        this.demographicExtDao = demographicExtDao;
    }

    /**
     * The level for this provider prescribing for this patient.
     *
     * @param loggedInInfo  the prescribing provider's session; its current facility supplies the default
     * @param demographicNo the patient
     * @return one of {@link #NOT_SPECIFIED}, {@link #LOW}, {@link #MEDIUM}, {@link #HIGH}, {@link #NONE}
     */
    public int resolve(LoggedInInfo loggedInInfo, int demographicNo) {
        int level = NOT_SPECIFIED;
        Facility facility = loggedInInfo.getCurrentFacility();
        if (facility != null && isLevel(facility.getRxInteractionWarningLevel())) {
            level = facility.getRxInteractionWarningLevel();
        }

        UserProperty providerSetting = userPropertyDao.getProp(loggedInInfo.getLoggedInProviderNo(), PROPERTY_NAME);
        int providerLevel = parse(providerSetting == null ? null : providerSetting.getValue());
        if (providerLevel > NOT_SPECIFIED) {
            level = providerLevel;
        }

        DemographicExt patientSetting = demographicExtDao.getLatestDemographicExt(demographicNo, PROPERTY_NAME);
        int patientLevel = parse(patientSetting == null ? null : patientSetting.getValue());
        if (patientLevel > NOT_SPECIFIED) {
            level = patientLevel;
        }
        return level;
    }

    /**
     * Whether an interaction is shown at this level.
     *
     * <p>An interaction DrugRef could not grade ({@code significance} outside 1-3) is shown at every
     * level except {@link #NONE}: nothing says it falls below the provider's threshold, and hiding
     * a possibly major interaction is the worse error.</p>
     *
     * @param level        the resolved level
     * @param significance DrugRef's significance: 1 minor, 2 moderate, 3 major; anything else is ungraded
     * @return {@code true} when the interaction must be shown
     */
    public static boolean isShown(int level, int significance) {
        if (level == NONE) {
            return false;
        }
        if (significance < LOW || significance > HIGH) {
            return true;
        }
        return significance >= Math.max(level, LOW);
    }

    /**
     * Parses a stored level.
     *
     * @param raw the stored text, possibly {@code null} or padded
     * @return the level, or {@link #NOT_SPECIFIED} when the text is not one of the five levels
     */
    static int parse(String raw) {
        if (raw == null) {
            return NOT_SPECIFIED;
        }
        String value = raw.trim();
        if (value.length() != 1 || value.charAt(0) < '0' || value.charAt(0) > '4') {
            return NOT_SPECIFIED;
        }
        return value.charAt(0) - '0';
    }

    private static boolean isLevel(int value) {
        return value >= NOT_SPECIFIED && value <= NONE;
    }
}
