/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.commn.dao.ValidationsDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.commn.model.Validations;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsLegacyProjectionService;
import io.github.carlos_emr.carlos.managers.MeasurementManagerImpl;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.MeasurementResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.EmbeddedMeasurementTo1;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.ws.rs.BadRequestException;

/**
 * Read-after-write for {@code POST /measurements/{demographicNo}/new} against the database (#6136).
 *
 * <p>The measurement type and validation rule are the BP row as the ON and BC seeds configure it
 * ({@code BP} / {@code sitting position} / rule "Blood Pressure"). The DAOs and the measurement
 * manager are the real ones, so the write is persisted and read back through
 * {@code POST /measurements/{demographicNo}}. Only the caller's identity, the access checks and the
 * CDS projection guard are stubbed; those are covered by the endpoint and OAuth interceptor tests.</p>
 */
@Tag("integration")
@Tag("measurement")
@Tag("create")
@Tag("read")
@DisplayName("MeasurementService write then read against the database")
@Transactional
class MeasurementServiceWriteReadIntegrationTest extends CarlosTestBase {

    private static final String PROVIDER_NO = "999998";

    @Autowired
    private MeasurementDao measurementDao;
    @Autowired
    private MeasurementTypeDao measurementTypeDao;
    @Autowired
    private ValidationsDao validationsDao;
    @Autowired
    private DemographicDao demographicDao;
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private final ObjectMapper mapper = new ObjectMapper();
    private MeasurementService service;
    private int patient;

    @BeforeEach
    void setUp() {
        Validations bloodPressure = new Validations();
        bloodPressure.setName("Blood Pressure");
        bloodPressure.setRegularExp("[0-9]{2,3}/{1}[0-9]{2,3}");
        validationsDao.persist(bloodPressure);

        for (MeasurementType existing : measurementTypeDao.findByType("BP")) {
            measurementTypeDao.remove(existing.getId());
        }
        MeasurementType bp = new MeasurementType();
        bp.setType("BP");
        bp.setTypeDisplayName("BP");
        bp.setTypeDescription("Blood Pressure");
        bp.setMeasuringInstruction("sitting position");
        bp.setValidation(String.valueOf(bloodPressure.getId()));
        measurementTypeDao.persist(bp);

        Demographic demographic = new Demographic();
        demographic.setFirstName("FAKE-Reading");
        demographic.setLastName("FAKE-Measurement6136");
        demographic.setSex("U");
        demographic.setProviderNo(PROVIDER_NO);
        entityManager.persist(demographic);
        entityManager.flush();
        patient = demographic.getDemographicNo();

        SecurityInfoManager security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(LoggedInInfo.class), eq("_measurement"), anyString(), anyString()))
                .thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(any(LoggedInInfo.class), eq(patient))).thenReturn(true);
        MeasurementManagerImpl manager = new MeasurementManagerImpl();
        ReflectionTestUtils.setField(manager, "measurementDao", measurementDao);
        manager.setDemographicDao(demographicDao);
        LoggedInInfo caller = mock(LoggedInInfo.class);
        when(caller.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);

        service = new MeasurementService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return caller;
            }
        };
        service.setSecurityInfoManager(security);
        service.setMeasurementManager(manager);
        service.setDemographicDao(demographicDao);
        service.setMeasurementTypeDao(measurementTypeDao);
        service.setValidationsDao(validationsDao);
        service.setCdsLegacyProjectionService(mock(CdsLegacyProjectionService.class));
    }

    @Test
    @DisplayName("should persist a seeded-rule blood pressure and return it from the measurement read")
    void shouldReadBackMeasurement_whenRecordedThroughWriteEndpoint() throws Exception {
        long observed = 1_770_000_000_000L;
        EmbeddedMeasurementTo1 written = service.addMeasurement((ObjectNode) mapper.readTree(
                "{\"type\":\"BP\",\"dataField\":\"122/80\",\"measuringInstruction\":\"sitting position\","
                        + "\"comments\":\"FAKE reading\",\"dateObserved\":" + observed + "}"), patient);

        assertThat(written.getId()).isNotNull();
        entityManager.flush();
        entityManager.clear();

        MeasurementResponse read = service.getMeasurements(
                (ObjectNode) mapper.readTree("{\"types\":[\"BP\"]}"), patient);
        List<Measurement> readings = read.getMeasurements().values().stream().flatMap(List::stream).toList();
        assertThat(readings).singleElement().satisfies(reading -> {
            assertThat(reading.getId()).isEqualTo(written.getId());
            assertThat(reading.getDemographicId()).isEqualTo(patient);
            assertThat(reading.getType()).isEqualTo("BP");
            assertThat(reading.getMeasuringInstruction()).isEqualTo("sitting position");
            assertThat(reading.getDataField()).isEqualTo("122/80");
            assertThat(reading.getComments()).isEqualTo("FAKE reading");
            assertThat(reading.getProviderNo()).isEqualTo(PROVIDER_NO);
            assertThat(reading.getDateObserved().getTime()).isEqualTo(observed);
        });
    }

    @Test
    @DisplayName("should resolve and store the single configured instruction when measuringInstruction is omitted")
    void shouldStoreConfiguredInstruction_whenInstructionOmittedForSingleInstructionType() throws Exception {
        EmbeddedMeasurementTo1 written = service.addMeasurement((ObjectNode) mapper.readTree(
                "{\"type\":\"BP\",\"dataField\":\"120/78\",\"comments\":\"FAKE omitted instruction\"}"), patient);

        assertThat(written.getId()).isNotNull();
        assertThat(written.getMeasuringInstruction()).isEqualTo("sitting position");
        assertThat(written.getDateObserved()).isNotNull();
        entityManager.flush();
        entityManager.clear();

        List<Measurement> stored = measurementDao.findByType(patient, List.of("BP"));
        assertThat(stored).singleElement().satisfies(reading -> {
            assertThat(reading.getId()).isEqualTo(written.getId());
            assertThat(reading.getMeasuringInstruction()).isEqualTo("sitting position");
            assertThat(reading.getDataField()).isEqualTo("120/78");
            assertThat(reading.getDateObserved()).isNotNull();
        });
    }

    @Test
    @DisplayName("should require measuringInstruction for a multi-instruction type and accept an explicit one")
    void shouldRequireInstructionThenPersist_whenTypeHasMultipleInstructions() throws Exception {
        Validations yesNo = new Validations();
        yesNo.setName("Yes/No/NA");
        yesNo.setRegularExp("YES|yes|Yes|Y|NO|no|No|N|NotApplicable|NA");
        validationsDao.persist(yesNo);

        for (MeasurementType existing : measurementTypeDao.findByType("FEET")) {
            measurementTypeDao.remove(existing.getId());
        }
        for (String instr : List.of("pulses (Yes/No)", "reflexes (Yes/No)")) {
            MeasurementType feet = new MeasurementType();
            feet.setType("FEET");
            feet.setTypeDisplayName("FEET");
            feet.setTypeDescription("Feet Exam");
            feet.setMeasuringInstruction(instr);
            feet.setValidation(String.valueOf(yesNo.getId()));
            measurementTypeDao.persist(feet);
        }
        entityManager.flush();

        ObjectNode omitted = (ObjectNode) mapper.readTree("{\"type\":\"FEET\",\"dataField\":\"Yes\"}");
        BadRequestException refused = catchThrowableOfType(() -> service.addMeasurement(omitted, patient),
                BadRequestException.class);
        assertThat(refused).isNotNull();
        assertThat(refused.getResponse().getEntity())
                .isInstanceOfSatisfying(Map.class, error -> assertThat(error.get("code"))
                        .isEqualTo("INSTRUCTION_REQUIRED"));
        assertThat(measurementDao.findByType(patient, List.of("FEET"))).isEmpty();

        EmbeddedMeasurementTo1 written = service.addMeasurement((ObjectNode) mapper.readTree(
                "{\"type\":\"FEET\",\"dataField\":\"Yes\",\"measuringInstruction\":\"pulses (Yes/No)\"}"), patient);
        entityManager.flush();
        entityManager.clear();

        assertThat(measurementDao.findByType(patient, List.of("FEET"))).singleElement().satisfies(reading -> {
            assertThat(reading.getId()).isEqualTo(written.getId());
            assertThat(reading.getMeasuringInstruction()).isEqualTo("pulses (Yes/No)");
            assertThat(reading.getDataField()).isEqualTo("Yes");
        });
    }

    @Test
    @DisplayName("should store a retried dated reading once")
    void shouldStoreOnce_whenDatedRequestIsRepeated() throws Exception {
        String body = "{\"type\":\"BP\",\"dataField\":\"118/76\",\"measuringInstruction\":\"sitting position\","
                + "\"dateObserved\":1770000000000}";
        EmbeddedMeasurementTo1 first = service.addMeasurement((ObjectNode) mapper.readTree(body), patient);
        entityManager.flush();
        EmbeddedMeasurementTo1 retry = service.addMeasurement((ObjectNode) mapper.readTree(body), patient);

        assertThat(retry.getId()).isEqualTo(first.getId());
        assertThat(measurementDao.findByType(patient, List.of("BP"))).hasSize(1);
    }

    @Test
    @DisplayName("should keep readings distinct when comments differ only by letter case")
    void shouldStoreDistinctReadings_whenCommentsDifferOnlyByCase() throws Exception {
        String lower = "{\"type\":\"BP\",\"dataField\":\"118/76\",\"measuringInstruction\":\"sitting position\","
                + "\"comments\":\"dose 1 mg\",\"dateObserved\":1770000000000}";
        String upper = "{\"type\":\"BP\",\"dataField\":\"118/76\",\"measuringInstruction\":\"sitting position\","
                + "\"comments\":\"dose 1 Mg\",\"dateObserved\":1770000000000}";

        EmbeddedMeasurementTo1 first = service.addMeasurement((ObjectNode) mapper.readTree(lower), patient);
        entityManager.flush();
        EmbeddedMeasurementTo1 second = service.addMeasurement((ObjectNode) mapper.readTree(upper), patient);
        entityManager.flush();
        EmbeddedMeasurementTo1 retrySecond = service.addMeasurement((ObjectNode) mapper.readTree(upper), patient);

        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(second.getComments()).isEqualTo("dose 1 Mg");
        assertThat(retrySecond.getId()).isEqualTo(second.getId());
        assertThat(measurementDao.findByType(patient, List.of("BP")))
                .hasSize(2)
                .extracting(Measurement::getComments)
                .containsExactlyInAnyOrder("dose 1 mg", "dose 1 Mg");
    }

    @Test
    @DisplayName("should refuse a value the seeded blood pressure rule rejects and store nothing")
    void shouldStoreNothing_whenValueViolatesSeededRule() throws Exception {
        ObjectNode body = (ObjectNode) mapper.readTree(
                "{\"type\":\"BP\",\"dataField\":\"high\",\"measuringInstruction\":\"sitting position\"}");

        BadRequestException refused = catchThrowableOfType(() -> service.addMeasurement(body, patient),
                BadRequestException.class);

        assertThat(refused).isNotNull();
        assertThat(refused.getResponse().getEntity())
                .isInstanceOfSatisfying(Map.class, error -> assertThat(error.get("code"))
                        .isEqualTo("VALUE_REJECTED"));
        assertThat(measurementDao.findByType(patient, List.of("BP"))).isEmpty();
    }
}
