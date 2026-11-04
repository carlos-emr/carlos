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
package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RxAddReaction2Action}, which opens the allergy reaction dialogue: the ATC
 * class a class shortcut sends (issue #4576) reaches the form only when it is a class code.
 *
 * @since 2026-10-15
 */
@DisplayName("RxAddReaction2Action Unit Tests")
@Tag("unit")
@Tag("rx")
class RxAddReaction2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private SecurityInfoManager securityInfoManager;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_rx"), eq("w"), isNull())).thenReturn(true);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class))).thenReturn(loggedInInfo);

        request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setParameter("ID", "");
        request.setParameter("type", "8");
        request.setParameter("name", "PENICILLINS");
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
    }

    @AfterEach
    void tearDown() {
        servletActionContextMock.close();
        loggedInInfoMock.close();
    }

    @Test
    @DisplayName("should carry a class shortcut's ATC class onto the dialogue form")
    void shouldCarryAtcClass_whenShortcutSendsClassCode() throws Exception {
        request.setParameter("atc", " J01C ");

        assertThat(new RxAddReaction2Action().execute()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(request.getAttribute("atc")).isEqualTo("J01C");
        assertThat(request.getAttribute("type")).isEqualTo("8");
        assertThat(request.getAttribute("name")).isEqualTo("PENICILLINS");
    }

    @ParameterizedTest(name = "atc={0}")
    @ValueSource(strings = {"", "J", "j01c", "J01C\"><script>", "44452"})
    @DisplayName("should carry no ATC class when what was sent is not a class code")
    void shouldCarryNoAtcClass_whenCodeIsNotAClassCode(String atc) throws Exception {
        request.setParameter("atc", atc);

        new RxAddReaction2Action().execute();

        assertThat(request.getAttribute("atc")).isNull();
    }

    @Test
    @DisplayName("should carry no ATC class when none was sent, as for a search result")
    void shouldCarryNoAtcClass_whenNoneWasSent() throws Exception {
        new RxAddReaction2Action().execute();

        assertThat(request.getAttribute("atc")).isNull();
    }

    @Test
    @DisplayName("should refuse a user without Rx write access")
    void shouldThrowSecurityException_whenRxWriteIsMissing() {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_rx"), eq("w"), isNull())).thenReturn(false);

        assertThatThrownBy(() -> new RxAddReaction2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_rx)");
    }
}
