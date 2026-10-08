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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalAccountDto;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalBookingPromptDto;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalBookingPromptRequest;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalOfferedSlot;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalService;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalStaffContext;
import io.github.carlos_emr.carlos.integration.patientportal.PortalStaffContextResolver;
import io.github.carlos_emr.carlos.integration.patientportal.booking.PortalBookingOfferService;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Staff send open times with a prompt in one step (#3850); the times need schedule access. */
@Tag("unit")
@Tag("patient-portal")
class PortalBookingPrompt2ActionOfferedTimesUnitTest {
    private static final PatientPortalOfferedSlot SLOT = new PatientPortalOfferedSlot("slot-a",
            OffsetDateTime.parse("2026-10-20T09:30:00-04:00"), 15, "in_person", null);
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final PatientPortalService portal = mock(PatientPortalService.class);
    private final PortalStaffContextResolver resolver = mock(PortalStaffContextResolver.class);
    private final PortalBookingOfferService offers = mock(PortalBookingOfferService.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final LoggedInInfo user = mock(LoggedInInfo.class);
    private final PatientPortalStaffContext staff = new PatientPortalStaffContext("999998", "Synthetic Provider",
            Set.of(PatientPortalStaffContext.PERMISSION_BOOKING_PROMPT_MANAGE,
                    PatientPortalStaffContext.PERMISSION_ACCOUNT_MANAGE));
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> login;
    private MockedStatic<LogAction> audit;

    @BeforeEach
    void setUp() {
        request.setMethod("POST");
        request.setParameter("method", "create");
        request.setParameter("demographicNo", "123");
        request.setParameter("operationId", "operation-1");
        request.setParameter("urgency", "soon");
        request.setParameter("appointmentType", "follow_up");
        request.setParameter("offerFrom", "101");
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
        when(portal.findAccount(eq(123), same(staff)))
                .thenReturn(new PatientPortalAccountDto(3, "clinic-a", 123, "active", false, false, null, null));
        Instant timestamp = Instant.parse("2026-10-01T12:00:00Z");
        when(portal.createBookingPrompt(eq(123), any(), same(staff))).thenReturn(new PatientPortalBookingPromptDto.Creation(
                new PatientPortalBookingPromptDto(7, 123, "soon", "follow_up", null, "sent", "Synthetic Provider",
                        timestamp, timestamp.plusSeconds(86400), null, null, null, null), true));
    }

    @AfterEach
    void tearDown() {
        login.close();
        servlet.close();
        audit.close();
    }

    private void execute() throws Exception {
        new PortalBookingPrompt2Action(security, portal, resolver, offers).execute();
    }

    @Test
    void shouldSendOpenTimesWithThePrompt_whenStaffOfferThem() throws Exception {
        when(offers.offer(eq("operation-1"), eq(123), eq("101"), any(), any(), eq(4), eq("999998"), any()))
                .thenReturn(List.of(SLOT));
        execute();
        assertThat(response.getStatus()).isEqualTo(201);
        verify(security).hasPrivilege(any(), eq(PortalBookingPrompt2Action.OBJECT_APPOINTMENT), eq(SecurityInfoManager.READ), eq("123"));
        ArgumentCaptor<PatientPortalBookingPromptRequest> sent = ArgumentCaptor.forClass(PatientPortalBookingPromptRequest.class);
        verify(portal).createBookingPrompt(eq(123), sent.capture(), same(staff));
        assertThat(sent.getValue().offeredSlots()).containsExactly(SLOT);
        verify(offers).attachPrompt("operation-1", 7);
        assertThat(new ObjectMapper().readTree(response.getContentAsString()).get("offeredTimes").asInt()).isEqualTo(1);
    }

    @Test
    void shouldOfferAFortnightFromToday_byDefault() throws Exception {
        when(offers.offer(any(), anyInt(), any(), any(), any(), anyInt(), any(), any())).thenReturn(List.of(SLOT));
        execute();
        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(offers).offer(any(), anyInt(), any(), from.capture(), to.capture(), anyInt(), any(), any());
        assertThat(to.getValue()).isEqualTo(from.getValue().plusDays(13));
    }

    @Test
    void shouldSayNoTimesWereFound_withoutCreatingAPrompt() throws Exception {
        when(offers.offer(any(), anyInt(), any(), any(), any(), anyInt(), any(), any())).thenReturn(List.of());
        execute();
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(new ObjectMapper().readTree(response.getContentAsString()).get("reason").asText())
                .isEqualTo("no_open_times");
        verify(portal, never()).createBookingPrompt(anyInt(), any(), any());
    }

    @Test
    void shouldRefuse_withoutScheduleAccess() throws Exception {
        when(security.hasPrivilege(any(), eq(PortalBookingPrompt2Action.OBJECT_APPOINTMENT), anyString(), eq("123")))
                .thenReturn(false);
        execute();
        assertThat(response.getStatus()).isEqualTo(403);
        verify(offers, never()).offer(any(), anyInt(), any(), any(), any(), anyInt(), any(), any());
        verify(portal, never()).createBookingPrompt(anyInt(), any(), any());
    }

    @Test
    void shouldRefuse_whenTheWindowIsMalformed() throws Exception {
        request.setParameter("offerWithinDays", "0");
        execute();
        assertThat(response.getStatus()).isEqualTo(400);
        request.setParameter("offerWithinDays", "14");
        request.setParameter("offerFrom", "101; DROP");
        execute();
        assertThat(response.getStatus()).isEqualTo(400);
        verify(offers, never()).offer(any(), anyInt(), any(), any(), any(), anyInt(), any(), any());
    }
}
