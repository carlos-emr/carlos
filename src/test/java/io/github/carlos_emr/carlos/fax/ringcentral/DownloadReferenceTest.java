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
package io.github.carlos_emr.carlos.fax.ringcentral;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.carlos.commn.model.FaxJob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DownloadReference}, the cross-call contract carried over
 * {@code FaxJob.file_name} between {@code listInboundFaxes} and
 * {@code downloadFax}/{@code markFaxAsRead}.
 *
 * @since 2026-05-07
 */
@Tag("unit")
@Tag("fax")
@Tag("ringcentral")
@DisplayName("DownloadReference Unit Tests")
class DownloadReferenceTest {

    @Test
    @DisplayName("should reject canonical construction when messageId is blank")
    void shouldRejectCanonicalConstruction_whenMessageIdIsBlank() {
        assertThatThrownBy(() -> new DownloadReference(" ", "att"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("messageId");
    }

    @Test
    @DisplayName("should reject canonical construction when attachmentId is null")
    void shouldRejectCanonicalConstruction_whenAttachmentIdIsNull() {
        assertThatThrownBy(() -> new DownloadReference("msg", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attachmentId");
    }

    @Test
    @DisplayName("should reject format when messageId is blank")
    void shouldRejectFormat_whenMessageIdIsBlank() {
        assertThatThrownBy(() -> DownloadReference.format("", "att", "label.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should reject format when attachmentId is blank")
    void shouldRejectFormat_whenAttachmentIdIsBlank() {
        assertThatThrownBy(() -> DownloadReference.format("msg", " ", "label.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should default to ringcentral-fax.pdf when filename is blank")
    void shouldDefaultFilename_whenFileNameIsBlank() {
        String formatted = DownloadReference.format("123", "456", "");

        assertThat(formatted).isEqualTo("123:456:ringcentral-fax.pdf");
    }

    @Test
    @DisplayName("should round-trip messageId and attachmentId through format and parse")
    void shouldRoundTripIds_throughFormatAndParse() throws Exception {
        String formatted = DownloadReference.format("789", "att-9", "patient.pdf");
        FaxJob fax = new FaxJob();
        fax.setFile_name(formatted);

        DownloadReference parsed = DownloadReference.parse(fax);

        assertThat(parsed.messageId()).isEqualTo("789");
        assertThat(parsed.attachmentId()).isEqualTo("att-9");
    }

    @Test
    @DisplayName("should reject parse when FaxJob is null")
    void shouldRejectParse_whenFaxJobIsNull() {
        assertThatThrownBy(() -> DownloadReference.parse(null))
                .isInstanceOf(RingCentralException.class)
                .hasMessageContaining("Fax metadata");
    }

    @Test
    @DisplayName("should reject parse when reference has no separator")
    void shouldRejectParse_whenReferenceHasNoSeparator() {
        FaxJob fax = new FaxJob();
        fax.setFile_name("just-a-filename.pdf");

        assertThatThrownBy(() -> DownloadReference.parse(fax))
                .isInstanceOf(RingCentralException.class)
                .hasMessageContaining("identifiers");
    }

    @Test
    @DisplayName("should reject parse when reference has only one part")
    void shouldRejectParse_whenReferenceMissingAttachmentId() {
        FaxJob fax = new FaxJob();
        fax.setFile_name("123:");

        assertThatThrownBy(() -> DownloadReference.parse(fax))
                .isInstanceOf(RingCentralException.class)
                .hasMessageContaining("identifiers");
    }

    @Test
    @DisplayName("should keep extra colons in filename when reference has 4+ parts")
    void shouldKeepExtraColons_whenFilenameContainsSeparator() throws Exception {
        FaxJob fax = new FaxJob();
        // split with limit 3 leaves "label:with:colons.pdf" intact in the third slot
        fax.setFile_name("123:att:label:with:colons.pdf");

        DownloadReference parsed = DownloadReference.parse(fax);

        assertThat(parsed.messageId()).isEqualTo("123");
        assertThat(parsed.attachmentId()).isEqualTo("att");
    }
}
