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
package io.github.carlos_emr.carlos.utility;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@Tag("fast")
@Tag("pdf")
@DisplayName("DocumentManifest")
class DocumentManifestUnitTest {

    static DocumentManifest fakeManifest(String title) {
        return new DocumentManifest("FAKE-document-id", 1, 1_792_454_400L, "clinic-a", "FAKE Demo Clinic",
                "FAKE-Provider, Demo", null, "FAKE-Manifest-Patient, Jo", "1985-06-15", title, "lab_report", null);
    }

    @Test
    @DisplayName("should write RFC 8785 JSON with sorted keys and nulls")
    void shouldWriteCanonicalJson_withSortedKeysAndNulls() {
        String json = new String(fakeManifest("FAKE CBC").toCanonicalJson("FAKE-kid"), StandardCharsets.UTF_8);

        assertThat(json).isEqualTo("{"
                + "\"clinic_id\":\"clinic-a\","
                + "\"clinic_name\":\"FAKE Demo Clinic\","
                + "\"document\":{\"media_type\":\"application/pdf\",\"service_date\":null,\"title\":\"FAKE CBC\","
                + "\"type\":\"lab_report\"},"
                + "\"document_id\":\"FAKE-document-id\","
                + "\"issued_at\":1792454400,"
                + "\"kid\":\"FAKE-kid\","
                + "\"patient\":{\"birth_date\":\"1985-06-15\",\"name\":\"FAKE-Manifest-Patient, Jo\"},"
                + "\"provider_name\":\"FAKE-Provider, Demo\","
                + "\"recipient_id\":null,"
                + "\"typ\":\"carlos-document-manifest\","
                + "\"v\":1,"
                + "\"version\":1"
                + "}");
    }

    @Test
    @DisplayName("should escape only what JSON requires")
    void shouldEscapeOnlyWhatJsonRequires_inStrings() {
        String title = "a\"b\\c\nd\te" + (char) 1 + "é ";

        String json = new String(fakeManifest(title).toCanonicalJson("k"), StandardCharsets.UTF_8);

        // Short escapes, a lowercase six-character escape for other control characters, and every
        // other character as itself (RFC 8785 section 3.2.2.2).
        assertThat(json).contains("\"title\":\"a\\\"b\\\\c\\nd\\te\\u0001é \"");
    }

    @Test
    @DisplayName("should keep a surrogate pair and refuse an unpaired surrogate")
    void shouldRefuseUnpairedSurrogates_inStrings() {
        String pair = new String(Character.toChars(0x1F600));

        String json = new String(fakeManifest("a" + pair).toCanonicalJson("k"), StandardCharsets.UTF_8);

        assertThat(json).contains("\"title\":\"a" + pair + "\"");
        assertThatThrownBy(() -> fakeManifest("a\uD83D").toCanonicalJson("k"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fakeManifest("\uDE00b").toCanonicalJson("k"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should refuse a blank document ID or a version below 1")
    void shouldRefuseInvalidValues_forTheDocumentIdentity() {
        assertThatThrownBy(() -> new DocumentManifest(" ", 1, 0, null, null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DocumentManifest("id", 0, 0, null, null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
