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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import io.github.carlos_emr.carlos.commn.dao.CSSStylesDAO;
import io.github.carlos_emr.carlos.commn.model.CssStyle;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import org.mockito.ArgumentCaptor;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
 * Tests for {@link ManageCss2Action}'s default render path. Until this fix,
 * the action gated {@code save()} and {@code delete()} on {@code _admin/w}
 * but the bare {@code execute()} path forwarded to the form unauthenticated.
 * This pins the {@code _admin/r} gate that closes the privilege gap.
 */
@DisplayName("ManageCss2Action")
@Tag("unit")
@Tag("billing")
class ManageCss2ActionUnitTest extends CarlosUnitTestBase {

    private CSSStylesDAO stylesDao;
    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;
    private SecurityInfoManager mockSecurity;
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private LoggedInInfo mockLoggedInInfo;

    @BeforeEach
    void setUp() {
        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        mockSecurity = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, mockSecurity);
        stylesDao = mock(CSSStylesDAO.class);
        registerMock(CSSStylesDAO.class, stylesDao);
        when(stylesDao.findAll()).thenReturn(new ArrayList<>());

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        mockLoggedInInfo = mock(LoggedInInfo.class);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
    }

    @Test
    void shouldReturnInit_whenAuthorizedReadOfDefaultRender() throws Exception {
        when(mockSecurity.hasPrivilege(any(LoggedInInfo.class), eq("_admin"), eq("r"), isNull()))
                .thenReturn(true);

        assertThat(new ManageCss2Action().execute()).isEqualTo("init");
    }

    @Test
    void shouldThrowSecurityException_whenSessionMissing() {
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> new ManageCss2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing session");
    }

    @Test
    void shouldCheckSessionBeforeMethodDispatch_whenSaveRequested() {
        mockRequest.setParameter("method", "save");
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> new ManageCss2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing session");
    }

    @Test
    void shouldThrowSecurityException_whenAdminReadPrivilegeMissing() {
        when(mockSecurity.hasPrivilege(any(LoggedInInfo.class), eq("_admin"), eq("r"), isNull()))
                .thenReturn(false);

        assertThatThrownBy(() -> new ManageCss2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin");
    }

    @Test
    void shouldRouteThroughSave_whenMethodIsSave() {
        // Pre-fix the action would still hit the save() gate; we just verify
        // the dispatcher routes by method param without touching the new
        // _admin/r gate (save() has its own _admin/w gate).
        mockRequest.setParameter("method", "save");
        when(mockSecurity.hasPrivilege(any(LoggedInInfo.class), eq("_admin"), eq("w"), isNull()))
                .thenReturn(false);

        assertThatThrownBy(() -> new ManageCss2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin");
    }

    @Test
    void shouldReturn405WithAllowHeader_whenSaveCalledViaGet() throws Exception {
        // POST gate guards the mutation path against forged GETs sidestepping
        // CSRFGuard's body-token validation.
        mockRequest.setMethod("GET");
        mockRequest.setParameter("method", "save");
        mockRequest.setParameter("editStyle", "test");
        mockRequest.setParameter("styleName", "test");
        when(mockSecurity.hasPrivilege(any(LoggedInInfo.class), eq("_admin"), eq("w"), isNull()))
                .thenReturn(true);

        String result = new ManageCss2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
    }

    @Test
    void shouldReturn405WithAllowHeader_whenDeleteCalledViaGet() throws Exception {
        // delete() cascades a null update to billing_service.display_style
        // for every code referencing the style — must be POST-only.
        mockRequest.setMethod("GET");
        mockRequest.setParameter("method", "delete");
        mockRequest.setParameter("editStyle", "test");
        when(mockSecurity.hasPrivilege(any(LoggedInInfo.class), eq("_admin"), eq("w"), isNull()))
                .thenReturn(true);

        String result = new ManageCss2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
    }
    private ManageCss2Action writableAction() {
        mockRequest.setMethod("POST");
        when(mockSecurity.hasPrivilege(any(LoggedInInfo.class), eq("_admin"), eq("w"), isNull())).thenReturn(true);
        // Translation is exercised through the installed JSP; action tests pin the error keys.
        ManageCss2Action action = new ManageCss2Action() {
            @Override public String getText(String key) { return key; }
        };
        action.setStyleName("Owned style");
        return action;
    }

    @Test
    void shouldPersistEveryTypedDeclaration_withoutUsingTheLegacyIdentityAsNewText() {
        ManageCss2Action action = writableAction();
        String typed = " color:ReBeccaPurple; background-color:#abc; text-decoration:underline ";
        action.setStyleText(typed);
        action.setEditStyle("color:black;");
        assertThat(action.save()).isEqualTo("init");
        ArgumentCaptor<CssStyle> captor = ArgumentCaptor.forClass(CssStyle.class);
        verify(stylesDao).persist(captor.capture());
        assertThat(captor.getValue().getStyle()).isEqualTo(typed);
        assertThat(captor.getValue().getName()).isEqualTo("Owned style");
        assertThat(captor.getValue().getStatus()).isEqualTo(CssStyle.ACTIVE);
        assertThat(mockRequest.getAttribute("success")).isEqualTo("true");
    }

    @Test
    void shouldEditTheSameRow_usingOriginalIdentityAndNewTextSeparately() {
        CssStyle saved = new CssStyle();
        saved.setId(27);
        saved.setStyle("color:red;");
        saved.setName("Original");
        when(stylesDao.findAll()).thenReturn(new ArrayList<>(List.of(saved)));
        ManageCss2Action action = writableAction();
        action.setSelectedStyle(saved.getStyle());
        action.setEditStyle(saved.getStyle());
        action.setStyleText("color:#abcdef;text-decoration:underline;");
        assertThat(action.save()).isEqualTo("init");
        verify(stylesDao).merge(saved);
        verify(stylesDao, never()).persist(any(CssStyle.class));
        assertThat(saved.getId()).isEqualTo(27);
        assertThat(saved.getStyle()).isEqualTo("color:#abcdef;text-decoration:underline;");
        assertThat(action.getStyleText()).isEmpty();
        assertThat(action.getSelectedStyle()).isEqualTo("-1");
        assertThat(saved.getName()).isEqualTo("Owned style");
    }

    @ParameterizedTest
    @ValueSource(strings = {"color:red;position:fixed;", "color:not-a-colour;", "background-color:url(https://example.invalid);",
            "font-weight:expression(alert(1));", "font-size:huge;", "color:#12345;", "color:red;broken", ""})
    void shouldRejectUnsupportedInput_withoutPersistingAnyPartialStyle(String typed) {
        ManageCss2Action action = writableAction();
        action.setStyleText(typed);
        assertThat(action.save()).isEqualTo("init");
        assertThat(action.getActionErrors()).contains("admin.manageCodeStyles.invalidDeclarations");
        assertThat(action.getStyleText()).isEqualTo(typed);
        verify(stylesDao, never()).persist(any(CssStyle.class));
        verify(stylesDao, never()).merge(any(CssStyle.class));
        assertThat(mockRequest.getAttribute("success")).isNull();
    }

    @Test
    void shouldRejectAnInvalidEdit_withoutChangingTheExistingRecord() {
        CssStyle saved = new CssStyle();
        saved.setStyle("color:red;");
        saved.setName("Original");
        when(stylesDao.findAll()).thenReturn(new ArrayList<>(List.of(saved)));
        ManageCss2Action action = writableAction();
        action.setSelectedStyle(saved.getStyle());
        action.setEditStyle(saved.getStyle());
        action.setStyleText("color:blue;position:fixed;");
        action.save();
        assertThat(action.hasActionErrors()).isTrue();
        assertThat(saved.getStyle()).isEqualTo("color:red;");
        assertThat(saved.getName()).isEqualTo("Original");
        verify(stylesDao, never()).merge(any(CssStyle.class));
    }

    @Test
    void shouldRejectMissingAndOverlongNames() {
        for (String name : new String[] {null, "  ", "x".repeat(256)}) {
            ManageCss2Action action = writableAction();
            action.setStyleName(name);
            action.setStyleText("color:red;");
            action.save();
            assertThat(action.getActionErrors()).contains("admin.manageCodeStyles.invalidName");
        }
        verify(stylesDao, never()).persist(any(CssStyle.class));
    }

}
