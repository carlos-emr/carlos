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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import io.github.carlos_emr.carlos.commn.model.Provider;

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
        // Round-7: OhipClaimExtractService is now Spring-prototype-scoped and
        // injected via ObjectFactory. The DI test stubs it with a no-op mock
        // factory; the report-generation tests below don't drive the per-claim
        // extract surface (covered by OhipClaimExtractServiceUnitTest).
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectFactory<OhipClaimExtractService> factory =
                mock(org.springframework.beans.factory.ObjectFactory.class);
        when(factory.getObject()).thenReturn(mock(OhipClaimExtractService.class));
        org.springframework.transaction.PlatformTransactionManager txManager =
                mock(org.springframework.transaction.PlatformTransactionManager.class);
        service = new OhipReportGenerationService(
                billActivityDao, providerDao, billingDao, billingDetailDao, factory, txManager);
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

    @Test
    void shouldRequestFreshExtractInstance_perProviderInBatch() {
        // Pin the prototype-per-claim contract on the OhipClaimExtractService
        // factory: a future "optimization" that caches the factory result and
        // reuses the same instance across providers would let mutated state
        // (totalAmount, billings) bleed between claims, mixing one doctor's
        // totals into another's batch — a billing-data-integrity regression
        // that would not throw, only mis-bill.
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectFactory<OhipClaimExtractService> trackingFactory =
                mock(org.springframework.beans.factory.ObjectFactory.class);
        OhipClaimExtractService extract1 = mock(OhipClaimExtractService.class);
        OhipClaimExtractService extract2 = mock(OhipClaimExtractService.class);
        when(extract1.getValue()).thenReturn("file1");
        when(extract1.getHtmlCode()).thenReturn("html1");
        when(extract2.getValue()).thenReturn("file2");
        when(extract2.getHtmlCode()).thenReturn("html2");
        when(trackingFactory.getObject()).thenReturn(extract1, extract2);

        org.springframework.transaction.PlatformTransactionManager txManager =
                mock(org.springframework.transaction.PlatformTransactionManager.class);
        OhipReportGenerationService twoProviderService = new OhipReportGenerationService(
                billActivityDao, providerDao, billingDao, billingDetailDao, trackingFactory, txManager);

        Provider p1 = mock(Provider.class);
        when(p1.getOhipNo()).thenReturn("111111");
        when(p1.getProviderNo()).thenReturn("11");
        when(p1.getComments()).thenReturn("");
        Provider p2 = mock(Provider.class);
        when(p2.getOhipNo()).thenReturn("222222");
        when(p2.getProviderNo()).thenReturn("22");
        when(p2.getComments()).thenReturn("");
        when(providerDao.getActiveProviders()).thenReturn(java.util.List.of(p1, p2));

        try (MockedStatic<CarlosProperties> propsMock = mockStatic(CarlosProperties.class)) {
            CarlosProperties props = mock(CarlosProperties.class);
            when(props.getProperty("hybrid_billing", "")).thenReturn("");
            propsMock.when(CarlosProperties::getInstance).thenReturn(props);

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setParameter("monthCode", "OB");
            request.setParameter("providers", "all");
            request.setParameter("verCode", "V03");
            request.setParameter("billcenter", "G");
            request.setParameter("curUser", "tester");

            twoProviderService.generateReport(request, OhipReportGenerationService.Mode.SOLO_REPORT);
        }

        verify(trackingFactory, times(2)).getObject();
        // Each provider's extract instance receives its own setProviderNo.
        verify(extract1).setProviderNo("111111");
        verify(extract2).setProviderNo("222222");
    }

    @Test
    void shouldReturnFailedProviderEntry_whenOneProviderRollsBack() {
        // Round-7 contract: a per-provider failure must (a) NOT abort the
        // batch, (b) emit a FailedProvider record into the returned list
        // with providerNo/ohipNo/causeClass populated, (c) leave the other
        // providers' commits durable. Without this pin, a regression to
        // void-return or always-empty list silently kills the operator's
        // "N skipped" banner and bills can be silently dropped.
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectFactory<OhipClaimExtractService> trackingFactory =
                mock(org.springframework.beans.factory.ObjectFactory.class);
        OhipClaimExtractService failing = mock(OhipClaimExtractService.class);
        OhipClaimExtractService ok = mock(OhipClaimExtractService.class);
        // Provider 1's extract throws BillingFileWriteException mid-batch.
        org.mockito.Mockito.doThrow(new BillingFileWriteException("disk full simulation"))
                .when(failing).dbQuery();
        when(ok.getValue()).thenReturn("file2");
        when(ok.getHtmlCode()).thenReturn("html2");
        when(trackingFactory.getObject()).thenReturn(failing, ok);

        org.springframework.transaction.PlatformTransactionManager txManager =
                mock(org.springframework.transaction.PlatformTransactionManager.class);
        OhipReportGenerationService twoProviderService = new OhipReportGenerationService(
                billActivityDao, providerDao, billingDao, billingDetailDao, trackingFactory, txManager);

        Provider p1 = mock(Provider.class);
        when(p1.getOhipNo()).thenReturn("111111");
        when(p1.getProviderNo()).thenReturn("11");
        when(p1.getComments()).thenReturn("");
        Provider p2 = mock(Provider.class);
        when(p2.getOhipNo()).thenReturn("222222");
        when(p2.getProviderNo()).thenReturn("22");
        when(p2.getComments()).thenReturn("");
        when(providerDao.getActiveProviders()).thenReturn(java.util.List.of(p1, p2));

        java.util.List<OhipReportGenerationService.FailedProvider> skipped;
        try (MockedStatic<CarlosProperties> propsMock = mockStatic(CarlosProperties.class)) {
            CarlosProperties props = mock(CarlosProperties.class);
            when(props.getProperty("hybrid_billing", "")).thenReturn("");
            propsMock.when(CarlosProperties::getInstance).thenReturn(props);

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setParameter("monthCode", "OB");
            request.setParameter("providers", "all");
            request.setParameter("verCode", "V03");
            request.setParameter("billcenter", "G");
            request.setParameter("curUser", "tester");

            skipped = twoProviderService.generateReport(request,
                    OhipReportGenerationService.Mode.SOLO_REPORT);
        }

        // Exactly one FailedProvider — only p1 rolled back.
        assertThat(skipped).hasSize(1);
        OhipReportGenerationService.FailedProvider fp = skipped.get(0);
        assertThat(fp.providerNo()).isEqualTo("11");
        assertThat(fp.ohipNo()).isEqualTo("111111");
        assertThat(fp.causeClass()).isEqualTo("BillingFileWriteException");
        assertThat(fp.causeMessage()).isEqualTo("disk full simulation");
    }

    @Test
    void shouldDefaultCauseMessage_whenExceptionMessageIsNull() {
        // S4 contract: an NPE (or anything with null getMessage()) must
        // surface a placeholder string, not the literal word "null", so
        // operators know to look at the server log.
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectFactory<OhipClaimExtractService> trackingFactory =
                mock(org.springframework.beans.factory.ObjectFactory.class);
        OhipClaimExtractService failing = mock(OhipClaimExtractService.class);
        org.mockito.Mockito.doThrow(new NullPointerException()).when(failing).dbQuery();
        when(trackingFactory.getObject()).thenReturn(failing);

        org.springframework.transaction.PlatformTransactionManager txManager =
                mock(org.springframework.transaction.PlatformTransactionManager.class);
        OhipReportGenerationService oneProviderService = new OhipReportGenerationService(
                billActivityDao, providerDao, billingDao, billingDetailDao, trackingFactory, txManager);

        Provider p = mock(Provider.class);
        when(p.getOhipNo()).thenReturn("111111");
        when(p.getProviderNo()).thenReturn("11");
        when(p.getComments()).thenReturn("");
        when(providerDao.getActiveProviders()).thenReturn(java.util.List.of(p));

        java.util.List<OhipReportGenerationService.FailedProvider> skipped;
        try (MockedStatic<CarlosProperties> propsMock = mockStatic(CarlosProperties.class)) {
            CarlosProperties props = mock(CarlosProperties.class);
            when(props.getProperty("hybrid_billing", "")).thenReturn("");
            propsMock.when(CarlosProperties::getInstance).thenReturn(props);

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setParameter("monthCode", "OB");
            request.setParameter("providers", "all");
            skipped = oneProviderService.generateReport(request,
                    OhipReportGenerationService.Mode.SOLO_REPORT);
        }

        assertThat(skipped).hasSize(1);
        assertThat(skipped.get(0).causeMessage())
                .as("null exception message must default to placeholder, not literal 'null'")
                .contains("check server log");
    }
}
