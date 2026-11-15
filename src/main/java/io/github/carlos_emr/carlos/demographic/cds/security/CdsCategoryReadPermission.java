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
package io.github.carlos_emr.carlos.demographic.cds.security;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;

/**
 * Shared read-domain selection only. Callers still verify account/provider scope, demographic
 * access, patient restrictions and additional sensitive-data permissions. This confers no grant.
 */
public final class CdsCategoryReadPermission {
    private CdsCategoryReadPermission() { }
    public static String object(CdsCategory category) {
        return switch (category) {
            case DEMOGRAPHICS -> "_demographic";
            case MEDICATIONS_AND_TREATMENTS -> "_rx";
            case ALLERGIES_AND_ADVERSE_REACTIONS -> "_allergy";
            case IMMUNIZATIONS -> "_prevention";
            case LABORATORY_RESULTS -> "_lab";
            case REPORTS -> "_edoc";
            case APPOINTMENTS -> "_appointment";
            case CARE_ELEMENTS -> "_measurement";
            case PERSONAL_HISTORY, FAMILY_HISTORY, PAST_HEALTH, PROBLEM_LIST, RISK_FACTORS,
                    ALERTS_AND_SPECIAL_NEEDS, CLINICAL_NOTES, NEW_CATEGORY -> "_casemgmt.notes";
        };
    }
}
