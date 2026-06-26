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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import cdsrourke.PatientDocument;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.DataExportDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.dao.forms.Rourke2009DAO;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.apache.struts2.ServletActionContext;
import org.apache.xmlbeans.XmlOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

/**
 * Unit coverage for {@code RourkeExport2Action} export-file handling (issue #3064): each export
 * must use a unique temp filename and must abort rather than package a missing/partial XML.
 *
 * @since 2026-06-26
 */
@DisplayName("RourkeExport2Action export file handling")
@Tag("unit")
@Tag("demographic")
class RourkeExport2ActionExportUnitTest extends CarlosUnitTestBase {

    @Mock
    private SecurityInfoManager securityInfoManager;
    @Mock
    private ClinicDAO clinicDAO;
    @Mock
    private DataExportDao dataExportDAO;
    @Mock
    private DemographicDao demographicDao;
    @Mock
    private Rourke2009DAO rourke2009DAO;
    @Mock
    private PartialDateDao partialDateDao;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    private AutoCloseable mocks;
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private RourkeExport2Action action;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);

        // The no-arg constructor resolves these via SpringUtils.getBean(...).
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(ClinicDAO.class, clinicDAO);
        registerMock(DataExportDao.class, dataExportDAO);
        registerMock(DemographicDao.class, demographicDao);
        registerMock(Rourke2009DAO.class, rourke2009DAO);
        // Util's static initializer resolves PartialDateDao via SpringUtils when the class loads.
        registerMock(PartialDateDao.class, partialDateDao);

        // Field initializers read request/response from ServletActionContext at construction.
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        action = new RourkeExport2Action();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    // makeFiles(...) uses only its arguments and static collaborators, not instance fields.
    private Object invokeMakeFiles(PatientDocument doc, String tmpDir) throws Throwable {
        Method makeFiles = RourkeExport2Action.class.getDeclaredMethod("makeFiles", PatientDocument.class, String.class);
        makeFiles.setAccessible(true);
        try {
            return makeFiles.invoke(action, doc, tmpDir);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @Test
    @DisplayName("should abort the export without zipping when the XML save fails")
    void shouldAbortWithoutZipping_whenXmlSaveFails(@TempDir Path tmpDir) throws Exception {
        PatientDocument document = mock(PatientDocument.class);
        doAnswer(invocation -> {
            throw new IOException("simulated disk failure");
        }).when(document).save(any(File.class), any(XmlOptions.class));

        try (MockedStatic<Util> util = mockStatic(Util.class)) {
            assertThatThrownBy(() -> invokeMakeFiles(document, tmpDir.toString()))
                    .isInstanceOf(IOException.class);

            // The export must never proceed to zip a missing/partial XML.
            util.verify(() -> Util.zipFiles(any(), anyString(), anyString()), never());
        }
    }

    @Test
    @DisplayName("should use a unique XML filename per export across repeated exports")
    void shouldUseUniqueXmlFilename_acrossRepeatedExports(@TempDir Path tmpDir) throws Exception {
        PatientDocument document = mock(PatientDocument.class);
        List<File> savedTargets = new ArrayList<>();
        doAnswer(invocation -> {
            savedTargets.add(invocation.getArgument(0, File.class));
            throw new IOException("stop before zipping");
        }).when(document).save(any(File.class), any(XmlOptions.class));

        catchThrowable(() -> invokeMakeFiles(document, tmpDir.toString()));
        catchThrowable(() -> invokeMakeFiles(document, tmpDir.toString()));

        assertThat(savedTargets).hasSize(2);
        assertThat(savedTargets.get(0).getName())
                .startsWith("Rourke2009Export-")
                .endsWith(".xml");
        assertThat(savedTargets.get(0).getName())
                .as("each export must target a distinct temp filename")
                .isNotEqualTo(savedTargets.get(1).getName());
    }

    @Test
    @DisplayName("should zip and return a unique export name when the XML save succeeds")
    void shouldZipAndReturnUniqueExportName_whenSaveSucceeds(@TempDir Path tmpDir) throws Throwable {
        PatientDocument document = mock(PatientDocument.class); // save() is a no-op success

        File documentDir = tmpDir.resolve("documents").toFile();
        assertThat(documentDir.mkdirs()).isTrue();
        CarlosProperties properties = mock(CarlosProperties.class);
        when(properties.getProperty("DOCUMENT_DIR")).thenReturn(documentDir.getAbsolutePath());

        String firstZip;
        String secondZip;
        // FileUtils is mocked so the real copyFileToDirectory (which would fail on the
        // never-actually-written temp zip) is suppressed; CarlosProperties supplies DOCUMENT_DIR.
        try (MockedStatic<Util> util = mockStatic(Util.class);
             MockedStatic<CarlosProperties> carlosProperties = mockStatic(CarlosProperties.class);
             MockedStatic<FileUtils> fileUtils = mockStatic(FileUtils.class)) {
            util.when(() -> Util.zipFiles(any(), anyString(), anyString())).thenReturn(true);
            carlosProperties.when(CarlosProperties::getInstance).thenReturn(properties);

            firstZip = (String) invokeMakeFiles(document, tmpDir.toString());
            secondZip = (String) invokeMakeFiles(document, tmpDir.toString());

            util.verify(() -> Util.zipFiles(any(), anyString(), anyString()), times(2));
            fileUtils.verify(() -> FileUtils.copyFileToDirectory(any(File.class), any(File.class)), times(2));
        }

        assertThat(firstZip).startsWith("rourke2009_export-").endsWith(".zip");
        assertThat(secondZip)
                .as("each successful export must produce a distinct zip name")
                .isNotEqualTo(firstZip);
    }

    @Test
    @DisplayName("should abort the export and not copy when DOCUMENT_DIR does not exist")
    void shouldAbortWithoutCopying_whenDocumentDirectoryIsMissing(@TempDir Path tmpDir) throws Throwable {
        PatientDocument document = mock(PatientDocument.class); // save() is a no-op success

        // A configured DOCUMENT_DIR that does not exist must fail fast rather than be lazily created.
        File missingDir = tmpDir.resolve("missing-document-dir").toFile();
        assertThat(missingDir).doesNotExist();
        CarlosProperties properties = mock(CarlosProperties.class);
        when(properties.getProperty("DOCUMENT_DIR")).thenReturn(missingDir.getAbsolutePath());

        try (MockedStatic<Util> util = mockStatic(Util.class);
             MockedStatic<CarlosProperties> carlosProperties = mockStatic(CarlosProperties.class);
             MockedStatic<FileUtils> fileUtils = mockStatic(FileUtils.class)) {
            util.when(() -> Util.zipFiles(any(), anyString(), anyString())).thenReturn(true);
            carlosProperties.when(CarlosProperties::getInstance).thenReturn(properties);

            assertThatThrownBy(() -> invokeMakeFiles(document, tmpDir.toString()))
                    .isInstanceOf(SecurityException.class);

            // The zip is never copied into the misconfigured destination.
            fileUtils.verify(() -> FileUtils.copyFileToDirectory(any(File.class), any(File.class)), never());
        }
    }

    @Test
    @DisplayName("should delete the temp XML when the export aborts after a partial write")
    void shouldDeleteTempXml_whenExportAbortsAfterPartialWrite(@TempDir Path tmpDir) throws Throwable {
        PatientDocument document = mock(PatientDocument.class);
        List<File> written = new ArrayList<>();
        doAnswer(invocation -> {
            File target = invocation.getArgument(0, File.class);
            Files.writeString(target.toPath(), "<PatientRecord/>"); // simulate a partial PHI write
            written.add(target);
            throw new IOException("partial write then failure");
        }).when(document).save(any(File.class), any(XmlOptions.class));

        assertThatThrownBy(() -> invokeMakeFiles(document, tmpDir.toString()))
                .isInstanceOf(IOException.class);

        assertThat(written).hasSize(1);
        assertThat(written.get(0))
                .as("temp XML containing patient data must be removed on abort")
                .doesNotExist();
    }
}
