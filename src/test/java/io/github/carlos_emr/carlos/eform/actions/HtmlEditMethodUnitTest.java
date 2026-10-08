/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.eform.actions;

import io.github.carlos_emr.carlos.eform.data.EFormBase;
import io.github.carlos_emr.carlos.eform.EFormUtil;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** The HTML editor action writes both newly created and existing eForms. */
@Tag("unit")
class HtmlEditMethodUnitTest extends CarlosUnitTestBase {
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private LoggedInInfo login;
    private SecurityInfoManager security;
    private MockedStatic<ServletActionContext> servlet;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        login = mock(LoggedInInfo.class);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
        security = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, security);
        when(security.hasPrivilege(login, "_eform", "w", null)).thenReturn(true);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void tearDown() {
        servlet.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "PUT", "DELETE"})
    void shouldRefuseBeforeFormPersistence_whenMethodIsNotPost(String method) {
        request.setMethod(method);
        try (var forms = mockStatic(EFormUtil.class)) {
            assertThat(new HtmlEdit2Action().execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(405);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
            forms.verifyNoInteractions();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "7"})
    void shouldRetainCreateAndUpdate_whenAuthorizedPostIsSubmitted(String id) {
        request.setMethod("POST");
        var action = new HtmlEdit2Action();
        action.setFid(id);
        action.setFormName("Owned form");
        action.setFormSubject("Owned subject");
        action.setFormFileName("owned.html");
        action.setFormHtml("<p>Owned content</p>");
        action.setRoleType("eform");
        try (var forms = mockStatic(EFormUtil.class)) {
            forms.when(() -> EFormUtil.saveEForm("Owned form", "Owned subject", "owned.html",
                    "<p>Owned content</p>", false, false, "eform")).thenReturn("7");
            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
            assertThat(request.getAttribute("success")).isEqualTo("true");
            if (id.isEmpty()) {
                forms.verify(() -> EFormUtil.saveEForm("Owned form", "Owned subject", "owned.html",
                        "<p>Owned content</p>", false, false, "eform"));
            } else {
                forms.verify(() -> EFormUtil.updateEForm(any(EFormBase.class)));
            }
        }
    }

    @Test
    void shouldPreserveWriteAuthorization_whenPostIsDenied() {
        request.setMethod("POST");
        when(security.hasPrivilege(login, "_eform", "w", null)).thenReturn(false);
        try (var forms = mockStatic(EFormUtil.class)) {
            assertThatThrownBy(() -> new HtmlEdit2Action().execute()).isInstanceOf(SecurityException.class);
            forms.verifyNoInteractions();
        }
    }
}
