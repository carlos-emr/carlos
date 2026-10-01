/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import io.github.carlos_emr.carlos.managers.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.RxStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChartUpdateChartRecordsUnitTest {
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final RxManager drugs = mock(RxManager.class);
    private final AllergyManager allergies = mock(AllergyManager.class);
    private final MeasurementManager measurements = mock(MeasurementManager.class);
    private final PreventionManager preventions = mock(PreventionManager.class);
    private final LoggedInInfo user = mock(LoggedInInfo.class);
    private final ChartUpdateChartRecords records = new ChartUpdateChartRecords(security, drugs, allergies, measurements, preventions);

    @Test void shouldExcludeUnauthorizedModules_beforeCallingNativeManagers() {
        assertThat(records.load(user, 3001)).isEmpty();
        verifyNoInteractions(drugs, allergies, measurements, preventions);
    }

    @Test void shouldPreserveMedicationInstructionsAllergyReactionsAndObservationDates() {
        when(security.hasPrivilege(eq(user), anyString(), eq("r"), eq(3001))).thenReturn(true);
        Drug drug = new Drug(); drug.setId(1); drug.setDemographicId(3001); drug.setCustomName("Synthetic drug");
        drug.setSpecial("5 mg daily"); drug.setSpecialInstruction("Only if instructed");
        when(drugs.getDrugs(user, 3001, RxStatus.CURRENT)).thenReturn(List.of(drug));
        Allergy allergy = new Allergy(); allergy.setDemographicNo(3001);
        allergy.setDescription("Synthetic allergen"); allergy.setReaction("Rash"); allergy.setSeverityOfReaction("2");
        when(allergies.getActiveAllergies(user, 3001)).thenReturn(List.of(allergy));
        Measurement measurement = new Measurement(); measurement.setDemographicId(3001);
        measurement.setType("BP"); measurement.setDataField("120/80"); measurement.setDateObserved(java.sql.Date.valueOf("2026-01-09"));
        when(measurements.getMeasurementByDemographicIdAfter(eq(user), eq(3001), any())).thenReturn(List.of(measurement));
        var entries = records.load(user, 3001);
        assertThat(entries).hasSize(3);
        assertThat(entries.get(0).text()).contains("5 mg daily", "Only if instructed");
        assertThat(entries.get(1).text()).contains("Rash", "Severity:");
        assertThat(entries.get(2).text()).contains("BP: 120/80", "2026-01-09");
    }

    @Test void shouldRejectUnexpectedPatient_fromNativeManager() {
        when(security.hasPrivilege(user, "_allergy", "r", 3001)).thenReturn(true);
        Allergy allergy = new Allergy(); allergy.setDemographicNo(3002);
        when(allergies.getActiveAllergies(user, 3001)).thenReturn(List.of(allergy));
        assertThatThrownBy(() -> records.load(user, 3001)).isInstanceOf(SecurityException.class);
    }
}
