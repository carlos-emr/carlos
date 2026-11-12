/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.MeasurementGroup;
import io.github.carlos_emr.carlos.commn.model.MeasurementsDeleted;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.commn.model.Validations;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementTypesBean;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementTypesBeanHandler;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementsDataBean;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.data.MeasurementTypes;
import io.github.carlos_emr.carlos.encounter.pageUtil.EctSessionBean;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real history/prefill DAOs and independently committed deletes; only authorization and type metadata are mocked. */
@Tag("integration")
@Tag("measurement")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MeasurementRequestIsolationIntegrationTest extends CarlosTestBase {
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private MeasurementDao measurements;
    private TransactionTemplate tx;
    private Demographic a;
    private Demographic b;
    private MeasurementType common;
    private MeasurementType aOnly;
    private MeasurementType bOnly;
    private Validations validation;
    private MeasurementGroup group;
    private Measurement aReading;
    private Measurement bReading;
    private Measurement aExtra;
    private Measurement bExtra;
    private MockHttpSession session;
    private EctSessionBean global;
    private SecurityInfoManager security;
    private LoggedInInfo login;
    private String prefix;

    @BeforeEach
    void seedOwnedPatientsWithDifferentReadings() {
        tx = new TransactionTemplate(transactionManager);
        prefix = "iso4556-" + UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            a = patient("Patient A");
            b = patient("Patient B");
            validation = new Validations();
            validation.setName(prefix);
            validation.setNumeric(true);
            entityManager.persist(validation);
            common = type("shared");
            aOnly = type("onlyA");
            bOnly = type("onlyB");
            group = new MeasurementGroup();
            group.setName(prefix);
            group.setTypeDisplayName(common.getTypeDisplayName());
            entityManager.persist(group);
            aReading = reading(a, common, "61.25");
            bReading = reading(b, common, "92.75");
            aExtra = reading(a, aOnly, "11.0");
            bExtra = reading(b, bOnly, "22.0");
        });
        login = mock(LoggedInInfo.class);
        security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(eq(login), eq("_measurement"), anyString(), nullable(String.class))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(eq(login), anyInt())).thenReturn(true);
        session = new MockHttpSession();
        session.setAttribute("user", "999998");
        LoggedInInfo.setLoggedInInfoIntoSession(session, login);
        global = new EctSessionBean();
        global.demographicNo = b.getDemographicNo().toString();
        session.setAttribute("EctSessionBean", global);
        session.setAttribute("eChartID", global.demographicNo);
    }

    @AfterEach
    void removeOnlyOwnedFixtures() {
        if (a == null || b == null) return;
        tx.executeWithoutResult(status -> {
            List<Integer> patients = List.of(a.getDemographicNo(), b.getDemographicNo());
            entityManager.createQuery("delete from MeasurementsDeleted d where d.demographicNo in :ids")
                    .setParameter("ids", patients).executeUpdate();
            entityManager.createQuery("delete from Measurement m where m.demographicId in :ids")
                    .setParameter("ids", patients).executeUpdate();
            for (Object row : List.of(group, common, aOnly, bOnly, validation, a, b)) {
                entityManager.remove(entityManager.merge(row));
            }
        });
    }

    @Test
    void shouldRunRealHistoryQueriesForAThenBThenAInOneSession() throws Exception {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        try (var spring = mockStatic(SpringUtils.class, CALLS_REAL_METHODS);
             var metadata = mockStatic(MeasurementTypes.class)) {
            spring.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
            MeasurementTypes types = mock(MeasurementTypes.class);
            metadata.when(MeasurementTypes::getInstance).thenReturn(types);
            when(types.getByType(common.getType())).thenReturn(new EctMeasurementTypesBean(common.getId(),
                    common.getType(), common.getTypeDisplayName(), "Isolation", "", validation.getId().toString()));
            for (Demographic patient : List.of(a, b, a)) {
                String expected = patient == a ? "61.25" : "92.75";
                Collection<EctMeasurementsDataBean> history = history(patient);
                assertThat(history).extracting(EctMeasurementsDataBean::getDataField).containsExactly(expected);
                assertThat(history).extracting(EctMeasurementsDataBean::getDemo)
                        .containsExactly(patient.getDemographicNo().toString());
                assertThat(index(patient)).extracting(EctMeasurementsDataBean::getType)
                        .containsExactlyInAnyOrder(common.getType(), patient == a ? aOnly.getType() : bOnly.getType());
            }
            verify(security, times(4)).hasPrivilege(login, "_measurement", "r", a.getDemographicNo().toString());
            verify(security, times(2)).hasPrivilege(login, "_measurement", "r", b.getDemographicNo().toString());
            assertGlobalUnchanged();
        }
    }

    @Test
    void shouldPrefillEachRequestedPatientsRealLatestReadingWithoutPublishingSessionBeans() throws Exception {
        try (var spring = mockStatic(SpringUtils.class, CALLS_REAL_METHODS)) {
            spring.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
            for (Demographic patient : List.of(a, b, a)) {
                MockHttpServletRequest request = request(patient);
                try (var servlet = mockStatic(ServletActionContext.class)) {
                    servlet.when(ServletActionContext::getRequest).thenReturn(request);
                    servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
                    assertThat(new EctSetupMeasurements2Action().execute()).isEqualTo("continue");
                }
                EctMeasurementTypesBeanHandler handler = (EctMeasurementTypesBeanHandler) request.getAttribute("measurementTypes");
                assertThat(handler).isNotNull();
                assertThat(handler.getMeasurementTypeVector()).hasSize(1);
                EctMeasurementTypesBean type = (EctMeasurementTypesBean) handler.getMeasurementTypeVector().get(0);
                assertThat(type.getLastData()).isEqualTo(patient == a ? "61.25" : "92.75");
                assertThat(request.getAttribute("mInstrcs0")).isNotNull();
            }
            assertThat(session.getAttribute("measurementTypes")).isNull();
            assertThat(session.getAttribute("mInstrcs0")).isNull();
            assertGlobalUnchanged();
        }
    }

    @Test
    void shouldRejectMixedOwnedPatientBatchWithoutAnyCommittedDeleteOrTombstone() throws Exception {
        try (var spring = mockStatic(SpringUtils.class, CALLS_REAL_METHODS)) {
            spring.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertThat(delete(a, response, aReading.getId(), bReading.getId())).isEqualTo("none");
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(response.getRedirectedUrl()).isNull();
            assertThat(measurements.find(aReading.getId()).getDataField()).isEqualTo("61.25");
            assertThat(measurements.find(bReading.getId()).getDataField()).isEqualTo("92.75");
            assertThat(tombstones()).isEmpty();
            assertGlobalUnchanged();
        }
    }

    @Test
    void shouldPreserveStoredReadingsAndNotes_whenMeasurementWritePatientIsLocked() {
        String patient = a.getDemographicNo().toString();
        when(security.isAllowedAccessToPatientRecord(login, a.getDemographicNo())).thenReturn(false);
        List<?> before = measurementSnapshot();
        long notesBefore = noteCount(a);
        assertThat(before).hasSize(4);
        session.setAttribute("textOnEncounter", "Retained note draft");
        MockHttpServletRequest request = request(a);
        request.setParameter("numType", "1");
        request.setParameter("inputType-0", common.getType());
        request.setParameter("inputTypeDisplayName-0", common.getTypeDisplayName());
        request.setParameter("inputValue-0", "70");
        request.setParameter("inputMInstrc-0", "");
        request.setParameter("comments-0", "A new clinical reading");
        request.setParameter("date-0", "2026-10-15");
        try (var spring = mockStatic(SpringUtils.class, CALLS_REAL_METHODS);
             var servlet = mockStatic(ServletActionContext.class)) {
            spring.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            assertThatThrownBy(() -> new EctMeasurements2Action().execute())
                    .isInstanceOf(SecurityException.class).hasMessage("Patient record access denied");
            verify(security).hasPrivilege(login, "_measurement", "w", patient);
            verify(security).isAllowedAccessToPatientRecord(login, a.getDemographicNo());
        }
        assertThat(measurementSnapshot()).usingRecursiveComparison().isEqualTo(before);
        assertThat(noteCount(a)).isEqualTo(notesBefore);
        assertThat(tombstones()).isEmpty();
        assertThat(session.getAttribute("textOnEncounter")).isEqualTo("Retained note draft");
        assertGlobalUnchanged();
    }

    private List<?> measurementSnapshot() {
        return tx.execute(status -> entityManager.createQuery(
                "select m.id,m.demographicId,m.type,m.dataField,m.measuringInstruction,m.comments,m.dateObserved,m.createDate "
                        + "from Measurement m where m.demographicId in :ids order by m.id")
                .setParameter("ids", List.of(a.getDemographicNo(), b.getDemographicNo())).getResultList());
    }

    private long noteCount(Demographic patient) {
        Number count = tx.execute(status -> (Number) entityManager.createNativeQuery(
                "select count(*) from casemgmt_note where demographic_no=?1")
                .setParameter(1, patient.getDemographicNo().toString()).getSingleResult());
        return count.longValue();
    }

    @Test
    void shouldCommitOnlyAsDeletionAndTombstoneLeavingBUnchanged() throws Exception {
        try (var spring = mockStatic(SpringUtils.class, CALLS_REAL_METHODS)) {
            spring.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertThat(delete(a, response, aReading.getId())).isEqualTo("none");
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(measurements.find(aReading.getId())).isNull();
            assertThat(measurements.find(bReading.getId()).getDataField()).isEqualTo("92.75");
            assertThat(measurements.find(aExtra.getId()).getDataField()).isEqualTo("11.0");
            assertThat(measurements.find(bExtra.getId()).getDataField()).isEqualTo("22.0");
            assertThat(tombstones()).singleElement().satisfies(row -> {
                assertThat(row.getOriginalId()).isEqualTo(aReading.getId());
                assertThat(row.getDemographicNo()).isEqualTo(a.getDemographicNo());
                assertThat(row.getDataField()).isEqualTo("61.25");
            });
            assertThat(response.getRedirectedUrl()).endsWith("&demographicNo=" + a.getDemographicNo());
            assertGlobalUnchanged();
        }
    }

    @Test
    void shouldDeleteTombstoneAndCloseWindow_whenPostedLikeReadOnlyMeasurementPage() throws Exception {
        // Issue #4715: AddMeasurementData.jsp opens a saved reading read-only and posts Delete with
        // its hidden inputs -- the chart's patient and the reading, no history type.
        try (var spring = mockStatic(SpringUtils.class, CALLS_REAL_METHODS)) {
            spring.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertThat(deleteFromReadOnlyPage(a, response, aReading.getId())).isEqualTo("success");
            assertThat(response.getRedirectedUrl()).isNull();
            assertThat(measurements.find(aReading.getId())).isNull();
            assertThat(measurements.find(bReading.getId()).getDataField()).isEqualTo("92.75");
            assertThat(tombstones()).singleElement().satisfies(row -> {
                assertThat(row.getOriginalId()).isEqualTo(aReading.getId());
                assertThat(row.getDemographicNo()).isEqualTo(a.getDemographicNo());
                assertThat(row.getDataField()).isEqualTo("61.25");
            });
            assertGlobalUnchanged();
        }
    }

    @Test
    void shouldRefuseOtherPatientsReading_whenPostedLikeReadOnlyMeasurementPage() throws Exception {
        try (var spring = mockStatic(SpringUtils.class, CALLS_REAL_METHODS)) {
            spring.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertThat(deleteFromReadOnlyPage(a, response, bReading.getId())).isEqualTo("none");
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(measurements.find(aReading.getId()).getDataField()).isEqualTo("61.25");
            assertThat(measurements.find(bReading.getId()).getDataField()).isEqualTo("92.75");
            assertThat(tombstones()).isEmpty();
            assertGlobalUnchanged();
        }
    }

    private Demographic patient(String name) {
        Demographic patient = new Demographic();
        patient.setFirstName(name);
        patient.setLastName("Isolation4556");
        patient.setSex("U");
        patient.setProviderNo("999998");
        entityManager.persist(patient);
        return patient;
    }

    private MeasurementType type(String suffix) {
        MeasurementType type = new MeasurementType();
        type.setType(prefix + suffix);
        type.setTypeDisplayName(prefix + suffix);
        type.setTypeDescription("Isolation fixture");
        type.setMeasuringInstruction("");
        type.setValidation(validation.getId().toString());
        entityManager.persist(type);
        return type;
    }

    private Measurement reading(Demographic patient, MeasurementType type, String value) {
        Measurement reading = new Measurement();
        reading.setDemographicId(patient.getDemographicNo());
        reading.setType(type.getType());
        reading.setProviderNo("0");
        reading.setDataField(value);
        reading.setDateObserved(new Date(1700000000000L));
        entityManager.persist(reading);
        return reading;
    }

    private MockHttpServletRequest request(Demographic patient) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/carlos/encounter/oscarMeasurements/test");
        request.setSession(session);
        request.setContextPath("/carlos");
        request.setParameter("demographicNo", patient.getDemographicNo().toString());
        request.setParameter("type", common.getType());
        request.setParameter("groupName", group.getName());
        return request;
    }

    private Collection<EctMeasurementsDataBean> history(Demographic patient) throws Exception {
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request(patient));
            servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            EctSetupDisplayHistory2Action action = new EctSetupDisplayHistory2Action();
            assertThat(action.execute()).isEqualTo("continue");
            return action.getMeasurementsData().getMeasurementsDataVector();
        }
    }

    private Collection<EctMeasurementsDataBean> index(Demographic patient) throws Exception {
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request(patient));
            servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            EctSetupHistoryIndex2Action action = new EctSetupHistoryIndex2Action();
            assertThat(action.execute()).isEqualTo("continue");
            return action.getMeasurementsData().getMeasurementsDataVector();
        }
    }

    private String delete(Demographic patient, MockHttpServletResponse response, Integer... ids) throws Exception {
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request(patient));
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            EctDeleteData2Action action = new EctDeleteData2Action();
            action.setType(common.getType());
            action.setDeleteCheckbox(java.util.Arrays.stream(ids).map(String::valueOf).toArray(String[]::new));
            return action.execute();
        }
    }

    /** The Delete AddMeasurementData.jsp sends for a saved reading: the patient and the reading, nothing else. */
    private String deleteFromReadOnlyPage(Demographic patient, MockHttpServletResponse response, Integer id) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/carlos/encounter/oscarMeasurements/DeleteData2");
        request.setSession(session);
        request.setContextPath("/carlos");
        request.setParameter("demographicNo", patient.getDemographicNo().toString());
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            EctDeleteData2Action action = new EctDeleteData2Action();
            action.setDeleteCheckbox(new String[] {String.valueOf(id)});
            return action.execute();
        }
    }

    private List<MeasurementsDeleted> tombstones() {
        return tx.execute(status -> entityManager.createQuery(
                "select d from MeasurementsDeleted d where d.demographicNo in :ids", MeasurementsDeleted.class)
                .setParameter("ids", List.of(a.getDemographicNo(), b.getDemographicNo())).getResultList());
    }

    private void assertGlobalUnchanged() {
        assertThat(session.getAttribute("EctSessionBean")).isSameAs(global);
        assertThat(global.demographicNo).isEqualTo(b.getDemographicNo().toString());
        assertThat(session.getAttribute("eChartID")).isEqualTo(b.getDemographicNo().toString());
    }
}
