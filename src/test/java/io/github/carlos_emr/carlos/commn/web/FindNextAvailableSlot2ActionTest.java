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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.appointment.web.NextAppointmentSearchHelper;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.ScheduleDateDao;
import io.github.carlos_emr.carlos.commn.dao.ScheduleTemplateCodeDao;
import io.github.carlos_emr.carlos.commn.dao.ScheduleTemplateDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.ScheduleDate;
import io.github.carlos_emr.carlos.commn.model.ScheduleTemplate;
import io.github.carlos_emr.carlos.commn.model.ScheduleTemplateCode;
import io.github.carlos_emr.carlos.commn.model.ScheduleTemplatePrimaryKey;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises the real shared schedule search through the quick-search JSON action. */
@Isolated // The shared helper caches DAO references and the ordinal is a global property.
@DisplayName("FindNextAvailableSlot2Action shared schedule search")
@Tag("integration")
@Tag("appointment")
class FindNextAvailableSlot2ActionTest extends CarlosWebTestBase {
    private static final String PROVIDER = "999998";
    private final ScheduleDateDao schedules = mock(ScheduleDateDao.class);
    private final ScheduleTemplateDao templates = mock(ScheduleTemplateDao.class);
    private final ScheduleTemplateCodeDao codes = mock(ScheduleTemplateCodeDao.class);
    private final OscarAppointmentDao appointments = mock(OscarAppointmentDao.class);
    private final Map<Field, Object> originalDaos = new HashMap<>();
    private FindNextAvailableSlot2Action action;
    private LocalDate scheduledDay;
    private ScheduleTemplate template;
    private String previousOrdinal;

    @BeforeEach
    void setUpSearch() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_appointment"), eq("r"), isNull())).thenReturn(true);
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        setSessionAttribute(LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY", mockLoggedInInfo);
        previousOrdinal = CarlosProperties.getInstance().getProperty("TARGET_SLOT_ORDINAL");
        CarlosProperties.getInstance().setProperty("TARGET_SLOT_ORDINAL", "1");
        // Replace and restore the helper's actual collaborators: the old action-local
        // timecode/time parsing algorithm no longer exists.
        replaceHelperDao("providerDao", mock(ProviderDao.class));
        replaceHelperDao("scheduleDateDao", schedules);
        replaceHelperDao("scheduleTemplateDao", templates);
        replaceHelperDao("scheduleTemplateCodeDao", codes);
        replaceHelperDao("oscarAppointmentDao", appointments);
        scheduledDay = LocalDate.now().plusDays(2);
        ScheduleDate schedule = new ScheduleDate();
        schedule.setHour("fixture");
        when(schedules.findByProviderNoAndDate(anyString(), any(Date.class))).thenAnswer(call ->
                toLocalDate(call.getArgument(1)).equals(scheduledDay) ? schedule : null);
        template = new ScheduleTemplate();
        template.setTimecode("_".repeat(36) + "A".repeat(4) + "_".repeat(56)); // 09:00..09:45
        when(templates.find(any(ScheduleTemplatePrimaryKey.class))).thenReturn(template);
        ScheduleTemplateCode code = new ScheduleTemplateCode();
        code.setCode('A');
        code.setDuration("15");
        when(codes.getByCode('A')).thenReturn(code);
        when(appointments.getByProviderAndDay(any(Date.class), anyString())).thenReturn(List.of());
        action = new FindNextAvailableSlot2Action();
        addRequestParameter("providerNos", PROVIDER);
    }

    private void replaceHelperDao(String name, Object value) throws Exception {
        Field field = NextAppointmentSearchHelper.class.getDeclaredField(name);
        field.setAccessible(true);
        originalDaos.put(field, field.get(null));
        field.set(null, value);
    }

    @AfterEach
    void restoreSharedState() throws Exception {
        for (var entry : originalDaos.entrySet()) entry.getKey().set(null, entry.getValue());
        if (previousOrdinal == null) CarlosProperties.getInstance().remove("TARGET_SLOT_ORDINAL");
        else CarlosProperties.getInstance().setProperty("TARGET_SLOT_ORDINAL", previousOrdinal);
    }

    private static LocalDate toLocalDate(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private Map<String, Object> search() throws Exception {
        executeAction(action);
        assertThat(mockResponse.getContentType()).startsWith("application/json");
        return new ObjectMapper().readValue(mockResponse.getContentAsString(), new TypeReference<>() { });
    }

    @Test
    void shouldRejectSearch_whenAppointmentReadDenied() {
        denyPrivilege("_appointment", "r");
        assertThatThrownBy(() -> executeAction(action)).isInstanceOf(SecurityException.class)
                .hasMessageContaining("_appointment");
        verifyNoInteractions(schedules, templates, appointments);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void shouldReturnInputError_whenProviderIsMissing(String provider) throws Exception {
        mockRequest.removeParameter("providerNos");
        if (provider != null) mockRequest.setParameter("providerNos", provider);
        assertThat(search()).containsEntry("found", false)
                .containsEntry("error", "providerNos parameter is required");
        verifyNoInteractions(schedules);
    }

    @Test
    void shouldReturnFirstSlotWithDateAndDuration_whenScheduleIsAvailable() throws Exception {
        assertThat(search()).containsEntry("found", true).containsEntry("providerNo", PROVIDER)
                .containsEntry("year", scheduledDay.getYear()).containsEntry("month", scheduledDay.getMonthValue())
                .containsEntry("day", scheduledDay.getDayOfMonth()).containsEntry("startTime", "09:00")
                .containsEntry("duration", 15);
    }

    @Test
    void shouldReturnConfiguredOrdinal_whenSeveralSlotsAreAvailable() throws Exception {
        CarlosProperties.getInstance().setProperty("TARGET_SLOT_ORDINAL", "3");
        assertThat(search()).containsEntry("startTime", "09:30");
    }

    @Test
    void shouldReturnLastAvailableSlot_whenFewerThanRequestedExist() throws Exception {
        CarlosProperties.getInstance().setProperty("TARGET_SLOT_ORDINAL", "5");
        assertThat(search()).containsEntry("startTime", "09:45");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"bad", "0", "-1", "999999999999999999"})
    void shouldUseThirdSlot_whenOrdinalConfigurationIsInvalid(String ordinal) throws Exception {
        if (ordinal == null) CarlosProperties.getInstance().remove("TARGET_SLOT_ORDINAL");
        else CarlosProperties.getInstance().setProperty("TARGET_SLOT_ORDINAL", ordinal);
        assertThat(search()).containsEntry("startTime", "09:30");
    }

    @Test
    void shouldSkipOverlappingSlots_whenAnAppointmentIsBooked() throws Exception {
        Appointment booked = new Appointment();
        booked.setStartTime(Date.from(scheduledDay.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant()));
        booked.setEndTime(Date.from(scheduledDay.atTime(9, 29).atZone(ZoneId.systemDefault()).toInstant()));
        when(appointments.getByProviderAndDay(any(Date.class), eq(PROVIDER))).thenReturn(List.of(booked));
        assertThat(search()).containsEntry("startTime", "09:30");
    }

    @Test
    void shouldReturnNotFound_whenNoScheduleExists() throws Exception {
        when(schedules.findByProviderNoAndDate(anyString(), any(Date.class))).thenReturn(null);
        assertThat(search()).containsEntry("found", false)
                .containsEntry("lookaheadDays", NextAppointmentSearchHelper.MAX_DAYS_TO_SEARCH);
        verifyNoInteractions(appointments);
    }

    @Test
    void shouldReturnNotFound_whenScheduleTemplateIsMissing() throws Exception {
        when(templates.find(any(ScheduleTemplatePrimaryKey.class))).thenReturn(null);
        assertThat(search()).containsEntry("found", false);
        verifyNoInteractions(appointments);
    }

    @Test
    void shouldReturnNotFound_whenAllTemplateSlotsAreClosed() throws Exception {
        template.setTimecode("_".repeat(96));
        assertThat(search()).containsEntry("found", false);
    }

    @Test
    void shouldChooseEarlierProviderGlobally_whenInputOrderIsReversed() throws Exception {
        addRequestParameter("providerNos", PROVIDER + ",999997");
        ScheduleTemplate earlier = new ScheduleTemplate();
        earlier.setTimecode("_".repeat(32) + "A" + "_".repeat(63));
        when(templates.find(new ScheduleTemplatePrimaryKey("999997", "fixture"))).thenReturn(earlier);
        assertThat(search()).containsEntry("providerNo", "999997").containsEntry("startTime", "08:00");
    }

    @Test
    void shouldCountEachSlotOnce_whenProviderListContainsDuplicatesAndBlanks() throws Exception {
        CarlosProperties.getInstance().setProperty("TARGET_SLOT_ORDINAL", "3");
        addRequestParameter("providerNos", PROVIDER + ",, " + PROVIDER + ", ,");
        assertThat(search()).containsEntry("startTime", "09:30");
        verify(appointments, times(1)).getByProviderAndDay(any(Date.class), eq(PROVIDER));
    }

    @Test
    void shouldPropagateDatabaseFailure_whenScheduleLookupFails() throws Exception {
        IllegalStateException outage = new IllegalStateException("Synthetic schedule outage");
        when(schedules.findByProviderNoAndDate(anyString(), any(Date.class))).thenThrow(outage);
        assertThatThrownBy(this::search).isSameAs(outage);
        assertThat(mockResponse.getContentAsString()).isEmpty();
    }

    @Test
    void shouldPropagateDatabaseFailure_whenBookingLookupFails() throws Exception {
        IllegalStateException outage = new IllegalStateException("Synthetic booking outage");
        when(appointments.getByProviderAndDay(any(Date.class), anyString())).thenThrow(outage);
        assertThatThrownBy(this::search).isSameAs(outage);
        assertThat(mockResponse.getContentAsString()).isEmpty();
    }
}
