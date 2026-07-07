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
package io.github.carlos_emr.carlos.webserv.rest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.exception.AccessDeniedException;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Unit tests for the ownership check added to
 * {@link NotesService#getCurrentNote(Integer, ObjectNode)}.
 *
 * <p>Regression coverage for issue #2839's IDOR class: this endpoint took
 * demographicNo directly from the caller with no privilege or ownership
 * check at all.</p>
 *
 * @since 2026-07-06
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotesService.getCurrentNote unit tests")
@Tag("unit")
@Tag("fast")
class NotesServiceGetCurrentNoteUnitTest extends CarlosUnitTestBase {

    private static final Integer DEMOGRAPHIC_NO = 100;
    private static final String PROVIDER_NO = "provider1";

    @Mock
    private CaseManagementManager caseManagementMgr;

    @Mock
    private SecurityInfoManager securityInfoManager;

    private NotesService service;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        Provider provider = new Provider();
        provider.setProviderNo(PROVIDER_NO);
        loggedInInfo = new LoggedInInfo();
        loggedInInfo.setLoggedInProvider(provider);

        service = new NotesService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return loggedInInfo;
            }
        };
        injectDependency(service, "caseManagementMgr", caseManagementMgr);
        injectDependency(service, "securityInfoManager", securityInfoManager);

        lenient().when(securityInfoManager.hasPrivilege(any(), eq("_eChart"), eq("r"), any()))
                .thenReturn(true);
        lenient().when(caseManagementMgr.isClientInProgramDomain(any(List.class), any(List.class)))
                .thenReturn(true);
        lenient().when(securityInfoManager.isAllowedAccessToPatientRecord(any(), any()))
                .thenReturn(true);
    }

    private ObjectNode emptyJson() {
        return new ObjectMapper().createObjectNode();
    }

    @Test
    @DisplayName("should deny access when caller lacks _eChart read privilege")
    void shouldDenyAccess_whenCallerLacksEChartPrivilege() {
        when(securityInfoManager.hasPrivilege(any(), eq("_eChart"), eq("r"), any())).thenReturn(false);

        ObjectNode json = emptyJson();

        assertThatThrownBy(() -> service.getCurrentNote(DEMOGRAPHIC_NO, json))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("should deny access when demographicNo is outside the caller's program domain")
    void shouldDenyAccess_whenDemographicNoNotInCallerProgramDomain() {
        when(caseManagementMgr.isClientInProgramDomain(any(List.class), any(List.class))).thenReturn(false);
        when(caseManagementMgr.isClientReferredInProgramDomain(any(List.class), eq(String.valueOf(DEMOGRAPHIC_NO))))
                .thenReturn(false);

        ObjectNode json = emptyJson();

        assertThatThrownBy(() -> service.getCurrentNote(DEMOGRAPHIC_NO, json))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("should deny access when the patient has an eChart access opt-out override")
    void shouldDenyAccess_whenPatientHasOptOutOverride() {
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(), any())).thenReturn(false);

        ObjectNode json = emptyJson();

        assertThatThrownBy(() -> service.getCurrentNote(DEMOGRAPHIC_NO, json))
                .isInstanceOf(AccessDeniedException.class);
    }
}
