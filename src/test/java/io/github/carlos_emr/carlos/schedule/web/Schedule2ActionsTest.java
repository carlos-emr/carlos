/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.schedule.web;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Suite for the 11 schedule 2Action gate classes introduced by PR #1619.
 *
 * <p>Each nested class covers one action with at minimum:
 * <ul>
 *   <li>privilege-deny → SecurityException</li>
 *   <li>privilege-allow → SUCCESS</li>
 *   <li>mutation-on-GET (where applicable) → 405 + NONE</li>
 * </ul>
 *
 * @since 2026-04-12
 */
@DisplayName("Schedule 2Action gate tests")
@Tag("unit")
@Tag("web")
@Tag("schedule")
class Schedule2ActionsTest extends CarlosWebTestBase {

    @BeforeEach
    void initMocks() {
        MockitoAnnotations.openMocks(this);
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);

        String key = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(key, mockLoggedInInfo);
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
    }

    private <T extends ActionSupport> T inject(T action) throws Exception {
        java.lang.reflect.Field f = action.getClass().getDeclaredField("securityInfoManager");
        f.setAccessible(true);
        f.set(action, mockSecurityInfoManager);
        return action;
    }

    private void denyAll() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), any()))
                .thenReturn(false);
    }

    @Nested
    @DisplayName("ScheduleTemplateSetting2Action (_admin.schedule r)")
    class TemplateSetting {
        @Test
        @DisplayName("should throw SecurityException when privilege missing")
        void shouldThrow_whenNoPrivilege() throws Exception {
            denyAll();
            var action = inject(new ScheduleTemplateSetting2Action());
            assertThatThrownBy(() -> executeAction(action))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("_admin.schedule");
        }

        @Test
        @DisplayName("should return SUCCESS when privilege held")
        void shouldReturnSuccess_whenAllowed() throws Exception {
            allowPrivilege("_admin.schedule", "r");
            assertThat(executeAction(inject(new ScheduleTemplateSetting2Action())))
                    .isEqualTo(ActionSupport.SUCCESS);
        }
    }

    @Nested
    @DisplayName("ScheduleEditTemplate2Action (_admin.schedule w, POST for mutations)")
    class EditTemplate {
        @Test
        void shouldThrow_whenNoPrivilege() throws Exception {
            denyAll();
            var action = inject(new ScheduleEditTemplate2Action());
            assertThatThrownBy(() -> executeAction(action))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        void shouldReturnSuccess_onGetWithoutMutation() throws Exception {
            allowPrivilege("_admin.schedule", "w");
            mockRequest.setMethod("GET");
            assertThat(executeAction(inject(new ScheduleEditTemplate2Action())))
                    .isEqualTo(ActionSupport.SUCCESS);
        }

        @Test
        void shouldReturn405_whenSaveOnGet() throws Exception {
            allowPrivilege("_admin.schedule", "w");
            mockRequest.setMethod("GET");
            addRequestParameter("dboperation", "Save");
            String result = executeAction(inject(new ScheduleEditTemplate2Action()));
            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }

        @Test
        void shouldReturnSuccess_whenDeleteOnPost() throws Exception {
            allowPrivilege("_admin.schedule", "w");
            mockRequest.setMethod("POST");
            addRequestParameter("dboperation", "Delete");
            assertThat(executeAction(inject(new ScheduleEditTemplate2Action())))
                    .isEqualTo(ActionSupport.SUCCESS);
        }
    }

    @Nested
    @DisplayName("ScheduleTemplateCodeSetting2Action (_admin.schedule w, POST for Save/Delete)")
    class TemplateCodeSetting {
        @Test
        void shouldThrow_whenNoPrivilege() throws Exception {
            denyAll();
            var action = inject(new ScheduleTemplateCodeSetting2Action());
            assertThatThrownBy(() -> executeAction(action))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        void shouldReturn405_whenDeleteOnGet() throws Exception {
            allowPrivilege("_admin.schedule", "w");
            mockRequest.setMethod("GET");
            addRequestParameter("dboperation", "Delete");
            String result = executeAction(inject(new ScheduleTemplateCodeSetting2Action()));
            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }

        @Test
        void shouldAllowEditOnGet() throws Exception {
            allowPrivilege("_admin.schedule", "w");
            mockRequest.setMethod("GET");
            addRequestParameter("dboperation", "Edit");
            assertThat(executeAction(inject(new ScheduleTemplateCodeSetting2Action())))
                    .isEqualTo(ActionSupport.SUCCESS);
        }

        @Test
        void shouldSucceed_whenSaveOnPost() throws Exception {
            allowPrivilege("_admin.schedule", "w");
            mockRequest.setMethod("POST");
            addRequestParameter("dboperation", "Save");
            assertThat(executeAction(inject(new ScheduleTemplateCodeSetting2Action())))
                    .isEqualTo(ActionSupport.SUCCESS);
        }
    }

    @Nested
    @DisplayName("ScheduleHolidaySetting2Action (_admin.schedule w, POST for Save/Delete only)")
    class HolidaySetting {
        @Test
        void shouldThrow_whenNoPrivilege() throws Exception {
            denyAll();
            assertThatThrownBy(() -> executeAction(inject(new ScheduleHolidaySetting2Action())))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("should allow GET navigation (bFirstDisp=0) even with dboperation unset")
        void shouldAllowGetNavigation() throws Exception {
            allowPrivilege("_admin.schedule", "w");
            mockRequest.setMethod("GET");
            addRequestParameter("bFirstDisp", "0");
            assertThat(executeAction(inject(new ScheduleHolidaySetting2Action())))
                    .isEqualTo(ActionSupport.SUCCESS);
        }

        @Test
        @DisplayName("should 405 on Save via GET (space-padded ' Save ')")
        void shouldReturn405_whenSaveOnGetWithSpaces() throws Exception {
            allowPrivilege("_admin.schedule", "w");
            mockRequest.setMethod("GET");
            addRequestParameter("dboperation", " Save ");
            String result = executeAction(inject(new ScheduleHolidaySetting2Action()));
            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }

        @Test
        @DisplayName("should NOT block unknown dboperation on GET (narrow mutation detection)")
        void shouldAllowUnknownOperationOnGet() throws Exception {
            allowPrivilege("_admin.schedule", "w");
            mockRequest.setMethod("GET");
            addRequestParameter("dboperation", "Edit");
            assertThat(executeAction(inject(new ScheduleHolidaySetting2Action())))
                    .isEqualTo(ActionSupport.SUCCESS);
        }

        @Test
        void shouldSucceed_whenSaveOnPost() throws Exception {
            allowPrivilege("_admin.schedule", "w");
            mockRequest.setMethod("POST");
            addRequestParameter("dboperation", " Save ");
            assertThat(executeAction(inject(new ScheduleHolidaySetting2Action())))
                    .isEqualTo(ActionSupport.SUCCESS);
        }
    }

    @Nested
    @DisplayName("ScheduleDateSave2Action (_appointment w, POST-only)")
    class DateSave {
        @Test
        void shouldThrow_whenNoPrivilege() throws Exception {
            denyAll();
            var action = inject(new ScheduleDateSave2Action());
            assertThatThrownBy(() -> executeAction(action))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        void shouldReturn405_onGet() throws Exception {
            allowPrivilege("_appointment", "w");
            mockRequest.setMethod("GET");
            String result = executeAction(inject(new ScheduleDateSave2Action()));
            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }

        private io.github.carlos_emr.carlos.schedule.service.ScheduleDateOverrideService overrides;

        /** The override the mocked service last committed for the date, as the database would hold it. */
        private final java.util.concurrent.atomic.AtomicReference<io.github.carlos_emr.carlos.commn.model.ScheduleDate> committed =
                new java.util.concurrent.atomic.AtomicReference<>();

        /** An override service whose replacements commit to {@link #committed} and whose reads return it. */
        private io.github.carlos_emr.carlos.schedule.service.ScheduleDateOverrideService committingService() {
            var service = mock(io.github.carlos_emr.carlos.schedule.service.ScheduleDateOverrideService.class);
            when(service.replaceOverride(any(), any(), any())).thenAnswer(call -> {
                committed.set(call.getArgument(2));
                return true;
            });
            when(service.currentOverride(any(), any())).thenAnswer(call -> committed.get());
            return service;
        }

        /** An authorized POST of the popup form, with the override service mocked. */
        private ScheduleDateSave2Action postedSave(String submit) throws Exception {
            return postedSave(submit, "2031-05-14");
        }

        /** An authorized POST of the popup form for the given date, with the override service mocked. */
        private ScheduleDateSave2Action postedSave(String submit, String date) throws Exception {
            allowPrivilege("_appointment", "w");
            mockRequest.setMethod("POST");
            addRequestParameter("provider_no", "999998");
            addRequestParameter("date", date);
            addRequestParameter("Submit", submit);
            // The month view the popup was opened from: this provider's rule schedule and days.
            setSessionAttribute("scheduleRscheduleBean", ruleSchedule("999998", false));
            setSessionAttribute("scheduleDateBeanProvider", "999998");
            overrides = committingService();
            ScheduleDateSave2Action action = inject(new ScheduleDateSave2Action());
            injectField(action, "overrideService", overrides);
            return action;
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = {
                "2031-02-30", "2031-2-29", "2031-13-01", "2031-00-10", "2031-05-00", "14/05/2031", "2031-05-14x"})
        @DisplayName("should refuse a date that is not a calendar day before anything is written (#5297)")
        void shouldRefuseImpossibleDate_beforeAnyWrite(String date) throws Exception {
            ScheduleDateSave2Action action = postedSave("Save", date);
            addRequestParameter("available", "1");
            java.util.Hashtable<String, Object> cache = new java.util.Hashtable<>();
            cache.put(date, "cached");
            setSessionAttribute("scheduleDateBean", cache);

            assertThat(executeAction(action)).isEqualTo(ActionSupport.NONE);

            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            assertThat(mockRequest.getAttribute(io.github.carlos_emr.carlos.utility.RefusalNotice.KEY_ATTRIBUTE))
                    .isEqualTo("error.refusal.scheduleDateInvalid");
            org.mockito.Mockito.verifyNoInteractions(overrides);
            assertThat(cache).containsOnlyKeys(date).containsEntry(date, "cached");
        }

        @Test
        @DisplayName("should read the popup's unpadded month and day (#5297)")
        void shouldReplaceOverride_forTheUnpaddedPopupDate() throws Exception {
            ScheduleDateSave2Action action = postedSave("Save", "2032-2-29");
            addRequestParameter("available", "1");

            assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);

            verify(overrides).replaceOverride(org.mockito.ArgumentMatchers.eq("999998"),
                    org.mockito.ArgumentMatchers.eq(java.sql.Date.valueOf("2032-02-29")), any());
        }

        @Test
        @DisplayName("should refuse, and keep the session cache, when the provider does not exist (#5297)")
        void shouldRefuseAndKeepCache_whenTheProviderDoesNotExist() throws Exception {
            ScheduleDateSave2Action action = postedSave("Save");
            addRequestParameter("available", "1");
            when(overrides.replaceOverride(any(), any(), any())).thenReturn(false);
            java.util.Hashtable<String, Object> cache = new java.util.Hashtable<>();
            cache.put("2031-05-14", "cached");
            setSessionAttribute("scheduleDateBean", cache);

            assertThat(executeAction(action)).isEqualTo(ActionSupport.NONE);

            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            assertThat(mockRequest.getAttribute(io.github.carlos_emr.carlos.utility.RefusalNotice.KEY_ATTRIBUTE))
                    .isEqualTo("error.refusal.scheduleDateIncomplete");
            assertThat(cache).containsEntry("2031-05-14", "cached");
            verify(overrides, never()).currentOverride(any(), any());
        }

        @Test
        @DisplayName("should refresh the calendar's padded key when Save names an unpadded date (#5297)")
        void shouldRefreshThePaddedCalendarKey_onSaveOfAnUnpaddedDate() throws Exception {
            ScheduleDateSave2Action action = postedSave("Save", "2032-2-29");
            addRequestParameter("available", "0");
            addRequestParameter("reason", "Leap day off");
            java.util.Hashtable<String, Object> cache = new java.util.Hashtable<>();
            cache.put("2032-02-29", new io.github.carlos_emr.HScheduleDate("1", "c", "stale", "0800", "FAKE"));
            setSessionAttribute("scheduleDateBean", cache);

            assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);

            // schedulecreatedate.jsp looks the day up as year-MM-dd.
            var shown = (io.github.carlos_emr.HScheduleDate) cache.get("2032-02-29");
            assertThat(shown.reason).isEqualTo("Leap day off");
            assertThat(shown.available).isEqualTo("0");
            assertThat(cache).containsOnlyKeys("2032-02-29");
        }

        @Test
        @DisplayName("should clear the calendar's padded key when Delete names an unpadded date (#5297)")
        void shouldClearThePaddedCalendarKey_onDeleteOfAnUnpaddedDate() throws Exception {
            ScheduleDateSave2Action action = postedSave("Delete", "2032-2-29");
            setSessionAttribute("scheduleRscheduleBean", ruleSchedule(false));
            java.util.Hashtable<String, Object> cache = new java.util.Hashtable<>();
            cache.put("2032-02-29", new io.github.carlos_emr.HScheduleDate("0", "c", "stale", "", "FAKE"));
            setSessionAttribute("scheduleDateBean", cache);

            assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);

            verify(overrides).replaceOverride(org.mockito.ArgumentMatchers.eq("999998"),
                    org.mockito.ArgumentMatchers.eq(java.sql.Date.valueOf("2032-02-29")),
                    org.mockito.ArgumentMatchers.isNull());
            assertThat(cache).isEmpty();
        }

        @Test
        @DisplayName("should show the last committed Save when an earlier Save refreshes the calendar last (#5297)")
        void shouldShowTheLastCommittedOverride_whenAnEarlierSaveRefreshesLast() throws Exception {
            ScheduleDateSave2Action first = postedSave("Save");
            addRequestParameter("available", "1");
            addRequestParameter("reason", "First save");
            java.util.Hashtable<String, Object> cache = new java.util.Hashtable<>();
            setSessionAttribute("scheduleDateBean", cache);
            ScheduleDateSave2Action second = inject(new ScheduleDateSave2Action());
            injectField(second, "overrideService", committingService());
            // The second Save commits and refreshes the calendar after the first has committed but
            // before the first reaches the cache, as two overlapping requests in one session can.
            when(overrides.replaceOverride(any(), any(), any())).thenAnswer(call -> {
                committed.set(call.getArgument(2));
                runOverlappingSave(second, "Second save");
                return true;
            });

            assertThat(executeAction(first)).isEqualTo(ActionSupport.SUCCESS);

            assertThat(committed.get().getReason()).isEqualTo("Second save");
            assertThat(((io.github.carlos_emr.HScheduleDate) cache.get("2031-05-14")).reason).isEqualTo("Second save");
        }

        @Test
        @DisplayName("should read the committed override under the session mutex (#5297)")
        void shouldReadTheCommittedOverride_underTheSessionMutex() throws Exception {
            ScheduleDateSave2Action action = postedSave("Save");
            addRequestParameter("available", "1");
            var lockHeld = new java.util.ArrayList<Boolean>();
            when(overrides.currentOverride(any(), any())).thenAnswer(call -> {
                lockHeld.add(Thread.holdsLock(org.springframework.web.util.WebUtils.getSessionMutex(mockSession)));
                return committed.get();
            });

            assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);

            assertThat(lockHeld).containsExactly(true);
            verify(overrides).currentOverride("999998", java.sql.Date.valueOf("2031-05-14"));
        }

        /** Runs a whole Save for the same session on its own request, then restores the outer request. */
        private void runOverlappingSave(ScheduleDateSave2Action action, String reason) throws Exception {
            var request = new org.springframework.mock.web.MockHttpServletRequest("POST", "/schedule/DateSave");
            request.setSession(mockSession);
            request.setParameter("provider_no", "999998");
            request.setParameter("date", "2031-05-14");
            request.setParameter("Submit", "Save");
            request.setParameter("available", "1");
            request.setParameter("reason", reason);
            org.apache.struts2.ActionContext outer = org.apache.struts2.ActionContext.getContext();
            org.apache.struts2.ActionContext.of()
                    .withServletRequest(request)
                    .withServletResponse(new org.springframework.mock.web.MockHttpServletResponse())
                    .bind();
            try {
                assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
            } finally {
                outer.bind();
            }
        }

        @Test
        @DisplayName("should replace the override and then refresh the session cache on Save (#5297)")
        void shouldReplaceOverride_thenUpdateCache_onSave() throws Exception {
            ScheduleDateSave2Action action = postedSave("Save");
            addRequestParameter("available", "1");
            addRequestParameter("reason", "Half day");
            addRequestParameter("hour", "0800");
            java.util.Hashtable<String, Object> cache = new java.util.Hashtable<>();
            cache.put("2031-05-14", new io.github.carlos_emr.HScheduleDate("0", "c", "stale", "", "FAKE"));
            setSessionAttribute("scheduleDateBean", cache);

            assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);

            var replacement = org.mockito.ArgumentCaptor.forClass(io.github.carlos_emr.carlos.commn.model.ScheduleDate.class);
            verify(overrides).replaceOverride(org.mockito.ArgumentMatchers.eq("999998"),
                    org.mockito.ArgumentMatchers.eq(java.sql.Date.valueOf("2031-05-14")), replacement.capture());
            assertThat(replacement.getValue().getReason()).isEqualTo("Half day");
            assertThat(replacement.getValue().getAvailable()).isEqualTo('1');
            assertThat(replacement.getValue().getPriority()).isEqualTo('c');
            assertThat(((io.github.carlos_emr.HScheduleDate) cache.get("2031-05-14")).reason).isEqualTo("Half day");
        }

        @Test
        @DisplayName("should reject a Save without availability before anything is written (#5297)")
        void shouldRejectSaveWithoutAvailability_beforeAnyWrite() throws Exception {
            ScheduleDateSave2Action action = postedSave("Save");
            java.util.Hashtable<String, Object> cache = new java.util.Hashtable<>();
            cache.put("2031-05-14", "cached");
            setSessionAttribute("scheduleDateBean", cache);

            assertThat(executeAction(action)).isEqualTo(ActionSupport.NONE);

            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            org.mockito.Mockito.verifyNoInteractions(overrides);
            assertThat(cache).containsEntry("2031-05-14", "cached");
        }

        @Test
        @DisplayName("should reject an unnamed submit instead of deleting the override (#5297)")
        void shouldRejectUnnamedSubmit_insteadOfDeleting() throws Exception {
            ScheduleDateSave2Action action = postedSave("");

            assertThat(executeAction(action)).isEqualTo(ActionSupport.NONE);

            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            org.mockito.Mockito.verifyNoInteractions(overrides);
        }

        /** A rule schedule that does or does not cover every date, with fixed hours. */
        private io.github.carlos_emr.RscheduleBean ruleSchedule(boolean coversDate) {
            return ruleSchedule("999998", coversDate);
        }

        /** The given provider's rule schedule, as the month view leaves it in the session. */
        private io.github.carlos_emr.RscheduleBean ruleSchedule(String providerNo, boolean coversDate) {
            io.github.carlos_emr.RscheduleBean rule = new io.github.carlos_emr.RscheduleBean() {
                @Override
                public boolean getDateAvail(String date) {
                    return coversDate;
                }

                @Override
                public String getDateAvailHour(String date) {
                    return "0900-1700";
                }
            };
            rule.provider_no = providerNo;
            return rule;
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.CsvSource({"provider_no, ABSENT", "provider_no, '  '", "date, ABSENT", "date, ''"})
        @DisplayName("should refuse a missing or blank provider or date before anything is written (#5297)")
        void shouldRefuseMissingProviderOrDate_beforeAnyWrite(String parameter, String value) throws Exception {
            ScheduleDateSave2Action action = postedSave("Save");
            addRequestParameter("available", "1");
            if ("ABSENT".equals(value)) {
                mockRequest.removeParameter(parameter);
            } else {
                addRequestParameter(parameter, value);
            }

            assertThat(executeAction(action)).isEqualTo(ActionSupport.NONE);

            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            assertThat(mockRequest.getAttribute(io.github.carlos_emr.carlos.utility.RefusalNotice.KEY_ATTRIBUTE))
                    .isEqualTo("error.refusal.scheduleDateIncomplete");
            org.mockito.Mockito.verifyNoInteractions(overrides);
        }

        @Test
        @DisplayName("should reject a Save with an empty availability before anything is written (#5297)")
        void shouldRejectSaveWithEmptyAvailability_beforeAnyWrite() throws Exception {
            ScheduleDateSave2Action action = postedSave("Save");
            addRequestParameter("available", "");

            assertThat(executeAction(action)).isEqualTo(ActionSupport.NONE);

            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            assertThat(mockRequest.getAttribute(io.github.carlos_emr.carlos.utility.RefusalNotice.KEY_ATTRIBUTE))
                    .isEqualTo("error.refusal.scheduleAvailability");
            org.mockito.Mockito.verifyNoInteractions(overrides);
        }

        @Test
        @DisplayName("should refuse Delete before writing when another provider's calendar is open (#5297)")
        void shouldRefuseDelete_whenAnotherProvidersCalendarIsOpen() throws Exception {
            ScheduleDateSave2Action action = postedSave("Delete");
            // Another tab opened provider 999997's calendar after this popup was drawn.
            setSessionAttribute("scheduleRscheduleBean", ruleSchedule("999997", true));
            setSessionAttribute("scheduleDateBeanProvider", "999997");
            java.util.Hashtable<String, Object> cache = new java.util.Hashtable<>();
            cache.put("2031-05-14", "provider 999997's day");
            setSessionAttribute("scheduleDateBean", cache);

            assertThat(executeAction(action)).isEqualTo(ActionSupport.NONE);

            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_CONFLICT);
            assertThat(mockRequest.getAttribute(io.github.carlos_emr.carlos.utility.RefusalNotice.KEY_ATTRIBUTE))
                    .isEqualTo("error.refusal.scheduleCalendarChanged");
            org.mockito.Mockito.verifyNoInteractions(overrides);
            assertThat(cache).containsOnlyKeys("2031-05-14").containsEntry("2031-05-14", "provider 999997's day");
        }

        @Test
        @DisplayName("should refuse Save before writing when the calendar's days belong to another provider (#5297)")
        void shouldRefuseSave_whenTheCalendarDaysBelongToAnotherProvider() throws Exception {
            ScheduleDateSave2Action action = postedSave("Save");
            addRequestParameter("available", "1");
            setSessionAttribute("scheduleDateBeanProvider", "999997");

            assertThat(executeAction(action)).isEqualTo(ActionSupport.NONE);

            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_CONFLICT);
            org.mockito.Mockito.verifyNoInteractions(overrides);
        }

        @Test
        @DisplayName("should refuse before writing when the session has no calendar open (#5297)")
        void shouldRefuse_whenNoCalendarIsOpen() throws Exception {
            ScheduleDateSave2Action action = postedSave("Save");
            addRequestParameter("available", "1");
            mockSession.removeAttribute("scheduleRscheduleBean");

            assertThat(executeAction(action)).isEqualTo(ActionSupport.NONE);

            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_CONFLICT);
            org.mockito.Mockito.verifyNoInteractions(overrides);
        }

        @Test
        @DisplayName("should acknowledge a committed Save and leave the calendar to reload when the readback fails (#5297)")
        void shouldAcknowledgeTheSave_whenReadingTheChangeBackFails() throws Exception {
            ScheduleDateSave2Action action = postedSave("Save");
            addRequestParameter("available", "1");
            addRequestParameter("reason", "Half day");
            java.util.Hashtable<String, Object> cache = new java.util.Hashtable<>();
            cache.put("2031-05-14", new io.github.carlos_emr.HScheduleDate("0", "c", "before", "", "FAKE"));
            setSessionAttribute("scheduleDateBean", cache);
            when(overrides.currentOverride(any(), any())).thenThrow(new IllegalStateException("synthetic read failure"));

            assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);

            assertThat(committed.get().getReason()).isEqualTo("Half day");
            assertThat(((io.github.carlos_emr.HScheduleDate) cache.get("2031-05-14")).reason).isEqualTo("before");
            assertThat(mockSession.getAttribute("scheduleDateBeanStale")).isEqualTo(Boolean.TRUE);
        }

        @Test
        @DisplayName("should restore the rule schedule's day and clear the cache on Delete (#5297)")
        void shouldReplaceWithRuleSchedule_onDeleteWhenTheRuleCoversTheDate() throws Exception {
            ScheduleDateSave2Action action = postedSave("Delete");
            setSessionAttribute("scheduleRscheduleBean", ruleSchedule(true));
            java.util.Hashtable<String, Object> cache = new java.util.Hashtable<>();
            cache.put("2031-05-14", "cached");
            setSessionAttribute("scheduleDateBean", cache);

            assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);

            var replacement = org.mockito.ArgumentCaptor.forClass(io.github.carlos_emr.carlos.commn.model.ScheduleDate.class);
            verify(overrides).replaceOverride(org.mockito.ArgumentMatchers.eq("999998"),
                    org.mockito.ArgumentMatchers.eq(java.sql.Date.valueOf("2031-05-14")), replacement.capture());
            assertThat(replacement.getValue().getPriority()).isEqualTo('b');
            assertThat(replacement.getValue().getAvailable()).isEqualTo('1');
            assertThat(replacement.getValue().getHour()).isEqualTo("0900-1700");
            assertThat(cache).doesNotContainKey("2031-05-14");
        }

        @Test
        @DisplayName("should leave the date without an override and clear the cache on Delete with no rule day (#5297)")
        void shouldRetireOverrideWithoutReplacement_onDeleteWhenNoRuleCoversTheDate() throws Exception {
            ScheduleDateSave2Action action = postedSave("Delete");
            setSessionAttribute("scheduleRscheduleBean", ruleSchedule(false));
            java.util.Hashtable<String, Object> cache = new java.util.Hashtable<>();
            cache.put("2031-05-14", "cached");
            setSessionAttribute("scheduleDateBean", cache);

            assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);

            verify(overrides).replaceOverride(org.mockito.ArgumentMatchers.eq("999998"),
                    org.mockito.ArgumentMatchers.eq(java.sql.Date.valueOf("2031-05-14")),
                    org.mockito.ArgumentMatchers.isNull());
            assertThat(cache).doesNotContainKey("2031-05-14");
        }

        @Test
        @DisplayName("should keep the session cache when the replacement fails (#5297)")
        void shouldKeepSessionCache_whenReplacementFails() throws Exception {
            ScheduleDateSave2Action action = postedSave("Delete");
            doThrow(new IllegalStateException("synthetic failure")).when(overrides)
                    .replaceOverride(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                            org.mockito.ArgumentMatchers.any());
            java.util.Hashtable<String, Object> cache = new java.util.Hashtable<>();
            cache.put("2031-05-14", "cached");
            setSessionAttribute("scheduleDateBean", cache);

            assertThatThrownBy(() -> executeAction(action)).hasMessage("synthetic failure");

            assertThat(cache).containsEntry("2031-05-14", "cached");
        }
    }

    @Nested
    @DisplayName("ScheduleCreateDate2Action (_admin.schedule w, POST for bulk generation)")
    class CreateDate {
        @Test
        void shouldThrow_whenNoPrivilege() throws Exception {
            denyAll();
            assertThatThrownBy(() -> executeAction(inject(new ScheduleCreateDate2Action())))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("should 405 when bFirstDisp=1 and method=GET (bulk write)")
        void shouldReturn405_onBulkWriteViaGet() throws Exception {
            allowPrivilege("_admin.schedule", "w");
            mockRequest.setMethod("GET");
            addRequestParameter("bFirstDisp", "1");
            String result = executeAction(inject(new ScheduleCreateDate2Action()));
            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }

        @Test
        @DisplayName("should succeed when bFirstDisp=0 (month navigation)")
        void shouldAllowMonthNavigationOnGet() throws Exception {
            allowPrivilege("_admin.schedule", "w");
            mockRequest.setMethod("GET");
            addRequestParameter("bFirstDisp", "0");
            assertThat(executeAction(inject(new ScheduleCreateDate2Action())))
                    .isEqualTo(ActionSupport.SUCCESS);
        }

        @Test
        @DisplayName("should 405 when bFirstDisp is absent on GET (absent treated as mutation)")
        void shouldReturn405_whenBFirstDispAbsentOnGet() throws Exception {
            allowPrivilege("_admin.schedule", "w");
            mockRequest.setMethod("GET");
            String result = executeAction(inject(new ScheduleCreateDate2Action()));
            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }

        @Test
        @DisplayName("should succeed when bFirstDisp=1 on POST (bulk generation happy path)")
        void shouldSucceed_whenBulkWriteOnPost() throws Exception {
            allowPrivilege("_admin.schedule", "w");
            mockRequest.setMethod("POST");
            addRequestParameter("bFirstDisp", "1");
            assertThat(executeAction(inject(new ScheduleCreateDate2Action())))
                    .isEqualTo(ActionSupport.SUCCESS);
        }
    }

    @Nested
    @DisplayName("ScheduleTemplateApplying2Action (_admin.schedule w, POST for delete)")
    class TemplateApplying {
        @Test
        void shouldThrow_whenNoPrivilege() throws Exception {
            denyAll();
            assertThatThrownBy(() -> executeAction(inject(new ScheduleTemplateApplying2Action())))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        void shouldReturn405_whenDeleteOnGet() throws Exception {
            allowPrivilege("_admin.schedule", "w");
            mockRequest.setMethod("GET");
            addRequestParameter("delete", "1");
            String result = executeAction(inject(new ScheduleTemplateApplying2Action()));
            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }

        @Test
        void shouldSucceedOnGetForDisplay() throws Exception {
            allowPrivilege("_admin.schedule", "w");
            mockRequest.setMethod("GET");
            assertThat(executeAction(inject(new ScheduleTemplateApplying2Action())))
                    .isEqualTo(ActionSupport.SUCCESS);
        }

        @Test
        void shouldSucceed_whenDeleteOnPost() throws Exception {
            allowPrivilege("_admin.schedule", "w");
            mockRequest.setMethod("POST");
            addRequestParameter("delete", "1");
            assertThat(executeAction(inject(new ScheduleTemplateApplying2Action())))
                    .isEqualTo(ActionSupport.SUCCESS);
        }
    }

    @Nested
    @DisplayName("ScheduleFlipView2Action (_appointment r)")
    class FlipView {
        @Test
        void shouldThrow_whenNoPrivilege() throws Exception {
            denyAll();
            var action = inject(new ScheduleFlipView2Action());
            assertThatThrownBy(() -> executeAction(action))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        void shouldSucceed_whenAllowed() throws Exception {
            allowPrivilege("_appointment", "r");
            assertThat(executeAction(inject(new ScheduleFlipView2Action())))
                    .isEqualTo(ActionSupport.SUCCESS);
        }
    }

    @Nested
    @DisplayName("ScheduleDisplayTemplate2Action (_admin.schedule w OR _appointment r)")
    class DisplayTemplate {
        @Test
        void shouldThrow_whenNeitherPrivilege() throws Exception {
            denyAll();
            var action = inject(new ScheduleDisplayTemplate2Action());
            assertThatThrownBy(() -> executeAction(action))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("_admin.schedule or _appointment");
        }

        @Test
        void shouldSucceed_withScheduleAdminOnly() throws Exception {
            denyAll();
            allowPrivilege("_admin.schedule", "w");
            assertThat(executeAction(inject(new ScheduleDisplayTemplate2Action())))
                    .isEqualTo(ActionSupport.SUCCESS);
        }

        @Test
        void shouldSucceed_withAppointmentReadOnly() throws Exception {
            denyAll();
            allowPrivilege("_appointment", "r");
            assertThat(executeAction(inject(new ScheduleDisplayTemplate2Action())))
                    .isEqualTo(ActionSupport.SUCCESS);
        }
    }

    @Nested
    @DisplayName("ScheduleDatePopup2Action (_admin.schedule w OR _appointment w)")
    class DatePopup {
        @Test
        void shouldThrow_whenNeitherPrivilege() throws Exception {
            denyAll();
            var action = inject(new ScheduleDatePopup2Action());
            assertThatThrownBy(() -> executeAction(action))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("_admin.schedule or _appointment");
        }

        @Test
        void shouldSucceed_withScheduleAdminOnly() throws Exception {
            denyAll();
            allowPrivilege("_admin.schedule", "w");
            assertThat(executeAction(inject(new ScheduleDatePopup2Action())))
                    .isEqualTo(ActionSupport.SUCCESS);
        }

        @Test
        void shouldSucceed_withAppointmentWriteOnly() throws Exception {
            denyAll();
            allowPrivilege("_appointment", "w");
            assertThat(executeAction(inject(new ScheduleDatePopup2Action())))
                    .isEqualTo(ActionSupport.SUCCESS);
        }
    }

    @Nested
    @DisplayName("ScheduleDateFinal2Action (_admin.schedule w OR _appointment r)")
    class DateFinal {
        @Test
        void shouldThrow_whenNeitherPrivilege() throws Exception {
            denyAll();
            var action = inject(new ScheduleDateFinal2Action());
            assertThatThrownBy(() -> executeAction(action))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("_admin.schedule or _appointment");
        }

        @Test
        void shouldSucceed_withScheduleAdminOnly() throws Exception {
            denyAll();
            allowPrivilege("_admin.schedule", "w");
            assertThat(executeAction(inject(new ScheduleDateFinal2Action())))
                    .isEqualTo(ActionSupport.SUCCESS);
        }

        @Test
        void shouldSucceed_withAppointmentReadOnly() throws Exception {
            denyAll();
            allowPrivilege("_appointment", "r");
            assertThat(executeAction(inject(new ScheduleDateFinal2Action())))
                    .isEqualTo(ActionSupport.SUCCESS);
        }
    }
}
