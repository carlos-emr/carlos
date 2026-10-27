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

import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementsExtDao;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.MeasurementsExt;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsCareElementDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsClinicalException;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** One typed care observation and bounded metadata, committed only by the native unit owner. */
@Service
public class CdsCarePersister {
    public static final Map<String, String> RESERVED_INSTRUCTIONS = Map.of(
            "CDS_GOALS", "Source documented goals", "CDS_EXAM", "Source exam performed; result not supplied",
            "CDS_CARE_VALUE", "Uninterpreted source care value");
    private final MeasurementDao measurements;
    private final MeasurementsExtDao extensions;
    private final MeasurementTypeDao types;
    private final CdsImportLedgerService ledger;
    @Value("${cds.import.clinicalZone:}") private String clinicalZone = "";

    public CdsCarePersister(MeasurementDao measurements, MeasurementsExtDao extensions,
            MeasurementTypeDao types, CdsImportLedgerService ledger) {
        this.measurements = measurements; this.extensions = extensions; this.types = types; this.ledger = ledger;
    }

    public void validate(CdsCareElementDto value) {
        value.date().timestampStorageAnchor(zone());
        String requiredInstruction = RESERVED_INSTRUCTIONS.get(value.measurementType());
        if (value.measurementType().equals("WT")) requiredInstruction = "in kg";
        else if (value.measurementType().equals("HT")) requiredInstruction = "in cm";
        else if (value.measurementType().equals("WAIS")) requiredInstruction = "Waist Circum in cm";
        if (!types.hasCdsImportType(value.measurementType(), requiredInstruction)) {
            throw new CdsClinicalException("CDS_CARE_NATIVE_TYPE_NOT_CONFIGURED", value.element());
        }
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Map<String, String> write(String run, long epoch, String unitKey, int patient,
            CdsRecordDto record, CdsCareElementDto value) {
        CdsImportUnitService.requireActive(run, epoch, unitKey);
        validate(value);
        Measurement measurement = candidate(patient, value);
        measurements.persist(measurement); measurements.flush();
        Map<String, String> references = new LinkedHashMap<>();
        references.put("measurements", measurement.getId().toString());
        List<MeasurementsExt> metadata = new ArrayList<>();
        add(metadata, measurement, "cdsCareElement", value.element());
        add(metadata, measurement, "date_precision", "FULL_DATE");
        add(metadata, measurement, "cdsSourceDate", value.date().original());
        add(metadata, measurement, "unit", value.unit());
        add(metadata, measurement, "loinc", value.code());
        add(metadata, measurement, "cdsNativeOverflow", value.nativeOverflow() ? "true" : null);
        add(metadata, measurement, "cdsUninterpreted", value.uninterpreted() ? "true" : null);
        add(metadata, measurement, "cdsValueDigest", value.sourceValueDigest());
        add(metadata, measurement, "cdsSourcePath", record.sourceXPath());
        if (value.date().appliedShiftDays() != 0) add(metadata, measurement, "cdsShiftDays", Integer.toString(value.date().appliedShiftDays()));
        for (MeasurementsExt item : metadata) extensions.persist(item);
        extensions.flush();
        for (int index = 0; index < metadata.size(); index++) {
            references.put("measurementsExt#" + index, metadata.get(index).getId().toString());
        }
        String patientKey = ledger.unit(run, unitKey).getPatientKey();
        if (value.date().calendarAnchorAdjusted(zone()))
            ledger.diagnostic(run, epoch, patientKey, unitKey, "WARNING", "CDS_CALENDAR_STORAGE_ANCHOR_NORMALIZED",
                    "NORMALIZATION", record.sourceXPath() + "/Date",
                    "A native calendar storage anchor preserves the source date and its separate precision; no source clock was asserted",
                    null, false);
        for (var diagnostic : value.diagnostics()) {
            boolean blocking = diagnostic.code().equals("CDS_CARE_VALUE_NOT_QUANTIFIED");
            ledger.diagnostic(run, epoch, patientKey, unitKey, blocking ? "REVIEW" : "WARNING", diagnostic.code(),
                    "NORMALIZATION", record.sourceXPath() + "/" + diagnostic.field(),
                    blocking ? "Source care value is retained as typed text and requires clinical review before quantitative use"
                            : "Source care normalization or bounded native reference is explicit; complete original remains in protected Care Elements",
                    null, blocking);
        }
        return Map.copyOf(references);
    }

    Measurement candidate(int patient, CdsCareElementDto value) {
        if (patient < 1) throw new IllegalArgumentException("Invalid target patient");
        Measurement row = new Measurement();
        row.setDemographicId(patient); row.setType(value.measurementType());
        row.setImportedSourceProviderNo(null); // Source has no author field; the importing actor is audited separately.
        row.setDataField(value.value());
        row.setMeasuringInstruction(RESERVED_INSTRUCTIONS.getOrDefault(value.measurementType(),
                value.unit() == null ? "Source assertion" : "in " + value.unit()));
        row.setDateObserved(value.date().timestampStorageAnchor(zone()));
        row.setComments(value.nativeOverflow() ? "CDS_COMPLETE_VALUE_IN_SOURCE"
                : value.uninterpreted() ? "CDS_UNINTERPRETED_SOURCE_VALUE" : "");
        return row;
    }
    private ZoneId zone() {
        if (clinicalZone == null || clinicalZone.isBlank()) throw new CdsClinicalException("CDS_CLINICAL_ZONE_REQUIRED", "Date");
        try { return ZoneId.of(clinicalZone); }
        catch (DateTimeException invalid) { throw new CdsClinicalException("CDS_CLINICAL_ZONE_INVALID", "Date"); }
    }
    private static void add(List<MeasurementsExt> metadata, Measurement row, String key, String value) {
        if (value == null) return;
        MeasurementsExt extension = new MeasurementsExt(row.getId());
        extension.setKeyVal(key); extension.setVal(value); metadata.add(extension);
    }
}
