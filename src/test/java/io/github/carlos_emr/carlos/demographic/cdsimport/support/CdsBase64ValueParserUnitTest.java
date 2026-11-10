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
package io.github.carlos_emr.carlos.demographic.cdsimport.support;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class CdsBase64ValueParserUnitTest {
    @ParameterizedTest @CsvSource({"TQ==,M","TWE=,Ma","TWFu,Man"})
    void shouldDecodeExactBytes_whenExplicitBinaryTextIsWellFormed(String encoded,String expected) throws Exception {
        try(var stream=CdsBase64ValueParser.decode(source(" \t"+encoded+"\r\n"))) {
            assertThat(new String(stream.readAllBytes(),StandardCharsets.UTF_8)).isEqualTo(expected);
        }
    }
    @ParameterizedTest @ValueSource(strings={"TQ","TWE","TQ===","TR==","TWF=","TQ==AAAA","TQ==$","TQ==\u00a0","TQ==TQ==","=TQ=","TQ==\rX"})
    void shouldRefuseMalformedOrTrailingInput_whenPaddingOrAlphabetIsInvalid(String encoded) {
        assertThatThrownBy(()->{try(var stream=CdsBase64ValueParser.decode(source(encoded))){stream.transferTo(java.io.OutputStream.nullOutputStream());}})
                .isInstanceOf(IOException.class);
    }
    private static ByteArrayInputStream source(String text) { return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)); }
}
