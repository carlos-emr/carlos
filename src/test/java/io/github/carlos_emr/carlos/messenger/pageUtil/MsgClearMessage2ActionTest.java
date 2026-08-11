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
package io.github.carlos_emr.carlos.messenger.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.servlet.http.Cookie;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;

/**
 * Regression coverage for clearing Messenger attachments and its missing-bean redirect.
 *
 * @since 2026-08-11
 */
@DisplayName("MsgClearMessage2Action")
@Tag("integration")
@Tag("messenger")
class MsgClearMessage2ActionTest extends CarlosWebTestBase {

    private MsgClearMessage2Action action;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);

        mockResponse = new UrlRewritingResponse();
        setUpActionContext();
        action = new MsgClearMessage2Action();

        java.lang.reflect.Field securityManager =
                MsgClearMessage2Action.class.getDeclaredField("securityInfoManager");
        securityManager.setAccessible(true);
        securityManager.set(action, mockSecurityInfoManager);
    }

    @ParameterizedTest(name = "existing session cookie: {0}")
    @ValueSource(booleans = {false, true})
    @DisplayName("should redirect without a session identifier when the message bean is missing")
    void shouldRedirectWithoutSessionId_whenMessageBeanIsMissing(boolean withSessionCookie) throws Exception {
        allowPrivilege("_msg", "w");
        getMockRequest().setContextPath("/carlos");
        if (withSessionCookie) {
            getMockRequest().setCookies(new Cookie("JSESSIONID", "existing-session"));
        }

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getRedirectedUrl())
                .isEqualTo("/carlos/messenger/DisplayMessages")
                .doesNotContain(";jsessionid=");
    }

    @Test
    @DisplayName("should clear every attachment when the message bean exists")
    void shouldClearAttachments_whenMessageBeanExists() throws Exception {
        allowPrivilege("_msg", "w");
        MsgSessionBean bean = new MsgSessionBean();
        bean.setAttachment("<item>document</item>");
        bean.setPDFAttachment("encoded-pdf");
        getMockSession().setAttribute("msgSessionBean", bean);

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(bean.getAttachment()).isNull();
        assertThat(bean.getPDFAttachment()).isNull();
        assertThat(bean.getTotalAttachmentCount()).isZero();
        assertThat(getMockResponse().getRedirectedUrl()).isNull();
    }

    @Test
    @DisplayName("should configure the application for cookie-only session tracking")
    void shouldUseCookieOnlySessionTracking_inDeploymentDescriptor() throws Exception {
        String webXml = Files.readString(Path.of("src", "main", "webapp", "WEB-INF", "web.xml"));

        assertThat(webXml)
                .contains("<tracking-mode>COOKIE</tracking-mode>")
                .doesNotContain("<tracking-mode>URL</tracking-mode>");
    }

    private static final class UrlRewritingResponse extends MockHttpServletResponse {
        @Override
        public String encodeRedirectURL(String url) {
            return url + ";jsessionid=simulated-url-session";
        }
    }
}
