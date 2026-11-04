/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.eform.actions;

import io.github.carlos_emr.carlos.eform.EFormUtil;
import io.github.carlos_emr.carlos.eform.data.EFormBase;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Pins the method boundary before both the create and update paths of the editor save. */
@Tag("unit")
@Tag("security")
@Tag("eform")
class HtmlEdit2ActionUnitTest extends CarlosUnitTestBase {

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "PUT", "DELETE", "PATCH", "OPTIONS"})
    void shouldRejectBeforeFormAccess_forNonPostMethods(String method) throws Exception {
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, security);
        for (String fid : new String[] {"", "42"}) {
            MockHttpServletRequest request = new MockHttpServletRequest(method, "/eform/editForm");
            MockHttpServletResponse response = new MockHttpServletResponse();
            try (MockedStatic<ServletActionContext> servlet = mockStatic(ServletActionContext.class);
                    MockedStatic<EFormUtil> forms = mockStatic(EFormUtil.class)) {
                servlet.when(ServletActionContext::getRequest).thenReturn(request);
                servlet.when(ServletActionContext::getResponse).thenReturn(response);
                HtmlEdit2Action action = new HtmlEdit2Action();
                action.setFid(fid);
                action.setFormName("Synthetic template");
                action.setFormHtml("Synthetic replacement");

                assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
                assertThat(response.getStatus()).isEqualTo(405);
                assertThat(response.getHeader("Allow")).isEqualTo("POST");
                forms.verifyNoInteractions();
                verifyNoInteractions(security);
                assertThat(request.getAttribute("success")).isNull();
            }
        }
    }

    @Test
    void shouldDenyWrite_forPostWithoutPrivilege() {
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, security);
        try (MockedStatic<ServletActionContext> servlet = mockStatic(ServletActionContext.class);
                MockedStatic<EFormUtil> forms = mockStatic(EFormUtil.class)) {
            servlet.when(ServletActionContext::getRequest)
                    .thenReturn(new MockHttpServletRequest("POST", "/eform/editForm"));
            servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());

            assertThatThrownBy(() -> new HtmlEdit2Action().execute())
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_eform)");
            forms.verifyNoInteractions();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "42"})
    void shouldSaveTemplate_forAuthorizedPost(String fid) throws Exception {
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, security);
        when(security.hasPrivilege(any(), eq("_eform"), eq("w"), isNull())).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/eform/editForm");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String html = "<html><body>Synthetic edited template</body></html>";
        try (MockedStatic<ServletActionContext> servlet = mockStatic(ServletActionContext.class);
                MockedStatic<LoggedInInfo> login = mockStatic(LoggedInInfo.class);
                MockedStatic<EFormUtil> forms = mockStatic(EFormUtil.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(mock(LoggedInInfo.class));
            forms.when(() -> EFormUtil.saveEForm("Synthetic template", "Subject", "template.html",
                    html, false, false, "")).thenReturn("43");
            HtmlEdit2Action action = new HtmlEdit2Action();
            action.setFid(fid);
            action.setFormName("Synthetic template");
            action.setFormSubject("Subject");
            action.setFormFileName("template.html");
            action.setFormHtml(html);

            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(request.getAttribute("success")).isEqualTo("true");
            if (fid.isEmpty()) {
                forms.verify(() -> EFormUtil.saveEForm("Synthetic template", "Subject", "template.html",
                        html, false, false, ""));
            } else {
                ArgumentCaptor<EFormBase> saved = ArgumentCaptor.forClass(EFormBase.class);
                forms.verify(() -> EFormUtil.updateEForm(saved.capture()));
                assertThat(saved.getValue().getFormHtml()).isEqualTo(html);
                assertThat(saved.getValue().getFid()).isEqualTo(fid);
            }
        }
    }
}
