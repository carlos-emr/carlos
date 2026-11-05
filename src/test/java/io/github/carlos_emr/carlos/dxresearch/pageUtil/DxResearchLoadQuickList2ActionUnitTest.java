/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.dxresearch.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.QuickListDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** Regression for #5527: a missing read right must take the standard 403 exception path. */
@Tag("dxresearch")
class DxResearchLoadQuickList2ActionUnitTest extends CarlosUnitTestBase {

    @Test
    void shouldRefuseQuickListRead_whenReadPrivilegeIsMissing() {
        SecurityInfoManager security = createAndRegisterMock(SecurityInfoManager.class);
        QuickListDao quickLists = createAndRegisterMock(QuickListDao.class);
        LoggedInInfo login = mock(LoggedInInfo.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/carlos/oscarResearch/oscarDxResearch/dxResearchLoadQuickList");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);

        try (MockedStatic<ServletActionContext> servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            dxResearchLoadQuickList2Action action = new dxResearchLoadQuickList2Action();

            assertThatThrownBy(action::execute)
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_dxresearch)");

            verify(security).hasPrivilege(login, "_dxresearch", "r", null);
            verifyNoInteractions(quickLists);
            assertThat(request.getSession().getAttribute("allQuickLists")).isNull();
        }
    }
}
