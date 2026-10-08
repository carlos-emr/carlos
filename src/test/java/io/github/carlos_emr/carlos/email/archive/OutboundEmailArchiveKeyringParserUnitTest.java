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

package io.github.carlos_emr.carlos.email.archive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import static io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveTestKeyrings.keyring;
import static io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveTestKeyrings.syntheticKey;
import static io.github.carlos_emr.carlos.email.archive.OutboundEmailArchiveTestKeyrings.syntheticKeyBase64;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@DisplayName("OutboundEmailArchiveKeyringParser (#3448)")
class OutboundEmailArchiveKeyringParserUnitTest {

    @Test
    void shouldReadWhatItWrites_forARotatedKeyring() {
        OutboundEmailArchiveKeyring written = keyring(2, 1, 2);

        OutboundEmailArchiveKeyring read = OutboundEmailArchiveKeyringParser.parse(
                OutboundEmailArchiveKeyringParser.format(written));

        assertThat(read.currentKeyId()).isEqualTo(2);
        assertThat(read.keyIds()).containsExactly(1, 2);
        assertThat(read.encodedKey(1)).isEqualTo(syntheticKey(1));
        assertThat(read.encodedKey(2)).isEqualTo(syntheticKey(2));
    }

    @Test
    void shouldAcceptAHandEditedFile_withCommentsBlankLinesAndCrlf() {
        String file = "# restored from backup\r\n\r\nformat = 1\r\ncurrent=3\r\nkey.1=" + syntheticKeyBase64(1)
                + "\r\n  key.3 = " + syntheticKeyBase64(3) + "  \r\n";

        OutboundEmailArchiveKeyring read = OutboundEmailArchiveKeyringParser.parse(ascii(file));

        assertThat(read.currentKeyId()).isEqualTo(3);
        assertThat(read.keyIds()).containsExactly(1, 3);
    }

    @Test
    void shouldAddTheNewKeyAndKeepEveryOldOne_whenRotating() {
        OutboundEmailArchiveKeyring rotated = keyring(1, 1).withCurrentKey(2, new SecureRandom());

        assertThat(rotated.currentKeyId()).isEqualTo(2);
        assertThat(rotated.keyIds()).containsExactly(1, 2);
        assertThat(rotated.encodedKey(1)).isEqualTo(syntheticKey(1));
        assertThat(rotated.encodedKey(2)).hasSize(32).isNotEqualTo(syntheticKey(1));
    }

    @Test
    void shouldReuseAHandAddedKey_whenRotatingToIt() {
        OutboundEmailArchiveKeyring rotated = keyring(1, 1, 2).withCurrentKey(2, new SecureRandom());

        assertThat(rotated.encodedKey(2)).isEqualTo(syntheticKey(2));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "empty file|''|the file is empty",
            "missing format|current=1;key.1=K1|format is missing",
            "future format|format=2;current=1;key.1=K1|format is not 1",
            "missing current|format=1;key.1=K1|current is missing",
            "current not present|format=1;current=2;key.1=K1|current names key 2",
            "no keys|format=1;current=1|holds no key.N lines",
            "duplicate key|format=1;current=1;key.1=K1;key.1=K2|line 4 repeats key.1",
            "duplicate current|format=1;current=1;current=1;key.1=K1|line 3 repeats current",
            "leading zero id|format=1;current=1;key.01=K1|line 3: key id is not a positive whole number",
            "zero id|format=1;current=0;key.1=K1|line 2: current is not a positive whole number",
            "short key|format=1;current=1;key.1=c2hvcnQ=|line 3: key.1 is not a Base64-encoded 32-byte key",
            "bad base64|format=1;current=1;key.1=%%%|line 3: key.1 is not a Base64-encoded 32-byte key",
            "unknown name|format=1;current=1;key.1=K1;active=1|line 4 has an unknown name",
            "no equals|format=1;current=1;key.1=K1;junk|line 4 is not name=value",
    })
    void shouldRefuseMalformedFiles_withTheLineAndFieldButNoValue(String scenario, String lines, String expected) {
        byte[] content = ascii(lines.replace(";", "\n").replace("K1", syntheticKeyBase64(1))
                .replace("K2", syntheticKeyBase64(2)));

        assertThatThrownBy(() -> OutboundEmailArchiveKeyringParser.parse(content))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expected)
                .satisfies(e -> assertThat(e.getMessage())
                        .doesNotContain(syntheticKeyBase64(1))
                        .doesNotContain(syntheticKeyBase64(2)));
    }

    @Test
    void shouldRefuseNonAsciiContent_asNotAKeyring() {
        byte[] content = "format=1\ncurrent=1\nkey.1=é".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> OutboundEmailArchiveKeyringParser.parse(content))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("the file contains non-ASCII bytes");
    }

    @Test
    void shouldRefuseOversizedFiles_beforeParsing() {
        byte[] content = new byte[OutboundEmailArchiveKeyringParser.MAX_FILE_BYTES + 1];
        java.util.Arrays.fill(content, (byte) '#');

        assertThatThrownBy(() -> OutboundEmailArchiveKeyringParser.parse(content))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("larger than");
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }
}
