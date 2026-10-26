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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.eform;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.github.carlos_emr.carlos.eform.data.EForm;
import io.github.carlos_emr.carlos.eform.upload.ImageUpload2Action;
import io.github.carlos_emr.carlos.utility.FileValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins how {@link EFormExportZip#importForm(java.io.InputStream)} treats the non-content entries
 * that archivers add to eForm packages.
 *
 * <p>Verified on a packaged install with two OSCAR Galaxy Rich Text Letter packages: both had been
 * zipped with macOS Finder, which adds a {@code __MACOSX/} tree of AppleDouble {@code ._<name>}
 * companions. The hidden-component check refused the first of them and the import answered 403
 * with nothing imported ("Invalid eform import zip entry: hidden or traversal component"). The same
 * packages with those entries removed imported normally. One of them also carried a folder entry,
 * which the importer "extracted" as an empty file and published into the shared eForm image
 * directory under the folder's name.</p>
 *
 * @since 2026-10-19
 */
@Tag("unit")
@Tag("eform")
@DisplayName("EFormExportZip.importForm archiver metadata")
class EFormExportZipArchiverMetadataImportUnitTest {

    /** First bytes of a real AppleDouble file: magic 0x00051607, version 2, "Mac OS X" filler. */
    private static final byte[] APPLE_DOUBLE = {0x00, 0x05, 0x16, 0x07, 0x00, 0x02, 0x00, 0x00,
            'M', 'a', 'c', ' ', 'O', 'S', ' ', 'X', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '};
    private static final String HTML = "<html><body><form method=\"post\">Letter</form></body></html>";

    @TempDir
    Path imageFolder;

    private record Result(List<String> errors, List<EForm> saved) { }

    /** Entries in order; a {@code null} value writes a directory entry (name must end in '/'). */
    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream output = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                if (entry.getValue() != null) {
                    output.write(entry.getValue());
                }
                output.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private Result runImport(byte[] zip) throws Exception {
        List<EForm> saved = new ArrayList<>();
        try (MockedStatic<ImageUpload2Action> images = mockStatic(ImageUpload2Action.class);
             MockedStatic<EFormUtil> forms = mockStatic(EFormUtil.class)) {
            images.when(ImageUpload2Action::getImageFolder).thenReturn(imageFolder.toFile());
            forms.when(() -> EFormUtil.formExistsInDB(any())).thenReturn(false);
            forms.when(() -> EFormUtil.saveEForm(any(EForm.class))).thenAnswer(invocation -> {
                saved.add(invocation.getArgument(0));
                return "1";
            });
            List<String> errors = new EFormExportZip().importForm(new ByteArrayInputStream(zip));
            return new Result(errors, saved);
        }
    }

    /** Everything the import left in the image folder, except its own staging parent. */
    private List<String> publishedNames() throws IOException {
        try (Stream<Path> files = Files.list(imageFolder)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> !"extractFolder".equals(name)).sorted().toList();
        }
    }

    @Test
    @DisplayName("should import the form and its image when the package was compressed by macOS Finder")
    void shouldImportFormAndImage_whenPackageCarriesFinderMetadata() throws Exception {
        // Entry order and shape copied from the failing Galaxy package: folder entry first, then
        // each file followed by its __MACOSX/._ companion.
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("Rich Text Letter(1)/", null);
        entries.put("__MACOSX/._Rich Text Letter(1)", APPLE_DOUBLE);
        entries.put("Rich Text Letter(1)/RichTextLetter.html", utf8(HTML));
        entries.put("__MACOSX/Rich Text Letter(1)/._RichTextLetter.html", APPLE_DOUBLE);
        entries.put("Rich Text Letter(1)/eform.properties",
                utf8("form.name=Galaxy Rich Text Letter\nform.htmlFilename=RichTextLetter.html\n"));
        entries.put("__MACOSX/Rich Text Letter(1)/._eform.properties", APPLE_DOUBLE);
        entries.put("Rich Text Letter(1)/letterhead.png", utf8("PNG-BYTES"));
        entries.put("__MACOSX/Rich Text Letter(1)/._letterhead.png", APPLE_DOUBLE);

        Result result = runImport(zip(entries));

        assertThat(result.errors()).isEmpty();
        assertThat(result.saved()).singleElement().satisfies(form -> {
            assertThat(form.getFormName()).isEqualTo("Galaxy Rich Text Letter");
            assertThat(form.getFormHtml()).isEqualTo(HTML);
        });
        // Only the real asset is published: no AppleDouble companion, and no empty file named after
        // the package folder.
        assertThat(publishedNames()).containsExactly("letterhead.png");
        assertThat(Files.readString(imageFolder.resolve("letterhead.png"))).isEqualTo("PNG-BYTES");
    }

    @Test
    @DisplayName("should skip AppleDouble companions and .DS_Store written beside the real files")
    void shouldSkipStrayAppleDoubleAndDsStore_besideContent() throws Exception {
        // Command-line archivers on a Mac write ._<name> next to the file instead of under __MACOSX.
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("pkg/._letterhead.png", APPLE_DOUBLE);
        entries.put("pkg/letterhead.png", utf8("PNG-BYTES"));
        entries.put(".DS_Store", APPLE_DOUBLE);
        entries.put("pkg/.DS_Store", APPLE_DOUBLE);

        Result result = runImport(zip(entries));

        assertThat(result.errors()).isEmpty();
        assertThat(publishedNames()).containsExactly("letterhead.png");
    }

    @ParameterizedTest
    @ValueSource(strings = {".htaccess", "pkg/.hidden.png", "pkg/.._.png"})
    @DisplayName("should still refuse the whole import for a hidden entry that is not archiver metadata")
    void shouldRefuseImport_whenHiddenEntryIsNotArchiverMetadata(String hiddenEntry) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("pkg/letterhead.png", utf8("PNG-BYTES"));
        entries.put(hiddenEntry, utf8("payload"));
        byte[] zip = zip(entries);

        assertThatThrownBy(() -> runImport(zip)).isInstanceOf(FileValidationException.class);
        assertThat(publishedNames()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"__MACOSX/../../escape.png", "__MACOSX/../._escape.png", "/__MACOSX/._abs.png"})
    @DisplayName("should still refuse a traversal or absolute name even under the metadata folder")
    void shouldRefuseImport_whenMetadataEntryNameIsUnsafe(String unsafeEntry) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(unsafeEntry, APPLE_DOUBLE);
        byte[] zip = zip(entries);

        assertThatThrownBy(() -> runImport(zip)).isInstanceOf(FileValidationException.class);
        assertThat(publishedNames()).isEmpty();
    }

    @Test
    @DisplayName("should recognize only macOS archiver metadata names")
    void shouldRecognizeOnlyMacArchiverMetadata_forEntryNames() {
        assertThat(EFormExportZip.isArchiverMetadataEntry("__MACOSX/pkg/._form.html")).isTrue();
        assertThat(EFormExportZip.isArchiverMetadataEntry("__MACOSX/anything.html")).isTrue();
        assertThat(EFormExportZip.isArchiverMetadataEntry("pkg/._form.html")).isTrue();
        assertThat(EFormExportZip.isArchiverMetadataEntry("pkg\\._form.html")).isTrue();
        assertThat(EFormExportZip.isArchiverMetadataEntry("pkg/.DS_Store")).isTrue();

        assertThat(EFormExportZip.isArchiverMetadataEntry("pkg/form.html")).isFalse();
        assertThat(EFormExportZip.isArchiverMetadataEntry("pkg/.htaccess")).isFalse();
        assertThat(EFormExportZip.isArchiverMetadataEntry("pkg/__MACOSX/form.html")).isFalse();
        assertThat(EFormExportZip.isArchiverMetadataEntry("__macosx/._form.html")).isTrue();
        assertThat(EFormExportZip.isArchiverMetadataEntry("__macosx/form.html")).isFalse();
    }
}
