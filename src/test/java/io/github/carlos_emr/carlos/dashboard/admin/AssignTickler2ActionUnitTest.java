/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.dashboard.admin;

import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class AssignTickler2ActionUnitTest extends CarlosUnitTestBase {
    private AssignTickler2Action action;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private TicklerManager ticklers;

    @BeforeEach
    void prepare() {
        action = mock(AssignTickler2Action.class, CALLS_REAL_METHODS);
        request = new MockHttpServletRequest("POST", "/AssignTickler");
        response = new MockHttpServletResponse();
        action.request = request;
        action.response = response;
        var user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("4245");
        when(user.getLoggedInSecurity()).thenReturn(mock(Security.class));
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        var security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(eq(user), anyString(), anyString(), isNull())).thenReturn(true);
        ticklers = mock(TicklerManager.class);
        when(ticklers.addTickler(eq(user), any())).thenReturn(true);
        ReflectionTestUtils.setField(action, "securityInfoManager", security);
        ReflectionTestUtils.setField(action, "ticklerManager", ticklers);
        request.setParameter("demographics", "101,102");
        request.setParameter("serviceDate", "12-31-2030");
        request.setParameter("serviceTime", "10:30 AM");
        request.setParameter("ticklerCategoryId", "1");
        request.setParameter("priority", "High");
        request.setParameter("taskAssignedTo", "4245");
        request.setParameter("message", "");
        request.setParameter("messageAppend", "Follow up");
        request.setParameter("ticklerSubmission", TicklerSubmission.issue(request.getSession(), "101,102"));
    }

    @ParameterizedTest
    @CsvSource({"serviceDate,02-30-2030", "serviceDate,invalid", "serviceTime,25:00 AM",
            "serviceTime,10:30 AM trailing", "ticklerCategoryId,not-a-number", "priority,unknown", "taskAssignedTo,''"})
    void shouldAllowCorrectionWithSameReceipt_whenInputIsInvalid(String field, String invalid) throws Exception {
        String valid = request.getParameter(field);
        request.setParameter(field, invalid);
        action.saveTickler();
        assertEquals(400, response.getStatus());
        verifyNoInteractions(ticklers);
        request.setParameter(field, valid);
        response.reset();
        action.saveTickler();
        assertEquals(200, response.getStatus());
        assertTrue(response.getContentAsString().contains("true"));
        action.saveTickler();
        verify(ticklers, times(2)).addTickler(any(), any());
    }

    @ParameterizedTest
    @CsvSource({"2026-10-15,10:30 AM", "2026-10-15,14:30", "10-15-2026,10:30 AM", "10-15-2026,14:30", " 2026-10-15 , 02:30 PM "})
    void shouldAcceptPickerAndLegacyFormats_forServiceDateAndTime(String date, String time) throws Exception {
        request.setParameter("serviceDate", date);
        request.setParameter("serviceTime", time);
        action.saveTickler();
        assertEquals(200, response.getStatus());
        assertTrue(response.getContentAsString().contains("true"));
        var captured = org.mockito.ArgumentCaptor.forClass(io.github.carlos_emr.carlos.commn.model.Tickler.class);
        verify(ticklers, times(2)).addTickler(any(), captured.capture());
        var cal = java.util.Calendar.getInstance();
        cal.setTime(captured.getValue().getServiceDate());
        assertEquals(2026, cal.get(java.util.Calendar.YEAR));
        assertEquals(java.util.Calendar.OCTOBER, cal.get(java.util.Calendar.MONTH));
        assertEquals(15, cal.get(java.util.Calendar.DAY_OF_MONTH));
        assertEquals(time.trim().startsWith("10") ? 10 : 14, cal.get(java.util.Calendar.HOUR_OF_DAY));
        assertEquals(30, cal.get(java.util.Calendar.MINUTE));
    }

    @ParameterizedTest
    @CsvSource({"serviceDate,2026-13-01,service date", "serviceDate,15/10/2026,service date",
            "serviceTime,24:61,service time", "serviceTime,noon,service time", "ticklerCategoryId,abc,category",
            "priority,Urgent,priority"})
    void shouldNameTheFieldToFix_whenValidationFails(String field, String invalid, String expected) throws Exception {
        request.setParameter(field, invalid);
        action.saveTickler();
        assertEquals(400, response.getStatus());
        String body = response.getContentAsString();
        assertTrue(body.contains("\"success\":\"false\""));
        assertTrue(body.toLowerCase().contains(expected), body);
        assertFalse(body.contains(invalid), "error must not echo request data");
        verifyNoInteractions(ticklers);
    }

    @Test
    void shouldRefuseNonexistentLocalTime_whenSpringForwardGap() {
        var original = java.util.TimeZone.getDefault();
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Toronto"));
        try {
            var ex = assertThrows(IllegalArgumentException.class,
                    () -> TicklerRequest.parseServiceDateTime("2027-03-14", "02:30"));
            assertTrue(ex.getMessage().contains("does not exist"));
            assertNotNull(TicklerRequest.parseServiceDateTime("2027-03-14", "03:30"));
        } finally {
            java.util.TimeZone.setDefault(original);
        }
    }

    @Test
    void shouldRejectMissingOrRepeatedFields_beforeConsumingReceipt() {
        request.removeParameter("message");
        action.saveTickler();
        assertEquals(400, response.getStatus());
        request.setParameter("message", "");
        request.setParameter("priority", "High", "Low");
        response.reset();
        action.saveTickler();
        assertEquals(400, response.getStatus());
        verifyNoInteractions(ticklers);
        request.setParameter("priority", "High");
        response.reset();
        action.saveTickler();
        verify(ticklers, times(2)).addTickler(any(), any());
    }

    @ParameterizedTest
    @CsvSource({"'1,,2'", "'1,2,'", "'[1,2'", "'1,2]'", "'0'", "'-1'", "'2147483648'"})
    void shouldRejectMalformedPatients_beforeAnyWrite(String patients) {
        request.setParameter("demographics", patients);
        request.setParameter("ticklerSubmission", TicklerSubmission.issue(request.getSession(), patients));
        action.saveTickler();
        assertEquals(400, response.getStatus());
        verifyNoInteractions(ticklers);
    }

    @Test
    void shouldSaveOncePerPatient_whenTheSelectionRepeatsAnId() {
        request.setParameter("demographics", "[101,101,102]");
        request.setParameter("ticklerSubmission", TicklerSubmission.issue(request.getSession(), "[101,101,102]"));
        action.saveTickler();
        verify(ticklers, times(2)).addTickler(any(), any());
    }
}
