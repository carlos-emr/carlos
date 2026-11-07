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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportActorDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportJobDocumentDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStorageBindingDto;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The golden bytes came from the actual pre-storage seven-component compiled record, not this new writer. */
class CdsExportStorageProtocolUnitTest {
    @Test void shouldRetainExactVersionOneSerializationAndReadability_afterAddingStorageBindings() throws Exception {
        String original = Files.readString(Path.of("src/test/resources/cds-export/legacy-job-document-v1.json"), StandardCharsets.UTF_8).stripTrailing();
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original.getBytes(StandardCharsets.UTF_8))))
                .isEqualTo("9a74689d24bfe4b4a4d3808afefa4fb5463af80bb7ef86e204d39e9091ad3105");
        var json = new ObjectMapper(); var legacy = document(1, null);
        assertThat(json.writeValueAsString(legacy)).isEqualTo(original);
        assertThat(json.readValue(original, CdsExportJobDocumentDto.class)).isEqualTo(legacy);
        assertThat(json.readTree(original).has("storage")).isFalse();
    }
    @Test void shouldBindOnlyOpaqueStorageIdentityAndFingerprint_withoutPrivateKeyMaterialOrPaths() throws Exception {
        var binding = new CdsExportStorageBindingDto("primary", "active", "d".repeat(64));
        var document = document(2, binding); var json = new ObjectMapper(); String text = json.writeValueAsString(document);
        assertThat(json.readValue(text, CdsExportJobDocumentDto.class)).isEqualTo(document);
        assertThat(json.readTree(text).path("storage").properties()).hasSize(3);
        assertThat(text).doesNotContain("/", "encoded", "AES", "password");
        assertThat(binding.toString()).doesNotContain("primary", "active", "d".repeat(64));
    }
    @Test void shouldRejectInvalidStorageIdentifiersAndFingerprints() {
        assertThatThrownBy(() -> new CdsExportStorageBindingDto("../primary", "active", "d".repeat(64))).hasMessage("CDS_EXPORT_STORAGE_BINDING_INVALID");
        assertThatThrownBy(() -> new CdsExportStorageBindingDto("primary", "active/key", "d".repeat(64))).hasMessage("CDS_EXPORT_STORAGE_BINDING_INVALID");
        assertThatThrownBy(() -> new CdsExportStorageBindingDto("primary", "active", "D".repeat(64))).hasMessage("CDS_EXPORT_STORAGE_BINDING_INVALID");
    }
    private CdsExportJobDocumentDto document(int protocol, CdsExportStorageBindingDto storage) {
        var command = new CdsExportCommand("fixture", "999998", null, CdsLogicalProfile.ONTARIO_MD_5_2,
                CdsExportCommand.Selection.SELECTED_PATIENTS, List.of(), List.of(27), Set.of(CdsCategory.DEMOGRAPHICS), "recipient", "audit");
        return new CdsExportJobDocumentDto(protocol, new CdsExportActorDto("fixture", "999998", null, null), command,
                "a".repeat(64), "fixture", "b".repeat(64), "c".repeat(64), storage);
    }
}
