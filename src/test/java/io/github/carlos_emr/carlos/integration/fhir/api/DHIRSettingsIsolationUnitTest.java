/*
 * Copyright (c) 2026 CARLOS Contributors.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details. You should have received a copy of the GNU General
 * Public License along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * CARLOS EMR Project, https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.integration.fhir.api;

import io.github.carlos_emr.carlos.integration.fhir.builder.FhirBundleBuilder;
import io.github.carlos_emr.carlos.integration.fhir.manager.OscarFhirConfigurationManager;
import io.github.carlos_emr.carlos.integration.fhir.manager.OscarFhirResourceManager;
import io.github.carlos_emr.carlos.integration.fhir.model.Patient;
import io.github.carlos_emr.carlos.integration.fhir.model.SubmittingPractitioner;
import io.github.carlos_emr.carlos.integration.fhir.resources.Settings;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class DHIRSettingsIsolationUnitTest {
    @Test
    void shouldUseFreshSenderSettingsForEachBundle_whenSpecificAndAllImmunizationsAlternate() {
        LoggedInInfo login = mock(LoggedInInfo.class);
        Patient patient = mock(Patient.class);
        List<Settings> observed = new ArrayList<>();
        try (var resources = mockStatic(OscarFhirResourceManager.class);
             var configuration = mockConstruction(OscarFhirConfigurationManager.class, (mock, context) -> {
                 observed.add((Settings) context.arguments().get(1));
                 when(mock.getLoggedInInfo()).thenReturn(login);
             });
             var bundles = mockConstruction(FhirBundleBuilder.class);
             var practitioner = mockConstruction(SubmittingPractitioner.class)) {
            resources.when(() -> OscarFhirResourceManager.getPatientByDemographicNumber(any(), eq(1))).thenReturn(patient);
            DHIR.getFhirBundleBuilder(login, 1, 100);
            DHIR.getFhirBundleBuilder(login, 1);
            DHIR.getFhirBundleBuilder(login, 1, 101);
        }
        assertThat(observed.stream().map(Settings::isIncludeSenderEndpoint).toList()).containsExactly(true, false, true);
        assertThat(observed.get(0)).isNotSameAs(observed.get(1)).isNotSameAs(observed.get(2));
    }
}
