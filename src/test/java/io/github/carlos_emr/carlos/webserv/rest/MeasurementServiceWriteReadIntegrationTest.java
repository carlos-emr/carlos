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
        ReflectionTestUtils.setField(manager, "demographicDao", demographicDao);
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
    @DisplayName("should refuse a value the seeded blood pressure rule rejects and store nothing")
    void shouldStoreNothing_whenValueViolatesSeededRule() throws Exception {
        ObjectNode body = (ObjectNode) mapper.readTree(
                "{\"type\":\"BP\",\"dataField\":\"high\",\"measuringInstruction\":\"sitting position\"}");

        BadRequestException refused = catchThrowableOfType(() -> service.addMeasurement(body, patient),
                BadRequestException.class);

        assertThat(refused).isNotNull();
        assertThat(measurementDao.findByType(patient, List.of("BP"))).isEmpty();
    }

    @Test
    @DisplayName("should persist and read back a 4,000-character free-text measurement and refuse 4,001 characters")
    void shouldPersist4000CharFreeText_whenTypeHasNoValidationRule() throws Exception {
        MeasurementType ecg = new MeasurementType();
        ecg.setType("ECGT");
        ecg.setTypeDisplayName("ECG Report");
        ecg.setTypeDescription("OCR ECG report text");
        ecg.setMeasuringInstruction("");
        ecg.setValidation("");
        measurementTypeDao.persist(ecg);
        entityManager.flush();

        String report = "FAKE sinus rhythm\n" + "R".repeat(4_000 - "FAKE sinus rhythm\n".length());
        assertThat(report).hasSize(4_000);
        ObjectNode validBody = mapper.createObjectNode();
        validBody.put("type", "ECGT");
        validBody.put("dataField", report);
        validBody.put("dateObserved", 1_770_000_000_000L);

        EmbeddedMeasurementTo1 written = service.addMeasurement(validBody, patient);
        entityManager.flush();
        entityManager.clear();

        MeasurementResponse read = service.getMeasurements(
                (ObjectNode) mapper.readTree("{\"types\":[\"ECGT\"]}"), patient);
        List<Measurement> readings = read.getMeasurements().values().stream().flatMap(List::stream).toList();
        assertThat(readings).singleElement().satisfies(reading -> {
            assertThat(reading.getId()).isEqualTo(written.getId());
            assertThat(reading.getDataField()).isEqualTo(report);
        });

        // A reading at the same dateObserved differing only in letter case must be stored separately.
        ObjectNode caseVariant = mapper.createObjectNode();
        caseVariant.put("type", "ECGT");
        caseVariant.put("dataField", report.toLowerCase(java.util.Locale.ROOT));
        caseVariant.put("dateObserved", 1_770_000_000_000L);
        EmbeddedMeasurementTo1 second = service.addMeasurement(caseVariant, patient);
        assertThat(second.getId()).isNotEqualTo(written.getId());

        ObjectNode tooLong = mapper.createObjectNode();
        tooLong.put("type", "ECGT");
        tooLong.put("dataField", report + "x");
        BadRequestException refused = catchThrowableOfType(() -> service.addMeasurement(tooLong, patient),
                BadRequestException.class);
        assertThat(refused).isNotNull();
    }
}
