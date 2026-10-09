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
package io.github.carlos_emr.carlos.demographic.pageUtil;

import io.github.carlos_emr.carlos.encounter.data.EctProgramManager;
import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.util.LabelValueBean;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import static org.mockito.Mockito.mock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.springframework.web.context.WebApplicationContext;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.demographic.data.DemographicData;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.mockConstruction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ImportDemographicDataAction42Action}.
 */
@DisplayName("ImportDemographicDataAction42Action Tests")
@Tag("unit")
@Tag("web")
@Tag("demographic")
class ImportDemographicDataAction42ActionUnitTest extends CarlosWebTestBase {

    private static final String LOGGED_IN_INFO_SESSION_KEY = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
    private static final String TEST_PROVIDER = "999998";
    private static final String NO_VALID_XML_WARNING = "No valid XML files found to import. Please check the uploaded file structure.";

    @TempDir
    Path tempDir;

    @Mock
    private EctProgramManager mockEctProgramManager;

    @Mock
    private NioFileManager mockNioFileManager;

    @Mock
    private ProviderDao mockProviderDao;

    private ImportDemographicDataAction42Action action;
    private org.mockito.MockedStatic<io.github.carlos_emr.carlos.utility.SpringUtils> lockBeans;

    @org.junit.jupiter.api.AfterEach
    void restoreLockLookup() {
        if (lockBeans != null) lockBeans.close();
    }

    @BeforeEach
    void setUp() throws Exception {
        replaceSpringUtilsBean(EctProgramManager.class, mockEctProgramManager);
        replaceSpringUtilsBean(NioFileManager.class, mockNioFileManager);
        replaceSpringUtilsBean(ProviderDao.class, mockProviderDao);
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        allowPrivilege("_demographic", "w");

        getMockSession().getServletContext().setAttribute(
                WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, webApplicationContext);

        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);
        setSessionAttribute("user", TEST_PROVIDER);
        setSessionAttribute(LOGGED_IN_INFO_SESSION_KEY, mockLoggedInInfo);

        when(mockEctProgramManager.getProgramBeans(TEST_PROVIDER, null))
                .thenReturn(List.of(new LabelValueBean("Default Program", "0")));
        when(mockEctProgramManager.getDefaultProgramId(TEST_PROVIDER)).thenReturn(0);
        when(mockProviderDao.getActiveProviders()).thenReturn(List.of());

        action = new ImportDemographicDataAction42Action();
    }

    @Test
    @DisplayName("should return success when no upload is present")
    void shouldReturnSuccess_whenNoUploadIsPresent() throws Exception {
        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
    }

    @ParameterizedTest
    @ValueSource(strings = {".hidden.xml", "../patient.xml"})
    void shouldReturnJsonWarning_whenUploadFilenameIsRejected(String name) throws Exception {
        UploadedFile upload = mock(UploadedFile.class);
        when(upload.getContent()).thenReturn(Files.createFile(tempDir.resolve("upload.tmp")).toFile());
        when(upload.getOriginalName()).thenReturn(name);
        action.withUploadedFiles(List.of(upload));
        ReflectionTestUtils.setField(action, "importedPatients", 1);
        ReflectionTestUtils.setField(action, "refusedPatients", 2);
        assertThat(executeAction(action)).isEqualTo(ActionSupport.NONE);
        var json = new ObjectMapper().readTree(getMockResponse().getContentAsString());
        assertThat(json.get("warnings").get(0).asText()).isEqualTo(PathValidationUtils.INVALID_FILENAME_MESSAGE);
        assertThat(json.get("importedPatients").asInt()).isZero();
        assertThat(json.get("refusedPatients").asInt()).isZero();
        assertThat(json.get("importLog").isNull()).isTrue();
        assertThat(getMockResponse().getContentType()).startsWith("application/json");
    }

    @Test
    @DisplayName("should return success when uploaded filename is blank")
    void shouldReturnSuccess_whenUploadedFilenameIsBlank() throws Exception {
        // The blank filename path returns before any file IO, so the File only needs to be non-null.
        action.setImportFile(new File("dummy-upload"));
        action.setImportFileFileName(" ");

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
    }

    /**
     * A filename without a multipart file should be treated like the initial page load and render the import form.
     */
    @Test
    @DisplayName("should return success when uploaded file is missing")
    void shouldReturnSuccess_whenUploadedFileIsMissing() throws Exception {
        action.setImportFile(null);
        action.setImportFileFileName("patient.xml");

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    @DisplayName("should return logout when user session attribute is missing")
    void shouldReturnLogout_whenUserSessionAttributeIsMissing() throws Exception {
        setSessionAttribute("user", null);

        String result = executeAction(action);

        assertThat(result).isEqualTo("logout");
    }

    @Test
    @DisplayName("should return logout when user session attribute is whitespace only")
    void shouldReturnLogout_whenUserSessionAttributeIsWhitespaceOnly() throws Exception {
        setSessionAttribute("user", "   ");

        String result = executeAction(action);

        assertThat(result).isEqualTo("logout");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "NULL"})
    @DisplayName("should return logout when user session attribute is literal null")
    void shouldReturnLogout_whenUserSessionAttributeIsLiteralNullString(String sessionUser) throws Exception {
        setSessionAttribute("user", sessionUser);

        String result = executeAction(action);

        assertThat(result).isEqualTo("logout");
    }

    @Test
    @DisplayName("should return logout when logged in info is missing")
    void shouldReturnLogout_whenLoggedInInfoIsMissing() throws Exception {
        setSessionAttribute(LOGGED_IN_INFO_SESSION_KEY, null);

        String result = executeAction(action);

        assertThat(result).isEqualTo("logout");
    }

    private void prepareLockResult(int lockResult) throws Exception {
        javax.sql.DataSource dataSource = mock(javax.sql.DataSource.class);
        java.sql.Connection connection = mock(java.sql.Connection.class);
        java.sql.PreparedStatement statement = mock(java.sql.PreparedStatement.class);
        java.sql.ResultSet resultSet = mock(java.sql.ResultSet.class);
        // Replacing the shared Spring singleton destroys its dependent EntityManagerFactory.
        // Scope only this lookup to the action test and preserve the integration context.
        lockBeans = org.mockito.Mockito.mockStatic(io.github.carlos_emr.carlos.utility.SpringUtils.class,
                org.mockito.Mockito.CALLS_REAL_METHODS);
        lockBeans.when(() -> io.github.carlos_emr.carlos.utility.SpringUtils.getBean(javax.sql.DataSource.class))
                .thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(org.mockito.ArgumentMatchers.anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt(1)).thenReturn(lockResult);
    }

    @Test
    void shouldKeepSharedPersistenceOpen_whenPreparingImportLock() throws Exception {
        var factory = applicationContext.getBean("entityManagerFactory", jakarta.persistence.EntityManagerFactory.class);
        var source = applicationContext.getBean("dataSource");
        prepareLockResult(0);
        assertThat(factory.isOpen()).isTrue();
        assertThat(applicationContext.getBean("entityManagerFactory")).isSameAs(factory);
        assertThat(applicationContext.getBean("dataSource")).isSameAs(source);
    }

    @Test
    void shouldReturnRetryWarning_withoutProcessingFile_whenAnotherImportOwnsLock() throws Exception {
        prepareLockResult(0);
        action.setImportFile(Files.createFile(tempDir.resolve("waiting.xml")).toFile());
        action.setImportFileFileName("waiting.xml");
        assertThat(executeAction(action)).isEqualTo(ActionSupport.NONE);
        var json = new ObjectMapper().readTree(getMockResponse().getContentAsString());
        assertThat(json.get("importedPatients").asInt()).isZero();
        assertThat(json.get("refusedPatients").asInt()).isZero();
        assertThat(json.get("importLog").isNull()).isTrue();
        assertThat(json.get("warnings").get(0).asText()).contains("Another CDS import", "retry");
        org.mockito.Mockito.verifyNoInteractions(mockNioFileManager);
    }

    @Test
    @DisplayName("should set import response attributes when upload file and filename are present")
    void shouldSetImportResponseAttributes_whenUploadFileAndFilenameArePresent() throws Exception {
        Path uploadFile = Files.createTempFile(tempDir, "demographic-import-", ".txt");
        Path processingDirectory = Files.createTempDirectory(tempDir, "processing-");
        Files.writeString(uploadFile, "not xml");
        getMockSession().getServletContext().setAttribute("jakarta.servlet.context.tempdir", tempDir.toFile());

        when(mockNioFileManager.createTempFile(eq("patient.txt"), any(ByteArrayOutputStream.class)))
                .thenReturn(processingDirectory);

        action.setImportFile(uploadFile.toFile());
        action.setImportFileFileName("patient.txt");

        prepareLockResult(1);

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        var json = new ObjectMapper().readTree(getMockResponse().getContentAsString());
        assertThat(json.get("importedPatients").asInt()).isZero();
        assertThat(json.get("refusedPatients").asInt()).isZero();
        @SuppressWarnings("unchecked")
        List<String> warnings = (List<String>) getMockRequest().getAttribute("warnings");
        assertThat(warnings).contains(NO_VALID_XML_WARNING);
        assertThat(getMockRequest().getAttribute("importlog")).isNotNull();
    }

    @Test
    void shouldCountRefusedPatient_withoutReusingPriorIdOrSchedulingContacts() throws Exception {
        Path xml = tempDir.resolve("duplicate.xml");
        Files.copy(Path.of("src/test/resources/demographic/cds-import-summary.xml"), xml);
        action.demographicNo = "123";
        action.demographic = new Demographic();
        ReflectionTestUtils.setField(action, "importedPatients", 1);
        ArrayList<String> warnings = new ArrayList<>();
        ArrayList<String[]> logs = new ArrayList<>();
        List<Path> contacts = new ArrayList<>();
        Method process = ImportDemographicDataAction42Action.class.getDeclaredMethod("processXmlFile",
                LoggedInInfo.class, Path.class, Path.class, ArrayList.class, ArrayList.class,
                jakarta.servlet.http.HttpServletRequest.class, int.class, List.class, int.class, List.class);
        process.setAccessible(true);
        try (var demographics = mockConstruction(DemographicData.class, (mock, context) ->
                when(mock.getDemographicWithLastFirstDOB(any(), any(), any(), any()))
                        .thenReturn(new ArrayList<>(List.of(new Demographic()))))) {
            process.invoke(action, mockLoggedInInfo, xml, tempDir, warnings, logs, getMockRequest(), 0, null, 0, contacts);
        }
        assertThat(ReflectionTestUtils.getField(action, "importedPatients")).isEqualTo(1);
        assertThat(ReflectionTestUtils.getField(action, "refusedPatients")).isEqualTo(1);
        assertThat(action.demographicNo).isNull();
        assertThat(action.demographic).isNull();
        assertThat(contacts).isEmpty();
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst()[0]).isNull();
        assertThat(warnings).anyMatch(warning -> warning.contains("already exist! Not imported."));
        assertThat(warnings).noneMatch(warning -> warning.contains("Demographic no=123"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/tmp/report.pdf", "C:/reports/report.pdf", "C:\\reports\\report.pdf"})
    @DisplayName("should identify absolute report paths without constructing File objects")
    void shouldIdentifyAbsoluteReportPaths_withoutConstructingFileObjects(String path) throws Exception {
        assertThat(invokeIsAbsoluteReportPath(path)).isTrue();
    }

    @Test
    @DisplayName("should allow relative report paths")
    void shouldAllowRelativeReportPaths_whenPathIsRelative() throws Exception {
        assertThat(invokeIsAbsoluteReportPath("reports/result.pdf")).isFalse();
    }

    @Test
    @DisplayName("should reject report paths containing null characters")
    void shouldRejectReportPath_whenPathContainsNullCharacter() throws Exception {
        assertThat(invokeIsAbsoluteReportPath("reports/result.pdf\0")).isTrue();
    }

    @Test
    @DisplayName("should extract report file name from platform-neutral separators")
    void shouldExtractReportFileName_fromPlatformNeutralSeparators() throws Exception {
        assertThat(invokeExtractReportFileName("nested/result.pdf")).isEqualTo("result.pdf");
        assertThat(invokeExtractReportFileName("nested\\result.pdf")).isEqualTo("result.pdf");
        assertThat(invokeExtractReportFileName("nested/result.pdf/")).isEqualTo("result.pdf");
        assertThat(invokeExtractReportFileName("result.pdf")).isEqualTo("result.pdf");
    }

    private boolean invokeIsAbsoluteReportPath(String path) throws Exception {
        Method method = ImportDemographicDataAction42Action.class.getDeclaredMethod("isAbsoluteReportPath", String.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(action, path);
    }

    private String invokeExtractReportFileName(String path) throws Exception {
        Method method = ImportDemographicDataAction42Action.class.getDeclaredMethod("extractReportFileName", String.class);
        method.setAccessible(true);
        return (String) method.invoke(action, path);
    }
}
