/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.schedule.web;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@Tag("unit")
@DisplayName("Schedule template code method boundaries")
class ScheduleTemplateCodeMethodUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE", "post", "PoSt", "POſT"})
    @DisplayName("should reject non-POST schedule template writes before the mutation JSP")
    void shouldRejectNonPost_beforeMutationView(String method) throws Exception {
        var security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), eq("_admin.schedule"), eq("w"), isNull())).thenReturn(true);
        var request = new MockHttpServletRequest(method, "/schedule/templateCode");
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            for (String operation : new String[] {"Save", "Delete"}) {
                var response = new MockHttpServletResponse();
                servlet.when(ServletActionContext::getResponse).thenReturn(response);
                request.setParameter("dboperation", operation);
                assertThat(new ScheduleTemplateCodeSetting2Action().execute()).isEqualTo(ActionSupport.NONE);
                assertThat(response.getStatus()).isEqualTo(405);
                assertThat(response.getHeader("Allow")).isEqualTo("POST");
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"POST,Save", "POST,Delete", "GET,Edit", "GET,''", "HEAD,Edit"})
    @DisplayName("should preserve authorized exact-POST writes and read-only schedule views")
    void shouldPreserveAllowedMethods_whenAuthorized(String method, String operation) throws Exception {
        var security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), eq("_admin.schedule"), eq("w"), isNull())).thenReturn(true);
        var request = new MockHttpServletRequest(method, "/schedule/templateCode");
        request.setParameter("dboperation", operation);
        var response = new MockHttpServletResponse();
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            assertThat(new ScheduleTemplateCodeSetting2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
