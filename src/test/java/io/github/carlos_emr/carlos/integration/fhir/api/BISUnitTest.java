/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.integration.fhir.api;

import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.integration.fhir.builder.FhirCommunicationBuilder;
import io.github.carlos_emr.carlos.integration.fhir.manager.OscarFhirConfigurationManager;
import io.github.carlos_emr.carlos.integration.fhir.manager.OscarFhirResourceManager;
import io.github.carlos_emr.carlos.integration.fhir.model.Patient;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
class BISUnitTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void keepsThePatientClinicAndOptionalFormsTogether(boolean includeForms) {
        Clinic clinic = new Clinic();
        clinic.setId(5);
        clinic.setClinicName("Test Clinic");
        var patient = mock(Patient.class);
        var login = mock(LoggedInInfo.class);
        String form = includeForms ? "synthetic" : null;
        try (var resources = mockStatic(OscarFhirResourceManager.class);
             var configuration = mockConstruction(OscarFhirConfigurationManager.class);
             var builders = mockConstruction(FhirCommunicationBuilder.class)) {
            resources.when(() -> OscarFhirResourceManager.getPatientByDemographicNumber(any(), eq(1)))
                    .thenReturn(patient);
            var result = BIS.getFhirCommunicationBuilder(login, 1, form, form, form, clinic);
            assertThat(result).isSameAs(builders.constructed().getFirst());
            verify(result).setSubject(patient);
            verify(result, times(includeForms ? 3 : 0)).addAttachment(any());
            verify(patient).setManagingOrganizationReference("#Organization5");
        }
    }
}
