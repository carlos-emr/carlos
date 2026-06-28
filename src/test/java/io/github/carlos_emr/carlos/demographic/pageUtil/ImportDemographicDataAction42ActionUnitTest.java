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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        @SuppressWarnings("unchecked")
        List<String> warnings = (List<String>) getMockRequest().getAttribute("warnings");
        assertThat(warnings).contains(NO_VALID_XML_WARNING);
        assertThat(getMockRequest().getAttribute("importlog")).isNotNull();
        assertThat(getMockResponse().getContentType()).contains("application/json");
        assertThat(getMockResponse().getContentAsString())
                .contains(NO_VALID_XML_WARNING)
                .contains("importLog");
    }
}
