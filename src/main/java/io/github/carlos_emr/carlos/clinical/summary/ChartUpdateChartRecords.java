/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import io.github.carlos_emr.carlos.managers.*;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.RxStatus;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Patient-authorized comparison only. Native record managers retain consent/access filtering. */
@Service
public class ChartUpdateChartRecords {
    private final SecurityInfoManager security;
    private final RxManager medications;
    private final AllergyManager allergies;
    private final MeasurementManager measurements;
    private final PreventionManager preventions;

    public ChartUpdateChartRecords(SecurityInfoManager security, RxManager medications, AllergyManager allergies,
            MeasurementManager measurements, PreventionManager preventions) {
        this.security = security;
        this.medications = medications;
        this.allergies = allergies;
        this.measurements = measurements;
        this.preventions = preventions;
    }

    public List<ChartUpdateContext.Entry> load(LoggedInInfo user, int patient) {
        List<ChartUpdateContext.Entry> entries = new ArrayList<>();
        if (security.hasPrivilege(user, "_rx", "r", patient)) {
            for (var drug : medications.getDrugs(user, patient, RxStatus.CURRENT)) {
                requirePatient(patient, drug.getDemographicId());
                entries.add(new ChartUpdateContext.Entry("medication-" + drug.getId(), "medication",
                        value(drug.getDrugName()) + "\n" + value(drug.getSpecial())
                        + "\n" + value(drug.getSpecialInstruction()) + "\n" + value(drug.getDosageDisplay())
                        + "\n" + value(drug.getRoute()) + "\n" + value(drug.getFreqCode())
                        + "\nStart: " + date(drug.getRxDate()) + "\nEnd: " + date(drug.getEndDate())));
            }
        }
        if (security.hasPrivilege(user, "_allergy", "r", patient)) {
            for (var allergy : allergies.getActiveAllergies(user, patient)) {
                requirePatient(patient, allergy.getDemographicNo());
                entries.add(new ChartUpdateContext.Entry("allergy-" + allergy.getId(), "allergy",
                        value(allergy.getDescription()) + "\nReaction: " + value(allergy.getReaction())
                        + "\nSeverity: " + value(allergy.getSeverityOfReactionDesc()) + "\nStart: " + date(allergy.getStartDate())));
            }
        }
        if (security.hasPrivilege(user, "_measurement", "r", patient)) {
            for (var measurement : measurements.getMeasurementByDemographicIdAfter(user, patient, new Date(0))) {
                requirePatient(patient, measurement.getDemographicId());
                entries.add(new ChartUpdateContext.Entry("measurement-" + measurement.getId(), "measurement",
                        value(measurement.getType()) + ": " + value(measurement.getDataField())
                        + "\n" + value(measurement.getMeasuringInstruction()) + "\n" + value(measurement.getComments())
                        + "\nObserved: " + date(measurement.getDateObserved())));
            }
        }
        if (security.hasPrivilege(user, "_prevention", "r", patient)) {
            for (var prevention : preventions.getImmunizationsByDemographic(user, patient)) {
                requirePatient(patient, prevention.getDemographicId());
                if (!prevention.isDeleted()) {
                    entries.add(new ChartUpdateContext.Entry("prevention-" + prevention.getId(), "prevention",
                            value(prevention.getPreventionType()) + "\nDate: " + date(prevention.getPreventionDate())
                            + "\nDose: " + value(prevention.getDose()) + "\n" + value(prevention.getComment())
                            + "\nRefused: " + prevention.getImmunizationRefused()));
                }
            }
        }
        return List.copyOf(entries);
    }

    private static void requirePatient(int expected, Integer actual) {
        if (!Objects.equals(expected, actual)) throw new SecurityException("Chart-update access unavailable");
    }
    private static String value(String value) { return Objects.toString(value, ""); }
    private static String date(Date value) {
        return value == null ? "" : new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(value);
    }
}
