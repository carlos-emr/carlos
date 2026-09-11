/**
 * Copyright (c) 2026 CARLOS Contributors
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 * CARLOS EMR
 */
package io.github.carlos_emr.carlos.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Pins the output encoding of the views that render clinician prose whose WAF inspection the
 * packaged exclusions relax.
 *
 * <p>Each row is a legacy view that wrote a stored value straight into HTML. With the CRS
 * prose-scoring rules removed from the argument that feeds it, the application's own encoder
 * is the only thing between a stored payload and the next reader, so the raw form must not
 * come back.
 */
@Tag("unit")
@Tag("security")
class ProseSinkEncodingRegressionTest {

    static Stream<Arguments> sinks() {
        return Stream.of(
                Arguments.of("src/main/webapp/WEB-INF/jsp/casemgmt/showHistory.jsp",
                        "${note.note}", "${carlos:forHtmlContentWithBreaks(note.note)}"),
                Arguments.of("src/main/java/io/github/carlos_emr/carlos/casemgmt/web/CaseManagementEntry2Action.java",
                        ".replace(\"\\n\", \"<br/>\")", "request.setAttribute(\"history\", history)"),
                Arguments.of("src/main/webapp/WEB-INF/jsp/tickler/ticklerDemoMain.jsp",
                        "<%=t.getMessage()%>", "<%=SafeEncode.forHtmlContent(t.getMessage())%>"),
                Arguments.of("src/main/webapp/WEB-INF/jsp/tickler/ticklerDemoMain.jsp",
                        "<%=tc.getMessage()%>", "<%=SafeEncode.forHtmlContent(tc.getMessage())%>"),
                Arguments.of("src/main/webapp/WEB-INF/jsp/rx/DisplayRxRecord.jsp",
                        "<%= drug.getCustomName()%>", "<%= SafeEncode.forHtmlContent(drug.getCustomName())%>"),
                Arguments.of("src/main/webapp/WEB-INF/jsp/rx/DisplayRxRecord.jsp",
                        "<%= drug.getSpecial()%>", "<%= SafeEncode.forHtmlContent(drug.getSpecial())%>"),
                Arguments.of("src/main/webapp/WEB-INF/jsp/rx/DisplayRxRecord.jsp",
                        "<%= drug.getSpecialInstruction()%>",
                        "<%= SafeEncode.forHtmlContent(drug.getSpecialInstruction())%>"),
                Arguments.of("src/main/webapp/WEB-INF/jsp/rx/SelectReason.jsp",
                        "<%=drugReason.getComments() %>", "<%=SafeEncode.forHtmlContent(drugReason.getComments()) %>"),
                Arguments.of("src/main/webapp/WEB-INF/jsp/demographic/edit-form-clinical.jsp",
                        "rows=\"8\"><%=alert%>", "rows=\"8\"><%=SafeEncode.forHtmlContent(alert)%>"),
                Arguments.of("src/main/webapp/WEB-INF/jsp/demographic/edit-form-clinical.jsp",
                        "rows=\"8\"><%=notes%>", "rows=\"8\"><%=SafeEncode.forHtmlContent(notes)%>"));
    }

    @ParameterizedTest(name = "{0}: {2}")
    @MethodSource("sinks")
    @DisplayName("each prose sink should render through the null-safe encoder")
    void shouldEncodeStoredProse_atSink(String file, String rawForm, String encodedForm) throws IOException {
        String source = Files.readString(resolveProjectPath(Path.of(file)), StandardCharsets.UTF_8);

        assertThat(source)
                .as("%s renders through the encoder", file)
                .contains(encodedForm)
                .doesNotContain(rawForm);
    }

    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not resolve " + relativePath);
    }
}
