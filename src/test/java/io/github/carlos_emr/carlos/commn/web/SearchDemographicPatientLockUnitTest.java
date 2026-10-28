/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.model.security.Secobjprivilege;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Exercises the schedule quick search / patient typeahead ({@code demographic/SearchDemographic}):
 * a patient carrying a record lock for the signed-in provider must be dropped from the JSON
 * before any of its name, DOB, HIN, phone, address, alert or next appointment is looked up or
 * written, while unlocked rows keep their order, fields and the DAO's result limits.
 */
// @Isolated because workflow_enhance (which turns on the per-row alert/provider/appointment
// lookups this contract checks) lives in the process-wide CarlosProperties singleton.
@Isolated
@Tag("unit")
@Tag("security")
@Tag("demographic")
class SearchDemographicPatientLockUnitTest extends CarlosUnitTestBase {
    private static final String PROVIDER = "999998";
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/demographic/SearchDemographic");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final DemographicDao demographics = mock(DemographicDao.class);
    private final DemographicCustDao customFields = mock(DemographicCustDao.class);
    private final OscarAppointmentDao appointments = mock(OscarAppointmentDao.class);
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final LoggedInInfo user = mock(LoggedInInfo.class);
    private String previousWorkflowEnhance;

    @BeforeEach
    void setUpAction() {
        previousWorkflowEnhance = CarlosProperties.getInstance().getProperty("workflow_enhance");
        CarlosProperties.getInstance().setProperty("workflow_enhance", "true");
        registerMock(DemographicDao.class, demographics);
        registerMock(DemographicCustDao.class, customFields);
        registerMock(OscarAppointmentDao.class, appointments);
        registerMock(SecurityInfoManager.class, security);
        registerMock(ProviderDao.class, mock(ProviderDao.class));
        when(user.getLoggedInProviderNo()).thenReturn(PROVIDER);
        request.getSession().setAttribute(LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY", user);
        when(security.hasPrivilege(user, "_demographic", "r", null)).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(eq(user), anyInt())).thenReturn(true);
        when(security.getSecurityObjects(user)).thenReturn(List.of());
        when(appointments.findNextAppointmentDates(anyCollection())).thenReturn(Map.of());
        request.setParameter("term", "FAKE");
        request.setParameter("jqueryJSON", "true");
        ActionContext.of().withServletRequest(request).withServletResponse(response).bind();
    }

    @AfterEach
    void restoreRequestAndProperties() {
        ActionContext.clear();
        if (previousWorkflowEnhance == null) {
            CarlosProperties.getInstance().remove("workflow_enhance");
        } else {
            CarlosProperties.getInstance().setProperty("workflow_enhance", previousWorkflowEnhance);
        }
    }

    private static Demographic patient(int id, String tag) {
        Demographic patient = new Demographic();
        patient.setDemographicNo(id);
        patient.setFirstName("Patient");
        patient.setLastName("FAKE-" + tag);
        patient.setDateOfBirth("1990-01-15");
        patient.setHin("HIN-" + tag);
        patient.setPhone("PHONE-" + tag);
        patient.setAddress("ADDRESS-" + tag);
        patient.setPatientStatus("AC");
        return patient;
    }

    /** Six, seven and eight in DAO order; seven is the locked patient where a test locks it. */
    private List<Demographic> openLockedOpen() {
        return List.of(patient(6, "OPEN-SIX"), patient(7, "LOCKED-SEVEN"), patient(8, "OPEN-EIGHT"));
    }

    private void stubEverySearch(List<Demographic> rows) {
        when(demographics.searchDemographicByName("FAKE", 100, 0, PROVIDER, false)).thenReturn(rows);
        when(demographics.searchDemographicByHIN("FAKE", 20, 0, PROVIDER, false)).thenReturn(rows);
        when(demographics.searchDemographicByPhone("FAKE", 20, 0, PROVIDER, false)).thenReturn(rows);
        when(demographics.searchDemographicByAddress("FAKE", 20, 0, PROVIDER, false)).thenReturn(rows);
        when(demographics.searchDemographicByDOB("1990-01-15", 100, 0, PROVIDER, false)).thenReturn(rows);
    }

    private void lockPatientSeven() {
        when(security.isAllowedAccessToPatientRecord(user, 7)).thenReturn(false);
        when(security.getSecurityObjects(user)).thenReturn(
                List.of(new Secobjprivilege("doctor", "_eChart$7", "|o|", 0, PROVIDER)));
    }

    private List<Map<String, Object>> search() throws Exception {
        assertThat(new SearchDemographicAutoComplete2Action().execute()).isNull();
        assertThat(response.getContentType()).startsWith("application/json");
        return new ObjectMapper().readValue(response.getContentAsString(), new TypeReference<>() { });
    }

    private static List<Object> ids(List<Map<String, Object>> rows) {
        List<Object> ids = new ArrayList<>();
        rows.forEach(row -> ids.add(row.get("demographicNo")));
        return ids;
    }

    @ParameterizedTest
    @CsvSource({"name,FAKE", "hin,FAKE", "phone,FAKE", "address,FAKE", "name,19900115"})
    void shouldDropLockedPatient_forEverySearchType(String searchType, String term) throws Exception {
        request.setParameter("searchType", searchType);
        request.setParameter("term", term);
        stubEverySearch(openLockedOpen());
        lockPatientSeven();

        List<Map<String, Object>> rows = search();

        assertThat(ids(rows)).containsExactly("6", "8");
        assertThat(response.getContentAsString()).doesNotContain("LOCKED-SEVEN", "\"7\"");
    }

    @Test
    void shouldSkipEveryPerPatientLookup_whenPatientIsLocked() throws Exception {
        stubEverySearch(openLockedOpen());
        lockPatientSeven();

        search();

        verify(demographics).searchDemographicByName("FAKE", 100, 0, PROVIDER, false);
        verify(customFields).find((Object) Integer.valueOf(6));
        verify(customFields).find((Object) Integer.valueOf(8));
        verify(customFields, never()).find((Object) Integer.valueOf(7));
        verify(appointments).findNextAppointmentDates(argThat(requested ->
                requested.size() == 2 && requested.contains(6) && requested.contains(8)));
    }

    @Test
    void shouldReturnEveryRowUnchanged_whenNoPatientIsLocked() throws Exception {
        stubEverySearch(openLockedOpen());

        List<Map<String, Object>> rows = search();

        assertThat(ids(rows)).containsExactly("6", "7", "8");
        assertThat(rows.get(1)).containsEntry("hin", "HIN-LOCKED-SEVEN")
                .containsEntry("phone", "PHONE-LOCKED-SEVEN")
                .containsEntry("address", "ADDRESS-LOCKED-SEVEN")
                .containsEntry("value", "7");
        verify(customFields).find((Object) Integer.valueOf(7));
    }

    @Test
    void shouldDropLockedPatient_fromResultsEnvelope() throws Exception {
        request.removeParameter("jqueryJSON");
        stubEverySearch(openLockedOpen());
        lockPatientSeven();

        new SearchDemographicAutoComplete2Action().execute();

        Map<String, List<Map<String, Object>>> envelope = new ObjectMapper()
                .readValue(response.getContentAsString(), new TypeReference<>() { });
        assertThat(ids(envelope.get("results"))).containsExactly("6", "8");
        assertThat(response.getContentAsString()).doesNotContain("LOCKED-SEVEN");
    }

    @Test
    void shouldReturnFewerRowsThanTheLimit_whenALockedPatientFillsAFullPage() throws Exception {
        List<Demographic> fullPage = new ArrayList<>();
        for (int id = 1; id <= 100; id++) {
            fullPage.add(patient(id, id == 7 ? "LOCKED-SEVEN" : "OPEN-" + id));
        }
        stubEverySearch(fullPage);
        lockPatientSeven();

        List<Map<String, Object>> rows = search();

        assertThat(rows).hasSize(99);
        assertThat(ids(rows)).doesNotContain("7").startsWith("1", "2", "3", "4", "5", "6", "8");
        verify(demographics).searchDemographicByName("FAKE", 100, 0, PROVIDER, false);
    }

    @Test
    void shouldFailClosed_whenPrivilegeMetadataIsMissing() {
        when(security.getSecurityObjects(user)).thenReturn(null);

        assertThatThrownBy(() -> new SearchDemographicAutoComplete2Action().execute())
                .isInstanceOf(SecurityException.class);

        assertThat(response.getContentAsByteArray()).isEmpty();
        verifyNoInteractions(demographics, customFields, appointments);
    }
}
