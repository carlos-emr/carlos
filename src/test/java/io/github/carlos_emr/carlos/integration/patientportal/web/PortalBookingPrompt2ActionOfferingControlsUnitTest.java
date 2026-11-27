/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.integration.patientportal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalService;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalStaffContext;
import io.github.carlos_emr.carlos.integration.patientportal.PortalStaffContextResolver;
import io.github.carlos_emr.carlos.integration.patientportal.booking.PortalBookingOfferService;
import io.github.carlos_emr.carlos.integration.patientportal.booking.PortalBookingSettings;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The staff dialog's offer controls (#3850): the provider list the server builds for the window, and
 * what the panel says about whether this user may offer times, whether the clinic has chosen any
 * bookable template codes, and how the times will be shown. FAKE data only.
 */
@Tag("unit")
@Tag("patient-portal")
class PortalBookingPrompt2ActionOfferingControlsUnitTest {
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final PatientPortalService portal = mock(PatientPortalService.class);
    private final PortalStaffContextResolver resolver = mock(PortalStaffContextResolver.class);
    private final PortalBookingOfferService offers = mock(PortalBookingOfferService.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final LoggedInInfo user = mock(LoggedInInfo.class);
    private final PatientPortalStaffContext staff = new PatientPortalStaffContext("999998", "Synthetic Provider",
            Set.of(PatientPortalStaffContext.PERMISSION_BOOKING_PROMPT_MANAGE));
    private PortalBookingSettings clinic = new PortalBookingSettings(Set.of('B'), 24, "phone", null, false, 60);
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> login;
    private MockedStatic<LogAction> audit;

    @BeforeEach
    void setUp() {
        request.setMethod("POST");
        request.setParameter("method", "providers");
        request.setParameter("demographicNo", "123");
        audit = mockStatic(LogAction.class);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        login = mockStatic(LoggedInInfo.class);
        login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(user);
        when(user.getLoggedInProviderNo()).thenReturn("999998");
        when(security.hasPrivilege(any(), anyString(), anyString(), eq("123"))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(any(), eq(123))).thenReturn(true);
        when(resolver.resolveForPatient(any(), any(), eq(123))).thenReturn(staff);
        when(portal.isBookingEligible(eq(123), any())).thenReturn(true);
        when(portal.listBookingPrompts(eq(123), any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        login.close();
        servlet.close();
        audit.close();
    }

    private void execute() throws Exception {
        new PortalBookingPrompt2Action(security, portal, resolver, offers, () -> clinic).execute();
    }

    private JsonNode body() throws Exception {
        return new ObjectMapper().readTree(response.getContentAsString());
    }

    @Test
    void shouldListTheProvidersWithOpenTime_forTheWindowTheDialogAsksFor() throws Exception {
        request.setParameter("offerAfterDays", "3");
        request.setParameter("offerWithinDays", "10");
        LocalDate from = LocalDate.now().plusDays(3);
        when(offers.offerableProviders(eq(from), eq(from.plusDays(9)), any())).thenReturn(List.of(
                new PortalBookingOfferService.OfferableProvider("101", "Alpha, Ann"),
                new PortalBookingOfferService.OfferableProvider("102", "Zeta, Zoe")));
        execute();
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode json = body();
        assertThat(json.get("ok").asBoolean()).isTrue();
        assertThat(json.get("codesConfigured").asBoolean()).isTrue();
        assertThat(json.get("providers")).hasSize(2);
        assertThat(json.get("providers").get(0).get("providerNo").asText()).isEqualTo("101");
        assertThat(json.get("providers").get(0).get("name").asText()).isEqualTo("Alpha, Ann");
        // Only a number and a name: nothing about their schedule, patients or settings.
        assertThat(json.get("providers").get(0).size()).isEqualTo(2);
        // It reads CARLOS only: nothing goes to the portal, and nothing is audited as a change.
        verify(portal, never()).createBookingPrompt(org.mockito.ArgumentMatchers.anyInt(), any(), any());
    }

    @Test
    void shouldListNothingAndSayWhy_whenTheClinicChoseNoTemplateCodes() throws Exception {
        clinic = new PortalBookingSettings(Set.of(), 24, "in_person", null, false, 60);
        when(offers.offerableProviders(any(), any(), any())).thenReturn(List.of());
        execute();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body().get("codesConfigured").asBoolean()).isFalse();
        assertThat(body().get("providers")).isEmpty();
    }

    @Test
    void shouldRefuseTheList_withoutScheduleWriteOrBookingPromptWrite() throws Exception {
        when(security.hasPrivilege(any(), eq(PortalBookingPrompt2Action.OBJECT_APPOINTMENT), anyString(), eq("123")))
                .thenReturn(false);
        execute();
        assertThat(response.getStatus()).isEqualTo(403);
        verify(offers, never()).offerableProviders(any(), any(), any());

        MockHttpServletResponse second = new MockHttpServletResponse();
        servlet.when(ServletActionContext::getResponse).thenReturn(second);
        when(security.hasPrivilege(any(), eq(PortalBookingPrompt2Action.OBJECT_APPOINTMENT), anyString(), eq("123")))
                .thenReturn(true);
        when(security.hasPrivilege(any(), eq(PortalStaffContextResolver.OBJECT_BOOKING_PROMPT),
                eq(SecurityInfoManager.WRITE), eq("123"))).thenReturn(false);
        new PortalBookingPrompt2Action(security, portal, resolver, offers, () -> clinic).execute();
        assertThat(second.getStatus()).isEqualTo(403);
        verify(offers, never()).offerableProviders(any(), any(), any());
    }

    @Test
    void shouldRefuseTheList_forAGetAndForAMalformedWindow() throws Exception {
        request.setMethod("GET");
        execute();
        assertThat(response.getStatus()).isEqualTo(405);
        verify(offers, never()).offerableProviders(any(), any(), any());

        MockHttpServletResponse second = new MockHttpServletResponse();
        servlet.when(ServletActionContext::getResponse).thenReturn(second);
        request.setMethod("POST");
        request.setParameter("offerWithinDays", "93");
        new PortalBookingPrompt2Action(security, portal, resolver, offers, () -> clinic).execute();
        assertThat(second.getStatus()).isEqualTo(400);
        verify(offers, never()).offerableProviders(any(), any(), any());
    }

    @Test
    void shouldDescribeTheOfferingInThePanel_forAUserWhoMayOffer() throws Exception {
        request.setParameter("method", "panel");
        execute();
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode offering = body().get("offering");
        assertThat(offering.get("mayOffer").asBoolean()).isTrue();
        assertThat(offering.get("codesConfigured").asBoolean()).isTrue();
        // The clinic's one visit mode, shown read-only: a per-request mode waits for its own column.
        assertThat(offering.get("visitMode").asText()).isEqualTo("phone");
        assertThat(offering.get("defaultWithinDays").asInt()).isEqualTo(14);
        assertThat(offering.get("maxWithinDays").asInt()).isEqualTo(92);
        assertThat(offering.get("maxAfterDays").asInt()).isEqualTo(PortalBookingOfferService.MAX_DAYS_AHEAD);
        assertThat(offering.get("defaultCount").asInt()).isEqualTo(4);
        assertThat(offering.get("maxCount").asInt()).isEqualTo(8);
    }

    @Test
    void shouldSayInThePanelThatOfferingIsOff_andThatAUserWithoutScheduleWriteMayNotOffer() throws Exception {
        request.setParameter("method", "panel");
        clinic = new PortalBookingSettings(Set.of(), 24, "in_person", null, false, 60);
        when(security.hasPrivilege(any(), eq(PortalBookingPrompt2Action.OBJECT_APPOINTMENT), anyString(), eq("123")))
                .thenReturn(false);
        execute();
        JsonNode offering = body().get("offering");
        assertThat(offering.get("mayOffer").asBoolean()).isFalse();
        assertThat(offering.get("codesConfigured").asBoolean()).isFalse();
    }

    @Test
    void shouldLeaveTheOfferingOutOfThePanel_forAUserWhoMayNotCreate() throws Exception {
        request.setParameter("method", "panel");
        when(security.hasPrivilege(any(), eq(PortalStaffContextResolver.OBJECT_BOOKING_PROMPT),
                eq(SecurityInfoManager.WRITE), eq("123"))).thenReturn(false);
        execute();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body().has("offering")).isFalse();
    }
}
