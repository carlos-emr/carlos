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
package io.github.carlos_emr.carlos.webserv.rest;

import io.github.carlos_emr.OscarProperties;
import io.github.carlos_emr.carlos.commn.dao.EFormDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.commn.model.EForm;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.GenericRestResponse.ResponseStatus;
import io.github.carlos_emr.carlos.webserv.rest.to.RestResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.EFormTo1;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EFormService}.
 *
 * <p>Tests eForm load, save, and update business logic using a testable subclass
 * that overrides {@code getLoggedInInfo()} to avoid requiring the CXF HTTP request
 * context at test time. Dependencies are injected via reflection.</p>
 *
 * @since 2026-03-14
 * @see EFormService
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EFormService Unit Tests")
@Tag("unit")
@Tag("fast")
class EFormServiceUnitTest extends CarlosUnitTestBase {

    @Mock
    private EFormDao mockEFormDao;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    private LoggedInInfo loggedInInfo;
    private MockedStatic<OscarProperties> oscarPropertiesMock;

    /**
     * Testable subclass that overrides getLoggedInInfo() to bypass CXF context.
     */
    private EFormService service;

    @BeforeEach
    void setUp() throws Exception {
        // Stub OscarProperties static calls (used in RestResponseHeaders constructor)
        oscarPropertiesMock = mockStatic(OscarProperties.class);
        oscarPropertiesMock.when(OscarProperties::getBuildDate).thenReturn("2026-01-01");
        oscarPropertiesMock.when(OscarProperties::getBuildTag).thenReturn("test");

        // LogAction is already silenced by CarlosUnitTestBase.setUpSpringUtilsMocking()

        // Build a minimal LoggedInInfo
        Provider provider = mock(Provider.class);
        when(provider.getProviderNo()).thenReturn("101");
        loggedInInfo = new LoggedInInfo();
        Field providerField = LoggedInInfo.class.getDeclaredField("loggedInProvider");
        providerField.setAccessible(true);
        providerField.set(loggedInInfo, provider);
        loggedInInfo.setIp("127.0.0.1");

        // Create a testable subclass
        LoggedInInfo capturedInfo = loggedInInfo;
        service = new EFormService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return capturedInfo;
            }
        };

        // Inject the mocked DAO and SecurityInfoManager
        Field eFormDaoField = EFormService.class.getDeclaredField("eFormDao");
        eFormDaoField.setAccessible(true);
        eFormDaoField.set(service, mockEFormDao);

        Field secField = EFormService.class.getDeclaredField("securityInfoManager");
        secField.setAccessible(true);
        secField.set(service, mockSecurityInfoManager);

        // Default: grant all privileges
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        if (oscarPropertiesMock != null) oscarPropertiesMock.close();
    }

    // ─── loadEForm ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("loadEForm")
    @Tag("read")
    class LoadEForm {

        @Test
        @DisplayName("should return success response when eForm exists for given id")
        void shouldReturnSuccessResponse_whenEFormExistsForGivenId() {
            EForm eForm = buildValidEForm(1, "Intake Form", "<html>body</html>");
            when(mockEFormDao.findById(1)).thenReturn(eForm);

            RestResponse<EFormTo1> response = service.loadEForm(1);

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.SUCCESS);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getFormName()).isEqualTo("Intake Form");
        }

        @Test
        @DisplayName("should return error response when eForm id does not exist")
        void shouldReturnErrorResponse_whenEFormIdDoesNotExist() {
            when(mockEFormDao.findById(999)).thenReturn(null);

            RestResponse<EFormTo1> response = service.loadEForm(999);

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.ERROR);
            assertThat(response.getError().getMessage()).isEqualTo("Failed to find EForm");
        }
    }

    // ─── saveEForm (typed object) ───────────────────────────────────────────────

    @Nested
    @DisplayName("saveEForm (EFormTo1)")
    @Tag("create")
    class SaveEFormTyped {

        @Test
        @DisplayName("should persist and return success when name is unique and data is valid")
        void shouldPersistAndReturnSuccess_whenNameIsUniqueAndDataIsValid() {
            EFormTo1 to = buildValidEFormTo1("Consent Form", "<html>valid</html>");
            when(mockEFormDao.findByName("Consent Form")).thenReturn(null);

            RestResponse<EFormTo1> response = service.saveEForm(to);

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.SUCCESS);
            verify(mockEFormDao).persist(any(EForm.class));
        }

        @Test
        @DisplayName("should return error when form name is already in use")
        void shouldReturnError_whenFormNameAlreadyInUse() {
            EFormTo1 to = buildValidEFormTo1("Existing Form", "<html>x</html>");
            when(mockEFormDao.findByName("Existing Form")).thenReturn(new EForm());

            RestResponse<EFormTo1> response = service.saveEForm(to);

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.ERROR);
            assertThat(response.getError().getMessage()).isEqualTo("EForm Name Already in Use");
            verify(mockEFormDao, never()).persist(any());
        }

        @Test
        @DisplayName("should return error when form HTML is blank")
        void shouldReturnError_whenFormHtmlIsBlank() {
            EFormTo1 to = buildValidEFormTo1("My Form", "   ");
            when(mockEFormDao.findByName("My Form")).thenReturn(null);

            RestResponse<EFormTo1> response = service.saveEForm(to);

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.ERROR);
            assertThat(response.getError().getMessage()).isEqualTo("Invalid Eform Data");
        }
    }

    // ─── saveEForm (JSON string) ────────────────────────────────────────────────

    @Nested
    @DisplayName("saveEForm (JSON string)")
    @Tag("create")
    class SaveEFormJson {

        @Test
        @DisplayName("should return error when JSON string is null")
        void shouldReturnError_whenJsonStringIsNull() {
            RestResponse<EFormTo1> response = service.saveEForm((String) null);

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.ERROR);
            assertThat(response.getError().getMessage()).isEqualTo("Invalid JSON");
        }

        @Test
        @DisplayName("should return error when JSON string is blank")
        void shouldReturnError_whenJsonStringIsBlank() {
            RestResponse<EFormTo1> response = service.saveEForm("   ");

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.ERROR);
            assertThat(response.getError().getMessage()).isEqualTo("Invalid JSON");
        }

        @Test
        @DisplayName("should return error when JSON string is malformed")
        void shouldReturnError_whenJsonStringIsMalformed() {
            RestResponse<EFormTo1> response = service.saveEForm("{not-valid-json");

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.ERROR);
            assertThat(response.getError().getMessage()).isEqualTo("Invalid JSON");
        }

        @Test
        @DisplayName("should persist and return success for valid JSON with required fields")
        void shouldPersistAndReturnSuccess_forValidJsonWithRequiredFields() {
            String json = "{\"formName\":\"JSON Form\",\"formHtml\":\"<html>ok</html>\"}";
            when(mockEFormDao.findByName("JSON Form")).thenReturn(null);

            RestResponse<EFormTo1> response = service.saveEForm(json);

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.SUCCESS);
            verify(mockEFormDao).persist(any(EForm.class));
        }

        @Test
        @DisplayName("should return error for JSON when form name is already in use")
        void shouldReturnError_whenFormNameAlreadyInUseForJson() {
            String json = "{\"formName\":\"Duplicate\",\"formHtml\":\"<html>x</html>\"}";
            when(mockEFormDao.findByName("Duplicate")).thenReturn(new EForm());

            RestResponse<EFormTo1> response = service.saveEForm(json);

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.ERROR);
            assertThat(response.getError().getMessage()).isEqualTo("EForm Name Already in Use");
        }

        @Test
        @DisplayName("should handle optional roleType field in JSON")
        void shouldHandleOptionalRoleType_inJson() {
            String json = "{\"formName\":\"Role Form\",\"formHtml\":\"<html>r</html>\",\"roleType\":\"nurse\"}";
            when(mockEFormDao.findByName("Role Form")).thenReturn(null);

            RestResponse<EFormTo1> response = service.saveEForm(json);

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.SUCCESS);
        }
    }

    // ─── updateEForm (typed object) ─────────────────────────────────────────────

    @Nested
    @DisplayName("updateEForm (EFormTo1)")
    @Tag("update")
    class UpdateEFormTyped {

        @Test
        @DisplayName("should return error when eformTo1 is null")
        void shouldReturnError_whenEformTo1IsNull() {
            RestResponse<EFormTo1> response = service.updateEForm(1, null);

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.ERROR);
            assertThat(response.getError().getMessage()).isEqualTo("Path id does not match payload id");
        }

        @Test
        @DisplayName("should return error when path id does not match body id")
        void shouldReturnError_whenPathIdDoesNotMatchBodyId() {
            EFormTo1 to = buildValidEFormTo1("My Form", "<html>x</html>");
            to.setId(2);

            RestResponse<EFormTo1> response = service.updateEForm(1, to);

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.ERROR);
            assertThat(response.getError().getMessage()).isEqualTo("Path id does not match payload id");
            verify(mockEFormDao, never()).merge(any());
        }

        @Test
        @DisplayName("should return error when body id is null")
        void shouldReturnError_whenBodyIdIsNull() {
            EFormTo1 to = buildValidEFormTo1("My Form", "<html>x</html>");
            // id is null by default

            RestResponse<EFormTo1> response = service.updateEForm(1, to);

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.ERROR);
        }

        @Test
        @DisplayName("should merge and return success when path and body ids match and data is valid")
        void shouldMergeAndReturnSuccess_whenPathAndBodyIdsMatchAndDataIsValid() {
            EFormTo1 to = buildValidEFormTo1("Updated Form", "<html>updated</html>");
            to.setId(5);

            RestResponse<EFormTo1> response = service.updateEForm(5, to);

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.SUCCESS);
            verify(mockEFormDao).merge(any(EForm.class));
        }
    }

    // ─── updateEFormJson ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateEFormJson")
    @Tag("update")
    class UpdateEFormJson {

        @Test
        @DisplayName("should return error when JSON string is null")
        void shouldReturnError_whenJsonStringIsNull() {
            RestResponse<EFormTo1> response = service.updateEFormJson(1, null);

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.ERROR);
            assertThat(response.getError().getMessage()).isEqualTo("Invalid JSON");
        }

        @Test
        @DisplayName("should return error when JSON string is blank")
        void shouldReturnError_whenJsonStringIsBlank() {
            RestResponse<EFormTo1> response = service.updateEFormJson(1, "");

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.ERROR);
        }

        @Test
        @DisplayName("should return error when JSON is malformed")
        void shouldReturnError_whenJsonIsMalformed() {
            RestResponse<EFormTo1> response = service.updateEFormJson(1, "{{bad json");

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.ERROR);
        }

        @Test
        @DisplayName("should return error when eForm does not exist for given dataId")
        void shouldReturnError_whenEFormDoesNotExistForGivenDataId() {
            String json = "{\"formName\":\"Updated\",\"formHtml\":\"<html>u</html>\"}";
            when(mockEFormDao.findById(99)).thenReturn(null);

            RestResponse<EFormTo1> response = service.updateEFormJson(99, json);

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.ERROR);
            verify(mockEFormDao, never()).merge(any());
        }

        @Test
        @DisplayName("should merge and return success when eForm exists and JSON is valid")
        void shouldMergeAndReturnSuccess_whenEFormExistsAndJsonIsValid() {
            EForm existing = buildValidEForm(7, "Old Name", "<html>old</html>");
            when(mockEFormDao.findById(7)).thenReturn(existing);
            String json = "{\"formName\":\"New Name\",\"formHtml\":\"<html>new</html>\"}";

            RestResponse<EFormTo1> response = service.updateEFormJson(7, json);

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.SUCCESS);
            verify(mockEFormDao).merge(any(EForm.class));
        }

        @Test
        @DisplayName("should ignore JSON body id and use path dataId for update")
        void shouldIgnoreJsonBodyId_whenUpdatingEForm() {
            EForm existing = buildValidEForm(10, "Original", "<html>original</html>");
            when(mockEFormDao.findById(10)).thenReturn(existing);
            // JSON body contains id=999 which should be ignored; path dataId=10 is authoritative
            String json = "{\"id\":999,\"formName\":\"Updated\",\"formHtml\":\"<html>new</html>\"}";

            RestResponse<EFormTo1> response = service.updateEFormJson(10, json);

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.SUCCESS);
            verify(mockEFormDao).merge(existing);
            assertThat(existing.getFormName()).isEqualTo("Updated");
        }

        @Test
        @DisplayName("should preserve existing subject when formSubject is absent from JSON")
        void shouldPreserveExistingSubject_whenFormSubjectAbsentFromJson() {
            EForm existing = buildValidEForm(8, "My Form", "<html>x</html>");
            existing.setSubject("Original Subject");
            when(mockEFormDao.findById(8)).thenReturn(existing);
            String json = "{\"formName\":\"My Form\",\"formHtml\":\"<html>x</html>\"}";

            service.updateEFormJson(8, json);

            // The existing subject is preserved — verify merge was called with the eForm still having original subject
            verify(mockEFormDao).merge(existing);
            assertThat(existing.getSubject()).isEqualTo("Original Subject");
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private EForm buildValidEForm(Integer id, String formName, String formHtml) {
        EForm eForm = new EForm();
        try {
            Field idField = EForm.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(eForm, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        eForm.setFormName(formName);
        eForm.setFormHtml(formHtml);
        eForm.setCurrent(true);
        return eForm;
    }

    private EFormTo1 buildValidEFormTo1(String formName, String formHtml) {
        EFormTo1 to = new EFormTo1();
        to.setFormName(formName);
        to.setFormHtml(formHtml);
        return to;
    }
}
