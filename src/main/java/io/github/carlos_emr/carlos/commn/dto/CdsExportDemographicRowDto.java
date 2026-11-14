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
package io.github.carlos_emr.carlos.commn.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Exact raw demographic scalars for a protected export capture, before UI defaults/converters.
 * SQL NULL and the empty string remain distinct; dates and flags are not guessed here.
 * Includes derived identity and operational columns for independent field classification;
 * capture does not mean every column belongs in the clinical XML. Associated demographic
 * extension/contact/pharmacy rows require their own capture in the same patient snapshot.
 */
public record CdsExportDemographicRowDto(int demographicNo, Map<String, String> fields) {
    public static final List<String> COLUMNS = List.of(
            "address", "alias", "anonymous", "cds_identity_hin",
            "cds_identity_issuer", "chart_no", "children", "citizenship",
            "city", "consentToUseEmailForCare", "country_of_origin", "date_joined",
            "date_of_birth", "eff_date", "email", "end_date",
            "family_doctor", "first_name", "gender", "genderId",
            "hc_renew_date", "hc_type", "hin", "lastUpdateDate",
            "lastUpdateUser", "last_name", "middleNames", "month_of_birth",
            "myOscarUserName", "newsletter", "official_lang", "patient_status",
            "patient_status_date", "pcn_indicator", "phone", "phone2",
            "postal", "pref_name", "previousAddress", "pronoun",
            "pronounId", "provider_no", "province", "residentialAddress",
            "residentialCity", "residentialPostal", "residentialProvince", "roster_date",
            "roster_enrolled_to", "roster_status", "roster_termination_date", "roster_termination_reason",
            "sex", "sin", "sourceOfIncome", "spoken_lang",
            "title", "ver", "year_of_birth");
    private static final Set<String> COLUMN_NAMES = Set.copyOf(COLUMNS);
    public CdsExportDemographicRowDto {
        if (demographicNo < 1 || fields == null || !fields.keySet().equals(COLUMN_NAMES)) {
            throw new IllegalArgumentException("CDS_EXPORT_DEMOGRAPHIC_CAPTURE_INVALID");
        }
        // Map.copyOf rejects null; replacing SQL NULL would destroy source evidence.
        var copy = new LinkedHashMap<String, String>();
        for (String column : COLUMNS) copy.put(column, fields.get(column));
        fields = Collections.unmodifiableMap(copy);
    }
    @Override public String toString() { return "CdsExportDemographicRowDto[protected]"; }
}
