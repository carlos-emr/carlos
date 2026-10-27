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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.PartialDate;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalNativeDateDto;
import java.text.DateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Reads the latest explicit native precision without deriving clinical facts from an anchor. */
@Service
public class CdsTemporalReadService {
    private final PartialDateDao dates;
    public CdsTemporalReadService(PartialDateDao dates) { this.dates = Objects.requireNonNull(dates); }

    public CdsTemporalNativeDateDto date(Date stored, int table, Integer id, int field) {
        return CdsTemporalNativeDateDto.from(stored, id == null ? null : dates.getFormat(table, id, field));
    }
    public CdsTemporalNativeDateDto medicationStart(Drug drug) {
        return date(drug.getRxDate(), PartialDate.DRUGS, drug.getId(), PartialDate.DRUGS_STARTDATE);
    }
    public CdsTemporalNativeDateDto allergyStart(Allergy allergy) {
        return date(allergy.getStartDate(), PartialDate.ALLERGIES, allergy.getId(), PartialDate.ALLERGIES_STARTDATE);
    }
    public CdsTemporalNativeDateDto allergyRecorded(Allergy allergy) {
        return date(allergy.getEntryDate(), PartialDate.ALLERGIES, allergy.getId(), PartialDate.ALLERGIES_ENTRYDATE);
    }
    public Map<Integer, String> allergyRecordedLabels(List<Allergy> allergies) {
        Map<Integer, String> result = new LinkedHashMap<>();
        DateFormat exactFormat = new java.text.SimpleDateFormat("MM/dd/yy", java.util.Locale.ROOT);
        for (Allergy allergy : allergies) {
            CdsTemporalNativeDateDto date = allergyRecorded(allergy);
            result.put(allergy.getId(), date.isExact() ? exactFormat.format(allergy.getEntryDate()) : date.display());
        }
        return Map.copyOf(result);
    }
    public Map<Integer, String> medicationStartLabels(List<Drug> drugs) {
        Map<Integer, String> result = new LinkedHashMap<>();
        DateFormat exactFormat = new java.text.SimpleDateFormat("MM/dd/yy", java.util.Locale.ROOT);
        for (Drug drug : drugs) {
            CdsTemporalNativeDateDto date = medicationStart(drug);
            result.put(drug.getId(), date.isExact() ? exactFormat.format(drug.getRxDate()) : date.display());
        }
        return Map.copyOf(result);
    }
    public String display(Date stored, int table, Integer id, int field, DateFormat exactFormat) {
        CdsTemporalNativeDateDto value = date(stored, table, id, field);
        return value.isExact() ? exactFormat.format(stored) : value.display();
    }
}
