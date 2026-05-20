/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 * Maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("JSON response header regression tests")
@Tag("unit")
@Tag("web")
class JsonResponseHeaderRegressionTest {
    private static final Path REPORT_MACRO_ACTION = Path.of(
            "src/main/java/io/github/carlos_emr/carlos/mds/pageUtil/ReportMacro2Action.java");
    private static final Path MEASUREMENT_DATA_ACTION = Path.of(
            "src/main/java/io/github/carlos_emr/carlos/measurements/web/MeasurementData2Action.java");

    @Test
    @DisplayName("should set JSON content type before report macro writes")
    void shouldSetJsonContentType_beforeReportMacroWrites() throws IOException {
        String source = Files.readString(REPORT_MACRO_ACTION, StandardCharsets.UTF_8);

        int contentTypeIndex = source.indexOf("response.setContentType(JSON_CONTENT_TYPE);");
        int firstWriteIndex = source.indexOf("response.getWriter().write");

        assertThat(source).contains("application/json; charset=UTF-8");
        assertThat(contentTypeIndex).isNotNegative();
        assertThat(firstWriteIndex).isNotNegative();
        assertThat(contentTypeIndex).isLessThan(firstWriteIndex);
    }

    @Test
    @DisplayName("should use UTF-8 for measurement JSON bytes")
    void shouldUseUtf8_forMeasurementJsonBytes() throws IOException {
        String source = Files.readString(MEASUREMENT_DATA_ACTION, StandardCharsets.UTF_8);

        assertThat(source).contains("import java.nio.charset.StandardCharsets;");
        assertThat(source).contains("json.getBytes(StandardCharsets.UTF_8)");
        // Ensure we don't use an unsafe getBytes() call specifically in the response-writing path
        assertThat(source)
                .doesNotContainPattern("response\\.getOutputStream\\(\\)\\.write\\(.*\\.getBytes\\s*\\(\\s*\\)");
    }

    @Test
    @DisplayName("should set JSON content type for measurement write paths")
    void shouldSetJsonContentType_forMeasurementWritePaths() throws IOException {
        String source = Files.readString(MEASUREMENT_DATA_ACTION, StandardCharsets.UTF_8);

        assertThat(source).contains(
                "private static final String JSON_CONTENT_TYPE = \"application/json; charset=UTF-8\";");
        assertThat(source).contains("""
                response.setContentType(JSON_CONTENT_TYPE);
                response.getWriter().print(script);
                """);
        assertThat(source).contains("""
                response.setContentType(JSON_CONTENT_TYPE);

                objectMapper.writeValue(response.getWriter(), json);
                """);
        assertThat(source).contains("""
                private void writeJson(String json) throws IOException {
                    response.setContentType(JSON_CONTENT_TYPE);
                    response.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
                }
                """);
    }
}
