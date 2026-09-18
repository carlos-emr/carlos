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
package io.github.carlos_emr.carlos.www;

import io.github.carlos_emr.carlos.commn.dao.SystemMessageDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.*;
import org.mockito.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Authorization tests for {@link SystemMessage2Action}.
 *
 * <p>Separate from {@code SystemMessage2ActionTest} on purpose. That class is listed in
 * {@code src/test/resources/surefire-unmatched-baseline.txt}, so the pom's Surefire
 * {@code <includes>} never select it and a plain {@code mvn test} in CI skips everything in
 * it — new coverage added there would not actually run. It cannot simply be renamed either:
 * two of its pre-existing {@code edit()} cases error with {@code getContainer() is null}
 * (reproducible on an unmodified {@code release/2026.08}), so enabling it would turn CI red
 * for an unrelated defect. These cases therefore live in a class whose name Surefire matches.
 *
 * <p>The system-message banner on the provider schedule is a read-only broadcast: it must
 * render for any authenticated clinician. The management methods behind the same action
 * (list/edit/save) stay behind {@code _admin} write. See issue #3728.
 *
 * @since 2026-09-18
 */
@DisplayName("SystemMessage2Action Security Tests")
@Tag("unit")
@Tag("admin")
class SystemMessage2ActionSecurityUnitTest extends CarlosWebTestBase {

    @Mock
    private SystemMessageDao mockSystemMessageDao;

    private SystemMessage2Action action;

    @BeforeEach
    void setUp() {
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        replaceSpringUtilsBean(SystemMessageDao.class, mockSystemMessageDao);

        action = new SystemMessage2Action();
        injectField(action, "systemMessageDao", mockSystemMessageDao);
        injectField(action, "securityInfoManager", mockSecurityInfoManager);
    }

    @Test
    @DisplayName("should throw SecurityException when privilege is denied")
    void shouldThrowSecurityException_whenPrivilegeDenied() throws Exception {
        denyPrivilege("_admin", "w");

        assertThatThrownBy(() -> executeAction(action))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("missing required sec object");
    }

    @Test
    @DisplayName("should throw SecurityException when save is requested without admin write")
    void shouldThrowSecurityException_whenSaveRequestedWithoutAdminWrite() throws Exception {
        // The management methods stay behind _admin write.
        denyPrivilege("_admin", "w");
        addRequestParameter("method", "save");

        // Denial fires before any persistence happens.
        assertThatThrownBy(() -> executeAction(action))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("missing required sec object (_admin)");
        verifyNoInteractions(mockSystemMessageDao);
    }

    @Test
    @DisplayName("should render the broadcast banner when view is requested without admin write")
    void shouldRenderBroadcastBanner_whenViewRequestedWithoutAdminWrite() throws Exception {
        // A clinician with no admin rights loads the provider schedule (issue #3728).
        denyPrivilege("_admin", "w");
        denyPrivilege("_admin", "r");
        when(mockSystemMessageDao.findAll()).thenReturn(java.util.Collections.emptyList());
        addRequestParameter("method", "view");

        String result = executeAction(action);

        // The banner renders and no privilege check is consulted on the read path at all.
        assertThat(result).isEqualTo("view");
        verify(mockSystemMessageDao).findAll();
        verify(mockSecurityInfoManager, never()).hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("should throw SecurityException when view is requested without a logged-in session")
    void shouldThrowSecurityException_whenViewRequestedWithoutSession() throws Exception {
        // No LoggedInInfo in session; LoginFilter would normally have rejected this already,
        // so the check here is defence in depth against direct invocation.
        getMockSession().removeAttribute(new LoggedInInfo().LOGGED_IN_INFO_KEY);
        addRequestParameter("method", "view");

        assertThatThrownBy(() -> executeAction(action))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("not logged in");
        verifyNoInteractions(mockSystemMessageDao);
    }
}
