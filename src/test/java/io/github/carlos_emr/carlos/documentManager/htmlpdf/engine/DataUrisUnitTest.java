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
package io.github.carlos_emr.carlos.documentManager.htmlpdf.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DataUris}, the reader of {@code data:} URI payloads the user agent and the
 * image planning share.
 */
@Tag("unit")
@Tag("htmlpdf")
@Tag("security")
@DisplayName("DataUris Unit Tests")
class DataUrisUnitTest {

    @Test
    @DisplayName("should decode a base64 data URI, in any case of the scheme and header")
    void shouldDecodeBase64_ofDataUri() {
        byte[] payload = {1, 2, 3, 4, 5};
        String base64 = Base64.getEncoder().encodeToString(payload);

        assertThat(DataUris.decodeBase64("data:image/png;base64," + base64, 1024)).hasValue(payload);
        assertThat(DataUris.decodeBase64("DATA:image/png;BASE64," + base64, 1024)).hasValue(payload);
        assertThat(DataUris.decodeBase64("data:;base64," + base64, 1024)).hasValue(payload);
    }

    @Test
    @DisplayName("should decode a base64 payload broken into lines")
    void shouldDecodeBase64_withLineBreaks() {
        byte[] payload = new byte[200];
        java.util.Arrays.fill(payload, (byte) 7);
        String wrapped = Base64.getMimeEncoder().encodeToString(payload);

        assertThat(wrapped).contains("\r\n");
        assertThat(DataUris.decodeBase64("data:image/gif;base64," + wrapped, 1024)).hasValue(payload);
    }

    @Test
    @DisplayName("should refuse what is not a base64 data URI")
    void shouldRefuse_whatIsNotBase64DataUri() {
        assertThat(DataUris.decodeBase64("data:image/png,%89PNG", 1024)).isEmpty();
        assertThat(DataUris.decodeBase64("data:image/png;base64", 1024)).isEmpty();
        assertThat(DataUris.decodeBase64("data:image/png;base64,!!!not base64!!!", 1024)).isEmpty();
        assertThat(DataUris.decodeBase64("http://example.invalid/x.png", 1024)).isEmpty();
        assertThat(DataUris.decodeBase64("image.png", 1024)).isEmpty();
        assertThat(DataUris.decodeBase64(null, 1024)).isEmpty();
        assertThat(DataUris.decodeBase64("", 1024)).isEmpty();
    }

    @Test
    @DisplayName("should refuse a payload that would decode to more than the limit, before decoding it")
    void shouldRefuseOversizedPayload_beforeDecoding() {
        String base64 = Base64.getEncoder().encodeToString(new byte[300]);

        assertThat(DataUris.decodeBase64("data:image/png;base64," + base64, 300)).isPresent();
        assertThat(DataUris.decodeBase64("data:image/png;base64," + base64, 100)).isEmpty();
        // Declared length alone is enough: the text is not decoded to find out.
        assertThat(DataUris.decodeBase64("data:image/png;base64," + "A".repeat(4_000_000), 1000)).isEmpty();
    }

    @Test
    @DisplayName("should recognise data URIs by their scheme only")
    void shouldRecognise_dataUris() {
        assertThat(DataUris.isDataUri("data:image/png;base64,AAAA")).isTrue();
        assertThat(DataUris.isDataUri("Data:,x")).isTrue();
        assertThat(DataUris.isDataUri("xdata:,x")).isFalse();
        assertThat(DataUris.isDataUri(" data:,x")).isFalse();
        assertThat(DataUris.isDataUri(null)).isFalse();
        assertThat(DataUris.isDataUri("da")).isFalse();
    }
}
