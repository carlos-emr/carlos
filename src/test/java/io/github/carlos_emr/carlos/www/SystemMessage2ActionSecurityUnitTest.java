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

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.mockito.Mock;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The #3728 banner contract for {@link SystemMessage2Action}; the cases live in
 * {@link MessageBannerAuthorizationUnitTestBase}.
 *
 * <p>Separate from {@code SystemMessage2ActionTest} on purpose. That class is listed in
 * {@code src/test/resources/surefire-unmatched-baseline.txt}, so the pom's Surefire
 * {@code <includes>} never select it and a plain {@code mvn test} in CI skips everything in
 * it — coverage added there would not actually run. It cannot simply be renamed either: two of
 * its pre-existing {@code edit()} cases error with {@code getContainer() is null} (reproducible
 * on an unmodified {@code release/2026.08}), so enabling it would turn CI red for an unrelated
 * defect.
 */
@DisplayName("SystemMessage2Action Security Tests")
@Tag("unit")
@Tag("admin")
class SystemMessage2ActionSecurityUnitTest extends MessageBannerAuthorizationUnitTestBase {

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

    @Override
    protected ActionSupport bannerAction() {
        return action;
    }

    @Override
    protected void stubBannerRead() {
        when(mockSystemMessageDao.findAll()).thenReturn(java.util.Collections.emptyList());
    }

    @Override
    protected void verifyBannerRead() {
        verify(mockSystemMessageDao).findAll();
    }

    @Override
    protected Object managementCollaborator() {
        return mockSystemMessageDao;
    }

    @Override
    protected Object bannerCollaborator() {
        return mockSystemMessageDao;
    }
}
