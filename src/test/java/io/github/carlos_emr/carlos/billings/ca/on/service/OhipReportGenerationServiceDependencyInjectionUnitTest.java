/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billing.CA.dao.BillActivityDao;
import io.github.carlos_emr.carlos.billing.CA.dao.BillingDetailDao;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;

/**
 * Behavioral tests for {@link OhipReportGenerationService} — the orchestrator
 * for OHIP claim batch generation. The earlier shape was a single
 * {@code assertNotNull(service)} smoke that would pass through any refactor;
 * this replaces it with the early-return guards, the SIMULATION-rejection
 * contract on {@code generateReport}, the hybrid-billing toggle, and the
 * input-validation messages emitted by {@code generateSimulation}.
 *
 * <p>The full happy path is covered indirectly through the integration
 * surface; here we pin the guards that have to hold or money-moving
 * code runs against malformed inputs.</p>
 */
@DisplayName("OhipReportGenerationService")
@Tag("unit")
@Tag("billing")
class OhipReportGenerationServiceDependencyInjectionUnitTest {

    private BillActivityDao billActivityDao;
    private ProviderDao providerDao;
    private BillingDao billingDao;
    private BillingDetailDao billingDetailDao;
    private OhipReportGenerationService service;

    @BeforeEach
    void setUp() {
        billActivityDao = mock(BillActivityDao.class);
        providerDao = mock(ProviderDao.class);
        billingDao = mock(BillingDao.class);
        billingDetailDao = mock(BillingDetailDao.class);
        service = new OhipReportGenerationService(
                billActivityDao, providerDao, billingDao, billingDetailDao);
    }

    @Test
    void shouldRejectSimulationMode_onGenerateReport() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatThrownBy(() -> service.generateReport(request, OhipReportGenerationService.Mode.SIMULATION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("generateSimulation");

        verifyNoInteractions(billActivityDao, providerDao, billingDao, billingDetailDao);
    }

    @Test
    void shouldNoOp_whenMonthCodeMissing_onGenerateReport() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("providers", "999998");
        // monthCode absent

        service.generateReport(request, OhipReportGenerationService.Mode.GROUP_REPORT);

        verifyNoInteractions(billActivityDao, providerDao, billingDao, billingDetailDao);
    }

    @Test
    void shouldNoOp_whenProvidersParamBlank_onGenerateReport() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("monthCode", "OB");
        request.setParameter("providers", "   ");

        service.generateReport(request, OhipReportGenerationService.Mode.SOLO_REPORT);

        verifyNoInteractions(billActivityDao, providerDao, billingDao, billingDetailDao);
    }

    @Test
    void shouldFlagInvalidProviderBillingCode_onGenerateSimulation() {
        // Provider billing code must be 6 chars (PROVIDER_BILLINGNO_LENGTH).
        // A short value should land an error in the SimulationResult.errorMsg
        // without throwing, so the JSP can render the message to the operator.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("providers", "abc");  // 3 chars, not 6
        when(providerDao.getActiveProviders()).thenReturn(Collections.emptyList());

        OhipReportGenerationService.SimulationResult result = service.generateSimulation(request);

        assertThat(result.errorMsg()).contains("billing code is not correct");
        assertThat(result.htmlPreview()).isEmpty();
    }

    @Test
    void shouldReturnFalse_whenHybridBillingPropertyAbsent() {
        // CarlosProperties is a singleton with a static getInstance(); stub
        // the chain so the property lookup returns the empty default. The
        // service should treat anything other than literal "on" as off.
        try (MockedStatic<CarlosProperties> propsMock = mockStatic(CarlosProperties.class)) {
            CarlosProperties props = mock(CarlosProperties.class);
            when(props.getProperty("hybrid_billing", "")).thenReturn("");
            propsMock.when(CarlosProperties::getInstance).thenReturn(props);

            assertThat(service.isHybridBilling()).isFalse();
        }
    }

    @Test
    void shouldReturnTrue_whenHybridBillingPropertyOn() {
        try (MockedStatic<CarlosProperties> propsMock = mockStatic(CarlosProperties.class)) {
            CarlosProperties props = mock(CarlosProperties.class);
            when(props.getProperty("hybrid_billing", "")).thenReturn("on");
            propsMock.when(CarlosProperties::getInstance).thenReturn(props);

            assertThat(service.isHybridBilling()).isTrue();
        }
    }
}
