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
package io.github.carlos_emr.carlos.demographic.cds.contract;

import java.math.BigDecimal;

/** Shared finite clinical meaning for care values; returning null requires a nonquantitative source disposition. */
public final class CdsCareScalarContract {
    public static final java.util.Set<String> ELEMENTS = java.util.Set.of("SmokingStatus", "SmokingPacks", "Weight", "Height", "WaistCircumference", "BloodPressure",
            "DiabetesComplicationsScreening", "DiabetesMotivationalCounselling", "DiabetesSelfManagementCollaborative", "DiabetesSelfManagementChallenges",
            "DiabetesEducationalSelfManagement", "HypoglycemicEpisodes", "SelfMonitoringBloodGlucose");
    public static final int GOAL_NATIVE_BYTE_BUDGET = 32768;
    public static final String OVERFLOW_GOALS = "[Imported collaborative goals: complete value available in Care Elements source details]";
    private CdsCareScalarContract() { }
    public static String nonnegativeDecimal(String original) {
        if (original == null) return null;
        String value = original.strip();
        if (!value.matches("[+]?(?:[0-9]++(?:\\.[0-9]*+)?|\\.[0-9]++)")) return null;
        BigDecimal number = new BigDecimal(value);
        if (!Double.isFinite(number.doubleValue())) return null;
        return number.stripTrailingZeros().toPlainString();
    }
}
