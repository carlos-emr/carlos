/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.prevention.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.model.CVCImmunization;
import io.github.carlos_emr.carlos.commn.model.CVCMedication;
import io.github.carlos_emr.carlos.commn.model.CVCMedicationLotNumber;
import io.github.carlos_emr.carlos.managers.CanadianVaccineCatalogueManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("fast")
class VaccineCatalogue2ActionUnitTest extends CarlosUnitTestBase {
    private final CanadianVaccineCatalogueManager catalogue = mock(CanadianVaccineCatalogueManager.class);
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final LoggedInInfo user = mock(LoggedInInfo.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/cvc");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> session;
    private VaccineCatalogue2Action action;

    @BeforeEach
    void setUp() {
        registerMock(CanadianVaccineCatalogueManager.class, catalogue);
        registerMock(SecurityInfoManager.class, security);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        session = mockStatic(LoggedInInfo.class);
        session.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(user);
        when(security.hasPrivilege(user, "_prevention", SecurityInfoManager.READ, null)).thenReturn(true);
        action = new VaccineCatalogue2Action();
    }

    @AfterEach
    void tearDown() {
        session.close();
        servlet.close();
    }

    @Test
    void shouldReturnLotSearchContract_whenCatalogueMatches() throws Exception {
        request.setParameter("method", "query");
        request.setParameter("query", "DEMO");
        CVCImmunization vaccine = new CVCImmunization();
        vaccine.setGeneric(false);
        vaccine.setDisplayName("Synthetic vaccine");
        vaccine.setSnomedConceptId("123456");
        vaccine.setParentConceptId("123450");
        CVCMedication medication = mock(CVCMedication.class);
        CVCMedicationLotNumber lot = mock(CVCMedicationLotNumber.class);
        when(lot.getMedication()).thenReturn(medication);
        when(medication.getSnomedCode()).thenReturn("123456");
        when(catalogue.findByLotNumber(user, "DEMO123")).thenReturn(lot);
        when(catalogue.query(eq("DEMO"), eq(true), eq(true), eq(true), eq(false), any())).thenAnswer(call -> {
            ((StringBuilder) call.getArgument(5)).append("DEMO123");
            return List.of(vaccine);
        });
        action.execute();
        JsonNode result = new ObjectMapper().readTree(response.getContentAsString()).get("results").get(0);
        assertThat(result.get("name").asText()).isEqualTo("Synthetic vaccine");
        assertThat(result.get("lotNumber").asText()).isEqualTo("DEMO123");
        assertThat(result.get("snomedId").asText()).isEqualTo("123456");
        assertThat(result.get("genericSnomedId").asText()).isEqualTo("123450");
        assertThat(result.get("generic").asBoolean()).isFalse();
        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void shouldKeepLotBoundToItsVaccine_whenBrandAndLotSearchesOverlap() throws Exception {
        request.setParameter("method", "query");
        request.setParameter("query", "DEMO");
        CVCImmunization brand = new CVCImmunization();
        brand.setGeneric(false);
        brand.setSnomedConceptId("111111");
        brand.setDisplayName("DEMO brand");
        CVCImmunization generic = new CVCImmunization();
        generic.setGeneric(true);
        generic.setSnomedConceptId("222222");
        generic.setDisplayName("DEMO generic");
        CVCMedication medication = mock(CVCMedication.class);
        CVCMedicationLotNumber lot = mock(CVCMedicationLotNumber.class);
        when(lot.getMedication()).thenReturn(medication);
        when(medication.getSnomedCode()).thenReturn("222222");
        when(catalogue.findByLotNumber(user, "DEMO123")).thenReturn(lot);
        when(catalogue.query(eq("DEMO"), eq(true), eq(true), eq(true), eq(false), any())).thenAnswer(call -> {
            ((StringBuilder) call.getArgument(5)).append("DEMO123");
            return List.of(brand, generic);
        });
        action.execute();
        JsonNode results = new ObjectMapper().readTree(response.getContentAsString()).get("results");
        assertThat(results.size()).isEqualTo(2);
        assertThat(results.get(0).get("name").asText()).isEqualTo("DEMO brand");
        assertThat(results.get(0).get("lotNumber").asText()).isEmpty();
        assertThat(results.get(1).get("genericSnomedId").asText()).isEqualTo("222222");
        assertThat(results.get(1).get("lotNumber").asText()).isEqualTo("DEMO123");
    }

    @Test
    void shouldPreserveMissingExpiry_whenReturningVaccineLots() throws Exception {
        request.setParameter("method", "getLotNumberAndExpiryDates");
        request.setParameter("snomedConceptId", "123456");
        CVCMedication medication = mock(CVCMedication.class);
        CVCMedicationLotNumber dated = mock(CVCMedicationLotNumber.class);
        CVCMedicationLotNumber undated = mock(CVCMedicationLotNumber.class);
        when(dated.getLotNumber()).thenReturn("A-DATED");
        when(dated.getExpiryDate()).thenReturn(new Date(1798761600000L));
        when(undated.getLotNumber()).thenReturn("B-UNDATED");
        when(medication.getLotNumberList()).thenReturn(Set.of(undated, dated));
        when(catalogue.getMedicationBySnomedConceptId("123456")).thenReturn(medication);
        action.execute();
        JsonNode lots = new ObjectMapper().readTree(response.getContentAsString());
        assertThat(lots.size()).isEqualTo(2);
        assertThat(lots.get(0).get("expiryDate").get("time").asLong()).isEqualTo(1798761600000L);
        assertThat(lots.get(1).get("expiryDate").isNull()).isTrue();
    }

    @Test
    void shouldReturnEmptyLots_whenVaccineIsNotInCatalogue() throws Exception {
        request.setParameter("method", "getLotNumberAndExpiryDates");
        request.setParameter("snomedConceptId", "123456");
        action.execute();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo("[]");
    }

    @Test
    void shouldRejectLookup_whenSessionIsMissing() throws Exception {
        session.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(null);
        action.execute();
        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(catalogue);
    }

    @Test
    void shouldRejectLookup_whenPreventionAccessIsMissing() throws Exception {
        when(security.hasPrivilege(user, "_prevention", SecurityInfoManager.READ, null)).thenReturn(false);
        action.execute();
        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(catalogue);
    }

    @Test
    void shouldRejectLookup_whenRequestUsesGet() throws Exception {
        request.setMethod("GET");
        action.execute();
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(catalogue);
    }

    @Test
    void shouldRejectUnknownOperation_whenUpdateRequested() throws Exception {
        request.setParameter("method", "update");
        action.execute();
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(catalogue);
    }

    @Test
    void shouldRejectUnboundedSearch_whenQueryIsTooShort() throws Exception {
        request.setParameter("method", "query");
        request.setParameter("query", "a");
        action.execute();
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(catalogue);
    }
}
