// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.fax.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.PharmacyInfoDao;
import io.github.carlos_emr.carlos.commn.dao.ServiceSpecialistsDao;
import io.github.carlos_emr.carlos.commn.model.PharmacyInfo;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.ArrayList;
import java.util.List;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/** Exercises the fax recipient JSON endpoint, including its authorization and result cap.
 * @since 2026-09-20
 */
class FaxRecipientSearch2ActionUnitTest extends CarlosUnitTestBase {
    private final ObjectMapper mapper = new ObjectMapper();
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager security;
    private ServiceSpecialistsDao specialists;
    private PharmacyInfoDao pharmacies;
    private LoggedInInfo info;

    @BeforeEach
    void setUpEndpoint() {
        request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setParameter("term", "  clinic  ");
        response = new MockHttpServletResponse();
        info = mock(LoggedInInfo.class);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), info);
        security = mock(SecurityInfoManager.class);
        specialists = mock(ServiceSpecialistsDao.class);
        pharmacies = mock(PharmacyInfoDao.class);
        registerMock(SecurityInfoManager.class, security);
        registerMock(ServiceSpecialistsDao.class, specialists);
        registerMock(PharmacyInfoDao.class, pharmacies);
        when(security.hasPrivilege(eq(info), eq("_fax"), eq("r"), isNull())).thenReturn(true);
    }

    @Test
    void shouldRejectMutationMethod_whenPostPrecedesPrivilegeCheck() {
        request.setMethod("POST");

        assertThat(execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("GET");
        verifyNoInteractions(security, specialists, pharmacies);
    }

    @Test
    void shouldReturnForbiddenJson_whenFaxReadPrivilegeMissing() throws Exception {
        when(security.hasPrivilege(eq(info), eq("_fax"), eq("r"), isNull())).thenReturn(false);

        assertThat(execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(mapper.readTree(response.getContentAsString())).isEmpty();
        verifyNoInteractions(specialists, pharmacies);
    }

    @Test
    void shouldSkipDirectoryQueries_whenTermTooShort() throws Exception {
        request.setParameter("term", " x ");

        assertThat(execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(mapper.readTree(response.getContentAsString())).isEmpty();
        verifyNoInteractions(specialists, pharmacies);
    }

    @Test
    void shouldRenderSpecialistAndPharmacyRows_whenTermMatches() throws Exception {
        ProfessionalSpecialist organisation = specialist(null, "Clinic One", "416-555-1000");
        ProfessionalSpecialist person = specialist("Smith", "Ava", "416-555-1001");
        ProfessionalSpecialist noFax = specialist("Hidden", "No Fax", null);
        when(specialists.searchSpecialistsWithService("clinic", 20)).thenReturn(List.of(
                new Object[]{organisation, "Cardiology"},
                new Object[]{person, ""},
                new Object[]{noFax, "Dermatology"}));
        PharmacyInfo pharmacy = new PharmacyInfo();
        pharmacy.setName("Main Pharmacy");
        pharmacy.setCity("Toronto");
        pharmacy.setFax("416-555-2000");
        when(pharmacies.searchFaxablePharmacies("clinic", "", 18)).thenReturn(List.of(pharmacy));

        assertThat(execute()).isEqualTo(ActionSupport.NONE);

        JsonNode rows = mapper.readTree(response.getContentAsString());
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).get("name").asText()).isEqualTo("Clinic One");
        assertThat(rows.get(0).get("badge").asText()).isEqualTo("Cardiology");
        assertThat(rows.get(0).get("type").asText()).isEqualTo("SPECIALIST");
        assertThat(rows.get(1).get("name").asText()).isEqualTo("Smith, Ava");
        assertThat(rows.get(1).get("badge").asText()).isEqualTo("Specialist");
        assertThat(rows.get(2).get("name").asText()).isEqualTo("Main Pharmacy (Toronto)");
        assertThat(rows.get(2).get("fax").asText()).isEqualTo("416-555-2000");
        assertThat(rows.get(2).get("type").asText()).isEqualTo("PHARMACY");
        assertThat(response.getContentType()).contains("application/json");
    }

    @Test
    void shouldCapCombinedResults_whenSpecialistsFillMostSlots() throws Exception {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < 19; i++) {
            rows.add(new Object[]{specialist("Smith", "Person " + i, "416-555-1000"), "Cardiology"});
        }
        when(specialists.searchSpecialistsWithService("clinic", 20)).thenReturn(rows);
        PharmacyInfo pharmacy = new PharmacyInfo();
        pharmacy.setName("Last Slot");
        pharmacy.setFax("416-555-2000");
        when(pharmacies.searchFaxablePharmacies("clinic", "", 1)).thenReturn(List.of(pharmacy));

        assertThat(execute()).isEqualTo(ActionSupport.NONE);

        assertThat(mapper.readTree(response.getContentAsString())).hasSize(20);
        verify(pharmacies).searchFaxablePharmacies("clinic", "", 1);
    }

    @Test
    void shouldDiscardPartialResults_whenPharmacyDirectoryFails() throws Exception {
        when(specialists.searchSpecialistsWithService("clinic", 20)).thenReturn(List.<Object[]>of(
                new Object[]{specialist("Smith", "Ava", "416-555-1000"), "Cardiology"}));
        when(pharmacies.searchFaxablePharmacies("clinic", "", 19))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThat(execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(mapper.readTree(response.getContentAsString())).isEmpty();
    }

    private String execute() {
        try (MockedStatic<ServletActionContext> context = mockStatic(ServletActionContext.class)) {
            context.when(ServletActionContext::getRequest).thenReturn(request);
            context.when(ServletActionContext::getResponse).thenReturn(response);
            return new FaxRecipientSearch2Action().execute();
        }
    }

    private static ProfessionalSpecialist specialist(String lastName, String firstName, String fax) {
        ProfessionalSpecialist specialist = new ProfessionalSpecialist();
        specialist.setLastName(lastName);
        specialist.setFirstName(firstName);
        specialist.setFaxNumber(fax);
        return specialist;
    }
}
