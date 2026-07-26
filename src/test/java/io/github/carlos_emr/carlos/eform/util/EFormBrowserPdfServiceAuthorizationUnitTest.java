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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("EForm browser PDF service authorization")
@Tag("unit")
@Tag("fast")
@Tag("eform")
@Tag("security")
class EFormBrowserPdfServiceAuthorizationUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should enforce demographic-scoped read again inside the browser service")
    void shouldEnforceDemographicScopedRead() {
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
}
