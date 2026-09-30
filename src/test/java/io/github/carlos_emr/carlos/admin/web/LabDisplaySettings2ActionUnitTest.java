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
package io.github.carlos_emr.carlos.admin.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.lab.service.LabPdfPreviewSettings;
import io.github.carlos_emr.carlos.lab.service.LabPdfPreviewSettingsService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit tests for {@link LabDisplaySettings2Action}: the view needs {@code _admin} read, a save must
 * be a POST with {@code _admin} write, and an invalid size saves nothing.
 *
 * @since 2026-09-30
 */
@Tag("unit")
@Tag("admin")
@DisplayName("LabDisplaySettings2Action")
class LabDisplaySettings2ActionUnitTest {

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager security;
    private LabPdfPreviewSettingsService settingsService;
    private LoggedInInfo loggedInInfo;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> login;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setMethod("GET");
        response = new MockHttpServletResponse();
        security = mock(SecurityInfoManager.class);
        settingsService = mock(LabPdfPreviewSettingsService.class);
        loggedInInfo = mock(LoggedInInfo.class);
        when(settingsService.load()).thenReturn(LabPdfPreviewSettings.DEFAULTS);
        servlet = mockStatic(ServletActionContext.class);
        login = mockStatic(LoggedInInfo.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(loggedInInfo);
    }

    @AfterEach
    void tearDown() {
        login.close();
        servlet.close();
    }

    private LabDisplaySettings2Action action() {
        return new LabDisplaySettings2Action(security, settingsService);
    }

    @ParameterizedTest
    @CsvSource({"GET,r", "HEAD,r", "POST,w"})
    @DisplayName("should require _admin read to view and _admin write to post")
    void shouldCheckRequiredPrivilege_beforeRenderingSettings(String method, String privilege) {
        request.setMethod(method);

        assertThatThrownBy(action()::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin)");
        verifyNoInteractions(settingsService);

        when(security.hasPrivilege(loggedInInfo, "_admin", privilege, null)).thenReturn(true);
        assertThat(action().execute()).isEqualTo("success");
    }

    @Test
    @DisplayName("should show the stored settings in whole MB")
    void shouldExposeCurrentSettings_whenViewing() {
        when(security.hasPrivilege(loggedInInfo, "_admin", "r", null)).thenReturn(true);
        when(settingsService.load()).thenReturn(new LabPdfPreviewSettings(false, 3L * 1024 * 1024));

        action().execute();

        assertThat(request.getAttribute("labPdfInlinePreview")).isEqualTo(false);
        assertThat(request.getAttribute("labPdfMaxSizeMb")).isEqualTo(3L);
        assertThat(request.getAttribute("labPdfMaxSizeMbLimit")).isEqualTo(100L);
        assertThat(request.getAttribute("saved")).isEqualTo(false);
        verify(settingsService, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("should save the checkbox and size on an authorized POST")
    void shouldSaveSettings_whenAuthorizedPostSaves(boolean enabled) {
        request.setMethod("POST");
        request.setParameter("dboperation", "Save");
        request.setParameter("lab_pdf_max_size_mb", "25");
        if (enabled) {
            request.setParameter("lab_pdf_inline_preview", "true");
        }
        when(security.hasPrivilege(loggedInInfo, "_admin", "w", null)).thenReturn(true);

        assertThat(action().execute()).isEqualTo("success");

        verify(settingsService).save(new LabPdfPreviewSettings(enabled, 25L * 1024 * 1024));
        assertThat(request.getAttribute("saved")).isEqualTo(true);
        assertThat(request.getAttribute("labPdfInlinePreview")).isEqualTo(enabled);
        assertThat(request.getAttribute("labPdfMaxSizeMb")).isEqualTo(25L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "101", "-1", "abc", "2.5", ""})
    @DisplayName("should save nothing and flag the field for an invalid size")
    void shouldRejectInvalidSize_withoutSaving(String size) {
        request.setMethod("POST");
        request.setParameter("dboperation", "Save");
        request.setParameter("lab_pdf_max_size_mb", size);
        when(security.hasPrivilege(loggedInInfo, "_admin", "w", null)).thenReturn(true);

        assertThat(action().execute()).isEqualTo("success");

        verify(settingsService, never()).save(any());
        assertThat(request.getAttribute("invalidSize")).isEqualTo(true);
        assertThat(request.getAttribute("saved")).isEqualTo(false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    @DisplayName("should reject a save intent on a read method before any check")
    void shouldRejectSaveIntent_onReadMethods(String method) {
        request.setMethod(method);
        request.setParameter("dboperation", "Save");

        assertThat(action().execute()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(security, settingsService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "DELETE", "PATCH"})
    @DisplayName("should reject unsupported methods before any check")
    void shouldRejectUnsupportedMethods_withoutAccessingSettings(String method) {
        request.setMethod(method);

        assertThat(action().execute()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("GET, HEAD, POST");
        verifyNoInteractions(security, settingsService);
    }
}
