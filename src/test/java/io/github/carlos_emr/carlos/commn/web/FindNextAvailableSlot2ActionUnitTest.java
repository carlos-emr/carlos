/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */
package io.github.carlos_emr.carlos.commn.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.appointment.web.NextAppointmentSearchBean;
import io.github.carlos_emr.carlos.commn.dao.ScheduleDateDao;
import io.github.carlos_emr.carlos.commn.dao.ScheduleTemplateDao;
import io.github.carlos_emr.carlos.commn.dao.ScheduleTemplateCodeDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.appointment.web.NextAppointmentSearchHelper;
import io.github.carlos_emr.carlos.appointment.web.NextAppointmentSearchResult;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * Behaviour coverage for the appointment slot search endpoint.
 *
 * <p><b>Rewritten 2026-08-20.</b> The previous version of this file was written against an
 * implementation that scanned appointments day by day inside the action, and it reached in
 * through reflection to exercise private helpers such as {@code timeStrToMins}. The action has
 * since been rewritten to delegate to {@link NextAppointmentSearchHelper}, so those helpers no
 * longer exist and 24 of the old assertions failed with {@code NoSuchMethodException}. Because
 * the class was named {@code *Test}, Surefire never selected it and the drift was invisible.</p>
 *
 * <p>The suite is deliberately smaller than the one it replaces. It covers what this action still
 * decides for itself -- authorization, parameter handling, provider fan-out, ordinal selection and
 * response shape -- and does not restate behaviour that now belongs to the search helper and is
 * that class's to test.</p>
 */
@DisplayName("FindNextAvailableSlot2Action")
@Tag("unit")
@Tag("appointment")
class FindNextAvailableSlot2ActionUnitTest extends CarlosUnitTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MockedStatic<ServletActionContext> servletActionContext;
    private MockedStatic<NextAppointmentSearchHelper> searchHelper;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;
    private FindNextAvailableSlot2Action action;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        securityInfoManager = mock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_appointment"), eq("r"), isNull()))
                .thenReturn(true);

        servletActionContext = mockStatic(ServletActionContext.class);
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

        // Registered before the static mock: instrumenting NextAppointmentSearchHelper runs its
        // static initialiser, which resolves ProviderDao from SpringUtils. Mockito cannot
        // instrument a class whose initialisation throws.
        registerMock(ProviderDao.class, mock(ProviderDao.class));
        registerMock(ScheduleDateDao.class, mock(ScheduleDateDao.class));
        registerMock(ScheduleTemplateDao.class, mock(ScheduleTemplateDao.class));
        registerMock(ScheduleTemplateCodeDao.class, mock(ScheduleTemplateCodeDao.class));
        registerMock(OscarAppointmentDao.class, mock(OscarAppointmentDao.class));

        searchHelper = mockStatic(NextAppointmentSearchHelper.class);
        searchHelper.when(() -> NextAppointmentSearchHelper.search(any())).thenReturn(List.of());

        action = new FindNextAvailableSlot2Action();
    }

    @AfterEach
    void tearDown() {
        if (searchHelper != null) {
            searchHelper.close();
        }
        if (servletActionContext != null) {
            servletActionContext.close();
        }
    }

    @Test
    @DisplayName("should refuse the search without appointment read privilege")
    void shouldRefuseSearch_withoutAppointmentReadPrivilege() throws Exception {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_appointment"), eq("r"), isNull()))
                .thenReturn(false);
        request.setParameter("providerNos", "999998");

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_appointment)");

        // Refused before any schedule is read: the privilege check is the first thing execute does.
        searchHelper.verify(() -> NextAppointmentSearchHelper.search(any()), never());
    }

    @Test
    @DisplayName("should report an error when providerNos is missing")
    void shouldReportError_whenProviderNosMissing() throws Exception {
        action.execute();

        JsonNode json = MAPPER.readTree(response.getContentAsString());
        assertThat(json.get("found").asBoolean()).isFalse();
        assertThat(json.get("error").asText()).isEqualTo("providerNos parameter is required");
        searchHelper.verify(() -> NextAppointmentSearchHelper.search(any()), never());
    }

    @Test
    @DisplayName("should report an error when providerNos is blank")
    void shouldReportError_whenProviderNosBlank() throws Exception {
        // trimToNull, so whitespace is the same as absent rather than a search for one blank
        // provider -- worth pinning, because the split() below would otherwise yield one empty id.
        request.setParameter("providerNos", "   ");

        action.execute();

        assertThat(MAPPER.readTree(response.getContentAsString()).get("found").asBoolean()).isFalse();
        searchHelper.verify(() -> NextAppointmentSearchHelper.search(any()), never());
    }

    @Test
    @DisplayName("should report lookahead days when no slot is found")
    void shouldReportLookaheadDays_whenNoSlotFound() throws Exception {
        request.setParameter("providerNos", "999998");

        action.execute();

        JsonNode json = MAPPER.readTree(response.getContentAsString());
        assertThat(json.get("found").asBoolean()).isFalse();
        assertThat(json.get("lookaheadDays").asInt()).isEqualTo(NextAppointmentSearchHelper.MAX_DAYS_TO_SEARCH);
    }

    @Test
    @DisplayName("should search each provider when providerNos is comma delimited")
    void shouldSearchEachProvider_whenProviderNosCommaDelimited() throws Exception {
        request.setParameter("providerNos", "111,222,333");

        action.execute();

        ArgumentCaptor<NextAppointmentSearchBean> searchCaptor =
                ArgumentCaptor.forClass(NextAppointmentSearchBean.class);
        searchHelper.verify(() -> NextAppointmentSearchHelper.search(searchCaptor.capture()), times(3));

        assertThat(searchCaptor.getAllValues())
                .extracting(NextAppointmentSearchBean::getProviderNo)
                .containsExactly("111", "222", "333");
        assertThat(searchCaptor.getAllValues()).allSatisfy(searchBean -> {
            assertThat(searchBean.getDayOfWeek()).isEmpty();
            assertThat(searchBean.getStartTimeOfDay()).isEqualTo("0");
            assertThat(searchBean.getEndTimeOfDay()).isEqualTo("24");
            assertThat(searchBean.getCode()).isEmpty();
            assertThat(searchBean.getNumResults()).isEqualTo(3);
        });
    }

    @Test
    @DisplayName("should skip empty provider ids from stray commas")
    void shouldSkipEmptyProviderIds_fromStrayCommas() throws Exception {
        request.setParameter("providerNos", "111,,222,");

        action.execute();

        ArgumentCaptor<NextAppointmentSearchBean> searchCaptor =
                ArgumentCaptor.forClass(NextAppointmentSearchBean.class);
        searchHelper.verify(() -> NextAppointmentSearchHelper.search(searchCaptor.capture()), times(2));
        assertThat(searchCaptor.getAllValues())
                .extracting(NextAppointmentSearchBean::getProviderNo)
                .containsExactly("111", "222");
    }

    @Test
    @DisplayName("should apply the target ordinal to results sorted across all providers")
    void shouldApplyTargetOrdinal_toResultsSortedAcrossAllProviders() throws Exception {
        // Two things at once, because they are only correct together: results from every provider
        // are pooled and sorted by date before the ordinal is applied, so the Nth slot is the Nth
        // globally rather than the Nth from whichever provider happened to be searched first.
        // Supplied deliberately out of order; with the default ordinal of 3 the third-earliest wins.
        Date first = dateAt(2026, Calendar.SEPTEMBER, 1, 9, 15);
        Date second = dateAt(2026, Calendar.SEPTEMBER, 3, 10, 0);
        Date third = dateAt(2026, Calendar.SEPTEMBER, 7, 14, 30);
        Date fourth = dateAt(2026, Calendar.SEPTEMBER, 9, 8, 45);
        searchHelper.when(() -> NextAppointmentSearchHelper.search(any()))
                .thenAnswer(invocation -> {
                    NextAppointmentSearchBean searchBean = invocation.getArgument(0);
                    return switch (searchBean.getProviderNo()) {
                        case "111" -> List.of(slot("111", fourth, 15), slot("111", first, 15));
                        case "222" -> List.of(slot("222", third, 20));
                        case "333" -> List.of(slot("333", second, 10));
                        default -> List.of();
                    };
                });
        request.setParameter("providerNos", "111,222,333");

        action.execute();

        JsonNode json = MAPPER.readTree(response.getContentAsString());
        assertThat(json.get("found").asBoolean()).isTrue();
        assertThat(json.get("year").asInt()).isEqualTo(2026);
        assertThat(json.get("month").asInt()).isEqualTo(9);
        assertThat(json.get("day").asInt()).isEqualTo(7);
        assertThat(json.get("providerNo").asText()).isEqualTo("222");
        assertThat(json.get("startTime").asText()).isEqualTo("14:30");
        assertThat(json.get("duration").asInt()).isEqualTo(20);
    }

    @Test
    @DisplayName("should fall back to the last slot when fewer exist than the target ordinal")
    void shouldFallBackToLastSlot_whenFewerExistThanTargetOrdinal() throws Exception {
        // Default ordinal is 3. With two results the action must not index past the end; it
        // returns the last one rather than failing the request.
        Date first = dateAt(2026, Calendar.SEPTEMBER, 1, 9, 0);
        Date second = dateAt(2026, Calendar.SEPTEMBER, 2, 9, 0);
        searchHelper.when(() -> NextAppointmentSearchHelper.search(any()))
                .thenReturn(List.of(slot("111", first, 15), slot("111", second, 15)));
        request.setParameter("providerNos", "111");

        action.execute();

        JsonNode json = MAPPER.readTree(response.getContentAsString());
        assertThat(json.get("found").asBoolean()).isTrue();
        assertThat(json.get("day").asInt()).isEqualTo(2);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "0", "-1", "not-a-number"})
    @DisplayName("should default the target slot ordinal when the property is absent or invalid")
    void shouldDefaultTargetSlotOrdinal_whenPropertyAbsentOrInvalid(String configuredValue) {
        assertTargetSlotOrdinal(configuredValue, 3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", " 4 "})
    @DisplayName("should use a positive configured target slot ordinal")
    void shouldUseTargetSlotOrdinal_whenPositiveValueConfigured(String configuredValue) {
        assertTargetSlotOrdinal(configuredValue, Integer.parseInt(configuredValue.trim()));
    }

    @Test
    @DisplayName("should respond as JSON")
    void shouldRespondAsJson_whenSearchCompletes() throws Exception {
        request.setParameter("providerNos", "999998");

        action.execute();

        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getCharacterEncoding()).isEqualToIgnoringCase("UTF-8");
    }

    private NextAppointmentSearchResult slot(String providerNo, Date date, int duration) {
        NextAppointmentSearchResult result = new NextAppointmentSearchResult();
        result.setProviderNo(providerNo);
        result.setDate(date);
        result.setDuration(duration);
        return result;
    }

    private void assertTargetSlotOrdinal(String configuredValue, int expected) {
        CarlosProperties properties = CarlosProperties.getInstance();
        Object originalValue = properties.get("TARGET_SLOT_ORDINAL");
        try {
            if (configuredValue == null) {
                properties.remove("TARGET_SLOT_ORDINAL");
            } else {
                properties.setProperty("TARGET_SLOT_ORDINAL", configuredValue);
            }
            assertThat(FindNextAvailableSlot2Action.resolveTargetSlotOrdinal()).isEqualTo(expected);
        } finally {
            if (originalValue == null) {
                properties.remove("TARGET_SLOT_ORDINAL");
            } else {
                properties.put("TARGET_SLOT_ORDINAL", originalValue);
            }
        }
    }

    private Date dateAt(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month, day, hour, minute, 0);
        return calendar.getTime();
    }
}
