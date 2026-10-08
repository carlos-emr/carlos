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
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.eform.actions;

import io.github.carlos_emr.carlos.eform.EFormUtil;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RestoreEForm2Action} input validation (issue #3571).
 *
 * @since 2026-10-07
 */
@DisplayName("RestoreEForm2Action — fid validation")
@Tag("integration")
@Tag("eform")
@Tag("security")
@Tag("update")
class RestoreEForm2ActionIntegrationTest extends CarlosWebTestBase {

    private static final String FID = "42";

    private RestoreEForm2Action action;

    @BeforeEach
    void setUp() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), any()))
                .thenReturn(false);
        mockRequest.setMethod("POST");
        mockRequest.setParameter("fid", FID);
        action = new RestoreEForm2Action(mockSecurityInfoManager);
    }

    @Test
    @DisplayName("should restore eForm when fid is a positive integer and provider has _eform write")
    void shouldRestoreEForm_whenFidIsPositiveInteger() throws Exception {
        allowPrivilege("_eform", SecurityInfoManager.WRITE);

        try (MockedStatic<EFormUtil> eformUtils = mockStatic(EFormUtil.class)) {
            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
            eformUtils.verify(() -> EFormUtil.restoreEForm(FID));
        }
    }

    @Test
    @DisplayName("should reject restore when provider lacks _eform write privilege")
    void shouldRejectRestore_whenProviderLacksEFormPrivilege() {
        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_eform)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "4x", "0", "-5", "1.5", "9999999999999", "  "})
    @DisplayName("should return 400 and not restore when fid is invalid")
    void shouldReturn400_whenFidIsInvalid(String badFid) throws Exception {
        allowPrivilege("_eform", SecurityInfoManager.WRITE);
        mockRequest.setParameter("fid", badFid);

        try (MockedStatic<EFormUtil> eformUtils = mockStatic(EFormUtil.class)) {
            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            eformUtils.verifyNoInteractions();
        }
    }

    @Test
    @DisplayName("should return 400 and not restore when fid is missing")
    void shouldReturn400_whenFidIsMissing() throws Exception {
        allowPrivilege("_eform", SecurityInfoManager.WRITE);
        mockRequest.removeParameter("fid");

        try (MockedStatic<EFormUtil> eformUtils = mockStatic(EFormUtil.class)) {
            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            eformUtils.verifyNoInteractions();
        }
    }

    @Test
    @DisplayName("should reject GET with 405 and not restore")
    void shouldReturn405_whenMethodIsGet() throws Exception {
        allowPrivilege("_eform", SecurityInfoManager.WRITE);
        mockRequest.setMethod("GET");

        try (MockedStatic<EFormUtil> eformUtils = mockStatic(EFormUtil.class)) {
            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            eformUtils.verifyNoInteractions();
        }
    }
}
