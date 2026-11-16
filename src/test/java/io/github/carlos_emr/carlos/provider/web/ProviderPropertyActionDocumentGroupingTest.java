/*
 * Copyright (c) 2026 CARLOS Contributors.
 * Licensed under the GNU General Public License, version 2 or later.
 */
package io.github.carlos_emr.carlos.provider.web;

import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class ProviderPropertyActionDocumentGroupingTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"true", "false"})
    void shouldSaveDocumentGroupingForViewer_whenPreferenceSubmitted(String submitted) {
        UserPropertyDAO dao = mock(UserPropertyDAO.class);
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        LoggedInInfo viewer = mock(LoggedInInfo.class);
        when(viewer.getLoggedInProviderNo()).thenReturn("viewer");
        when(security.hasPrivilege(viewer, "_pref", "w", null)).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY", viewer);
        if (submitted != null) request.setParameter(UserProperty.ECHART_GROUP_DOCUMENTS_BY_TYPE, submitted);
        try (MockedStatic<SpringUtils> spring = mockStatic(SpringUtils.class)) {
            spring.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
            spring.when(() -> SpringUtils.getBean(UserPropertyDAO.class)).thenReturn(dao);

            ProviderPropertyAction.updateOrCreateProviderProperties(request);

            if (submitted == null) {
                verify(dao, never()).saveProp(eq("viewer"), eq(UserProperty.ECHART_GROUP_DOCUMENTS_BY_TYPE), anyString());
            } else {
                verify(dao).saveProp("viewer", UserProperty.ECHART_GROUP_DOCUMENTS_BY_TYPE, submitted);
            }
        }
    }
}
