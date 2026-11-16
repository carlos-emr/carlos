/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import io.github.carlos_emr.carlos.lab.ca.all.parsers.Factory;
import io.github.carlos_emr.carlos.lab.ca.all.util.Utilities;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;

/**
 * Filing of CML report versions as measurements, against real DAOs and committed transactions.
 *
 * <p>The contract is OSCAR's: the newest version of a report replaces the measurements of the
 * versions before it. The cases pin that contract and the two defects fixed within it: a
 * correction is newer than the report it corrects (#5819), and a version delivered late does not
 * displace a newer one (#5820).
 *
 * @since 2026-11-10
 */
@Tag("integration")
@Tag("lab")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Isolated("Binds legacy HL7 static DAOs to the real test context")
class CmlVersionFilingIntegrationTest extends CarlosTestBase {
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
        accession = "CML-FILING-" + UUID.randomUUID();
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

    /** Stores a report as the uploader does, without filing it. */
    private int store(String body, String status, int finalResults) {
        Hl7TextMessage message = new Hl7TextMessage();
        message.setType("CML");
        message.setBase64EncodedeMessage(Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8)));
        messages.persist(message);
        int report = message.getId();
        reports.add(report);
        var handler = Factory.getHandler("CML", body);
        var info = new Hl7TextInfo();
        info.setLabNumber(report);
        info.setAccessionNumber(accession);
        info.setFirstName(handler.getFirstName());
        info.setLastName(handler.getLastName());
        // The report date exactly as MessageUploader derives and reformats it; chain membership reads it.
        String reportDate = handler.getServiceDate().isEmpty() ? handler.getMsgDate() : handler.getServiceDate();
        String layout = "yyyy-MM-dd HH:mm:ss".substring(0, reportDate.length() - 1);
        info.setObrDate(UtilDateUtilities.DateToString(UtilDateUtilities.StringToDate(reportDate, layout), "yyyy-MM-dd HH:mm:ss"));
        info.setReportStatus(status);
        info.setSendingFacility(handler.getPatientLocation());
        info.setFinalResultCount(finalResults);
        infos.persist(info);
        return report;
    }

    /** Stores, files and routes a report in the order {@code MessageUploader} does. */
    private int ingestBody(String body, String status, int finalResults) {
        return tx.execute(transaction -> {
            int report = store(body, status, finalResults);
            Hl7textResultsData.populateMeasurementsTable(Integer.toString(report), Integer.toString(patient));
            patients.persist(new PatientLabRouting(report, "HL7", patient));
            return report;
        });
    }

    private int ingest(String time, String status, String... rows) {
        return ingestBody(CmlVersionPolicyUnitTest.body(accession, time, status, rows), status, rows.length);
    }

    private List<String> filed() {
        return tx.execute(status -> measurements.findByDemographicNo(patient).stream().map(Measurement::getDataField).sorted().toList());
    }

    private long archived() {
        return tx.execute(status -> em.createQuery("select count(d) from MeasurementsDeleted d where d.demographicNo=?1", Long.class)
                .setParameter(1, patient).getSingleResult());
    }

    @Test
    void shouldKeepOnlyTheCorrection_whenItFollowsTheOriginal() {
        int original = ingest("20260310080000", "F", "7010=5.1");
        int corrected = ingest("20260310120000", "C", "7010=12.7");

        assertThat(filed()).containsExactly("12.7");
        assertThat(archived()).isEqualTo(1);
        // The correction is the current version wherever the chain is read (#5819).
        assertThat(Hl7textResultsData.getMatchingLabs(Integer.toString(original)))
                .isEqualTo(original + "," + corrected);
    }

    @Test
    void shouldChangeNothing_whenAnOlderVersionIsDeliveredLast() {
        ingest("20260310080000", "F", "7010=5.1");
        int corrected = ingest("20260310120000", "C", "7010=12.7");
        int older = ingest("20260310070000", "F", "7010=4.3");

        // #5820: the late delivery used to remove 12.7 and file 4.3.
        assertThat(filed()).containsExactly("12.7");
        assertThat(archived()).isEqualTo(1);
        assertThat(Hl7textResultsData.getMatchingLabs(Integer.toString(older))).endsWith("," + corrected);
    }

    @Test
    void shouldFileNothing_whenTheOriginalArrivesAfterItsCorrection() {
        ingest("20260310120000", "C", "7010=12.7");
        ingest("20260310080000", "F", "7010=5.1");

        assertThat(filed()).containsExactly("12.7");
        assertThat(archived()).isZero();
    }

    @Test
    void shouldReplaceTheWholePreviousVersion_whenACorrectionReportsFewerResults() {
        ingest("20260310080000", "F", "7010=5.1", "7020=9.2");
        ingest("20260310120000", "C", "7010=12.7");

        // The OSCAR rule: the newest version's results are the filed results. The earlier
        // version's other result stays readable in its report.
        assertThat(filed()).containsExactly("12.7");
        assertThat(archived()).isEqualTo(2);
    }

    @Test
    void shouldKeepCompleteMeasurements_whenAPartialFinalIsDeliveredLate() {
        int complete = ingest("20260310", "F", "7010=5.1", "7020=9.2");
        int partial = ingest("20260310", "F", "7010=5.1");
        assertThat(filed()).containsExactly("5.1", "9.2");
        assertThat(archived()).isZero();
        assertThat(Hl7textResultsData.getMatchingLabs(Integer.toString(partial))).isEqualTo(partial + "," + complete);
    }

    @Test
    void shouldFileACorrection_whenOnlyItsCollectionDateIsEarlier() {
        ingest("20260311120000", "F", "7010=5.1");
        String correction = CmlVersionPolicyUnitTest.body(accession, "20260310", "C", "7010=12.7");
        String[] segments = correction.split("\\r");
        for (int i = 0; i < segments.length; i++) {
            if (segments[i].startsWith("OBR|")) {
                String[] fields = segments[i].split("\\|", -1);
                fields[22] = "";
                segments[i] = String.join("|", fields);
            }
        }
        ingestBody(String.join("\r", segments) + "\r", "C", 1);
        assertThat(filed()).containsExactly("12.7");
        assertThat(archived()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"20260310", "20260310120000"})
    void shouldLetTheLaterArrivalReplace_whenTheLabFieldsDoNotSeparateVersions(String time) {
        ingest(time, "F", "7010=5.1");
        ingest(time, "F", "7010=12.7");

        assertThat(filed()).containsExactly("12.7");
        assertThat(archived()).isEqualTo(1);
    }

    @Test
    void shouldReplaceEveryEarlierVersion_whenTheOldDefectLeftTwoFiled() {
        int first = ingest("20260310080000", "F", "7010=5.1");
        // Before the fix a correction could be filed beside the original instead of replacing it.
        tx.executeWithoutResult(status -> {
            int second = store(CmlVersionPolicyUnitTest.body(accession, "20260310120000", "C", "7010=12.7"), "C", 0);
            patients.persist(new PatientLabRouting(second, "HL7", patient));
            Hl7textResultsData.populateMeasurementsTable(Integer.toString(first), Integer.toString(patient));
        });
        assertThat(filed()).as("the stale state").contains("5.1");

        ingest("20260310140000", "C", "7010=13.4");

        assertThat(filed()).containsExactly("13.4");
    }

    @Test
    void shouldFileAVersion_whenItsResultStatusIsNotRecognised() {
        // Nothing about a report makes its results unfilable; OSCAR filed every result it could read.
        int unknown = ingest("20260310080000", "X", "7010=5.1");
        assertThat(filed()).containsExactly("5.1");

        int corrected = ingest("20260310120000", "C", "7010=12.7");
        assertThat(filed()).containsExactly("12.7");
        assertThat(Hl7textResultsData.getMatchingLabs(Integer.toString(unknown))).isEqualTo(unknown + "," + corrected);
    }

    @Test
    void shouldFileAResult_whenItsCommentIsLongerThanAMeasurementHolds() {
        String comment = "RECOMMENDED RANGE ".repeat(17); // 306 characters; measurements.comments holds 255
        ingestBody(CmlVersionPolicyUnitTest.body(accession, "20260310080000", "F", "7010=5.1") + "NTE|1|L|" + comment + "\r", "F", 1);

        List<Measurement> rows = tx.execute(status -> measurements.findByDemographicNo(patient));
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getDataField()).isEqualTo("5.1");
            assertThat(row.getComments()).isEqualTo(comment.substring(0, Measurement.COMMENTS_MAX_LENGTH));
        });
    }

    // dummy.hl7 is a field-name template with incomplete MSH-2 encoding characters; no handler parses it.
    @ParameterizedTest
    @ValueSource(strings = {"AJ19819", "CC63171", "CC63208", "CT71691", "HT05038"})
    void shouldFileRealCmlReports_withDateOnlyTimesAndLongComments(String sample) throws Exception {
        // The repository's real CML layout: no OBR-22 or OBX-14, a date-only OBR-7, and NTE
        // comments longer than a measurement holds. Split the file exactly as the upload handler does.
        Path documents = Files.createTempDirectory("cml-sample");
        String documentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        List<String> bodies;
        try {
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documents.toString());
            Path file = Files.copy(Path.of("src", "test", "resources", "labs", "HL7-CML", sample + ".hl7"),
                    documents.resolve(sample + ".hl7"));
            bodies = Utilities.separateMessages(file.toString());
        } finally {
            if (documentDir == null) CarlosProperties.getInstance().remove("DOCUMENT_DIR");
            else CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir);
            try (var paths = Files.walk(documents)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
        assertThat(bodies).isNotEmpty();
        for (String body : bodies) ingestBody(body, "F", 0);

        List<Measurement> rows = tx.execute(status -> measurements.findByDemographicNo(patient));
        assertThat(rows).as(sample).isNotEmpty().allSatisfy(row ->
                assertThat(Objects.toString(row.getComments(), "")).hasSizeLessThanOrEqualTo(Measurement.COMMENTS_MAX_LENGTH));
    }
}
