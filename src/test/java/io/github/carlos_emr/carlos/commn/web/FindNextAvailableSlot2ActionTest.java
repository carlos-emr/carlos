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
package io.github.carlos_emr.carlos.commn.web;

import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.ScheduleTemplateCodeDao;
import io.github.carlos_emr.carlos.commn.dao.ScheduleTemplateDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.ScheduleTemplateCode;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Test suite for {@link FindNextAvailableSlot2Action}.
 *
 * <p>Validates slot-finding algorithm, security enforcement, parameter handling,
 * and error recovery for the appointment quick-search feature.
 *
 * @since 2026-03-23
 */
@DisplayName("FindNextAvailableSlot2Action Tests")
@Tag("integration")
@Tag("web")
@Tag("appointment")
class FindNextAvailableSlot2ActionTest extends CarlosWebTestBase {

    private static final String TEST_PROVIDER = "999998";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private ScheduleTemplateCodeDao mockTemplateCodeDao;

    @Mock
    private ScheduleTemplateDao mockScheduleTemplateDao;

    @Mock
    private OscarAppointmentDao mockAppointmentDao;

    private FindNextAvailableSlot2Action action;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        // Re-configure security after openMocks re-created parent's mock instances
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), any()))
                .thenReturn(true);

        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        replaceSpringUtilsBean(ScheduleTemplateCodeDao.class, mockTemplateCodeDao);
        replaceSpringUtilsBean(ScheduleTemplateDao.class, mockScheduleTemplateDao);
        replaceSpringUtilsBean(OscarAppointmentDao.class, mockAppointmentDao);

        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);
        setSessionAttribute("user", TEST_PROVIDER);
        String loggedInInfoKey = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(loggedInInfoKey, mockLoggedInInfo);

        // Default: template code 'A' = 15 min appointments
        ScheduleTemplateCode codeA = new ScheduleTemplateCode();
        codeA.setCode('A');
        codeA.setDuration("15");
        when(mockTemplateCodeDao.findTemplateCodes()).thenReturn(List.of(codeA));

        // Default: no booked appointments
        when(mockAppointmentDao.findByProviderAndDayandNotStatuses(anyString(), any(Date.class), any(String[].class)))
                .thenReturn(Collections.emptyList());

        action = new FindNextAvailableSlot2Action();
        injectField("securityInfoManager", mockSecurityInfoManager);
        injectField("request", mockRequest);
        injectField("response", mockResponse);
    }

    private void injectField(String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = FindNextAvailableSlot2Action.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(action, value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeAndParseJson() throws Exception {
        executeAction(action);
        String json = mockResponse.getContentAsString();
        return MAPPER.readValue(json, Map.class);
    }

    /** Creates a timecode string of all 'A' slots for a standard 15-min schedule (96 slots/day). */
    private String allOpenTimecode() {
        return "A".repeat(96); // 96 x 15min = 1440 min = 24h
    }

    /** Sets up the schedule template DAO to return the given timecode for any provider/date. */
    private void setTimecodeForAllDays(String timecode) {
        when(mockScheduleTemplateDao.findTimeCodeByProviderNo2(anyString(), any(Date.class)))
                .thenReturn(List.of((Object) timecode));
    }

    /** Creates an Appointment with start/end times at the given hours/minutes. */
    private Appointment createAppointment(int startHour, int startMin, int endHour, int endMin) {
        Appointment appt = new Appointment();
        Calendar cal = new GregorianCalendar(2026, Calendar.MARCH, 25);
        cal.set(Calendar.HOUR_OF_DAY, startHour);
        cal.set(Calendar.MINUTE, startMin);
        cal.set(Calendar.SECOND, 0);
        appt.setStartTime(cal.getTime());
        cal.set(Calendar.HOUR_OF_DAY, endHour);
        cal.set(Calendar.MINUTE, endMin);
        appt.setEndTime(cal.getTime());
        return appt;
    }

    // ── Security ─────────────────────────────────────────────

    @Nested
    @DisplayName("Security")
    class Security {

        @Test
        @DisplayName("Should throw SecurityException when _appointment read privilege denied")
        void shouldThrowSecurityException_whenPrivilegeDenied() {
            denyPrivilege("_appointment", "r");
            addRequestParameter("providerNos", TEST_PROVIDER);

            assertThatThrownBy(() -> executeAction(action))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("_appointment");

            verifySecurityCheck("_appointment", "r");
        }

        @Test
        @DisplayName("Should proceed normally when _appointment read privilege allowed")
        void shouldProceed_whenPrivilegeAllowed() throws Exception {
            addRequestParameter("providerNos", TEST_PROVIDER);
            setTimecodeForAllDays(allOpenTimecode());

            Map<String, Object> result = executeAndParseJson();

            assertThat(result.get("found")).isEqualTo(true);
        }
    }

    // ── Parameter Validation ─────────────────────────────────

    @Nested
    @DisplayName("Parameter Validation")
    class ParameterValidation {

        @Test
        @DisplayName("Should return error when providerNos is missing")
        void shouldReturnError_whenProviderNosMissing() throws Exception {
            // No providerNos parameter set

            Map<String, Object> result = executeAndParseJson();

            assertThat(result.get("found")).isEqualTo(false);
            assertThat(result.get("error")).isEqualTo("providerNos parameter is required");
        }

        @Test
        @DisplayName("Should return error when providerNos is blank")
        void shouldReturnError_whenProviderNosBlank() throws Exception {
            addRequestParameter("providerNos", "   ");

            Map<String, Object> result = executeAndParseJson();

            assertThat(result.get("found")).isEqualTo(false);
            assertThat(result.get("error")).isEqualTo("providerNos parameter is required");
        }

        @Test
        @DisplayName("Should use explicit startDate when provided")
        void shouldUseStartDate_whenProvided() throws Exception {
            addRequestParameter("providerNos", TEST_PROVIDER);
            addRequestParameter("startDate", "2026-06-15");
            setTimecodeForAllDays(allOpenTimecode());

            Map<String, Object> result = executeAndParseJson();

            assertThat(result.get("found")).isEqualTo(true);
            assertThat(result.get("year")).isEqualTo(2026);
            assertThat(result.get("month")).isEqualTo(6);
            assertThat(result.get("day")).isEqualTo(15);
        }

        @Test
        @DisplayName("Should default to tomorrow when startDate is unparseable")
        void shouldDefaultToTomorrow_whenStartDateUnparseable() throws Exception {
            addRequestParameter("providerNos", TEST_PROVIDER);
            addRequestParameter("startDate", "not-a-date");
            setTimecodeForAllDays(allOpenTimecode());

            Map<String, Object> result = executeAndParseJson();

            assertThat(result.get("found")).isEqualTo(true);
            // Should be tomorrow's date
            Calendar tomorrow = new GregorianCalendar();
            tomorrow.add(Calendar.DATE, 1);
            assertThat(result.get("year")).isEqualTo(tomorrow.get(Calendar.YEAR));
            assertThat(result.get("month")).isEqualTo(tomorrow.get(Calendar.MONTH) + 1);
            assertThat(result.get("day")).isEqualTo(tomorrow.get(Calendar.DAY_OF_MONTH));
        }

        @Test
        @DisplayName("Should default to tomorrow when startDate is omitted")
        void shouldDefaultToTomorrow_whenStartDateOmitted() throws Exception {
            addRequestParameter("providerNos", TEST_PROVIDER);
            setTimecodeForAllDays(allOpenTimecode());

            Map<String, Object> result = executeAndParseJson();

            assertThat(result.get("found")).isEqualTo(true);
            Calendar tomorrow = new GregorianCalendar();
            tomorrow.add(Calendar.DATE, 1);
            assertThat(result.get("year")).isEqualTo(tomorrow.get(Calendar.YEAR));
            assertThat(result.get("month")).isEqualTo(tomorrow.get(Calendar.MONTH) + 1);
            assertThat(result.get("day")).isEqualTo(tomorrow.get(Calendar.DAY_OF_MONTH));
        }
    }

    // ── Slot Finding Algorithm ───────────────────────────────

    @Nested
    @DisplayName("Slot Finding")
    class SlotFinding {

        @Test
        @DisplayName("Should return first available slot (TARGET_SLOT_ORDINAL=1)")
        void shouldReturnFirstSlot_whenMultipleSlotsAvailable() throws Exception {
            addRequestParameter("providerNos", TEST_PROVIDER);
            // 96 slots of 15 min each, all open
            setTimecodeForAllDays(allOpenTimecode());

            Map<String, Object> result = executeAndParseJson();

            assertThat(result.get("found")).isEqualTo(true);
            // 1st slot at index 0 → 0 * 15min = 0min = 00:00
            assertThat(result.get("startTime")).isEqualTo("00:00");
            assertThat(result.get("duration")).isEqualTo(15);
            assertThat(result.get("providerNo")).isEqualTo(TEST_PROVIDER);
        }

        @Test
        @DisplayName("Should skip booked slots and find next available")
        void shouldSkipBookedSlots_whenSomeSlotsOccupied() throws Exception {
            addRequestParameter("providerNos", TEST_PROVIDER);
            setTimecodeForAllDays(allOpenTimecode());

            // Book the first 6 slots (00:00 - 01:30), so slots 0-5 are occupied
            Appointment booked = createAppointment(0, 0, 1, 30);
            when(mockAppointmentDao.findByProviderAndDayandNotStatuses(
                    eq(TEST_PROVIDER), any(Date.class), any(String[].class)))
                    .thenReturn(List.of(booked));

            Map<String, Object> result = executeAndParseJson();

            assertThat(result.get("found")).isEqualTo(true);
            // Booking 00:00-01:30 occupies slots 0-6, first open at index 7 (01:45)
            assertThat(result.get("startTime")).isEqualTo("01:45");
        }

        @Test
        @DisplayName("Should return found=false with lookaheadDays when no template exists for provider")
        void shouldReturnNotFound_whenNoScheduleTemplate() throws Exception {
            addRequestParameter("providerNos", TEST_PROVIDER);
            when(mockScheduleTemplateDao.findTimeCodeByProviderNo2(anyString(), any(Date.class)))
                    .thenReturn(Collections.emptyList());

            Map<String, Object> result = executeAndParseJson();

            assertThat(result.get("found")).isEqualTo(false);
            assertThat(result.get("lookaheadDays")).isEqualTo(90);
        }

        @Test
        @DisplayName("Should return found=false with lookaheadDays when all slots closed for 90 days")
        void shouldReturnNotFound_whenAllSlotsClosed() throws Exception {
            addRequestParameter("providerNos", TEST_PROVIDER);
            // Only underscore (closed) slots
            setTimecodeForAllDays("_".repeat(96));

            Map<String, Object> result = executeAndParseJson();

            assertThat(result.get("found")).isEqualTo(false);
            assertThat(result.get("lookaheadDays")).isEqualTo(90);
        }

        @Test
        @DisplayName("Should search across multiple providers")
        void shouldSearchMultipleProviders_whenCommaDelimited() throws Exception {
            String provider2 = "999997";
            addRequestParameter("providerNos", TEST_PROVIDER + "," + provider2);

            // Provider 1 has no schedule, provider 2 has open slots
            when(mockScheduleTemplateDao.findTimeCodeByProviderNo2(eq(TEST_PROVIDER), any(Date.class)))
                    .thenReturn(Collections.emptyList());
            when(mockScheduleTemplateDao.findTimeCodeByProviderNo2(eq(provider2), any(Date.class)))
                    .thenReturn(List.of((Object) allOpenTimecode()));

            Map<String, Object> result = executeAndParseJson();

            assertThat(result.get("found")).isEqualTo(true);
            assertThat(result.get("providerNo")).isEqualTo(provider2);
        }

        @Test
        @DisplayName("Should skip empty provider numbers in comma-separated list")
        void shouldSkipEmptyProviders_whenExtraCommas() throws Exception {
            addRequestParameter("providerNos", TEST_PROVIDER + ",,  ,");
            setTimecodeForAllDays(allOpenTimecode());

            Map<String, Object> result = executeAndParseJson();

            assertThat(result.get("found")).isEqualTo(true);
            assertThat(result.get("providerNo")).isEqualTo(TEST_PROVIDER);
        }

        @Test
        @DisplayName("Should find slot on day 2 when day 1 is fully booked")
        void shouldFindSlotOnDay2_whenDay1FullyBooked() throws Exception {
            addRequestParameter("providerNos", TEST_PROVIDER);
            addRequestParameter("startDate", "2026-07-01");

            // Day 1: all closed, Day 2+: all open
            when(mockScheduleTemplateDao.findTimeCodeByProviderNo2(eq(TEST_PROVIDER), any(Date.class)))
                    .thenAnswer(invocation -> {
                        Date date = invocation.getArgument(1);
                        Calendar cal = new GregorianCalendar();
                        cal.setTime(date);
                        if (cal.get(Calendar.DAY_OF_MONTH) == 1) {
                            return List.of((Object) "_".repeat(96));
                        }
                        return List.of((Object) allOpenTimecode());
                    });

            Map<String, Object> result = executeAndParseJson();

            assertThat(result.get("found")).isEqualTo(true);
            assertThat(result.get("day")).isEqualTo(2);
        }

        @Test
        @DisplayName("Should handle malformed appointment times gracefully")
        void shouldSkipMalformedTimes_whenAppointmentTimesInvalid() throws Exception {
            addRequestParameter("providerNos", TEST_PROVIDER);
            setTimecodeForAllDays(allOpenTimecode());

            // Appointment with null times
            Appointment badAppt = new Appointment();
            badAppt.setStartTime(null);
            badAppt.setEndTime(null);
            when(mockAppointmentDao.findByProviderAndDayandNotStatuses(anyString(), any(Date.class), any(String[].class)))
                    .thenReturn(List.of(badAppt));

            Map<String, Object> result = executeAndParseJson();

            // Should still find slots (malformed appointments are skipped)
            assertThat(result.get("found")).isEqualTo(true);
        }
    }

    // ── Error Recovery ───────────────────────────────────────

    @Nested
    @DisplayName("Error Recovery")
    class ErrorRecovery {

        @Test
        @DisplayName("Should return error after 5 consecutive DAO failures")
        void shouldReturnError_whenFiveConsecutiveDaoFailures() throws Exception {
            addRequestParameter("providerNos", TEST_PROVIDER);
            when(mockScheduleTemplateDao.findTimeCodeByProviderNo2(anyString(), any(Date.class)))
                    .thenThrow(new RuntimeException("Database connection lost"));

            Map<String, Object> result = executeAndParseJson();

            assertThat(result.get("found")).isEqualTo(false);
            assertThat(result.get("error")).asString().contains("Scheduling system error");
        }

        @Test
        @DisplayName("Should recover after transient errors and continue searching")
        void shouldRecover_afterTransientErrors() throws Exception {
            addRequestParameter("providerNos", TEST_PROVIDER);
            addRequestParameter("startDate", "2026-08-01");

            // Fail for first 3 days, then succeed
            when(mockScheduleTemplateDao.findTimeCodeByProviderNo2(eq(TEST_PROVIDER), any(Date.class)))
                    .thenThrow(new RuntimeException("Transient error"))
                    .thenThrow(new RuntimeException("Transient error"))
                    .thenThrow(new RuntimeException("Transient error"))
                    .thenReturn(List.of((Object) allOpenTimecode()));

            Map<String, Object> result = executeAndParseJson();

            assertThat(result.get("found")).isEqualTo(true);
            // Should be day 4 (Aug 4) since first 3 days errored
            assertThat(result.get("day")).isEqualTo(4);
        }
    }

    // ── JSON Response ────────────────────────────────────────

    @Nested
    @DisplayName("JSON Response")
    class JsonResponse {

        @Test
        @DisplayName("Should return correct JSON structure on success")
        void shouldReturnCorrectJsonStructure_onSuccess() throws Exception {
            addRequestParameter("providerNos", TEST_PROVIDER);
            addRequestParameter("startDate", "2026-04-10");
            setTimecodeForAllDays(allOpenTimecode());

            Map<String, Object> result = executeAndParseJson();

            assertThat(result).containsKeys("found", "year", "month", "day", "providerNo", "startTime", "duration");
            assertThat(result.get("found")).isEqualTo(true);
            assertThat(result.get("year")).isEqualTo(2026);
            assertThat(result.get("month")).isEqualTo(4);
            assertThat(result.get("day")).isEqualTo(10);
            assertThat(result.get("providerNo")).isEqualTo(TEST_PROVIDER);
        }

        @Test
        @DisplayName("Should set application/json content type")
        void shouldSetJsonContentType() throws Exception {
            addRequestParameter("providerNos", TEST_PROVIDER);
            setTimecodeForAllDays(allOpenTimecode());

            executeAction(action);

            assertThat(mockResponse.getContentType()).startsWith("application/json");
        }
    }

    // ── timeStrToMins (via reflection for private method) ────

    @Nested
    @DisplayName("Time String Parsing")
    class TimeStringParsing {

        private int invokeTimeStrToMins(String timeStr) throws Exception {
            java.lang.reflect.Method method = FindNextAvailableSlot2Action.class
                    .getDeclaredMethod("timeStrToMins", String.class);
            method.setAccessible(true);
            return (int) method.invoke(action, timeStr);
        }

        @Test
        @DisplayName("Should parse HH:mm format correctly")
        void shouldParse_whenHHmmFormat() throws Exception {
            assertThat(invokeTimeStrToMins("09:30")).isEqualTo(570);
            assertThat(invokeTimeStrToMins("00:00")).isEqualTo(0);
            assertThat(invokeTimeStrToMins("23:59")).isEqualTo(1439);
        }

        @Test
        @DisplayName("Should parse HH:mm:ss format correctly")
        void shouldParse_whenHHmmssFormat() throws Exception {
            assertThat(invokeTimeStrToMins("09:30:00")).isEqualTo(570);
            assertThat(invokeTimeStrToMins("14:45:30")).isEqualTo(885);
        }

        @Test
        @DisplayName("Should return -1 for null input")
        void shouldReturnNegativeOne_whenNull() throws Exception {
            assertThat(invokeTimeStrToMins(null)).isEqualTo(-1);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "  ", "abc", "12", "25:00", "23:60", "ab:cd"})
        @DisplayName("Should return -1 for invalid time strings")
        void shouldReturnNegativeOne_whenInvalidFormat(String input) throws Exception {
            assertThat(invokeTimeStrToMins(input)).isEqualTo(-1);
        }

        @Test
        @DisplayName("Should handle single-digit hours and minutes")
        void shouldParse_whenSingleDigitValues() throws Exception {
            assertThat(invokeTimeStrToMins("9:5")).isEqualTo(545);
        }
    }
}
