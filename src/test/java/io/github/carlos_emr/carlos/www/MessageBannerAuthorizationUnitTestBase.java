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

import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The authorization contract shared by the two schedule message banners.
 *
 * <p>SystemMessage2Action and OrganizationMessage2Action are different actions over different
 * stores, but #3728 gave them one rule: {@code ?method=view} is a read-only broadcast that any
 * authenticated clinician may render, while the management methods (list / edit / save) stay
 * behind {@code _admin} write. Asserting that twice produced two near-identical test classes,
 * which is what SonarCloud's duplication gate flagged. The contract lives here once; each
 * subclass supplies only what differs — the action and the collaborators to stub and verify.
 */
abstract class MessageBannerAuthorizationUnitTestBase extends CarlosWebTestBase {

    /** The action under test, already wired with mocks by the subclass's own setup. */
    protected abstract ActionSupport bannerAction();

    /** Stubs whatever the view path reads, so it can render with no privilege at all. */
    protected abstract void stubBannerRead();

    /** Asserts the view path actually read the banner rather than short-circuiting. */
    protected abstract void verifyBannerRead();

    /** The collaborator a denied management request must never touch. */
    protected abstract Object managementCollaborator();

    /** The collaborator an unauthenticated view must never touch. */
    protected abstract Object bannerCollaborator();

    @Test
    @DisplayName("should render the banner when view is requested without admin write")
    void shouldRenderBanner_whenViewRequestedWithoutAdminWrite() throws Exception {
        // A clinician with no admin rights loads the provider schedule (issue #3728).
        denyPrivilege("_admin", "w");
        denyPrivilege("_admin", "r");
        stubBannerRead();
        addRequestParameter("method", "view");

        String result = executeAction(bannerAction());

        // The banner renders, and no privilege check is consulted on the read path at all.
        assertThat(result).isEqualTo("view");
        verifyBannerRead();
        verify(mockSecurityInfoManager, never())
                .hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("should throw SecurityException when view is requested without a logged-in session")
    void shouldThrowSecurityException_whenViewRequestedWithoutSession() throws Exception {
        // LoginFilter would normally have rejected this already; the check is defence in depth
        // against direct invocation and filter reordering.
        getMockSession().removeAttribute(new LoggedInInfo().LOGGED_IN_INFO_KEY);
        addRequestParameter("method", "view");

        assertThatThrownBy(() -> executeAction(bannerAction()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not logged in");
        verifyNoInteractions(bannerCollaborator());
    }

    @Test
    @DisplayName("should throw SecurityException when save is requested without admin write")
    void shouldThrowSecurityException_whenSaveRequestedWithoutAdminWrite() throws Exception {
        denyPrivilege("_admin", "w");
        addRequestParameter("method", "save");

        // Denial fires before any persistence happens.
        assertThatThrownBy(() -> executeAction(bannerAction()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_admin)");
        verifyNoInteractions(managementCollaborator());
    }

    @Test
    @DisplayName("should throw SecurityException when the admin list is requested without admin write")
    void shouldThrowSecurityException_whenListRequestedWithoutAdminWrite() throws Exception {
        // No method parameter routes to the administrative list.
        denyPrivilege("_admin", "w");

        assertThatThrownBy(() -> executeAction(bannerAction()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_admin)");
        verifyNoInteractions(managementCollaborator());
    }
}
