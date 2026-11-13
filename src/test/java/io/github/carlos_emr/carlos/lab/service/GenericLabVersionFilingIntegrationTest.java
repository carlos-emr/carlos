/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.lab.service;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
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
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Hl7TextInfo;
import io.github.carlos_emr.carlos.commn.model.Hl7TextMessage;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.lab.ca.all.Hl7textResultsData;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.DefaultGenericHandler;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.Factory;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;

/**
 * Filing of the versions of a generic (non-CML) HL7 report as measurements, against real DAOs, the
 * real chain query and the real generic handler.
 *
 * <p>A source without revision evidence has its versions ordered for display by the count of final
 * results. A correction counts fewer of them than the report it corrects (its corrected result has
 * status C), so it sorts first; the guard added for CML then took it for a late delivery of history
 * and never filed its values. The newest delivery is now the current version for these sources, and
 * it replaces every other version's measurements.</p>
 */
@Tag("integration")
@Tag("lab")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Isolated("Binds legacy HL7 static DAOs to the real test context")
@DisplayName("Generic HL7 report versions filed as measurements")
class GenericLabVersionFilingIntegrationTest extends CarlosTestBase {
    /** No handler is configured for this type, so the factory reads it with the generic handler. */
    private static final String TYPE = "SYNTHETIC-GENERIC";

    @Autowired PlatformTransactionManager transactions;
    @Autowired PatientLabRoutingDao patients;
    @Autowired MeasurementDao measurements;
    @Autowired Hl7TextMessageDao messages;
    @Autowired Hl7TextInfoDao infos;
    @Autowired DemographicDao demographics;
    @PersistenceContext(unitName = "entityManagerFactory") EntityManager em;

    private final Map<Field, Object> saved = new LinkedHashMap<>();
    private final List<Integer> reports = new ArrayList<>();
    private TransactionTemplate tx;
    private int patient;
    private String accession;
    private String oldFilter;

    @BeforeEach
    void prepareFixtures() throws Exception {
        tx = new TransactionTemplate(transactions);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        // A unit test may have initialised the legacy reader first with mocked Spring beans.
        for (Field field : Hl7textResultsData.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getName().endsWith("Dao")) {
                field.setAccessible(true);
                saved.put(field, field.get(null));
                field.set(null, applicationContext.getBean(field.getType()));
            }
        }
        oldFilter = CarlosProperties.getInstance().getProperty("HL7_LAB_MEASUREMENT_FILTER");
        CarlosProperties.getInstance().setProperty("HL7_LAB_MEASUREMENT_FILTER", ".*");
        accession = "GEN-FILING-" + UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            var demo = new Demographic();
            demo.setFirstName("Synthetic");
            demo.setLastName(accession);
            demo.setYearOfBirth("1980");
            demo.setMonthOfBirth("01");
            demo.setDateOfBirth("02");
            demo.setSex("F");
            demo.setPatientStatus("AC");
            demo.setProviderNo("999998");
            demographics.save(demo);
            patient = demo.getDemographicNo();
        });
    }

    @AfterEach
    void removeFixtures() throws Exception {
        try {
            tx.executeWithoutResult(status -> {
                var ids = em.createQuery("select e.measurementId from MeasurementsExt e where e.keyVal='lab_no' and e.val in ?1", Integer.class)
                        .setParameter(1, reports.stream().map(String::valueOf).toList()).getResultList();
                if (!ids.isEmpty()) {
                    em.createQuery("delete from MeasurementsExt e where e.measurementId in ?1").setParameter(1, ids).executeUpdate();
                    em.createQuery("delete from Measurement m where m.id in ?1").setParameter(1, ids).executeUpdate();
                    em.createQuery("delete from MeasurementsDeleted d where d.originalId in ?1").setParameter(1, ids).executeUpdate();
                }
                if (!reports.isEmpty()) {
                    em.createQuery("delete from PatientLabRouting p where p.labType='HL7' and p.labNo in ?1").setParameter(1, reports).executeUpdate();
                    em.createQuery("delete from Hl7TextInfo h where h.labNumber in ?1").setParameter(1, reports).executeUpdate();
                    em.createQuery("delete from Hl7TextMessage h where h.id in ?1").setParameter(1, reports).executeUpdate();
                }
                em.createQuery("delete from Demographic d where d.demographicNo=?1").setParameter(1, patient).executeUpdate();
            });
        } finally {
            if (oldFilter == null) CarlosProperties.getInstance().remove("HL7_LAB_MEASUREMENT_FILTER");
            else CarlosProperties.getInstance().setProperty("HL7_LAB_MEASUREMENT_FILTER", oldFilter);
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
                + "PID||9999999999|9999999999||FAKE-GENERIC^Workflow||19800102|F\r\n"
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

    /** Stores a report as the uploader does, with the handler's own count of final results, without filing it. */
    private int store(String body) {
        Hl7TextMessage message = new Hl7TextMessage();
        message.setType(TYPE);
        message.setBase64EncodedeMessage(Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8)));
        messages.persist(message);
        int report = message.getId();
        reports.add(report);
        MessageHandler handler = Factory.getHandler(TYPE, body);
        assertThat(handler).isInstanceOf(DefaultGenericHandler.class);
        var info = new Hl7TextInfo();
        info.setLabNumber(report);
        info.setAccessionNumber(accession);
        info.setFirstName(handler.getFirstName());
        info.setLastName(handler.getLastName());
        info.setObrDate("2026-03-10 08:00:00");
        info.setReportStatus("F");
        info.setSendingFacility(handler.getPatientLocation());
        // What orders the chain: the generic handler counts only results with status F.
        info.setFinalResultCount(handler.getOBXFinalResultCount());
        infos.persist(info);
        return report;
    }

    /** Stores, files and routes a report in the order {@code MessageUploader} does. */
    private int ingest(String... observations) {
        return tx.execute(transaction -> {
            int report = store(body(observations));
            Hl7textResultsData.populateMeasurementsTable(Integer.toString(report), Integer.toString(patient));
            patients.persist(new PatientLabRouting(report, "HL7", patient));
            return report;
        });
    }

    private List<String> filed() {
        return tx.execute(status -> measurements.findByDemographicNo(patient).stream().map(Measurement::getDataField).sorted().toList());
    }

    private long archived() {
        return tx.execute(status -> em.createQuery("select count(d) from MeasurementsDeleted d where d.demographicNo=?1", Long.class)
                .setParameter(1, patient).getSingleResult());
    }

    private int finalResults(int report) {
        return tx.execute(status -> infos.findLabId(report).getFinalResultCount());
    }

    @Test
    @DisplayName("should file a correction with fewer final results and archive the original's values")
    void shouldFileTheCorrection_whenItCountsFewerFinalResultsThanTheOriginal() {
        int original = ingest("7010=5.1:F", "7020=6.2:F", "7030=7.3:F", "7040=8.4:F", "7050=9.5:F");
        assertThat(filed()).containsExactly("5.1", "6.2", "7.3", "8.4", "9.5");

        int correction = ingest("7010=5.1:F", "7020=6.2:F", "7030=7.3:F", "7040=8.4:F", "7050=12.7:C");

        // The correction sorts before the original in the display order, which this fix leaves alone ...
        assertThat(finalResults(original)).isEqualTo(5);
        assertThat(finalResults(correction)).isEqualTo(4);
        assertThat(Hl7textResultsData.getMatchingLabs(Integer.toString(correction))).isEqualTo(correction + "," + original);
        // ... and it is still the newest delivery: flowsheets get the corrected value, not 9.5.
        assertThat(filed()).containsExactly("12.7", "5.1", "6.2", "7.3", "8.4");
        assertThat(archived()).isEqualTo(5);
    }

    @Test
    @DisplayName("should replace a partial report with the final that follows it, as before")
    void shouldReplaceThePartial_whenTheFinalFollowsIt() {
        int partial = ingest("7010=5.1:F", "7020=6.2:P");
        assertThat(filed()).containsExactly("5.1", "6.2");

        int complete = ingest("7010=5.1:F", "7020=6.4:F", "7030=7.3:F");

        assertThat(Hl7textResultsData.getMatchingLabs(Integer.toString(complete))).isEqualTo(partial + "," + complete);
        assertThat(filed()).containsExactly("5.1", "6.4", "7.3");
        assertThat(archived()).isEqualTo(2);
    }

    @Test
    @DisplayName("should leave one version's values after a partial, a final and a correction")
    void shouldKeepOnlyTheNewestDelivery_whenACorrectionFollowsAPartialAndAFinal() {
        int partial = ingest("7010=5.1:F");
        int complete = ingest("7010=5.1:F", "7020=6.2:F", "7030=7.3:F");
        int correction = ingest("7010=5.1:F", "7020=6.2:F", "7030=12.7:C");

        // Display order: partial (1 final), correction (2), final (3). The correction's neighbour in that
        // order is the partial; the values on the chart were the final's, and those are what it replaces.
        assertThat(Hl7textResultsData.getMatchingLabs(Integer.toString(correction)))
                .isEqualTo(partial + "," + correction + "," + complete);
        assertThat(filed()).containsExactly("12.7", "5.1", "6.2");
        assertThat(archived()).isEqualTo(4);
    }

    @Test
    @DisplayName("should file the correction when a patient is matched after both versions were uploaded")
    void shouldFileTheCorrection_whenThePatientIsMatchedAfterUpload() {
        // Neither version matched a patient at upload, so nothing is filed yet.
        int[] versions = tx.execute(status -> new int[]{
                store(body("7010=5.1:F", "7020=6.2:F", "7030=9.5:F")),
                store(body("7010=5.1:F", "7020=6.2:F", "7030=12.7:C"))});
        int original = versions[0];
        int correction = versions[1];
        assertThat(filed()).isEmpty();
        String[] displayOrder = Hl7textResultsData.getMatchingLabs(Integer.toString(original)).split(",");
        assertThat(displayOrder).containsExactly(Integer.toString(correction), Integer.toString(original));

        // The patient match names the last version of the display order, the original.
        tx.executeWithoutResult(status -> CommonLabResultData.populateMeasurementsTable(
                displayOrder[displayOrder.length - 1], Integer.toString(patient), "HL7"));

        assertThat(filed()).containsExactly("12.7", "5.1", "6.2");
        assertThat(archived()).isZero();
    }

    @Test
    @DisplayName("should not touch the chart when an older version of a generic report is filed again")
    void shouldLeaveTheNewestDeliveryFiled_whenAnOlderVersionIsFiledAgain() {
        int original = ingest("7010=5.1:F", "7020=9.5:F");
        ingest("7010=5.1:F", "7020=12.7:C");
        assertThat(filed()).containsExactly("12.7", "5.1");

        // The original is not the newest delivery: filing it again neither removes nor adds anything.
        tx.executeWithoutResult(status ->
                Hl7textResultsData.populateMeasurementsTable(Integer.toString(original), Integer.toString(patient)));

        assertThat(filed()).containsExactly("12.7", "5.1");
        assertThat(archived()).isEqualTo(2);
    }
}
