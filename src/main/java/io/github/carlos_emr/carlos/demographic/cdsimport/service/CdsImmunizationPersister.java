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
import org.springframework.beans.factory.annotation.Autowired;
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
    private final String preventionItems;
    private final io.github.carlos_emr.carlos.commn.dao.CVCMappingDao cvcMappings;
    private final io.github.carlos_emr.carlos.commn.dao.CVCImmunizationDao cvcImmunizations;
    private volatile Map<String, String> preventionTypes;

    public CdsImmunizationPersister(PreventionDao preventions, PreventionExtDao extensions,
            PartialDateDao dates, CdsClinicalNormalizationService normalization,
            String clinicZone) {
        this(preventions, extensions, dates, normalization, clinicZone,
                "classpath:oscar/prevention/PreventionItems.xml");
    }

    public CdsImmunizationPersister(PreventionDao preventions, PreventionExtDao extensions,
            PartialDateDao dates, CdsClinicalNormalizationService normalization,
            String clinicZone, String preventionItems) {
        this(preventions, extensions, dates, normalization, clinicZone, preventionItems, null, null);
    }

    @Autowired
    public CdsImmunizationPersister(PreventionDao preventions, PreventionExtDao extensions,
            PartialDateDao dates, CdsClinicalNormalizationService normalization,
            @Value("${cds.import.clinicalZone:}") String clinicZone,
            @Value("${PREVENTION_ITEMS:classpath:oscar/prevention/PreventionItems.xml}") String preventionItems,
            io.github.carlos_emr.carlos.commn.dao.CVCMappingDao cvcMappings,
            io.github.carlos_emr.carlos.commn.dao.CVCImmunizationDao cvcImmunizations) {
        this.preventions = preventions;
        this.extensions = extensions;
        this.dates = dates;
        this.normalization = normalization;
        this.clinicZone = clinicZone == null || clinicZone.isBlank() ? null : ZoneId.of(clinicZone);
        this.preventionItems = Objects.requireNonNull(preventionItems);
        this.cvcMappings = cvcMappings;
        this.cvcImmunizations = cvcImmunizations;
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
        extra(prevention.getId(), "cdsName", source.name());
        return Map.of("preventions", String.valueOf(prevention.getId()));
    }

    Prevention candidate(int demographicNo, String auditProviderNo, CdsImmunizationDto source) {
        if (demographicNo < 1) throw new IllegalArgumentException("Invalid target patient");
        Prevention prevention = new Prevention();
        prevention.setDemographicId(demographicNo);
        prevention.setProviderNo(""); // Import does not identify an administering clinician.
        prevention.setCreatorProviderNo(auditProviderNo);
        prevention.setPreventionType(preventionType(source.type()));
        prevention.setRefused(source.refused());
        prevention.setPreventionDate(nativeDate(source));
        return prevention;
    }

    /** Validate storage and warm the configured type mapping during preflight, before native unit writes. */
    public void validate(CdsImmunizationDto source) {
        nativeDate(source);
        preventionType(source.type());
    }

    /** Same injection/healthCanadaType mapping as the retired importer, loaded before native writes. */
    private String preventionType(String type) {
        Map<String, String> configured = preventionTypes;
        if (configured == null) configured = loadPreventionTypes();
        return configured.getOrDefault(type == null ? "" : type, "OtherA");
    }

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "PATH_TRAVERSAL_IN",
            justification = "PREVENTION_ITEMS is trusted server configuration, resolved by PathValidationUtils")
    private synchronized Map<String, String> loadPreventionTypes() {
        if (preventionTypes != null) return preventionTypes;
        try (var input = preventionItems.startsWith("classpath:")
                ? getClass().getClassLoader().getResourceAsStream(preventionItems.substring(10))
                : java.nio.file.Files.newInputStream(io.github.carlos_emr.carlos.utility.PathValidationUtils
                        .resolveTrustedPath(new java.io.File(preventionItems)).toPath())) {
            if (input == null) throw new IOException("Prevention configuration unavailable");
            byte[] bytes = input.readNBytes(1048577);
            if (bytes.length > 1048576) throw new IOException("Prevention configuration exceeds its bound");
            var document = io.github.carlos_emr.carlos.utility.XmlUtils.createSecureSAXBuilder()
                    .build(new java.io.ByteArrayInputStream(bytes));
            Map<String, String> mapped = new java.util.LinkedHashMap<>();
            var addedSnomeds = new java.util.HashSet<String>();
            boolean fallback = false;
            for (var item : document.getRootElement().getChildren("item")) {
                String name = item.getAttributeValue("name"), type = item.getAttributeValue("healthCanadaType");
                var cvc = cvcMappings == null ? null : cvcMappings.findByOscarName(name);
                if (cvc != null) {
                    if (Boolean.TRUE.equals(cvc.getPreferCVC())) continue;
                    if (cvcImmunizations.findBySnomedConceptId(cvc.getCvcSnomedId()) != null)
                        addedSnomeds.add(cvc.getCvcSnomedId());
                }
                if ("OtherA".equals(name)) fallback = true;
                if (name != null && type != null && !type.isBlank()
                        && "injection".equals(item.getAttributeValue("layout"))) mapped.put(type, name);
            }
            if (cvcImmunizations != null) {
                for (var vaccine : cvcImmunizations.findAllGeneric()) {
                    String name = vaccine.getPicklistName();
                    if (name != null && !addedSnomeds.contains(vaccine.getSnomedConceptId())) mapped.put(name, name);
                }
            }
            if (!fallback) throw new IOException("Prevention fallback unavailable");
            preventionTypes = Map.copyOf(mapped);
            return preventionTypes;
        } catch (IOException | org.jdom2.JDOMException invalid) {
            throw new CdsImportException("IMMUNIZATION_PREVENTION_CONFIG_UNAVAILABLE");
        }
    }

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
