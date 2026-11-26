/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

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
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoDao;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Hl7TextInfo;
import io.github.carlos_emr.carlos.commn.model.Hl7TextMessage;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.MeasurementsDeleted;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.lab.ca.all.Hl7textResultsData;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.DefaultGenericHandler;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.Factory;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Unlink of an HL7 report against real DAOs, the real version chain, the real generic handler and
 * the transactional {@link LabPatientUnlinkService} bean (#6214): the readings the report filed are
 * archived with the routing change and their audit rows, a later match files them for the new
 * patient, and a failed audit write leaves the chart and the routing as they were.
 */
@Tag("integration")
@Tag("lab")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Isolated("Binds legacy HL7 static DAOs and the audit DAO to the real test context")
@DisplayName("Unlinking an HL7 report archives the readings it filed")
class LabPatientUnlinkServiceIntegrationTest extends CarlosTestBase {
    /** No handler is configured for this type, so the factory reads it with the generic handler. */
    private static final String TYPE = "SYNTHETIC-GENERIC";

    @Autowired PlatformTransactionManager transactions;
    @Autowired LabPatientUnlinkService unlink;
    @Autowired PatientLabRoutingDao routings;
    @Autowired MeasurementDao measurements;
    @Autowired Hl7TextMessageDao messages;
    @Autowired Hl7TextInfoDao infos;
    @Autowired DemographicDao demographics;
    @Autowired OscarLogDao logs;
    @PersistenceContext(unitName = "entityManagerFactory") EntityManager em;

    private final Map<Field, Object> saved = new LinkedHashMap<>();
    private final List<Integer> reports = new ArrayList<>();
    private final List<Integer> patients = new ArrayList<>();
    private TransactionTemplate tx;
    private LoggedInInfo actor;
    private int wrongPatient;
    private int rightPatient;
    private String accession;
    private String oldFilter;
    private Object previousLogs;

    @BeforeEach
    void prepareFixtures() throws Exception {
        tx = new TransactionTemplate(transactions);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        // A unit test may have initialised the legacy readers first with mocked Spring beans.
        for (Class<?> legacy : List.of(Hl7textResultsData.class, CommonLabResultData.class)) {
            for (Field field : legacy.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) && field.getName().endsWith("Dao")) {
                    Object bean = applicationContext.getBeanProvider(field.getType()).getIfUnique();
                    if (bean == null) continue;
                    field.setAccessible(true);
                    saved.put(field, field.get(null));
                    field.set(null, bean);
                }
            }
        }
        previousLogs = ReflectionTestUtils.getField(LogAction.class, "oscarLogDao");
        ReflectionTestUtils.setField(LogAction.class, "oscarLogDao", logs);
        oldFilter = CarlosProperties.getInstance().getProperty("HL7_LAB_MEASUREMENT_FILTER");
        CarlosProperties.getInstance().setProperty("HL7_LAB_MEASUREMENT_FILTER", ".*");

        actor = new LoggedInInfo();
        Provider provider = new Provider();
        provider.setProviderNo("880011");
        actor.setLoggedInProvider(provider);

        accession = "UNLINK-" + UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            em.createNativeQuery("CREATE TABLE IF NOT EXISTS providerLabRoutingLock (lab_no INT PRIMARY KEY)").executeUpdate();
            wrongPatient = patient("Wrong");
            rightPatient = patient("Right");
        });
    }

    private int patient(String firstName) {
        var demo = new Demographic();
        demo.setFirstName(firstName);
        demo.setLastName(accession);
        demo.setYearOfBirth("1980");
        demo.setMonthOfBirth("01");
        demo.setDateOfBirth("02");
        demo.setSex("F");
        demo.setPatientStatus("AC");
        demo.setProviderNo("999998");
        demographics.save(demo);
        patients.add(demo.getDemographicNo());
        return demo.getDemographicNo();
    }

    @AfterEach
    void removeFixtures() throws Exception {
        try {
            tx.executeWithoutResult(status -> {
                var ids = em.createQuery("select e.measurementId from MeasurementsExt e where e.keyVal='lab_no' and e.val in ?1", Integer.class)
                        .setParameter(1, reports.stream().map(String::valueOf).toList()).getResultList();
                if (!ids.isEmpty()) {
                    em.createQuery("delete from OscarLog l where l.content=?1 and l.contentId in ?2")
                            .setParameter(1, LogConst.CON_MEASUREMENT)
                            .setParameter(2, ids.stream().map(String::valueOf).toList()).executeUpdate();
                    em.createQuery("delete from MeasurementsExt e where e.measurementId in ?1").setParameter(1, ids).executeUpdate();
                    em.createQuery("delete from Measurement m where m.id in ?1").setParameter(1, ids).executeUpdate();
                    em.createQuery("delete from MeasurementsDeleted d where d.originalId in ?1").setParameter(1, ids).executeUpdate();
                }
                if (!reports.isEmpty()) {
                    em.createQuery("delete from OscarLog l where l.content=?1 and l.contentId in ?2")
                            .setParameter(1, LogConst.CON_HL7_LAB)
                            .setParameter(2, reports.stream().map(String::valueOf).toList()).executeUpdate();
                    em.createQuery("delete from PatientLabRouting p where p.labType='HL7' and p.labNo in ?1").setParameter(1, reports).executeUpdate();
                    em.createQuery("delete from Hl7TextInfo h where h.labNumber in ?1").setParameter(1, reports).executeUpdate();
                    em.createQuery("delete from Hl7TextMessage h where h.id in ?1").setParameter(1, reports).executeUpdate();
                    for (int report : reports) {
                        em.createNativeQuery("delete from providerLabRoutingLock where lab_no = ?1").setParameter(1, report).executeUpdate();
                    }
                }
                if (!patients.isEmpty()) {
                    em.createQuery("delete from Demographic d where d.demographicNo in ?1").setParameter(1, patients).executeUpdate();
                }
            });
        } finally {
            if (oldFilter == null) CarlosProperties.getInstance().remove("HL7_LAB_MEASUREMENT_FILTER");
            else CarlosProperties.getInstance().setProperty("HL7_LAB_MEASUREMENT_FILTER", oldFilter);
            ReflectionTestUtils.setField(LogAction.class, "oscarLogDao", previousLogs);
            for (var entry : saved.entrySet()) entry.getKey().set(null, entry.getValue());
        }
    }

    /**
     * A generic result message. The trigger event is not {@code R01}: the generic handler reads the
     * flat segment list HAPI builds for a structure it does not model.
     *
     * @param observations {@code code=value:status} for each OBX
     */
    private String body(String... observations) {
        String time = "20260310080000";
        StringBuilder body = new StringBuilder("MSH|^~\\&|SYNTHETIC|LAB|CARLOS|TEST|" + time + "||ORU^Z01|SYNTHETIC|P|2.3\r\n"
                + "PID||9999999999|9999999999||FAKE-UNLINK^Workflow||19800102|F\r\n"
                + "ORC|RE||" + accession + "\r\n"
                + "OBR|1||" + accession + "|CHEM^Synthetic panel||" + time + "|" + time + "\r\n");
        for (int i = 0; i < observations.length; i++) {
            String[] code = observations[i].split("=", 2);
            String[] value = code[1].split(":", 2);
            body.append("OBX|").append(i + 1).append("|NM|").append(code[0]).append("^Synthetic result ").append(code[0])
                    .append("||").append(value[0]).append("|mmol/L|3-8||||").append(value[1]).append("|||").append(time).append("\r\n");
        }
        return body.toString();
    }

    /** Stores, files and routes a report to a patient in the order {@code MessageUploader} does. */
    private int ingest(int patient, String... observations) {
        return tx.execute(transaction -> {
            String text = body(observations);
            Hl7TextMessage message = new Hl7TextMessage();
            message.setType(TYPE);
            message.setBase64EncodedeMessage(Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)));
            messages.persist(message);
            int report = message.getId();
            reports.add(report);
            MessageHandler handler = Factory.getHandler(TYPE, text);
            assertThat(handler).isInstanceOf(DefaultGenericHandler.class);
            var info = new Hl7TextInfo();
            info.setLabNumber(report);
            info.setAccessionNumber(accession);
            info.setFirstName(handler.getFirstName());
            info.setLastName(handler.getLastName());
            info.setObrDate("2026-03-10 08:00:00");
            info.setReportStatus("F");
            info.setSendingFacility(handler.getPatientLocation());
            info.setFinalResultCount(handler.getOBXFinalResultCount());
            infos.persist(info);
            Hl7textResultsData.populateMeasurementsTable(Integer.toString(report), Integer.toString(patient));
            routings.persist(new PatientLabRouting(report, "HL7", patient));
            return report;
        });
    }

    private List<String> filed(int patient) {
        return tx.execute(status -> measurements.findByDemographicNo(patient).stream().map(Measurement::getDataField).sorted().toList());
    }

    private List<Integer> filedIds(int patient) {
        return tx.execute(status -> measurements.findByDemographicNo(patient).stream().map(Measurement::getId).sorted().toList());
    }

    private List<MeasurementsDeleted> archived(int patient) {
        return tx.execute(status -> em.createQuery("select d from MeasurementsDeleted d where d.demographicNo=?1", MeasurementsDeleted.class)
                .setParameter(1, patient).getResultList());
    }

    private List<Integer> routedTo(int report) {
        return tx.execute(status -> routings.findByLabNoAndLabType(report, "HL7").stream().map(PatientLabRouting::getDemographicNo).toList());
    }

    private List<OscarLog> audit(String action) {
        return tx.execute(status -> em.createQuery("select l from OscarLog l where l.action=?1 and l.content in ?2", OscarLog.class)
                .setParameter(1, action).setParameter(2, List.of(LogConst.CON_HL7_LAB, LogConst.CON_MEASUREMENT)).getResultList()
                .stream().filter(row -> row.getProviderNo() != null && row.getProviderNo().equals("880011")).toList());
    }

    @Test
    @DisplayName("should archive the report's readings and clear its routing when it is unlinked")
    void shouldArchiveReadingsAndClearRouting_whenReportIsUnlinked() {
        int report = ingest(wrongPatient, "7010=5.1:F", "7020=6.2:F");
        List<Integer> readings = filedIds(wrongPatient);
        assertThat(filed(wrongPatient)).containsExactly("5.1", "6.2");

        LabPatientUnlinkService.Result result = unlink.unlinkReport(actor, report, "incorrect match", "192.0.2.10");

        assertThat(result).isEqualTo(new LabPatientUnlinkService.Result(true, wrongPatient, 2));
        assertThat(filed(wrongPatient)).isEmpty();
        assertThat(archived(wrongPatient)).extracting(MeasurementsDeleted::getOriginalId)
                .containsExactlyInAnyOrderElementsOf(readings);
        assertThat(archived(wrongPatient)).extracting(MeasurementsDeleted::getDataField).containsExactlyInAnyOrder("5.1", "6.2");
        assertThat(routedTo(report)).containsExactly(PatientLabRoutingDao.UNMATCHED);
        assertThat(audit(LogConst.UNLINK)).singleElement().satisfies(row -> {
            assertThat(row.getContentId()).isEqualTo(Integer.toString(report));
            assertThat(row.getDemographicId()).isEqualTo(wrongPatient);
            assertThat(row.getData()).isEqualTo("incorrect match");
        });
        assertThat(audit(LogConst.ARCHIVE)).hasSize(2).allSatisfy(row -> {
            assertThat(row.getContent()).isEqualTo(LogConst.CON_MEASUREMENT);
            assertThat(row.getDemographicId()).isEqualTo(wrongPatient);
            assertThat(row.getData()).isEqualTo("lab " + report + " unlinked");
            assertThat(row.getIp()).isEqualTo("192.0.2.10");
        }).extracting(OscarLog::getContentId)
                .containsExactlyInAnyOrderElementsOf(readings.stream().map(String::valueOf).toList());
    }

    @Test
    @DisplayName("should file the readings for the right patient when the unlinked report is matched again")
    void shouldFileReadingsForRightPatient_whenUnlinkedReportIsMatchedAgain() {
        int report = ingest(wrongPatient, "7010=5.1:F", "7020=6.2:F");
        unlink.unlinkReport(actor, report, "incorrect match", "192.0.2.10");

        Boolean matched = tx.execute(status -> CommonLabResultData.updatePatientLabRouting(
                Integer.toString(report), Integer.toString(rightPatient), "HL7"));

        assertThat(matched).isTrue();
        assertThat(filed(rightPatient)).containsExactly("5.1", "6.2");
        assertThat(filed(wrongPatient)).isEmpty();
        assertThat(archived(wrongPatient)).hasSize(2);
        assertThat(routedTo(report)).containsExactly(rightPatient);
    }

    @Test
    @DisplayName("should leave the readings and the routing unchanged when an archive audit row cannot be written")
    void shouldLeaveChartUnchanged_whenArchiveAuditCannotBeWritten() {
        int report = ingest(wrongPatient, "7010=5.1:F", "7020=6.2:F");
        OscarLogDao failing = mock(OscarLogDao.class);
        doAnswer(call -> {
            OscarLog event = call.getArgument(0);
            if (LogConst.ARCHIVE.equals(event.getAction())) throw new PersistenceException("synthetic audit failure");
            logs.persist(event);
            return null;
        }).when(failing).persist(any());
        ReflectionTestUtils.setField(LogAction.class, "oscarLogDao", failing);

        assertThatThrownBy(() -> unlink.unlinkReport(actor, report, "incorrect match", "192.0.2.10"))
                .isInstanceOf(PersistenceException.class);

        ReflectionTestUtils.setField(LogAction.class, "oscarLogDao", logs);
        assertThat(filed(wrongPatient)).containsExactly("5.1", "6.2");
        assertThat(archived(wrongPatient)).isEmpty();
        assertThat(routedTo(report)).containsExactly(wrongPatient);
        assertThat(audit(LogConst.UNLINK)).isEmpty();
    }

    @Test
    @DisplayName("should keep the current version's readings when an older version of the report is unlinked")
    void shouldKeepCurrentVersionReadings_whenOlderVersionIsUnlinked() {
        int original = ingest(wrongPatient, "7010=5.1:F", "7020=9.5:F");
        int correction = ingest(wrongPatient, "7010=5.1:F", "7020=12.7:C");
        assertThat(filed(wrongPatient)).containsExactly("12.7", "5.1");

        LabPatientUnlinkService.Result result = unlink.unlinkReport(actor, original, "older version", "192.0.2.10");

        // Unlink stays a single-report operation: the original's values were already superseded.
        assertThat(result).isEqualTo(new LabPatientUnlinkService.Result(true, wrongPatient, 0));
        assertThat(filed(wrongPatient)).containsExactly("12.7", "5.1");
        assertThat(routedTo(original)).containsExactly(PatientLabRoutingDao.UNMATCHED);
        assertThat(routedTo(correction)).containsExactly(wrongPatient);
    }
}
