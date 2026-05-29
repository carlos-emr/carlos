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
package io.github.carlos_emr.carlos.prescript.util;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.ResourceStorageDao;
import io.github.carlos_emr.carlos.commn.model.ResourceStorage;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("prescription")
@DisplayName("LimitedUseLookup unit tests")
class LimitedUseLookupUnitTest extends CarlosUnitTestBase {

    private ResourceStorageDao mockResourceStorageDao;
    private Object previousFormularyFile;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        previousFormularyFile = CarlosProperties.getInstance().get("odb_formulary_file");
        CarlosProperties.getInstance().remove("odb_formulary_file");
        LimitedUseLookup.loaded = false;
        LimitedUseLookup.luLookup.clear();

        mockResourceStorageDao = mock(ResourceStorageDao.class);
        when(mockResourceStorageDao.findActive(ResourceStorage.LU_CODES)).thenReturn(null);
        registerMock(ResourceStorageDao.class, mockResourceStorageDao);
    }

    @AfterEach
    void tearDown() {
        LimitedUseLookup.loaded = false;
        LimitedUseLookup.luLookup.clear();
        if (previousFormularyFile == null) {
            CarlosProperties.getInstance().remove("odb_formulary_file");
        } else {
            CarlosProperties.getInstance().put("odb_formulary_file", previousFormularyFile);
        }
    }

    @Test
    @DisplayName("should declare loaded as volatile")
    void shouldDeclareLoadedAsVolatile() throws NoSuchFieldException {
        Field loaded = LimitedUseLookup.class.getDeclaredField("loaded");

        assertThat(Modifier.isVolatile(loaded.getModifiers())).isTrue();
    }

    @Test
    @DisplayName("should validate configured formulary path before opening")
    void shouldValidateConfiguredFormularyPathBeforeOpening() throws Exception {
        Path formularyPath = tempDir.resolve("formulary.xml");
        Files.writeString(formularyPath, minimalLimitedUseXml("99000001"));
        File formularyFile = formularyPath.toFile();
        CarlosProperties.getInstance().setProperty("odb_formulary_file", formularyFile.getPath());

        try (MockedStatic<PathValidationUtils> pathValidation = mockStatic(PathValidationUtils.class)) {
            pathValidation.when(() -> PathValidationUtils.validateExistingPath(any(File.class), any(File.class)))
                    .thenReturn(formularyFile);

            ArrayList<LimitedUseCode> result = LimitedUseLookup.getLUInfoForDin("99000001");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUseId()).isEqualTo("RFU1");
            pathValidation.verify(() -> PathValidationUtils.validateExistingPath(eq(formularyFile),
                    eq(formularyFile.getParentFile())));
        }
        verify(mockResourceStorageDao, never()).findActive(ResourceStorage.LU_CODES);
    }

    @Test
    @DisplayName("should fall back to resource storage when configured path is invalid")
    void shouldFallBackToResourceStorage_whenConfiguredPathIsInvalid() {
        File configuredFile = tempDir.resolve("invalid-formulary.xml").toFile();
        CarlosProperties.getInstance().setProperty("odb_formulary_file", configuredFile.getPath());
        ResourceStorage mockStorage = mock(ResourceStorage.class);
        when(mockStorage.getFileContents()).thenReturn(minimalLimitedUseXml("99000002").getBytes(StandardCharsets.UTF_8));
        when(mockStorage.getId()).thenReturn(1);
        when(mockResourceStorageDao.findActive(ResourceStorage.LU_CODES)).thenReturn(mockStorage);

        try (MockedStatic<PathValidationUtils> pathValidation = mockStatic(PathValidationUtils.class)) {
            pathValidation.when(() -> PathValidationUtils.validateExistingPath(any(File.class), any(File.class)))
                    .thenThrow(new SecurityException("Invalid file path"));

            ArrayList<LimitedUseCode> result = LimitedUseLookup.getLUInfoForDin("99000002");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUseId()).isEqualTo("RFU1");
            pathValidation.verify(() -> PathValidationUtils.validateExistingPath(eq(configuredFile),
                    eq(configuredFile.getParentFile())));
        }
        verify(mockResourceStorageDao).findActive(ResourceStorage.LU_CODES);
    }

    private static String minimalLimitedUseXml(String din) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<extract><formulary><pcgGroup>"
                + "<lccNote seq=\"1\" reasonForUseId=\"RFU1\" type=\"LU\">Limited use note</lccNote>"
                + "<drug id=\"" + din + "\"/>"
                + "</pcgGroup></formulary></extract>";
    }
}
