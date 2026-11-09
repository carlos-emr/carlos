/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Date;
import java.util.List;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.lab.ca.all.Hl7textResultsData;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.Factory;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler;
import io.github.carlos_emr.carlos.lab.ca.all.upload.ProviderLabRouting;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/** Real Spring transaction advice and committed H2 writes, including injected failures.
 * @since 2026-11-09
 */
@Tag("unit")
@Tag("lab")
class Hl7PatientUnlinkServiceUnitTest extends CarlosUnitTestBase {
    private JdbcTemplate jdbc;
    private Hl7PatientUnlinkService service;
    private PatientLabRoutingDao patients;
    private ProviderLabRoutingDao providers;
    private MeasurementDao measurements;
    private MeasurementsDeletedDao deleted;
    private MeasurementsExtDao extensions;
    private OscarLogDao audit;
    private SecurityInfoManager security;
    private LoggedInInfo actor;
    private PatientLabRouting first, second;
    private MessageHandler handler;
    private MockedStatic<Hl7textResultsData> chains;
    private MockedStatic<Factory> handlers;
    private MockedConstruction<ProviderLabRouting> routers;
    private String failAt;

    @BeforeEach void setUp() throws Exception {
        for (Field field : Hl7textResultsData.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getName().endsWith("Dao")) {
                createAndRegisterMock(field.getType());
            }
        }
        patients = (PatientLabRoutingDao) mockedBeans.get(PatientLabRoutingDao.class);
        measurements = (MeasurementDao) mockedBeans.get(MeasurementDao.class);
        deleted = (MeasurementsDeletedDao) mockedBeans.get(MeasurementsDeletedDao.class);
        extensions = (MeasurementsExtDao) mockedBeans.get(MeasurementsExtDao.class);
        var messages = (Hl7TextMessageDao) mockedBeans.get(Hl7TextMessageDao.class);
        var infoDao = (Hl7TextInfoDao) mockedBeans.get(Hl7TextInfoDao.class);
        providers = createAndRegisterMock(ProviderLabRoutingDao.class);
        security = createAndRegisterMock(SecurityInfoManager.class);
        audit = (OscarLogDao) mockedBeans.get(OscarLogDao.class);
        actor = mock(LoggedInInfo.class);
        when(actor.getLoggedInProviderNo()).thenReturn("999998");
        when(security.isAllowedAccessToPatientRecord(actor, 42)).thenReturn(true);
        when(security.hasPrivilege(actor, "_lab", "u", "42")).thenReturn(true);
        var message = new Hl7TextMessage(); message.setType("CML");
        when(messages.find(anyInt())).thenReturn(message);
        var info = new Hl7TextInfo(); info.setAccessionNumber("FAKE-ACC");
        when(infoDao.findByLabId(anyInt())).thenReturn(List.of(info));
        first = new PatientLabRouting(11, "HL7", 42);
        second = new PatientLabRouting(12, "HL7", 42);
        ReflectionTestUtils.setField(first, "id", 101); ReflectionTestUtils.setField(second, "id", 102);
        when(patients.findByLabNoAndLabType(11, "HL7")).thenReturn(List.of(first));
        when(patients.findByLabNoAndLabType(12, "HL7")).thenReturn(List.of(second));
        when(patients.findForUpdate(11, "HL7")).thenReturn(List.of(first));
        when(patients.findForUpdate(12, "HL7")).thenReturn(List.of(second));
        handler = mock(MessageHandler.class);
        when(handler.getFirstName()).thenReturn("FAKE"); when(handler.getLastName()).thenReturn("PATIENT");
        when(handler.getDOB()).thenReturn("1980-01-02"); when(handler.getPatientLocation()).thenReturn("CML");
        chains = mockStatic(Hl7textResultsData.class);
        chains.when(() -> Hl7textResultsData.getMatchingLabs(anyString())).thenReturn("11,12");
        handlers = mockStatic(Factory.class);
        handlers.when(() -> Factory.getHandler(anyString())).thenReturn(handler);

        var ds = new JdbcDataSource(); ds.setURL("jdbc:h2:mem:unlink-" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds); jdbc.execute("create table writes(kind varchar(30))");
        var value = new Measurement(); ReflectionTestUtils.setField(value, "id", 7);
        value.setDemographicId(42); value.setDataField("5.1"); value.setType("FAKE");
        value.setDateObserved(new Date(1000)); value.setCreateDate(new Date(2000));
        when(measurements.findByValue("lab_no", "12")).thenReturn(List.of(value));
        var source = new MeasurementsExt(); source.setMeasurementId(7); source.setKeyVal("lab_no"); source.setVal("12");
        when(extensions.getMeasurementsExtByMeasurementId(7)).thenReturn(List.of(source));
        doAnswer(call -> { write("archive"); return null; }).when(deleted).persist(any());
        doAnswer(call -> { write("extension"); return null; }).when(extensions).persist(any());
        doAnswer(call -> { write("measurement"); return null; }).when(measurements).batchRemoveAtomically(anyList());
        doAnswer(call -> { write("patient"); return null; }).when(patients).merge(any());
        doAnswer(call -> { write("audit"); return null; }).when(audit).persist(any());
        routers = mockConstruction(ProviderLabRouting.class, (router, context) ->
                when(router.reconcileMrpRouting(anyInt(), eq("HL7"), isNull(), isNull())).thenAnswer(call -> {
                    write("provider"); return false;
                }));
        var target = new Hl7PatientUnlinkService(patients, providers, measurements, deleted, extensions,
                messages, security, audit);
        var proxy = new ProxyFactory(target);
        var advice = new TransactionInterceptor(); advice.setTransactionManager(new DataSourceTransactionManager(ds));
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource()); proxy.addAdvice(advice);
        service = (Hl7PatientUnlinkService) proxy.getProxy();
    }

    private void write(String kind) {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        jdbc.update("insert into writes values(?)", kind);
        if (kind.equals(failAt)) throw new IllegalStateException("injected " + kind + " failure");
    }

    @AfterEach void closeMocks() { routers.close(); handlers.close(); chains.close(); jdbc.execute("shutdown"); }

    @Test void shouldArchiveUnmatchAndAuditEveryVersion_inOneTransaction() {
        String version = service.version(11);
        assertThat(service.unlink(11, version, "incorrect patient", actor, "127.0.0.1")).isEqualTo(42);
        assertThat(first.getDemographicNo()).isEqualTo(PatientLabRoutingDao.UNMATCHED);
        assertThat(second.getDemographicNo()).isEqualTo(PatientLabRoutingDao.UNMATCHED);
        assertThat(jdbc.queryForList("select kind from writes", String.class))
                .containsExactly("archive", "extension", "measurement", "patient", "patient", "provider", "audit", "provider", "audit");
        var order = inOrder(providers);
        order.verify(providers).lockRoutingReport(Hl7ChainLocks.key("CML", "FAKE-ACC"));
        order.verify(providers).lockRoutingReport(11); order.verify(providers).lockRoutingReport(12);
        verify(extensions, never()).remove(anyInt());
    }

    @ParameterizedTest @ValueSource(strings = {"archive", "extension", "measurement", "patient", "provider", "audit"})
    void shouldRollbackEveryWrite_whenAnyStageFails(String stage) {
        failAt = stage; String version = service.version(11);
        assertThatThrownBy(() -> service.unlink(11, version, "incorrect patient", actor, "127.0.0.1"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select count(*) from writes", Integer.class)).isZero();
    }

    @Test void shouldRefuseStaleRetry_afterPatientMatchChanges() {
        String version = service.version(11); second.setDemographicNo(99);
        assertThatThrownBy(() -> service.unlink(11, version, "incorrect patient", actor, "127.0.0.1"))
                .isInstanceOf(Hl7PatientUnlinkService.StaleMatch.class);
        verifyNoInteractions(measurements, deleted, extensions, audit);
        assertThat(routers.constructed()).isEmpty();
    }

    @Test void shouldRefusePatientLock_beforeAnyClinicalWrite() {
        String version = service.version(11);
        when(security.isAllowedAccessToPatientRecord(actor, 42)).thenReturn(false);
        assertThatThrownBy(() -> service.unlink(11, version, "incorrect patient", actor, "127.0.0.1"))
                .isInstanceOf(SecurityException.class);
        assertThat(jdbc.queryForObject("select count(*) from writes", Integer.class)).isZero();
        verifyNoInteractions(measurements, deleted, extensions, audit);
    }

    @Test void shouldRefuseUnlink_withDifferentSourcePatientIdentity() {
        var other = mock(MessageHandler.class); when(other.getFirstName()).thenReturn("OTHER");
        when(other.getLastName()).thenReturn("PATIENT"); when(other.getDOB()).thenReturn("1980-01-02");
        when(other.getPatientLocation()).thenReturn("CML");
        handlers.when(() -> Factory.getHandler("12")).thenReturn(other);
        assertThatThrownBy(() -> service.version(11)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(measurements, deleted, extensions, audit);
    }

    @Test void shouldKeepLaterDeliveryUnmatched_whenClinicianUnlinkedPreviousVersion() {
        first.setDemographicNo(PatientLabRoutingDao.UNMATCHED);
        assertThat(service.hasUnmatchedVersion(12, handler)).isTrue();
        verifyNoInteractions(measurements, deleted, extensions, audit);
    }

    @Test void shouldIgnoreOtherSourceIdentity_whenCheckingExplicitUnmatch() {
        first.setDemographicNo(PatientLabRoutingDao.UNMATCHED);
        var other = mock(MessageHandler.class);
        when(other.getFirstName()).thenReturn("UNRELATED"); when(other.getLastName()).thenReturn("PATIENT");
        when(other.getDOB()).thenReturn("1990-01-02");
        handlers.when(() -> Factory.getHandler("11")).thenReturn(other);
        assertThat(service.hasUnmatchedVersion(12, handler)).isFalse();
        verifyNoInteractions(measurements, deleted, extensions, audit);
    }

    @Test void shouldKeepCoordinationIdsNegative_whenQualifyingSources() {
        assertThat(Hl7ChainLocks.key("CML", "FAKE-ACC")).isNegative();
        assertThat(Hl7ChainLocks.key("CML", "FAKE-ACC")).isNotEqualTo(Hl7ChainLocks.key("OTHER", "FAKE-ACC"));
    }
}
