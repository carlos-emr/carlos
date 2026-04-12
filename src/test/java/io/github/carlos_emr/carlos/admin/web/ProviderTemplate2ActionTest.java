/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
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
package io.github.carlos_emr.carlos.admin.web;

import io.github.carlos_emr.carlos.commn.dao.EncounterTemplateDao;
import io.github.carlos_emr.carlos.commn.model.EncounterTemplate;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ProviderTemplate2Action} covering CRUD operations,
 * privilege checks, POST enforcement, and error handling.
 *
 * @since 2026-04-11
 */
@DisplayName("ProviderTemplate2Action")
@Tag("unit")
@Tag("admin")
class ProviderTemplate2ActionTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private EncounterTemplateDao mockEncounterTemplateDao;
    @Mock private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(EncounterTemplateDao.class, mockEncounterTemplateDao);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
            .thenReturn(mockLoggedInInfo);
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("doc1");

        when(mockEncounterTemplateDao.findAll()).thenReturn(Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
    }

    private ProviderTemplate2Action createActionWithPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_newCasemgmt.templates"), eq("w"), isNull()))
            .thenReturn(true);
        return new ProviderTemplate2Action();
    }

    @Nested
    @DisplayName("Privilege checks")
    class PrivilegeChecks {

        @Test
        @DisplayName("should throw SecurityException when privilege is denied")
        void shouldThrowSecurityException_whenPrivilegeDenied() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_newCasemgmt.templates"), eq("w"), isNull()))
                .thenReturn(false);

            ProviderTemplate2Action action = new ProviderTemplate2Action();
            assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
        }
    }

    @Nested
    @DisplayName("Save operations")
    class SaveOperations {

        @Test
        @DisplayName("should create new template when none exists")
        void shouldCreateNewTemplate_whenNoneExists() throws Exception {
            ProviderTemplate2Action action = createActionWithPrivilege();
            mockRequest.setMethod("POST");
            mockRequest.setParameter("dboperation", "Save");
            mockRequest.setParameter("name", "NewTemplate");
            mockRequest.setParameter("value", "template content");
            when(mockEncounterTemplateDao.find("NewTemplate")).thenReturn(null);

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            verify(mockEncounterTemplateDao).persist(any(EncounterTemplate.class));
            assertThat((String) mockRequest.getAttribute("resultMsg"))
                .isEqualTo("Template saved.");
        }

        @Test
        @DisplayName("should update existing template when one exists")
        void shouldUpdateExistingTemplate_whenOneExists() throws Exception {
            ProviderTemplate2Action action = createActionWithPrivilege();
            mockRequest.setMethod("POST");
            mockRequest.setParameter("dboperation", "Save");
            mockRequest.setParameter("name", "ExistingTemplate");
            mockRequest.setParameter("value", "updated content");

            EncounterTemplate existing = mock(EncounterTemplate.class);
            when(mockEncounterTemplateDao.find("ExistingTemplate")).thenReturn(existing);

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            verify(existing).setEncounterTemplateValue("updated content");
            verify(mockEncounterTemplateDao).merge(existing);
            assertThat((String) mockRequest.getAttribute("resultMsg"))
                .isEqualTo("Template saved.");
        }

        @Test
        @DisplayName("should set error message when template name is empty")
        void shouldSetErrorMessage_whenTemplateNameIsEmpty() throws Exception {
            ProviderTemplate2Action action = createActionWithPrivilege();
            mockRequest.setMethod("POST");
            mockRequest.setParameter("dboperation", "Save");
            mockRequest.setParameter("name", "  ");

            action.execute();

            assertThat((String) mockRequest.getAttribute("resultMsg"))
                .isEqualTo("Template name is required.");
            verify(mockEncounterTemplateDao, never()).persist(any());
            verify(mockEncounterTemplateDao, never()).merge(any());
        }

        @Test
        @DisplayName("should set error message when DAO persist throws RuntimeException")
        void shouldSetErrorMessage_whenDaoPersistThrows() throws Exception {
            ProviderTemplate2Action action = createActionWithPrivilege();
            mockRequest.setMethod("POST");
            mockRequest.setParameter("dboperation", "Save");
            mockRequest.setParameter("name", "FailTemplate");
            mockRequest.setParameter("value", "val");
            when(mockEncounterTemplateDao.find("FailTemplate")).thenReturn(null);
            doThrow(new RuntimeException("DB error")).when(mockEncounterTemplateDao).persist(any());

            action.execute();

            assertThat((String) mockRequest.getAttribute("resultMsg"))
                .isEqualTo("Failed to save template.");
        }
    }

    @Nested
    @DisplayName("Delete operations")
    class DeleteOperations {

        @Test
        @DisplayName("should delete existing template")
        void shouldDeleteExistingTemplate() throws Exception {
            ProviderTemplate2Action action = createActionWithPrivilege();
            mockRequest.setMethod("POST");
            mockRequest.setParameter("dboperation", "Delete");
            mockRequest.setParameter("name", "ToDelete");

            EncounterTemplate toDelete = mock(EncounterTemplate.class);
            when(mockEncounterTemplateDao.find("ToDelete")).thenReturn(toDelete);

            action.execute();

            verify(mockEncounterTemplateDao).remove(toDelete);
            assertThat((String) mockRequest.getAttribute("resultMsg"))
                .isEqualTo("Template deleted.");
        }

        @Test
        @DisplayName("should set not-found message when template does not exist")
        void shouldSetNotFoundMessage_whenTemplateDoesNotExist() throws Exception {
            ProviderTemplate2Action action = createActionWithPrivilege();
            mockRequest.setMethod("POST");
            mockRequest.setParameter("dboperation", "Delete");
            mockRequest.setParameter("name", "NonExistent");
            when(mockEncounterTemplateDao.find("NonExistent")).thenReturn(null);

            action.execute();

            verify(mockEncounterTemplateDao, never()).remove(any());
            assertThat((String) mockRequest.getAttribute("resultMsg"))
                .isEqualTo("Template not found.");
        }

        @Test
        @DisplayName("should set error message when DAO remove throws RuntimeException")
        void shouldSetErrorMessage_whenDaoRemoveThrows() throws Exception {
            ProviderTemplate2Action action = createActionWithPrivilege();
            mockRequest.setMethod("POST");
            mockRequest.setParameter("dboperation", "Delete");
            mockRequest.setParameter("name", "FailDelete");

            EncounterTemplate toDelete = mock(EncounterTemplate.class);
            when(mockEncounterTemplateDao.find("FailDelete")).thenReturn(toDelete);
            doThrow(new RuntimeException("DB error")).when(mockEncounterTemplateDao).remove(toDelete);

            action.execute();

            assertThat((String) mockRequest.getAttribute("resultMsg"))
                .isEqualTo("Failed to delete template.");
        }
    }

    @Nested
    @DisplayName("Edit operations")
    class EditOperations {

        @Test
        @DisplayName("should load template into editTemplate attribute on Edit")
        void shouldLoadTemplate_onEdit() throws Exception {
            ProviderTemplate2Action action = createActionWithPrivilege();
            mockRequest.setParameter("dboperation", "Edit");
            mockRequest.setParameter("name", "EditMe");

            EncounterTemplate editTemplate = mock(EncounterTemplate.class);
            when(mockEncounterTemplateDao.find("EditMe")).thenReturn(editTemplate);

            action.execute();

            assertThat(mockRequest.getAttribute("editTemplate")).isSameAs(editTemplate);
        }

        @Test
        @DisplayName("should allow Edit via GET without POST enforcement")
        void shouldAllowEdit_viaGet() throws Exception {
            ProviderTemplate2Action action = createActionWithPrivilege();
            mockRequest.setMethod("GET");
            mockRequest.setParameter("dboperation", "Edit");
            mockRequest.setParameter("name", "EditMe");

            when(mockEncounterTemplateDao.find("EditMe")).thenReturn(null);

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        }
    }

    @Nested
    @DisplayName("POST enforcement")
    class PostEnforcement {

        @Test
        @DisplayName("should not execute Save on GET request")
        void shouldNotExecuteSave_onGetRequest() throws Exception {
            ProviderTemplate2Action action = createActionWithPrivilege();
            mockRequest.setMethod("GET");
            mockRequest.setParameter("dboperation", "Save");
            mockRequest.setParameter("name", "MyTemplate");
            mockRequest.setParameter("value", "content");

            action.execute();

            verify(mockEncounterTemplateDao, never()).persist(any());
            verify(mockEncounterTemplateDao, never()).merge(any());
        }

        @Test
        @DisplayName("should not execute Delete on GET request")
        void shouldNotExecuteDelete_onGetRequest() throws Exception {
            ProviderTemplate2Action action = createActionWithPrivilege();
            mockRequest.setMethod("GET");
            mockRequest.setParameter("dboperation", "Delete");
            mockRequest.setParameter("name", "MyTemplate");

            action.execute();

            verify(mockEncounterTemplateDao, never()).remove(any());
        }
    }

    @Nested
    @DisplayName("Always-loaded attributes")
    class AlwaysLoadedAttributes {

        @Test
        @DisplayName("should always load allTemplates attribute")
        void shouldAlwaysLoadAllTemplates() throws Exception {
            ProviderTemplate2Action action = createActionWithPrivilege();
            List<EncounterTemplate> templates = List.of(mock(EncounterTemplate.class));
            when(mockEncounterTemplateDao.findAll()).thenReturn(templates);

            action.execute();

            assertThat(mockRequest.getAttribute("allTemplates")).isSameAs(templates);
            assertThat(mockRequest.getAttribute("curUser_no")).isEqualTo("doc1");
        }
    }
}
