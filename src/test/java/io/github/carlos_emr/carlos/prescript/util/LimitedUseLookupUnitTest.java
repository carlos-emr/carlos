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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the formulary source resolution, path validation, and lazy-load
 * publication behaviour of {@link LimitedUseLookup}.
 */
@Tag("unit")
@Tag("prescription")
@DisplayName("LimitedUseLookup unit tests")
class LimitedUseLookupUnitTest extends CarlosUnitTestBase {

    private static final String FORMULARY_PROPERTY = "odb_formulary_file";

    private ResourceStorageDao mockResourceStorageDao;
    private Object previousFormularyFile;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        previousFormularyFile = CarlosProperties.getInstance().get(FORMULARY_PROPERTY);
        CarlosProperties.getInstance().remove(FORMULARY_PROPERTY);
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
            CarlosProperties.getInstance().remove(FORMULARY_PROPERTY);
        } else {
            CarlosProperties.getInstance().put(FORMULARY_PROPERTY, previousFormularyFile);
        }
    }

    @Test
    @DisplayName("should declare the loaded flag volatile so the double-checked lock publishes safely")
    void shouldDeclareLoadedFlagVolatile_forSafePublication() throws NoSuchFieldException {
        Field loaded = LimitedUseLookup.class.getDeclaredField("loaded");

        assertThat(Modifier.isVolatile(loaded.getModifiers())).isTrue();
    }

    @Test
    @DisplayName("should load limited use codes from the configured absolute formulary path")
    void shouldLoadLimitedUseCodes_fromConfiguredAbsolutePath() throws Exception {
        Path formularyPath = tempDir.resolve("formulary.xml");
        Files.writeString(formularyPath, minimalLimitedUseXml("99000001"));
        CarlosProperties.getInstance().setProperty(FORMULARY_PROPERTY, formularyPath.toFile().getPath());

        ArrayList<LimitedUseCode> result = LimitedUseLookup.getLUInfoForDin("99000001");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUseId()).isEqualTo("RFU1");
        verify(mockResourceStorageDao, never()).findActive(ResourceStorage.LU_CODES);
    }

    /**
     * Regression guard: a bare filename has a {@code null} parent directory, so validating it
     * against {@code File.getParentFile()} would silently discard the configured formulary for
     * deployments that keep it in the server working directory.
     */
    @Test
    @DisplayName("should load limited use codes from a bare relative formulary filename")
    void shouldLoadLimitedUseCodes_fromBareRelativeFileName() throws Exception {
        String relativeName = "carlos-lu-test-" + UUID.randomUUID() + ".xml";
        Path workingDirFile = new File(relativeName).toPath();
        Files.writeString(workingDirFile, minimalLimitedUseXml("99000003"));
        try {
            assertThat(new File(relativeName)).hasNoParent();
            CarlosProperties.getInstance().setProperty(FORMULARY_PROPERTY, relativeName);

            ArrayList<LimitedUseCode> result = LimitedUseLookup.getLUInfoForDin("99000003");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUseId()).isEqualTo("RFU1");
            verify(mockResourceStorageDao, never()).findActive(ResourceStorage.LU_CODES);
        } finally {
            Files.deleteIfExists(workingDirFile);
        }
    }

    @Test
    @DisplayName("should fall back to resource storage when the configured path is invalid")
    void shouldFallBackToResourceStorage_whenConfiguredPathIsInvalid() {
        File missingFile = tempDir.resolve("missing-formulary.xml").toFile();
        CarlosProperties.getInstance().setProperty(FORMULARY_PROPERTY, missingFile.getPath());
        ResourceStorage mockStorage = mock(ResourceStorage.class);
        when(mockStorage.getFileContents())
                .thenReturn(minimalLimitedUseXml("99000002").getBytes(StandardCharsets.UTF_8));
        when(mockStorage.getId()).thenReturn(1);
        when(mockResourceStorageDao.findActive(ResourceStorage.LU_CODES)).thenReturn(mockStorage);

        ArrayList<LimitedUseCode> result = LimitedUseLookup.getLUInfoForDin("99000002");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUseId()).isEqualTo("RFU1");
        verify(mockResourceStorageDao).findActive(ResourceStorage.LU_CODES);
    }

    @Test
    @DisplayName("should fall back to the bundled formulary when the stored resource has no contents")
    void shouldFallBackToBundledFormulary_whenStoredResourceHasNoContents() {
        ResourceStorage mockStorage = mock(ResourceStorage.class);
        when(mockStorage.getFileContents()).thenReturn(null);
        when(mockStorage.getId()).thenReturn(1);
        when(mockResourceStorageDao.findActive(ResourceStorage.LU_CODES)).thenReturn(mockStorage);

        assertThatCode(() -> LimitedUseLookup.getLUInfoForDin("99000002")).doesNotThrowAnyException();

        assertThat(LimitedUseLookup.loaded).isTrue();
        assertThat(LimitedUseLookup.luLookup).isNotEmpty();
    }

    @Test
    @DisplayName("should leave the lookup unloaded when the formulary element is missing")
    void shouldLeaveLookupUnloaded_whenFormularyElementMissing() throws Exception {
        Path formularyPath = tempDir.resolve("no-formulary.xml");
        Files.writeString(formularyPath, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><extract/>");
        CarlosProperties.getInstance().setProperty(FORMULARY_PROPERTY, formularyPath.toFile().getPath());

        ArrayList<LimitedUseCode> result = LimitedUseLookup.getLUInfoForDin("99000001");

        assertThat(result).isNull();
        assertThat(LimitedUseLookup.loaded).isFalse();
        assertThat(LimitedUseLookup.luLookup).isEmpty();
    }

    private static String minimalLimitedUseXml(String din) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<extract><formulary><pcgGroup>"
                + "<lccNote seq=\"1\" reasonForUseId=\"RFU1\" type=\"LU\">Limited use note</lccNote>"
                + "<drug id=\"" + din + "\"/>"
                + "</pcgGroup></formulary></extract>";
    }
}
