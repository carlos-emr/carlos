/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.eform.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("EForm browser PDF service authorization")
@Tag("unit")
@Tag("fast")
@Tag("eform")
@Tag("security")
class EFormBrowserPdfServiceAuthorizationUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should enforce demographic-scoped read again inside the browser service")
    void shouldEnforceEformRead_forTheSavedFormsDemographic() {
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        EFormData data = new EFormData();
        data.setId(77);
        data.setDemographicId(123);
        EFormDataDao dao = mock(EFormDataDao.class);
        when(dao.find(77)).thenReturn(data);
        registerMock(EFormDataDao.class, dao);

        SecurityInfoManager security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(
                loggedInInfo, "_eform", SecurityInfoManager.READ, "123"))
                .thenReturn(false);
        registerMock(SecurityInfoManager.class, security);

        assertThatThrownBy(() ->
                new EFormBrowserPdfService().renderSavedEformPdf(loggedInInfo, 77))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_eform");
        verify(security).hasPrivilege(
                loggedInInfo, "_eform", SecurityInfoManager.READ, "123");
    }

    @Test
    @DisplayName("should fail generation when the requested saved eForm does not exist")
    void shouldFailGeneration_whenSavedEformNotFound() {
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        EFormDataDao dao = mock(EFormDataDao.class);
        when(dao.find(77)).thenReturn(null);
        registerMock(EFormDataDao.class, dao);
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, security);

        assertThatThrownBy(() ->
                new EFormBrowserPdfService().renderSavedEformPdf(loggedInInfo, 77))
                .isInstanceOf(PDFGenerationException.class);
        // A missing record must never reach a privilege check with a null demographic, which some
        // privilege implementations read as "unscoped" and allow.
        verifyNoInteractions(security);
    }

    @Test
    @DisplayName("should bind the render to the caller's own provider once authorization passes")
    void shouldBindRenderToCallersProvider_whenAuthorizationPasses() {
        // The render grant carries providerNo, and EFormApCacheForPdfGenerationServlet builds its
        // EForm from grant.providerNo(). If this ever sourced the provider from anywhere but the
        // authenticated caller, provider-scoped AP fields would resolve against the wrong clinician
        // — printing the wrong physician on a signed letter.
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        EFormData data = new EFormData();
        data.setId(77);
        data.setDemographicId(123);
        EFormDataDao dao = mock(EFormDataDao.class);
        when(dao.find(77)).thenReturn(data);
        registerMock(EFormDataDao.class, dao);

        SecurityInfoManager security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(
                loggedInInfo, "_eform", SecurityInfoManager.READ, "123"))
                .thenReturn(true);
        registerMock(SecurityInfoManager.class, security);

        // Rendering itself needs a browser, so it fails downstream — but only AFTER the provider has
        // been read from the authenticated caller, which is the binding under test.
        assertThatThrownBy(() ->
                new EFormBrowserPdfService().renderSavedEformPdf(loggedInInfo, 77))
                .isNotInstanceOf(SecurityException.class);
        verify(loggedInInfo).getLoggedInProviderNo();
    }
}
