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
            assertThatThrownBy(() -> executeAction(inject(new ScheduleTemplateSetting2Action())))
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
            assertThatThrownBy(() -> executeAction(inject(new ScheduleEditTemplate2Action())))
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
            assertThatThrownBy(() -> executeAction(inject(new ScheduleTemplateCodeSetting2Action())))
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
            assertThatThrownBy(() -> executeAction(inject(new ScheduleDateSave2Action())))
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

        @Test
        void shouldSucceedOnPost() throws Exception {
            allowPrivilege("_appointment", "w");
            mockRequest.setMethod("POST");
            assertThat(executeAction(inject(new ScheduleDateSave2Action())))
                    .isEqualTo(ActionSupport.SUCCESS);
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
            assertThatThrownBy(() -> executeAction(inject(new ScheduleFlipView2Action())))
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
            assertThatThrownBy(() -> executeAction(inject(new ScheduleDisplayTemplate2Action())))
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
            assertThatThrownBy(() -> executeAction(inject(new ScheduleDatePopup2Action())))
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
    @DisplayName("ScheduleDateFinal2Action (_appointment r)")
    class DateFinal {
        @Test
        void shouldThrow_whenNoPrivilege() throws Exception {
            denyAll();
            assertThatThrownBy(() -> executeAction(inject(new ScheduleDateFinal2Action())))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        void shouldSucceed_whenAllowed() throws Exception {
            allowPrivilege("_appointment", "r");
            assertThat(executeAction(inject(new ScheduleDateFinal2Action())))
                    .isEqualTo(ActionSupport.SUCCESS);
        }
    }
}
