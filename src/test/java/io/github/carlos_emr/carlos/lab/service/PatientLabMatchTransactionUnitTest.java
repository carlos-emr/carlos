/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.service;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.hospitalReportManager.service.HrmProviderRoutingService;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData;
import io.github.carlos_emr.carlos.lab.ca.all.upload.ProviderLabRouting;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Uses the service's real Spring transaction advice and H2 commits, with injected write failures. */
@Tag("unit")
class PatientLabMatchTransactionUnitTest extends CarlosUnitTestBase {
    private JdbcTemplate jdbc;
    private MrpRoutingService service;
    private MockedStatic<CommonLabResultData> matching;
    private MockedConstruction<CommonLabResultData> versions;
    private MockedConstruction<ProviderLabRouting> routers;
    private ProviderLabRoutingDao routes;
    private boolean failPatient;
    private boolean failSecondRoute;

    @BeforeEach
    void setUp() {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:patient-mrp-" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("create table changes(kind varchar(20), report int)");
        createAndRegisterMock(PatientLabRoutingDao.class);
        var messages = createAndRegisterMock(io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDao.class);
        when(messages.find(anyInt())).thenReturn(new io.github.carlos_emr.carlos.commn.model.Hl7TextMessage());
        createAndRegisterMock(QueueDocumentLinkDao.class);
        createAndRegisterMock(SecurityInfoManager.class);
        routes = createAndRegisterMock(ProviderLabRoutingDao.class);
        var demographics = mock(DemographicDao.class);
        var patient = new Demographic();
        patient.setProviderNo("101");
        when(demographics.getDemographicById(42)).thenReturn(patient);
        var providers = mock(ProviderDao.class);
        var provider = new Provider();
        provider.setStatus("1");
        when(providers.getProvider("101")).thenReturn(provider);
        var rules = mock(ProviderLinkingRulesService.class);
        when(rules.isEnabled()).thenReturn(true);
        var target = new MrpRoutingService(rules, demographics, providers, mock(HrmProviderRoutingService.class));
        var factory = new ProxyFactory(target);
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(new DataSourceTransactionManager(ds));
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        factory.addAdvice(interceptor);
        service = (MrpRoutingService) factory.getProxy();
        versions = mockConstruction(CommonLabResultData.class, (mock, context) ->
                when(mock.getMatchingLabsForMutation("555", "HL7")).thenReturn("556,555,556"));
        matching = mockStatic(CommonLabResultData.class);
        matching.when(() -> CommonLabResultData.updatePatientLabRouting(java.util.List.of(556, 555), "42", "HL7")).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            var order = inOrder(routes);
            order.verify(routes).lockRoutingReport(555);
            order.verify(routes).lockRoutingReport(556);
            jdbc.update("insert into changes values('patient',555)");
            return !failPatient;
        });
        routers = mockConstruction(ProviderLabRouting.class, (mock, context) -> {
            when(mock.reconcileMrpRouting(anyInt(), eq("HL7"), eq(42), eq("101"))).thenAnswer(call -> {
                int report = call.getArgument(0);
                jdbc.update("insert into changes values('provider',?)", report);
                if (failSecondRoute && report == 556) throw new IllegalStateException("injected second-version failure");
                logActionMock.verifyNoInteractions();
                return true;
            });
        });
    }

    @AfterEach
    void tearDown() {
        routers.close();
        matching.close();
        versions.close();
        jdbc.execute("shutdown");
    }

    @Test
    void shouldRollbackPatientWrite_whenMatchReportsFailure() {
        failPatient = true;
        assertThatThrownBy(() -> service.matchPatientLab("555", "HL7", 42, "actor"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select count(*) from changes", Integer.class)).isZero();
        assertThat(routers.constructed()).isEmpty();
        logActionMock.verifyNoInteractions();
    }

    @Test
    void shouldRollbackPatientAndEveryVersion_whenLaterRoutingFails() {
        failSecondRoute = true;
        assertThatThrownBy(() -> service.matchPatientLab("555", "HL7", 42, "actor"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select count(*) from changes", Integer.class)).isZero();
        logActionMock.verifyNoInteractions();
    }

    @Test
    void shouldCommitPatientAndEveryVersion_whenRoutingSucceeds() {
        service.matchPatientLab("555", "HL7", 42, "actor");
        assertThat(jdbc.queryForObject("select count(*) from changes", Integer.class)).isEqualTo(3);
        logActionMock.verify(() -> io.github.carlos_emr.carlos.log.LogAction.addLog("actor", "route to MRP",
                "providerLinkingRules", "HL7:555", null, "42", "mrp=101"));
        logActionMock.verify(() -> io.github.carlos_emr.carlos.log.LogAction.addLog("actor", "route to MRP",
                "providerLinkingRules", "HL7:556", null, "42", "mrp=101"));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"DOC", "HRM", "hl7", "unknown"})
    void shouldRejectUnsupportedSource_beforePatientWrite(String source) {
        assertThatThrownBy(() -> service.matchPatientLab("555", source, 42, "actor"))
                .isInstanceOf(IllegalArgumentException.class);
        matching.verifyNoInteractions();
        verifyNoInteractions(routes);
    }

    @Test
    void shouldRejectUnknownReport_beforePatientWrite() {
        when(io.github.carlos_emr.carlos.utility.SpringUtils.getBean(
                io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDao.class).find(555)).thenReturn(null);
        assertThatThrownBy(() -> service.matchPatientLab("555", "HL7", 42, "actor"))
                .isInstanceOf(IllegalArgumentException.class);
        matching.verifyNoInteractions();
        verifyNoInteractions(routes);
    }

    @Test
    void shouldFailWithoutChanges_whenSourceVersionLookupFails() {
        versions.close();
        versions = mockConstruction(CommonLabResultData.class, (mock, context) ->
                when(mock.getMatchingLabsForMutation("555", "HL7"))
                        .thenThrow(new IllegalStateException("source lookup failed")));
        assertThatThrownBy(() -> service.matchPatientLab("555", "HL7", 42, "actor"))
                .isInstanceOf(IllegalStateException.class);
        matching.verifyNoInteractions();
        verifyNoInteractions(routes);
    }

}
