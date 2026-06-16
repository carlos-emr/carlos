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

import io.github.carlos_emr.carlos.commn.dao.EFormDao;
import io.github.carlos_emr.carlos.commn.model.EForm;
import io.github.carlos_emr.carlos.eform.EFormUtil;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DelEForm2Action} privilege and ownership logic.
 *
 * <p>Admin-level delete requires the independent {@code _eform} delete ("d") privilege,
 * which is not implied by write ("w"). Providers with only write access may delete
 * eForms they personally created; shared templates (null/blank creator) are admin-only.
 *
 * @since 2026-06-15
 */
@DisplayName("DelEForm2Action — ownership and privilege checks")
@Tag("integration")
@Tag("eform")
@Tag("security")
@Tag("delete")
class DelEForm2ActionTest extends CarlosWebTestBase {

    private static final String LOGGED_IN_PROVIDER = "999998";
    private static final String OTHER_PROVIDER = "888888";
    private static final String FID = "42";

    @Mock
    private EFormDao mockEFormDao;

    private DelEForm2Action action;

    @BeforeEach
    void setUp() {
        // Base class allows all by default — override to deny all, then re-grant per test
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), any()))
                .thenReturn(false);
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(LOGGED_IN_PROVIDER);
        mockRequest.setMethod("POST");
        mockRequest.setParameter("fid", FID);
        action = new DelEForm2Action(mockSecurityInfoManager, mockEFormDao);
    }

    @Test
    @DisplayName("should delete any eForm when admin has _eform delete privilege")
    void shouldDeleteAnyEForm_whenAdminHasDeletePrivilege() throws Exception {
        allowPrivilege("_eform", SecurityInfoManager.DELETE);

        try (MockedStatic<EFormUtil> eformUtils = mockStatic(EFormUtil.class)) {
            String result = action.execute();
            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            verifyNoInteractions(mockEFormDao);
            eformUtils.verify(() -> EFormUtil.delEForm(FID));
        }
    }

    @Test
    @DisplayName("should delete own eForm when provider has write privilege and is the creator")
    void shouldDeleteOwnEForm_whenDoctorIsCreator() throws Exception {
        allowPrivilege("_eform", SecurityInfoManager.WRITE);
        when(mockEFormDao.findById(42)).thenReturn(eformWithCreator(LOGGED_IN_PROVIDER));

        try (MockedStatic<EFormUtil> eformUtils = mockStatic(EFormUtil.class)) {
            String result = action.execute();
            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            eformUtils.verify(() -> EFormUtil.delEForm(FID));
        }
    }

    @Test
    @DisplayName("should reject delete when provider tries to delete another provider's eForm")
    void shouldRejectDelete_whenDoctorDoesNotOwnEForm() {
        allowPrivilege("_eform", SecurityInfoManager.WRITE);
        when(mockEFormDao.findById(42)).thenReturn(eformWithCreator(OTHER_PROVIDER));

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_eform)");
    }

    @Test
    @DisplayName("should reject delete when eForm is a shared template with no creator")
    void shouldRejectDelete_whenFormIsSharedTemplate() {
        allowPrivilege("_eform", SecurityInfoManager.WRITE);
        when(mockEFormDao.findById(42)).thenReturn(eformWithCreator(null));

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_eform)");
    }

    @Test
    @DisplayName("should reject delete when eForm creator is blank")
    void shouldRejectDelete_whenFormCreatorIsBlank() {
        allowPrivilege("_eform", SecurityInfoManager.WRITE);
        when(mockEFormDao.findById(42)).thenReturn(eformWithCreator("   "));

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_eform)");
    }

    @Test
    @DisplayName("should reject delete when provider can write but eForm does not exist")
    void shouldRejectDelete_whenDoctorTargetsMissingEForm() {
        allowPrivilege("_eform", SecurityInfoManager.WRITE);
        when(mockEFormDao.findById(42)).thenReturn(null);

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_eform)");
    }

    @Test
    @DisplayName("should reject delete when provider has no eForm privilege at all")
    void shouldRejectDelete_whenProviderHasNoEFormPrivilege() {
        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_eform)");
    }

    @Test
    @DisplayName("should return 400 when fid parameter is missing")
    void shouldReturn400_whenFidIsMissing() throws Exception {
        mockRequest.removeParameter("fid");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(mockEFormDao);
    }

    @Test
    @DisplayName("should return 400 when fid parameter is non-numeric")
    void shouldReturn400_whenFidIsNonNumeric() throws Exception {
        mockRequest.setParameter("fid", "not-a-number");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(mockEFormDao);
    }

    @Test
    @DisplayName("should return 400 when fid overflows Integer range")
    void shouldReturn400_whenFidOverflowsInt() throws Exception {
        mockRequest.setParameter("fid", "99999999999999");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(mockEFormDao);
    }

    @Test
    @DisplayName("should reject GET with 405 and no mutation side-effects")
    void shouldReturn405_whenMethodIsGet() throws Exception {
        mockRequest.setMethod("GET");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(jakarta.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        verifyNoInteractions(mockEFormDao);
    }

    @Test
    @DisplayName("should reject HEAD with 405 and no mutation side-effects")
    void shouldReturn405_whenMethodIsHead() throws Exception {
        mockRequest.setMethod("HEAD");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(jakarta.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        verifyNoInteractions(mockEFormDao);
    }

    private EForm eformWithCreator(String creatorProviderNo) {
        EForm eform = new EForm();
        eform.setCreator(creatorProviderNo);
        return eform;
    }
}
