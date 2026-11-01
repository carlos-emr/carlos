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
package io.github.carlos_emr.carlos.login;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class TotpGeneratorUnitTest {
    private final TotpGenerator generator = new TotpGenerator();
    private final SecretKeySpec key = new SecretKeySpec(
            "12345678901234567890".getBytes(StandardCharsets.US_ASCII), "HmacSHA1");

    @Test
    void shouldMatchRfc6238Sha1Vectors_withSixDigitDefault() throws Exception {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            long[] seconds = {59, 1111111109, 1111111111, 1234567890, 2000000000, 20000000000L};
            // RFC 6238 appendix B gives eight digits; the application keeps the last six.
            String[] expected = {"287082", "081804", "050471", "005924", "279037", "353130"};
            for (int i = 0; i < seconds.length; i++) {
                assertThat(generator.generateOneTimePasswordString(key, Instant.ofEpochSecond(seconds[i])))
                        .as("RFC 6238 at %s seconds", seconds[i]).isEqualTo(expected[i]);
            }
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void shouldSwitchCodes_atThirtySecondBoundaries() throws Exception {
        Instant boundary = Instant.ofEpochSecond(60);
        assertThat(generator.generateOneTimePasswordString(key, boundary.minusMillis(1)))
                .isEqualTo(generator.generateOneTimePasswordString(key, Instant.ofEpochSecond(30)));
        assertThat(generator.generateOneTimePasswordString(key, boundary))
                .isEqualTo(generator.generateOneTimePasswordString(key, boundary.plusSeconds(29)));
        assertThat(generator.generateOneTimePasswordString(key, boundary))
                .isNotEqualTo(generator.generateOneTimePasswordString(key, boundary.minusMillis(1)));
    }

    @Test
    void shouldUseDefaultLocale_forFormattedDigits() throws Exception {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"));
            assertThat(generator.generateOneTimePasswordString(key, Instant.ofEpochSecond(59)))
                    .isEqualTo("٢٨٧٠٨٢");
        } finally {
            Locale.setDefault(previous);
        }
    }
}
