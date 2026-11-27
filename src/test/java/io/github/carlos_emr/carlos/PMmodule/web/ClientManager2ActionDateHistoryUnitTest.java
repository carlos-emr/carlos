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
package io.github.carlos_emr.carlos.PMmodule.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.PMmodule.model.ClientReferral;
import io.github.carlos_emr.carlos.PMmodule.service.AdmissionManager;
import io.github.carlos_emr.carlos.PMmodule.service.ClientManager;
import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.model.Admission;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Issue #6029: the referral and admission views no longer read date-change history from the
 * audit log. Nothing writes the {@code update_*_date} actions and no page renders the history, so
 * the reads only produced empty, unused request attributes.
 *
 * @since 2026-11-19
 */
@Tag("unit")
@Tag("pmmodule")
@DisplayName("PMM referral and admission views: no unrendered audit history")
class ClientManager2ActionDateHistoryUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockHttpServletRequest request;
    private ClientManager clientManager;
    private AdmissionManager admissionManager;
    private OscarLogDao oscarLogDao;

    @BeforeEach
    void setUp() {
        // The action resolves its collaborators in field initializers; a plain mock of each
        // unregistered one is enough for these views.
        springUtilsMock.when(() -> SpringUtils.getBean(any(Class.class)))
                .thenAnswer(invocation -> mockedBeans.computeIfAbsent(invocation.getArgument(0),
                        type -> mock((Class<?>) type)));
        clientManager = mock(ClientManager.class);
        registerMock(ClientManager.class, clientManager);
        admissionManager = mock(AdmissionManager.class);
        registerMock(AdmissionManager.class, admissionManager);
        oscarLogDao = mock(OscarLogDao.class);
        registerMock(OscarLogDao.class, oscarLogDao);

        request = new MockHttpServletRequest("GET", "/carlos/PMmodule/ClientManager");
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
    }

    @AfterEach
    void tearDown() {
        servletActionContextMock.close();
    }

    @Test
    void shouldNotReadAuditHistory_whenViewingAReferral() {
        ClientReferral referral = mock(ClientReferral.class);
        when(referral.getClientId()).thenReturn(7L);
        when(referral.getProviderNo()).thenReturn("999998");
        when(clientManager.getClientReferral("31")).thenReturn(referral);
        request.setParameter("referralId", "31");

        assertThat(new ClientManager2Action().view_referral()).isEqualTo("view_referral");

        verifyNoInteractions(oscarLogDao);
        assertThat(request.getAttribute("referral_date_updates")).isNull();
        assertThat(request.getAttribute("completion_date_updates")).isNull();
    }

    @Test
    void shouldNotReadAuditHistory_whenViewingAnAdmission() {
        Admission admission = mock(Admission.class);
        when(admission.getClientId()).thenReturn(7);
        when(admission.getProviderNo()).thenReturn("999998");
        when(admissionManager.getAdmission(43L)).thenReturn(admission);
        request.setParameter("admissionId", "43");

        assertThat(new ClientManager2Action().view_admission()).isEqualTo("view_admission");

        verifyNoInteractions(oscarLogDao);
        assertThat(request.getAttribute("admission_date_updates")).isNull();
        assertThat(request.getAttribute("discharge_date_updates")).isNull();
    }
}
