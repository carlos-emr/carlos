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
import io.github.carlos_emr.carlos.commn.dao.PreventionDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionExtDao;
import io.github.carlos_emr.carlos.commn.model.PartialDate;
import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.commn.model.PreventionExt;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImmunizationDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsClinicalTransactions;
import java.io.IOException;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Preserves administered/refused history even when its clinical date is unknown. */
@Service
public class CdsImmunizationPersister {
    private final PreventionDao preventions;
    private final PreventionExtDao extensions;
    private final PartialDateDao dates;
    private final CdsClinicalNormalizationService normalization;
    private final ZoneId clinicZone;

    public CdsImmunizationPersister(PreventionDao preventions, PreventionExtDao extensions,
            PartialDateDao dates, CdsClinicalNormalizationService normalization,
            @Value("${cds.import.clinicalZone:}") String clinicZone) {
        this.preventions = preventions;
        this.extensions = extensions;
        this.dates = dates;
        this.normalization = normalization;
        this.clinicZone = clinicZone == null || clinicZone.isBlank() ? null : ZoneId.of(clinicZone);
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Map<String, String> write(int demographicNo, String auditProviderNo, CdsRecordDto record)
            throws IOException {
        return write(demographicNo, auditProviderNo, normalization.immunization(record));
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Map<String, String> write(int demographicNo, String auditProviderNo, CdsImmunizationDto source) {
        CdsClinicalTransactions.requireUnit();
        Prevention prevention = candidate(demographicNo, auditProviderNo, source);
        preventions.persist(prevention);
        preventions.flush();
        if (source.date().legacyPartialFormat() != null) {
            dates.persist(new PartialDate(PartialDate.PREVENTION, prevention.getId(),
                    PartialDate.PREVENTION_PREVENTIONDATE, source.date().legacyPartialFormat()));
        }
        extra(prevention.getId(), "manufacture", source.manufacturer());
        extra(prevention.getId(), "lot", source.lot());
        extra(prevention.getId(), "route", source.route());
        extra(prevention.getId(), "location", source.site());
        extra(prevention.getId(), "dose", source.dose());
        extra(prevention.getId(), "comments", source.notes());
        extra(prevention.getId(), "instructions", source.instructions());
        extra(prevention.getId(), "cdsDateOriginal", source.date().original());
        extra(prevention.getId(), "cdsDatePrecision", source.date().precision().name());
        extra(prevention.getId(), "cdsCode", source.code());
        extra(prevention.getId(), "cdsCodeSystem", source.codeSystem());
        extra(prevention.getId(), "cdsType", source.type());
        return Map.of("preventions", String.valueOf(prevention.getId()));
    }

    Prevention candidate(int demographicNo, String auditProviderNo, CdsImmunizationDto source) {
        if (demographicNo < 1) throw new IllegalArgumentException("Invalid target patient");
        Prevention prevention = new Prevention();
        prevention.setDemographicId(demographicNo);
        prevention.setProviderNo(""); // Import does not identify an administering clinician.
        prevention.setCreatorProviderNo(auditProviderNo);
        prevention.setPreventionType(source.name());
        prevention.setRefused(source.refused());
        prevention.setPreventionDate(nativeDate(source));
        return prevention;
    }

    /** The same pure storage check runs during complete preflight, before any native unit writes. */
    public void validate(CdsImmunizationDto source) { nativeDate(source); }

    private java.util.Date nativeDate(CdsImmunizationDto source) {
        if (source.date().precision()
                == io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto.Precision.UNKNOWN) return null;
        java.util.Date value = Objects.requireNonNull(
                source.date().timestampStorageAnchor(requireClinicZone(source)),
                "Known immunization date requires a native storage anchor");
        // prevention_date is DATETIME(0); an adopted fractional-rounding mode may not move its day.
        return java.util.Date.from(value.toInstant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
    }

    private ZoneId requireClinicZone(CdsImmunizationDto source) {
        if (source.date().offsetDateTime() != null) return ZoneId.systemDefault();
        if (clinicZone == null) throw new io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsClinicalException("CDS_CLINICAL_ZONE_REQUIRED", "Date");
        return clinicZone;
    }

    private void extra(int preventionId, String key, String value) {
        if (value == null) return;
        PreventionExt ext = new PreventionExt();
        ext.setPreventionId(preventionId);
        ext.setKeyval(key);
        ext.setVal(value);
        extensions.persist(ext);
    }
}
