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

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Wire section names, independent of either engine's work units and report classifications. */
public enum CdsCategory {
    DEMOGRAPHICS("Demographics"),
    PERSONAL_HISTORY("PersonalHistory"),
    FAMILY_HISTORY("FamilyHistory"),
    PAST_HEALTH("PastHealth"),
    PROBLEM_LIST("ProblemList"),
    RISK_FACTORS("RiskFactors"),
    ALLERGIES_AND_ADVERSE_REACTIONS("AllergiesAndAdverseReactions"),
    MEDICATIONS_AND_TREATMENTS("MedicationsAndTreatments"),
    IMMUNIZATIONS("Immunizations"),
    LABORATORY_RESULTS("LaboratoryResults"),
    APPOINTMENTS("Appointments"),
    CLINICAL_NOTES("ClinicalNotes"),
    REPORTS("Reports"),
    CARE_ELEMENTS("CareElements"),
    ALERTS_AND_SPECIAL_NEEDS("AlertsAndSpecialNeeds"),
    NEW_CATEGORY("NewCategory");

    private static final Set<String> WIRE_NAMES = Arrays.stream(values())
            .map(CdsCategory::wireName).collect(Collectors.toUnmodifiableSet());
    private final String wireName;

    CdsCategory(String wireName) { this.wireName = wireName; }

    public String wireName() { return wireName; }

    public static Set<String> wireNames() { return WIRE_NAMES; }

    /** Reject unknown categories rather than silently sending them to NewCategory or Notes. */
    public static CdsCategory fromWireName(String name) {
        for (CdsCategory category : values()) {
            if (category.wireName.equals(name)) return category;
        }
        throw new IllegalArgumentException("CDS_CATEGORY_INVALID");
    }
}
