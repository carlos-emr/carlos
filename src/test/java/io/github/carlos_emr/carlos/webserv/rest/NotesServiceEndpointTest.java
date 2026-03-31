/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 */
package io.github.carlos_emr.carlos.webserv.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.carlos_emr.carlos.PMmodule.dao.SecUserRoleDao;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.PMmodule.service.ProviderManager;
import io.github.carlos_emr.carlos.casemgmt.dao.IssueDAO;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.service.NoteSelectionResult;
import io.github.carlos_emr.carlos.casemgmt.service.NoteService;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.NoteSelectionTo1;

/**
 * HTTP-level endpoint tests for {@link NotesService} using CXF local transport.
 *
 * <p>These tests verify path routing, JSON serialization, and HTTP status codes
 * for the clinical notes REST endpoints. All dependencies are mocked.</p>
 *
 * @since 2026-03-31
 * @see CarlosRestTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("NotesService REST endpoint tests")
class NotesServiceEndpointTest extends CarlosRestTestBase {

    @Mock
    private NoteService mockNoteService;

    @Mock
    private ProgramManager2 mockProgramManager2;

    @Mock
    private ProgramManager mockProgramMgr;

    @Mock
    private CaseManagementManager mockCaseManagementMgr;

    @Mock
    private ProviderManager mockProviderMgr;

    @Mock
    private IssueDAO mockIssueDao;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private SecUserRoleDao mockSecUserRoleDao;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected Object getServiceBean() {
        NotesService service = new NotesService();
        injectDependency(service, "noteService", mockNoteService);
        injectDependency(service, "programManager2", mockProgramManager2);
        injectDependency(service, "programMgr", mockProgramMgr);
        injectDependency(service, "caseManagementMgr", mockCaseManagementMgr);
        injectDependency(service, "providerMgr", mockProviderMgr);
        injectDependency(service, "issueDao", mockIssueDao);
        injectDependency(service, "securityInfoManager", mockSecurityInfoManager);
        injectDependency(service, "secUserRoleDao", mockSecUserRoleDao);
        return service;
    }

    /** Tests for POST /notes/{demographicNo}/all endpoint. */
    @Nested
    @DisplayName("POST /notes/{demographicNo}/all")
    class GetNotesWithFilter {

        @Test
        @DisplayName("should return 200 with empty result when client not in program domain")
        void shouldReturn200WithEmptyResult_whenClientNotInProgramDomain() {
            Provider testProvider = new Provider();
            testProvider.setProviderNo("100");
            when(mockLoggedInInfo.getLoggedInProvider()).thenReturn(testProvider);
            when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("100");
            when(mockLoggedInInfo.getSession()).thenReturn(null);

            when(mockSecUserRoleDao.getUserRoles("100")).thenReturn(Collections.emptyList());

            // Client is NOT in the program domain
            when(mockCaseManagementMgr.isClientInProgramDomain(eq("100"), eq("123")))
                .thenReturn(false);
            when(mockCaseManagementMgr.isClientReferredInProgramDomain(eq("100"), eq("123")))
                .thenReturn(false);

            ObjectNode body = objectMapper.createObjectNode();

            Response response = request().path("/notes/123/all")
                .query("numToReturn", 20)
                .query("offset", 0)
                .post(body);

            assertThat(response.getStatus()).isEqualTo(200);
            NoteSelectionTo1 result = response.readEntity(NoteSelectionTo1.class);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should return 200 when client is in program domain")
        void shouldReturn200_whenClientInProgramDomain() {
            Provider testProvider = new Provider();
            testProvider.setProviderNo("100");
            when(mockLoggedInInfo.getLoggedInProvider()).thenReturn(testProvider);
            when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("100");
            when(mockLoggedInInfo.getSession()).thenReturn(null);

            when(mockSecUserRoleDao.getUserRoles("100")).thenReturn(Collections.emptyList());

            // Client IS in program domain but no role means early return
            when(mockCaseManagementMgr.isClientInProgramDomain(eq("100"), eq("456")))
                .thenReturn(true);

            ProgramProvider pp = new ProgramProvider();
            pp.setProgramId(1L);
            when(mockProgramManager2.getCurrentProgramInDomain(any(LoggedInInfo.class), eq("100")))
                .thenReturn(pp);

            // NoteService.getNotesWithFilter will be called; mock it to return empty result
            when(mockNoteService.getNotesWithFilter(any(), any()))
                .thenReturn(new NoteSelectionResult());

            ObjectNode body = objectMapper.createObjectNode();

            Response response = request().path("/notes/456/all")
                .query("numToReturn", 20)
                .query("offset", 0)
                .post(body);

            // Should return 200 regardless -- the empty role causes early return
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
