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
package io.github.carlos_emr.carlos.eform;

import io.github.carlos_emr.carlos.eform.data.EForm;
import io.github.carlos_emr.carlos.eform.upload.ImageUpload2Action;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

@Tag("unit")
@Tag("eform")
class EFormExportZipLimitsUnitTest {
    @TempDir Path images;

    private void entry(ZipOutputStream zip, String name, int size) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        byte[] block = new byte[8192];
        for (int left = size; left > 0; left -= Math.min(left, block.length)) {
            zip.write(block, 0, Math.min(left, block.length));
        }
        zip.closeEntry();
    }

    private byte[] archive(String oversized, int count, int size) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("eform.properties"));
            zip.write("form.name=Synthetic\nform.htmlFilename=form.html\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            entry(zip, "form.html", 20);
            entry(zip, "asset.png", 20);
            for (int i = 0; i < count; i++) entry(zip, count == 1 ? oversized : oversized + i, size);
        }
        return bytes.toByteArray();
    }

    private void rejected(byte[] archive, String message) throws Exception {
        assertThat(archive.length).isLessThan(160_000);
        try (var folders = mockStatic(ImageUpload2Action.class); var forms = mockStatic(EFormUtil.class)) {
            folders.when(ImageUpload2Action::getImageFolder).thenReturn(images.toFile());
            // Scale down only rejection fixtures; acceptance tests exercise the public defaults.
            assertThatThrownBy(() -> new EFormExportZip().importForm(
                    new ByteArrayInputStream(archive), 16L << 20, 64L << 20, 1024))
                    .isInstanceOf(IOException.class).hasMessageContaining(message);
            forms.verify(() -> EFormUtil.saveEForm(any(EForm.class)), never());
        }
        try (var staged = Files.list(images.resolve("extractFolder")); var published = Files.list(images)) {
            assertThat(staged).isEmpty();
            assertThat(published.map(p -> p.getFileName().toString())).containsExactly("extractFolder");
        }
    }

    private void accepted(byte[] archive, String asset, long expectedBytes) throws Exception {
        try (var folders = mockStatic(ImageUpload2Action.class); var forms = mockStatic(EFormUtil.class)) {
            folders.when(ImageUpload2Action::getImageFolder).thenReturn(images.toFile());
            assertThat(new EFormExportZip().importForm(new ByteArrayInputStream(archive))).isEmpty();
            forms.verify(() -> EFormUtil.saveEForm(any(EForm.class)));
        }
        assertThat(Files.size(images.resolve(asset))).isEqualTo(expectedBytes);
        try (var staged = Files.list(images.resolve("extractFolder"))) {
            assertThat(staged).isEmpty();
        }
    }

    @Test
    void shouldAcceptLargeImage_whenEntryReachesDefaultLimit() throws Exception {
        accepted(archive("large.png", 1, 256 << 20), "large.png", 256L << 20);
    }

    @Test
    void shouldAcceptImageHeavyPackage_whenExpansionExceedsFormerArchiveLimit() throws Exception {
        accepted(archive("large.png", 4, 17 << 20), "large.png3", 17L << 20);
    }

    @Test
    void shouldAcceptManyEntries_whenCountReachesDefaultLimit() throws Exception {
        // The three content entries plus 9,997 metadata entries exactly fill the entry budget.
        accepted(archive("__MACOSX/._asset", 9997, 0), "asset.png", 20);
    }

    @Test
    void shouldRejectOversizedContent_whenEarlierFormAndAssetAreStaged() throws Exception {
        rejected(archive("payload", 1, (16 << 20) + 1), "expanded byte limit");
    }

    @Test
    void shouldBoundPropertiesAndSkippedMetadata_whenTheyExpandBeyondEntryLimit() throws Exception {
        for (String name : new String[] {"other/eform.properties", "__MACOSX/._asset", "folder/"}) {
            rejected(archive(name, 1, (16 << 20) + 1), "expanded byte limit");
        }
    }

    @Test
    void shouldRejectAggregateExpansion_whenIndividualEntriesFit() throws Exception {
        rejected(archive("payload", 4, 16 << 20), "expanded byte limit");
    }

    @Test
    void shouldCountSkippedEntries_whenMetadataEntriesHaveNoPayload() throws Exception {
        rejected(archive("__MACOSX/._asset", 1022, 0), "entry count limit");
    }
}
