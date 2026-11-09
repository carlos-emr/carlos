/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.lab.ca.all.Hl7textResultsData;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.Factory;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/** Real persisted clinical rows and transactions; also run by the physical MariaDB DAO tier.
 * @since 2026-11-09
 */
@Tag("integration")
@Tag("lab")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Isolated("Temporarily binds the legacy HL7 result reader to real test DAOs")
class Hl7PatientUnlinkIntegrationTest extends CarlosTestBase {
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private PatientLabRoutingDao patients;
    @Autowired private ProviderLabRoutingDao providers;
    @Autowired private MeasurementDao measurements;
    @Autowired private MeasurementsDeletedDao deleted;
    @Autowired private MeasurementsExtDao extensions;
    @Autowired private Hl7TextMessageDao messages;
    @Autowired private Hl7TextInfoDao infos;
    @Autowired private OscarLogDao audit;
    @Autowired private DemographicDao demographics;
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager em;
    private final Map<Field, Object> originalDaos = new LinkedHashMap<>();
    private final List<Integer> reports = new ArrayList<>();
    private TransactionTemplate tx;
    private Hl7PatientUnlinkService service;
    private LoggedInInfo actor;
    private int measurement;
    private int patient;
    private String accession;
    private boolean failAudit;
    private final java.util.Set<Integer> existingLocks = new java.util.HashSet<>();

    @BeforeEach void setUp() throws Exception {
        tx = new TransactionTemplate(transactions);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        for (Field field : Hl7textResultsData.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getName().endsWith("Dao")) {
                field.setAccessible(true); originalDaos.put(field, field.get(null));
                field.set(null, applicationContext.getBean(field.getType()));
            }
        }
        accession = "UNLINK-" + UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            em.createNativeQuery("CREATE TABLE IF NOT EXISTS providerLabRoutingLock (lab_no INT PRIMARY KEY)").executeUpdate();
            Demographic owned = new Demographic(); owned.setFirstName("Synthetic");
            owned.setLastName(accession); owned.setYearOfBirth("1980"); owned.setMonthOfBirth("01");
            owned.setDateOfBirth("02"); owned.setSex("F"); owned.setPatientStatus("AC"); owned.setProviderNo("999998");
            demographics.save(owned); patient = owned.getDemographicNo();
            for (int version = 0; version < 2; version++) addReport(version);
            List<Integer> locks = new ArrayList<>(reports); locks.add(Hl7ChainLocks.key("CML", accession));
            for (int lock : locks) {
                if (!em.createNativeQuery("select lab_no from providerLabRoutingLock where lab_no=?1")
                        .setParameter(1, lock).getResultList().isEmpty()) existingLocks.add(lock);
            }
            Measurement value = new Measurement(); value.setDemographicId(patient); value.setProviderNo("0");
            value.setType("A1C"); value.setDataField("12.7"); value.setMeasuringInstruction("Lab");
            value.setDateObserved(new java.util.Date(1_773_144_000_000L)); value.setComments("synthetic source annotation");
            measurements.persist(value); measurement = value.getId();
            MeasurementsExt source = new MeasurementsExt(); source.setMeasurementId(measurement);
            source.setKeyVal("lab_no"); source.setVal(reports.getLast().toString()); extensions.persist(source);
        });
        actor = mock(LoggedInInfo.class); when(actor.getLoggedInProviderNo()).thenReturn("999998");
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        when(security.isAllowedAccessToPatientRecord(actor, patient)).thenReturn(true);
        when(security.hasPrivilege(actor, "_lab", "u", Integer.toString(patient))).thenReturn(true);
        OscarLogDao faultAudit = mock(OscarLogDao.class);
        doAnswer(call -> {
            audit.persist(call.getArgument(0));
            if (failAudit) throw new IllegalStateException("injected audit failure after real INSERT");
            return null;
        }).when(faultAudit).persist(any());
        var target = new Hl7PatientUnlinkService(patients, providers, measurements, deleted, extensions,
                messages, security, faultAudit);
        var proxy = new ProxyFactory(target);
        var advice = new TransactionInterceptor(); advice.setTransactionManager(transactions);
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource()); proxy.addAdvice(advice);
        service = (Hl7PatientUnlinkService) proxy.getProxy();
    }

    private void addReport(int version) {
        String time = version == 0 ? "20260310080000" : "20260310120000";
        String status = version == 0 ? "F" : "C";
        String body = "MSH|^~\\&|Reports|CML|||" + time + "||ORU^R01|" + accession + version + "|P|2.3\r"
                + "PID|1||||FAKE-UNLINK^Synthetic||19800102|F\r"
                + "ORC|RE|" + accession + "|||" + status + "|||||||999998^DR. PROBE|||" + time + "\r"
                + "OBR|1|" + accession + "||ML70^SYNTHETIC PANEL||" + time + "|" + time
                + "|||||||||999998^DR. PROBE||||||" + time + "|||" + status + "\r"
                + "OBX|1|NM|7010^SYNTHETIC RESULT|^^CHEMISTRY|" + (version == 0 ? "5.1" : "12.7")
                + "|mmol/L|3-8|N|||" + status + "|||" + time + "\r";
        Hl7TextMessage message = new Hl7TextMessage(); message.setType("CML");
        message.setBase64EncodedeMessage(Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8)));
        messages.persist(message); reports.add(message.getId());
        var handler = Factory.getHandler("CML", body);
        Hl7TextInfo info = new Hl7TextInfo(); info.setLabNumber(message.getId());
        info.setAccessionNumber(accession); info.setFirstName(handler.getFirstName()); info.setLastName(handler.getLastName());
        info.setObrDate(handler.getMsgDate()); info.setReportStatus(status); info.setSendingFacility(handler.getPatientLocation());
        info.setFinalResultCount(version == 0 ? 1 : 0); infos.persist(info);
        patients.persist(new PatientLabRouting(message.getId(), "HL7", patient));
        providers.persist(new ProviderLabRoutingModel("999998", message.getId(), "A", "owned synthetic review",
                new java.util.Date(), "HL7"));
    }

    @AfterEach void cleanup() throws Exception {
        try {
            if (!reports.isEmpty()) tx.executeWithoutResult(status -> {
                em.createQuery("delete from MeasurementsExt e where e.measurementId=?1").setParameter(1, measurement).executeUpdate();
                em.createQuery("delete from MeasurementsDeleted d where d.originalId=?1").setParameter(1, measurement).executeUpdate();
                em.createQuery("delete from Measurement m where m.id=?1").setParameter(1, measurement).executeUpdate();
                em.createQuery("delete from PatientLabRouting p where p.labType='HL7' and p.labNo in ?1").setParameter(1, reports).executeUpdate();
                em.createQuery("delete from ProviderLabRoutingModel p where p.labType='HL7' and p.labNo in ?1").setParameter(1, reports).executeUpdate();
                em.createQuery("delete from Hl7TextInfo h where h.labNumber in ?1").setParameter(1, reports).executeUpdate();
                em.createQuery("delete from Hl7TextMessage m where m.id in ?1").setParameter(1, reports).executeUpdate();
                em.createQuery("delete from OscarLog l where l.contentId in ?1 and l.demographicId=?2")
                        .setParameter(1, reports.stream().map(String::valueOf).toList()).setParameter(2, patient).executeUpdate();
                em.createQuery("delete from Demographic d where d.demographicNo=?1").setParameter(1, patient).executeUpdate();
                List<Integer> locks = new ArrayList<>(reports); locks.add(Hl7ChainLocks.key("CML", accession));
                for (int lock : locks) if (!existingLocks.contains(lock)) {
                    em.createNativeQuery("delete from providerLabRoutingLock where lab_no=?1").setParameter(1, lock).executeUpdate();
                }
            });
        } finally {
            for (var entry : originalDaos.entrySet()) entry.getKey().set(null, entry.getValue());
        }
    }

    @Test void shouldArchiveRealValuesRetainExtensionsAndUnmatchEveryVersion_whenUnlinkCommits() {
        String expected = service.version(reports.getFirst());
        assertThat(service.unlink(reports.getFirst(), expected, "incorrect synthetic patient", actor, "127.0.0.1"))
                .isEqualTo(patient);
        tx.executeWithoutResult(status -> {
            assertThat(measurements.find(measurement)).isNull();
            var archives = em.createQuery("select d from MeasurementsDeleted d where d.originalId=?1", MeasurementsDeleted.class)
                    .setParameter(1, measurement).getResultList();
            assertThat(archives).singleElement().satisfies(row -> assertThat(row.getDataField()).isEqualTo("12.7"));
            assertThat(extensions.getMeasurementsExtByMeasurementId(measurement)).extracting(MeasurementsExt::getKeyVal)
                    .containsExactlyInAnyOrder("lab_no", CmlProjectionService.ARCHIVE);
            for (int report : reports) {
                assertThat(patients.findByLabNoAndLabType(report, "HL7")).singleElement()
                        .satisfies(row -> assertThat(row.getDemographicNo()).isZero());
                assertThat(providers.findAllLabRoutingByIdandType(report, "HL7")).singleElement()
                        .satisfies(row -> assertThat(row.getStatus()).isEqualTo("N"));
                assertThat(messages.find(report)).isNotNull();
            }
            assertThat(em.createQuery("select count(l) from OscarLog l where l.contentId in ?1 and l.demographicId=?2", Long.class)
                    .setParameter(1, reports.stream().map(String::valueOf).toList()).setParameter(2, patient)
                    .getSingleResult()).isEqualTo(4L);
        });
        assertThatThrownBy(() -> service.unlink(reports.getFirst(), expected, "stale retry", actor, "127.0.0.1"))
                .isInstanceOf(Hl7PatientUnlinkService.StaleMatch.class);
    }

    @Test void shouldRollbackRealClinicalRowsAndAudit_whenAuditInsertFailsLate() {
        String expected = service.version(reports.getFirst()); failAudit = true;
        assertThatThrownBy(() -> service.unlink(reports.getFirst(), expected, "incorrect synthetic patient", actor, "127.0.0.1"))
                .isInstanceOf(IllegalStateException.class);
        tx.executeWithoutResult(status -> {
            assertThat(measurements.find(measurement).getDataField()).isEqualTo("12.7");
            assertThat(em.createQuery("select count(d) from MeasurementsDeleted d where d.originalId=?1", Long.class)
                    .setParameter(1, measurement).getSingleResult()).isZero();
            assertThat(extensions.getMeasurementsExtByMeasurementId(measurement)).extracting(MeasurementsExt::getKeyVal)
                    .containsExactly("lab_no");
            for (int report : reports) {
                assertThat(patients.findByLabNoAndLabType(report, "HL7")).singleElement()
                        .satisfies(row -> assertThat(row.getDemographicNo()).isEqualTo(patient));
                assertThat(providers.findAllLabRoutingByIdandType(report, "HL7")).singleElement()
                        .satisfies(row -> assertThat(row.getStatus()).isEqualTo("A"));
            }
            assertThat(em.createQuery("select count(l) from OscarLog l where l.contentId in ?1 and l.demographicId=?2", Long.class)
                    .setParameter(1, reports.stream().map(String::valueOf).toList()).setParameter(2, patient)
                    .getSingleResult()).isZero();
        });
        assertThat(service.version(reports.getFirst())).isEqualTo(expected);
    }
}
