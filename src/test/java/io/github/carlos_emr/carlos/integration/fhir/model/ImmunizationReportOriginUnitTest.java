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
package io.github.carlos_emr.carlos.integration.fhir.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.integration.fhir.interfaces.ResourceAttributeFilterInterface.OptionalFHIRAttribute;
import io.github.carlos_emr.carlos.integration.fhir.manager.OscarFhirConfigurationManager;
import io.github.carlos_emr.carlos.integration.fhir.resources.ResourceAttributeFilter;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.hl7.fhir.dstu3.model.codesystems.ImmunizationOrigin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Verifies the external HL7 contract, including the DHIR historical-record override. */
@DisplayName("FHIR immunization report origin contract")
class ImmunizationReportOriginUnitTest extends CarlosUnitTestBase {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "External clinician")
    void shouldSerializeProviderCode_whenDoseWasDoneExternally(String providerName) throws Exception {
        Prevention prevention = prevention(false, false);
        prevention.setProviderName(providerName);

        Immunization<Prevention> resource = new Immunization<>(prevention);
        assertOrigin(resource, ImmunizationOrigin.PROVIDER,
                providerName == null || providerName.isEmpty() ? "other" : providerName);
        assertThat(resource.getFhirResource().getPrimarySource()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldKeepRecordCode_whenEstimatedDoseIsHistorical(boolean primarySource) throws Exception {
        Prevention prevention = prevention(primarySource, true);
        prevention.setProviderName("External clinician");

        Immunization<Prevention> resource = new Immunization<>(prevention);
        assertOrigin(resource, ImmunizationOrigin.RECORD, "other");
        assertThat(resource.getFhirResource().getPrimarySource()).isFalse();
    }

    @Test
    void shouldOmitReportOrigin_whenRecentDoseIsPrimarySource() throws Exception {
        Immunization<Prevention> resource = new Immunization<>(prevention(true, false));

        assertThat(resource.getFhirResource().getPrimarySource()).isTrue();
        assertThat(resource.getFhirResource().hasReportOrigin()).isFalse();
        assertThat(new ObjectMapper().readTree(resource.getFhirJSON()).has("reportOrigin")).isFalse();
    }

    @Test
    void shouldKeepProviderCode_whenHistoricalEstimationIsDisabled() throws Exception {
        OscarFhirConfigurationManager configuration = mock(OscarFhirConfigurationManager.class);
        ResourceAttributeFilter filter = mock(ResourceAttributeFilter.class);
        when(configuration.getResourceAttributeFilter(Immunization.class)).thenReturn(filter);
        when(filter.include(OptionalFHIRAttribute.dateIsEstimated)).thenReturn(false);
        Prevention prevention = prevention(false, true);
        prevention.setProviderName("External clinician");

        assertOrigin(new Immunization<>(prevention, configuration), ImmunizationOrigin.PROVIDER, "External clinician");
    }

    private Prevention prevention(boolean primarySource, boolean historical) {
        Prevention prevention = new Prevention();
        prevention.setImmunizationDate(Date.from(Instant.now().minus(historical ? 30 : 1, ChronoUnit.DAYS)));
        prevention.setPrimarySource(primarySource);
        prevention.setName("Synthetic vaccine");
        return prevention;
    }

    private void assertOrigin(Immunization<Prevention> resource, ImmunizationOrigin origin, String display) throws Exception {
        JsonNode coding = new ObjectMapper().readTree(resource.getFhirJSON()).path("reportOrigin").path("coding");
        assertThat(coding.size()).isEqualTo(1);
        assertThat(coding.get(0).path("system").asText()).isEqualTo(origin.getSystem());
        assertThat(coding.get(0).path("code").asText()).isEqualTo(origin.toCode());
        assertThat(coding.get(0).path("display").asText()).isEqualTo(display);
    }
}
