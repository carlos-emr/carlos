/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.service;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.demographic.PatientReadAccess;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/** Atomic manual deletion with source coordination and an explicit clinician tombstone.
 * @since 2026-11-09
 */
@Service
public class ClinicalMeasurementDeletionService {
    private final MeasurementDao measurements;
    private final MeasurementsDeletedDao deleted;
    private final MeasurementsExtDao extensions;
    private final SecurityInfoManager security;
    public ClinicalMeasurementDeletionService(MeasurementDao measurements, MeasurementsDeletedDao deleted,
            MeasurementsExtDao extensions, SecurityInfoManager security) {
        this.measurements = measurements; this.deleted = deleted; this.extensions = extensions; this.security = security;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(List<Integer> ids, String expectedPatient, LoggedInInfo actor) {
        List<Integer> ordered = ids.stream().distinct().sorted().toList();
        Map<Integer, Integer> owners = new HashMap<>();
        Map<Integer, List<Integer>> sources = new LinkedHashMap<>();
        for (int id : ordered) {
            Measurement row = measurements.find(id);
            if (row == null) throw new StaleMeasurement();
            owners.put(id, row.getDemographicId());
            sources.put(id, sourceReports(id));
        }
        Hl7ChainLocks.lockReports(sources.values().stream().flatMap(List::stream).distinct().toList());
        List<Measurement> rows = new ArrayList<>();
        for (int id : ordered) {
            Measurement row = measurements.findForUpdate(id);
            if (row == null || !Objects.equals(owners.get(id), row.getDemographicId())
                    || !sources.get(id).equals(sourceReports(id))) throw new StaleMeasurement();
            if (expectedPatient != null && !expectedPatient.equals(Integer.toString(row.getDemographicId()))) {
                throw new SecurityException("Measurement patient changed");
            }
            PatientReadAccess.requirePatient(security, actor, row.getDemographicId(), "_measurement", "d");
            rows.add(row);
        }
        for (Measurement row : rows) {
            deleted.persist(new MeasurementsDeleted(row));
            if (!sources.get(row.getId()).isEmpty()) {
                var marker = new MeasurementsExt(); marker.setMeasurementId(row.getId());
                marker.setKeyVal(CmlProjectionService.ARCHIVE); marker.setVal("CLINICIAN"); extensions.persist(marker);
            }
        }
        measurements.batchRemoveAtomically(rows);
    }

    private List<Integer> sourceReports(int measurement) {
        return extensions.getMeasurementsExtByMeasurementId(measurement).stream()
                .filter(row -> "lab_no".equals(row.getKeyVal())).map(row -> Integer.valueOf(row.getVal()))
                .distinct().sorted().toList();
    }
    public static final class StaleMeasurement extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public StaleMeasurement() { super("Measurement changed; reload its history"); }
    }

}
