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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import cdsrourke.PatientDocument;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;

import org.apache.commons.io.FileUtils;
import org.apache.xmlbeans.XmlOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

/**
 * Unit coverage for {@code RourkeExport2Action} export-file handling (issue #3064): each export
 * must use a unique temp filename and must abort rather than package a missing/partial XML.
 *
 * @since 2026-06-26
 */
@DisplayName("RourkeExport2Action export file handling")
@Tag("unit")
@Tag("web")
@Tag("demographic")
class RourkeExport2ActionExportUnitTest extends CarlosWebTestBase {

    // makeFiles(...) uses only its arguments and static collaborators, not instance fields,
    // so the action is constructed via its standard no-arg constructor.
    private RourkeExport2Action newAction() {
        return new RourkeExport2Action();
    }

    private static Object invokeMakeFiles(RourkeExport2Action action, PatientDocument doc, String tmpDir)
            throws Throwable {
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
        RourkeExport2Action action = newAction();
        PatientDocument document = mock(PatientDocument.class);
        doAnswer(invocation -> {
            throw new IOException("simulated disk failure");
        }).when(document).save(any(File.class), any(XmlOptions.class));

        try (MockedStatic<Util> util = mockStatic(Util.class)) {
            assertThatThrownBy(() -> invokeMakeFiles(action, document, tmpDir.toString()))
                    .isInstanceOf(IOException.class);

            // The export must never proceed to zip a missing/partial XML.
            util.verify(() -> Util.zipFiles(any(), anyString(), anyString()), never());
        }
    }

    @Test
    @DisplayName("should use a unique XML filename per export across repeated exports")
    void shouldUseUniqueXmlFilename_acrossRepeatedExports(@TempDir Path tmpDir) throws Exception {
        RourkeExport2Action action = newAction();
        PatientDocument document = mock(PatientDocument.class);
        List<File> savedTargets = new ArrayList<>();
        doAnswer(invocation -> {
            savedTargets.add(invocation.getArgument(0, File.class));
            throw new IOException("stop before zipping");
        }).when(document).save(any(File.class), any(XmlOptions.class));

        try (MockedStatic<Util> util = mockStatic(Util.class)) {
            catchThrowable(() -> invokeMakeFiles(action, document, tmpDir.toString()));
            catchThrowable(() -> invokeMakeFiles(action, document, tmpDir.toString()));
        }

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
        RourkeExport2Action action = newAction();
        PatientDocument document = mock(PatientDocument.class); // save() is a no-op success

        File documentDir = tmpDir.resolve("documents").toFile();
        assertThat(documentDir.mkdirs()).isTrue();
        CarlosProperties properties = mock(CarlosProperties.class);
        when(properties.getProperty("DOCUMENT_DIR")).thenReturn(documentDir.getAbsolutePath());

        String firstZip;
        String secondZip;
        try (MockedStatic<Util> util = mockStatic(Util.class);
             MockedStatic<CarlosProperties> carlosProperties = mockStatic(CarlosProperties.class);
             MockedStatic<FileUtils> fileUtils = mockStatic(FileUtils.class)) {
            util.when(() -> Util.zipFiles(any(), anyString(), anyString())).thenReturn(true);
            carlosProperties.when(CarlosProperties::getInstance).thenReturn(properties);

            firstZip = (String) invokeMakeFiles(action, document, tmpDir.toString());
            secondZip = (String) invokeMakeFiles(action, document, tmpDir.toString());

            util.verify(() -> Util.zipFiles(any(), anyString(), anyString()), times(2));
        }

        assertThat(firstZip).startsWith("rourke2009_export-").endsWith(".zip");
        assertThat(secondZip)
                .as("each successful export must produce a distinct zip name")
                .isNotEqualTo(firstZip);
    }
}
